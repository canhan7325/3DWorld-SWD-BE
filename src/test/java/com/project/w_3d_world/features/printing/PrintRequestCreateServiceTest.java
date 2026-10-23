package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.enums.ModelType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestCreateServiceTest {
    private PrintRequestRepository requests;
    private PrintRequestItemRepository items;
    private PrintMaterialRepository materials;
    private ModelVersionRepository versions;
    private Model3DRepository models;
    private ModelFileRepository files;
    private AuditLogRepository audits;
    private PrintRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        requests = mock(PrintRequestRepository.class);
        items = mock(PrintRequestItemRepository.class);
        materials = mock(PrintMaterialRepository.class);
        versions = mock(ModelVersionRepository.class);
        models = mock(Model3DRepository.class);
        files = mock(ModelFileRepository.class);
        audits = mock(AuditLogRepository.class);
        service = new PrintRequestServiceImpl(requests, items, materials, versions,
                models, files, audits, JsonMapper.builder().build());
        when(versions.findById(12)).thenReturn(Optional.of(version()));
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(5, 7))
                .thenReturn(Optional.of(model()));
        when(files.findPrimaryByModelVersionId(12)).thenReturn(Optional.of(file()));
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(material("PLA", "FDM", "GRAM", true)));
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequest value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "printRequestId", 44);
            return value;
        });
        doAnswer(call -> {
            PrintRequestItem value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "printRequestItemId", 45);
            return value;
        }).when(items).saveAndFlush(any());
    }

    @Test
    void createsDraftItemsAndRightsAuditForAuthenticatedOwner() {
        var response = service.createDraft(7, request(item(20, null)));
        assertEquals(44, response.printRequestId());
        assertEquals("DRAFT", response.status());
        assertEquals(1, response.items().size());
        assertEquals(45, response.items().getFirst().printRequestItemId());
        assertEquals("PENDING", response.items().getFirst().productionStatus());
        assertEquals("MODEL_VERSION", response.items().getFirst().sourceType());
        assertFalse(response.toString().contains("storageKey"));
        assertFalse(response.toString().contains("estimatedUnitCost"));

        var parent = ArgumentCaptor.forClass(PrintRequest.class);
        verify(requests).saveAndFlush(parent.capture());
        assertEquals(7, parent.getValue().getCustomerUserId());
        assertEquals("DRAFT", parent.getValue().getStatus());
        var child = ArgumentCaptor.forClass(PrintRequestItem.class);
        verify(items).saveAndFlush(child.capture());
        assertEquals(44, child.getValue().getPrintRequestId());
        assertEquals(12, child.getValue().getModelVersionId());
        assertNull(child.getValue().getUploadedFileKey());
        assertEquals(3, child.getValue().getMaterialId());
        var audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).saveAndFlush(audit.capture());
        assertEquals(7, audit.getValue().getActorUserId());
        assertEquals("POD_RIGHTS_DECLARED", audit.getValue().getActionType());
        assertEquals("PRINT_REQUEST", audit.getValue().getEntityType());
        assertEquals(44, audit.getValue().getEntityId());
        assertTrue(audit.getValue().getMetadata().contains("POD_RIGHTS_V1"));
        assertTrue(audit.getValue().getMetadata().contains("modelVersionIds"));
        assertTrue(audit.getValue().getMetadata().contains("acceptedAt"));
        var order = inOrder(versions, models, files, materials, requests, items, audits);
        order.verify(versions).findById(12);
        order.verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(5, 7);
        order.verify(files).findPrimaryByModelVersionId(12);
        order.verify(materials).findByIdForUpdate(3);
        order.verify(requests).saveAndFlush(any());
        order.verify(items).saveAndFlush(any());
        order.verify(audits).saveAndFlush(any());
    }

    @Test
    void multipleItemsCanBeReadThroughExistingOwnerScopedDetail() {
        List<PrintRequestItem> persistedItems = new ArrayList<>();
        AtomicInteger nextId = new AtomicInteger(45);
        doAnswer(call -> {
            PrintRequestItem value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "printRequestItemId", nextId.getAndIncrement());
            persistedItems.add(value);
            return value;
        }).when(items).saveAndFlush(any());
        when(requests.findByPrintRequestIdAndCustomerUserId(44, 7))
                .thenAnswer(call -> Optional.of(requestsSnapshot()));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44))
                .thenAnswer(call -> List.copyOf(persistedItems));

        var draft = new CreatePrintRequestRequest("Prototype", true,
                List.of(item(20, null), item(30, new BigDecimal("15.00"))));
        var created = service.createDraft(7, draft);
        var retrieved = service.getMyRequestById(7, created.printRequestId());

        assertEquals(2, created.items().size());
        assertEquals(List.of(45, 46), retrieved.items().stream()
                .map(response -> response.printRequestItemId()).toList());
        assertEquals("DRAFT", retrieved.status());
        assertEquals(44, retrieved.printRequestId());
        verify(items, times(2)).saveAndFlush(any());
        verify(requests).findByPrintRequestIdAndCustomerUserId(44, 7);
    }

    @Test
    void missingForeignAndDeletedModelsRejectBeforeWrites() {
        when(versions.findById(12)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.createDraft(7, request(item(20, null)))).getStatus());
        when(versions.findById(12)).thenReturn(Optional.of(version()));
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(5, 7)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.createDraft(7, request(item(20, null)))).getStatus());
        verifyNoInteractions(requests, items, audits);
    }

    @Test
    void missingOrUnsupportedPrimaryFileRejects() {
        when(files.findPrimaryByModelVersionId(12)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        when(files.findPrimaryByModelVersionId(12)).thenReturn(Optional.of(
                new ModelFile(12, ModelFileRole.PREVIEW, "png", "key", "image/png", "hash", 20L, true)));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        verifyNoInteractions(requests, items, audits);
    }

    @Test
    void missingInactiveAndLegacyMaterialRejectBeforeWrites() {
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(material("PLA", "FDM", "GRAM", false)));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        when(materials.findByIdForUpdate(3)).thenReturn(Optional.of(material("PLA", "SLA", "ML", true)));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        verifyNoInteractions(requests, items, audits);
    }

    @Test
    void slaRejectsInfillAndAllowsNull() {
        when(materials.findByIdForUpdate(3))
                .thenReturn(Optional.of(material("Standard Resin", "SLA", "ML", true)));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, null))));
        assertDoesNotThrow(() -> service.createDraft(7, request(item(null, null))));
    }

    @Test
    void invalidInputAndRightsRejectBeforeRepositories() {
        assertThrows(ApiException.class, () -> service.createDraft(7,
                new CreatePrintRequestRequest(null, false, List.of(item(20, null)))));
        assertThrows(ApiException.class, () -> service.createDraft(7,
                new CreatePrintRequestRequest(null, true, List.of())));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(20, BigDecimal.ZERO))));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(item(101, null))));
        assertThrows(ApiException.class, () -> service.createDraft(7, request(
                new CreatePrintRequestItemRequest(12, 3, null, null, null, null, null, null, 0, null))));
        verifyNoInteractions(versions, models, files, materials, requests, items, audits);
    }

    @Test
    void persistenceAndAuditFailuresPropagate() {
        doThrow(new IllegalStateException("item failure")).when(items).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.createDraft(7, request(item(20, null))));
        reset(items);
        when(items.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequestItem value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "printRequestItemId", 45);
            return value;
        });
        when(audits.saveAndFlush(any())).thenThrow(new IllegalStateException("audit failure"));
        assertThrows(IllegalStateException.class, () -> service.createDraft(7, request(item(20, null))));
        assertTrue(PrintRequestServiceImpl.class.getAnnotation(Transactional.class).readOnly());
        try {
            assertFalse(PrintRequestServiceImpl.class.getMethod("createDraft", Integer.class,
                    CreatePrintRequestRequest.class).getAnnotation(Transactional.class).readOnly());
        } catch (NoSuchMethodException exception) {
            fail(exception);
        }
    }

    private static CreatePrintRequestRequest request(CreatePrintRequestItemRequest item) {
        return new CreatePrintRequestRequest("Prototype", true, List.of(item));
    }

    private static CreatePrintRequestItemRequest item(Integer infill, BigDecimal width) {
        return new CreatePrintRequestItemRequest(12, 3, "Black", "STANDARD", infill,
                width, null, null, 1, "Sample");
    }

    private static ModelVersion version() {
        return new ModelVersion(5, null, null, 7, 1, ModelSourceType.UPLOAD, null);
    }

    private static Model3D model() {
        return new Model3D(7, "Owned", null, ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE");
    }

    private static ModelFile file() {
        return new ModelFile(12, ModelFileRole.SOURCE_MODEL, "glb", "models/5/versions/12/files/key.glb",
                "model/gltf-binary", "hash", 100L, true);
    }

    private static PrintMaterial material(String name, String technology, String unit, boolean active) {
        return new PrintMaterial(name, technology, unit, new BigDecimal("100.00"), active);
    }

    private static PrintRequest requestsSnapshot() {
        PrintRequest value = new PrintRequest(7, "Prototype");
        ReflectionTestUtils.setField(value, "printRequestId", 44);
        return value;
    }
}
