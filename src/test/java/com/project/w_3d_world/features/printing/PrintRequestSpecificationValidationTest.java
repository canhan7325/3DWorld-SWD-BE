package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationItemRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationRequest;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestSpecificationService;
import com.project.w_3d_world.features.printing.validation.PrintRequestCreateValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintingConfigurationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestSpecificationValidationTest {
    @Test
    void candidateTechnologyKeepsExistingFdmAndSlaUnits() {
        assertEquals("GRAM", PrintingConfigurationPolicy.requireUnit("FDM", "GRAM"));
        assertEquals("ML", PrintingConfigurationPolicy.requireUnit("SLA", "ML"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireUnit("SLA", "GRAM"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireUnit("RESIN", "ML"));
    }

    @Test
    void candidateColorAndQualityMustMatchMaterialAndTechnology() {
        var color = new PrintingConfigurationPolicy.ColorOption(3, "RED", "#aa0000",
                true, true, new BigDecimal("100"), 1);
        PrintingConfigurationPolicy.requireColorForMaterial(3, color);
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireColorForMaterial(4, color));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireColorForMaterial(3,
                        new PrintingConfigurationPolicy.ColorOption(3, "RED", "#aa0000",
                                false, true, BigDecimal.ZERO, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireColorForMaterial(3,
                        new PrintingConfigurationPolicy.ColorOption(3, "RED", "#aa0000",
                                true, false, BigDecimal.ZERO, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new PrintingConfigurationPolicy.ColorOption(3, "RED", "bad",
                        true, true, BigDecimal.ZERO, 1));
        var quality = new PrintingConfigurationPolicy.QualityOption("FDM", "FINE",
                new BigDecimal("0.12"), new BigDecimal("500"), true, Set.of(3));
        PrintingConfigurationPolicy.requireQualityForMaterial(3, "FDM", quality);
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireQualityForMaterial(4, "FDM", quality));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireQualityForMaterial(3, "SLA", quality));
        assertThrows(IllegalArgumentException.class,
                () -> new PrintingConfigurationPolicy.QualityOption("FDM", "FINE",
                        new BigDecimal("0.12"), new BigDecimal("-1"), true, Set.of(3)));
    }

    @Test
    void candidateFdmAndResinOptionsStayTechnologySpecific() {
        var fdm = new PrintingConfigurationPolicy.FdmLimits(10, 80,
                Set.of("NONE", "AUTO"), Set.of("STANDARD"), Set.of("BRIM"));
        var choice = new PrintingConfigurationPolicy.FdmSelection(20, "AUTO", "STANDARD", "BRIM");
        PrintingConfigurationPolicy.requireFdmOptions("FDM", choice, fdm);
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireFdmOptions("SLA", choice, fdm));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireFdmOptions("FDM",
                        new PrintingConfigurationPolicy.FdmSelection(90, "AUTO", "STANDARD", "BRIM"), fdm));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireFdmOptions("FDM",
                        new PrintingConfigurationPolicy.FdmSelection(20, "UNKNOWN", "STANDARD", "BRIM"), fdm));

        var resin = new PrintingConfigurationPolicy.ResinLimits(true, new BigDecimal("1.0"),
                new BigDecimal("3.0"), true, Set.of("AUTO"));
        var hollow = new PrintingConfigurationPolicy.ResinSelection(true,
                new BigDecimal("1.5"), true, "AUTO");
        PrintingConfigurationPolicy.requireResinOptions("SLA", hollow, resin);
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireResinOptions("FDM", hollow, resin));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireResinOptions("SLA",
                        new PrintingConfigurationPolicy.ResinSelection(true,
                                new BigDecimal("0.5"), true, "AUTO"), resin));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireResinOptions("SLA",
                        new PrintingConfigurationPolicy.ResinSelection(true,
                                new BigDecimal("1.5"), false, "AUTO"), resin));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requireResinOptions("SLA",
                        new PrintingConfigurationPolicy.ResinSelection(false,
                                new BigDecimal("1.5"), false, "AUTO"), resin));
    }

    @Test
    void candidateMandatoryPostProcessingCannotBeDropped() {
        var required = new PrintingConfigurationPolicy.PostProcessOption("CURE", "SLA",
                true, true, new BigDecimal("250"));
        var optional = new PrintingConfigurationPolicy.PostProcessOption("PAINT", "ALL",
                true, false, new BigDecimal("500"));
        var inactive = new PrintingConfigurationPolicy.PostProcessOption("POLISH", "SLA",
                false, false, BigDecimal.ZERO);
        PrintingConfigurationPolicy.requirePostProcessing(List.of(required, optional, inactive),
                Set.of("CURE", "PAINT"), "SLA");
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requirePostProcessing(
                        List.of(required, optional), Set.of("PAINT"), "SLA"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintingConfigurationPolicy.requirePostProcessing(
                        List.of(required, optional, inactive), Set.of("CURE", "POLISH"), "SLA"));
    }

    @Test
    void sharedSpecificationRulesAcceptOptionalFieldsAndBoundaryValues() {
        var item = new CreatePrintRequestItemRequest(12, 3, "Black", "STANDARD", 100,
                new BigDecimal("9999999999.99"), null, new BigDecimal("0.01"), 1, null);
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateSpecification(null, List.of(item)));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateInfill("FDM", 0));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateInfill("SLA", null));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateSpecification(null,
                List.of(item, item(12, 4, 2, null, null, null, null))));
    }

    @Test
    void allConfigurationsMustReferToOneModelVersion() {
        var first = item(12, 3, 1, null, null, null, null);
        var second = item(13, 3, 1, null, null, null, null);
        ApiException error = assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validateSpecification(null, List.of(first, second)));
        assertEquals("All Print Request items must use the same Model Version.", error.getMessage());
    }

    @Test
    void sharedSpecificationRulesRejectMissingOrInvalidItems() {
        var valid = item(12, 3, 1, null, null, null, null);
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(null, null));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(null, List.of()));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(
                null, java.util.Arrays.asList(valid, null)));
        for (var invalid : List.of(
                item(null, 3, 1, null, null, null, null),
                item(12, 0, 1, null, null, null, null),
                item(12, 3, 0, null, null, null, null),
                item(12, 3, 1, -1, null, null, null),
                item(12, 3, 1, 101, null, null, null),
                item(12, 3, 1, null, BigDecimal.ZERO, null, null),
                item(12, 3, 1, null, new BigDecimal("1.001"), null, null),
                item(12, 3, 1, null, new BigDecimal("10000000000.00"), null, null),
                item(12, 3, 1, null, null, "x".repeat(101), null),
                item(12, 3, 1, null, null, null, "x".repeat(41)))) {
            assertThrows(ApiException.class,
                    () -> PrintRequestCreateValidation.validateSpecification(null, List.of(invalid)));
        }
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validateSpecification("x".repeat(10_001), List.of(valid)));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateInfill("SLA", 20));
    }

    @Test
    void createStillRequiresExplicitRightsAcceptance() {
        var items = List.of(item(12, 3, 1, null, null, null, null));
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validate(new CreatePrintRequestRequest(null, false, items)));
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validate(new CreatePrintRequestRequest(null, null, items)));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validate(
                new CreatePrintRequestRequest(null, true, items)));
    }

    @Test
    void initialSnapshotAndUpdateKeepFileVersionIndependentAndHistoryImmutable() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var versions = mock(PrintRequestSpecVersionRepository.class);
        var materials = mock(PrintMaterialRepository.class);
        var mapper = JsonMapper.builder().build();
        var service = new PrintRequestSpecificationService(requests, items, versions,
                materials, mapper, mock(PrintRequestReviewPolicy.class));
        var draft = new PrintRequest(7, "Prototype");
        ReflectionTestUtils.setField(draft, "printRequestId", 44);
        draft.useFileVersion(1);
        var item = new PrintRequestItem(44, 12, 3, "Black", "STANDARD", 20,
                new BigDecimal("12.50"), null, null, 1, "Original");
        ReflectionTestUtils.setField(item, "printRequestItemId", 45);
        var stored = new ArrayList<PrintRequestSpecVersion>();
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(draft));
        when(requests.findByPrintRequestIdAndCustomerUserId(44, 7)).thenReturn(Optional.of(draft));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequestSpecVersion value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "specVersionId", 100 + stored.size());
            stored.add(value);
            return value;
        });
        when(versions.findByPrintRequestIdAndVersion(44, 1)).thenAnswer(call ->
                stored.isEmpty() ? Optional.empty() : Optional.of(stored.getFirst()));
        when(versions.findByPrintRequestIdOrderByVersionDesc(44)).thenAnswer(call ->
                stored.reversed());
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(
                new PrintMaterial("PLA", "FDM", "GRAM", null, true)));

        service.recordInitial(7, 44);
        assertEquals(1, draft.getCurrentSpecVersion());
        assertEquals(1, draft.getCurrentFileVersion());
        assertNull(draft.getSubmittedAt());
        assertEquals(1, stored.getFirst().getVersion());
        assertEquals("Black", mapper.readTree(stored.getFirst().getSpecificationData())
                .get("items").get(0).get("color").asText());
        assertFalse(stored.getFirst().getSpecificationData().contains("modelVersionId"));
        String oldSnapshot = stored.getFirst().getSpecificationData();
        Instant oldCreatedAt = stored.getFirst().getCreatedAt();

        ReflectionTestUtils.setField(draft, "previewConfirmed", true);
        var updated = service.update(7, 44, new UpdatePrintRequestSpecificationRequest(1,
                List.of(new UpdatePrintRequestSpecificationItemRequest(45, 3, "White",
                        "STANDARD", 30, new BigDecimal("15.00"), null, null, 2, "Updated"))));
        assertEquals(2, updated.version());
        assertTrue(updated.isCurrent());
        assertEquals("White", updated.specificationData().get("items").get(0)
                .get("color").asText());
        assertEquals(2, draft.getCurrentSpecVersion());
        assertEquals(1, draft.getCurrentFileVersion());
        assertFalse(draft.isPreviewConfirmed());
        assertEquals(12, item.getModelVersionId());
        assertEquals("White", item.getColor());
        assertEquals(2, item.getQuantity());
        assertEquals(oldSnapshot, stored.getFirst().getSpecificationData());
        assertEquals(oldCreatedAt, stored.getFirst().getCreatedAt());
        assertEquals(List.of(2, 1), service.history(7, 44).stream()
                .map(value -> value.version()).toList());
        assertFalse(service.history(7, 44).get(1).isCurrent());
        verify(items).saveAndFlush(item);
        verify(requests, times(2)).saveAndFlush(draft);
    }

    @Test
    void updateRejectsLegacyStaleAndLockedWithoutWriting() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var versions = mock(PrintRequestSpecVersionRepository.class);
        var materials = mock(PrintMaterialRepository.class);
        var service = new PrintRequestSpecificationService(requests, items, versions,
                materials, JsonMapper.builder().build(), mock(PrintRequestReviewPolicy.class));
        var input = new UpdatePrintRequestSpecificationRequest(1,
                List.of(new UpdatePrintRequestSpecificationItemRequest(45, 3, "White",
                        "STANDARD", null, null, null, null, 1, null)));
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.update(8, 44, input)).getStatus().value());
        var draft = new PrintRequest(7, null);
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(draft));
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.update(7, 44, input)).getStatus().value());
        draft.useSpecVersion(1);
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.update(7, 44,
                        new UpdatePrintRequestSpecificationRequest(2, input.items())))
                .getStatus().value());
        ReflectionTestUtils.setField(draft, "lockedAt", Instant.now());
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.update(7, 44, input)).getStatus().value());
        verifyNoInteractions(items, materials);
        verify(versions, never()).saveAndFlush(any());
    }

    @Test
    void staffAuthorizedSpecCorrectionPreservesFileAndRequiresFreshReview() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var versions = mock(PrintRequestSpecVersionRepository.class);
        var materials = mock(PrintMaterialRepository.class);
        var policy = mock(PrintRequestReviewPolicy.class);
        var service = new PrintRequestSpecificationService(requests, items, versions,
                materials, JsonMapper.builder().build(), policy);
        var request = new PrintRequest(7, "Prototype");
        ReflectionTestUtils.setField(request, "printRequestId", 44);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        var item = new PrintRequestItem(44, 12, 3, "Black", "STANDARD", 20,
                null, null, null, 1, null);
        ReflectionTestUtils.setField(item, "printRequestItemId", 45);
        var input = new UpdatePrintRequestSpecificationRequest(1,
                List.of(new UpdatePrintRequestSpecificationItemRequest(45, 3, "White",
                        "STANDARD", 30, null, null, null, 1, "Corrected")));
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(request));
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.update(7, 44, input)).getStatus().value());
        request.requireRevision();
        when(policy.requirePendingCorrection(request)).thenReturn(
                new PrintRequestReviewPolicy.Decision("CORRECTION_REQUESTED", 1, 1));
        when(versions.findByPrintRequestIdAndVersion(44, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(44, 1, "{\"items\":[]}", 7)));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(
                new PrintMaterial("PLA", "FDM", "GRAM", null, true)));
        when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals(2, service.update(7, 44, input).version());
        assertEquals(1, request.getCurrentFileVersion());
        assertEquals(2, request.getCurrentSpecVersion());
        assertFalse(request.isPreviewConfirmed());
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.update(7, 44, new UpdatePrintRequestSpecificationRequest(2,
                        input.items()))).getStatus().value());
        verify(versions, times(1)).saveAndFlush(any());
    }

    @Test
    void incompatibleMaterialAndInvalidDimensionsDoNotAdvanceSpecPointer() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var versions = mock(PrintRequestSpecVersionRepository.class);
        var materials = mock(PrintMaterialRepository.class);
        var service = new PrintRequestSpecificationService(requests, items, versions,
                materials, JsonMapper.builder().build(), mock(PrintRequestReviewPolicy.class));
        var draft = new PrintRequest(7, null);
        draft.useSpecVersion(1);
        var item = new PrintRequestItem(44, 12, 3, null, null, null,
                null, null, null, 1, null);
        ReflectionTestUtils.setField(item, "printRequestItemId", 45);
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(draft));
        when(versions.findByPrintRequestIdAndVersion(44, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(44, 1, "{\"items\":[]}", 7)));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(
                new PrintMaterial("RESIN", "SLA", "ML", null, true)));
        var invalidInfill = new UpdatePrintRequestSpecificationRequest(1,
                List.of(new UpdatePrintRequestSpecificationItemRequest(45, 3, null,
                        null, 20, null, null, null, 1, null)));
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.update(7, 44, invalidInfill)).getStatus().value());
        var invalidDimension = new UpdatePrintRequestSpecificationRequest(1,
                List.of(new UpdatePrintRequestSpecificationItemRequest(45, 3, null,
                        null, null, BigDecimal.ZERO, null, null, 1, null)));
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.update(7, 44, invalidDimension)).getStatus().value());
        assertEquals(1, draft.getCurrentSpecVersion());
        verify(items, never()).saveAndFlush(any());
        verify(versions, never()).saveAndFlush(any());
    }

    private static CreatePrintRequestItemRequest item(Integer versionId, Integer materialId,
            Integer quantity, Integer infill, BigDecimal width, String color, String quality) {
        return new CreatePrintRequestItemRequest(versionId, materialId, color, quality,
                infill, width, null, null, quantity, null);
    }
}
