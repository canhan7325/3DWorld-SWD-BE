package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.impl.AdminPrintMaterialServiceImpl;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintMaterialRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialCostRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialStatusRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPrintMaterialServiceTest {
    private PrintMaterialRepository repository;
    private AuditLogRepository auditLogs;
    private EntityManager entityManager;
    private Query advisoryQuery;
    private AdminPrintMaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PrintMaterialRepository.class);
        auditLogs = mock(AuditLogRepository.class);
        entityManager = mock(EntityManager.class);
        advisoryQuery = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)"))
                .thenReturn(advisoryQuery);
        when(advisoryQuery.setParameter(anyInt(), anyInt())).thenReturn(advisoryQuery);
        service = new AdminPrintMaterialServiceImpl(repository,
                auditLogs, entityManager, JsonMapper.builder().build());
    }

    @Test
    void dispatchesAllFourFilterCombinationsToRepository() {
        var page = new PageImpl<>(List.of(material(1, true)), PageRequest.of(0, 20), 1);
        when(repository.findAll(any(Pageable.class))).thenReturn(page);
        when(repository.findByTechnologyCode(anyString(), any(Pageable.class))).thenReturn(page);
        when(repository.findByIsActive(anyBoolean(), any(Pageable.class))).thenReturn(page);
        when(repository.findByTechnologyCodeAndIsActive(anyString(), anyBoolean(), any(Pageable.class))).thenReturn(page);

        service.getMaterials(null, null, 0, 20);
        service.getMaterials("FDM", null, 0, 20);
        service.getMaterials(null, true, 0, 20);
        service.getMaterials("FDM", false, 0, 20);

        verify(repository).findAll(any(Pageable.class));
        verify(repository).findByTechnologyCode(eq("FDM"), any(Pageable.class));
        verify(repository).findByIsActive(eq(true), any(Pageable.class));
        verify(repository).findByTechnologyCodeAndIsActive(eq("FDM"), eq(false), any(Pageable.class));
    }

    @Test
    void usesStableDatabaseSortAndPreservesAdminFields() {
        when(repository.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> material(index + 3, false)).toList(),
                        PageRequest.of(2, 10), 30));
        var result = service.getMaterials(null, null, 2, 10);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertEquals(Sort.by(Sort.Order.asc("materialName"), Sort.Order.asc("materialId")), pageable.getValue().getSort());
        assertEquals(2, result.page()); assertEquals(10, result.size()); assertEquals(30, result.totalElements());
        assertEquals(new BigDecimal("350.00"), result.content().getFirst().estimatedUnitCost());
        assertFalse(result.content().getFirst().isActive());
    }

    @Test
    void emptyResultsHaveCorrectMetadata() {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var result = service.getMaterials(null, null, 0, 20);
        assertTrue(result.content().isEmpty()); assertEquals(0, result.totalElements()); assertEquals(0, result.totalPages());
    }

    @Test
    void invalidFiltersAndPaginationRejectBeforeRepositoryAccess() {
        for (String code : List.of("", " ", " FDM", "FDM ", "x".repeat(31))) {
            assertThrows(ApiException.class, () -> service.getMaterials(code, null, 0, 20));
        }
        assertThrows(ApiException.class, () -> service.getMaterials(null, null, -1, 20));
        assertThrows(ApiException.class, () -> service.getMaterials(null, null, 0, 0));
        assertThrows(ApiException.class, () -> service.getMaterials(null, null, 0, 51));
        verifyNoInteractions(repository);
    }

    @Test
    void isReadOnlyAndNullsArePreserved() {
        var material = material(1, true);
        ReflectionTestUtils.setField(material, "unit", null);
        ReflectionTestUtils.setField(material, "estimatedUnitCost", null);
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(material)));
        var result = service.getMaterials(null, null, 0, 20);
        assertNull(result.content().getFirst().unit()); assertNull(result.content().getFirst().estimatedUnitCost());
        verify(repository, never()).save(any()); verify(repository, never()).delete(any()); verify(repository, never()).flush();
    }

    @Test
    void createCanonicalMaterialLocksBeforeDuplicateCheckAndAuditsGeneratedId() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PrintMaterial material = call.getArgument(0);
            ReflectionTestUtils.setField(material, "materialId", 42);
            return material;
        });
        var response = service.create(7,
                new CreatePrintMaterialRequest(" pla ", "fdm", "gram", new BigDecimal("350.00")));
        assertEquals(42, response.materialId());
        assertEquals("PLA", response.materialName());
        assertEquals("FDM", response.technologyCode());
        assertEquals("GRAM", response.unit());
        assertTrue(response.isActive());
        var order = inOrder(entityManager, advisoryQuery, repository, auditLogs);
        order.verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)");
        order.verify(advisoryQuery).getSingleResult();
        order.verify(repository).existsActiveIdentity("pla", "FDM");
        order.verify(repository).saveAndFlush(any(PrintMaterial.class));
        var audit = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        order.verify(auditLogs).saveAndFlush(audit.capture());
        assertEquals(42, audit.getValue().getEntityId());
        assertEquals(7, audit.getValue().getActorUserId());
        assertEquals("MATERIAL_CREATED", audit.getValue().getActionType());
        assertTrue(audit.getValue().getMetadata().contains("\"estimatedUnitCost\":350.00"));
    }

    @Test
    void approvedPairsAndNullableCostCreateSuccessfully() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PrintMaterial material = call.getArgument(0);
            ReflectionTestUtils.setField(material, "materialId", 42);
            return material;
        });
        for (String name : List.of("PETG", "ABS")) {
            assertDoesNotThrow(() -> service.create(7,
                    new CreatePrintMaterialRequest(name, "FDM", "GRAM", null)));
        }
        assertDoesNotThrow(() -> service.create(7,
                new CreatePrintMaterialRequest("Standard Resin", "SLA", "ML", null)));
        verify(repository, times(3)).saveAndFlush(any(PrintMaterial.class));
    }

    @Test
    void invalidCatalogOrCostIsRejectedBeforeAnyWrite() {
        for (CreatePrintMaterialRequest request : List.of(
                new CreatePrintMaterialRequest("PLA", "SLA", "ML", null),
                new CreatePrintMaterialRequest("Standard Resin", "FDM", "GRAM", null),
                new CreatePrintMaterialRequest("PLA", "DLP", "GRAM", null),
                new CreatePrintMaterialRequest("", "FDM", "GRAM", null),
                new CreatePrintMaterialRequest("PLA", "FDM", "ML", null),
                new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", new BigDecimal("-1")),
                new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", new BigDecimal("10.123")),
                new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", new BigDecimal("12345678901234567.00")))) {
            assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                    () -> service.create(7, request));
        }
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogs);
    }

    @Test
    void zeroWithNegativeScaleStillFitsNumericColumn() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PrintMaterial material = call.getArgument(0);
            ReflectionTestUtils.setField(material, "materialId", 42);
            return material;
        });
        assertDoesNotThrow(() -> service.create(7,
                new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", new BigDecimal("0E+18"))));
    }

    @Test
    void activeDuplicateReturnsConflictBeforePersistence() {
        when(repository.existsActiveIdentity("pla", "FDM")).thenReturn(true);
        var error = assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> service.create(7, new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", null)));
        assertEquals(409, error.getStatus().value());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogs);
    }

    @Test
    void costUpdateUsesRowLockAndAuditsOnlyNumericalChange() {
        var material = material(42, true);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(material));
        service.updateCost(7, 42, new UpdatePrintMaterialCostRequest(new BigDecimal("350.0")));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogs);

        var response = service.updateCost(7, 42, new UpdatePrintMaterialCostRequest(null));
        assertNull(response.estimatedUnitCost());
        var order = inOrder(repository, auditLogs);
        order.verify(repository, times(2)).findByIdForUpdate(42);
        order.verify(repository).saveAndFlush(material);
        var audit = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        order.verify(auditLogs).saveAndFlush(audit.capture());
        assertEquals("MATERIAL_COST_UPDATED", audit.getValue().getActionType());
        assertTrue(audit.getValue().getMetadata().contains("\"estimatedUnitCost\":null"));
    }

    @Test
    void reactivationUsesSameAdvisoryLockBeforeRowAndDuplicateCheck() {
        var material = material(42, false);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(material));
        var response = service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(true));
        assertTrue(response.isActive());
        var order = inOrder(entityManager, advisoryQuery, repository, auditLogs);
        order.verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)");
        order.verify(advisoryQuery).getSingleResult();
        order.verify(repository).findByIdForUpdate(42);
        order.verify(repository).existsOtherActiveIdentity(42, "pla", "FDM");
        order.verify(repository).saveAndFlush(material);
        order.verify(auditLogs).saveAndFlush(any(AuditLog.class));
    }

    @Test
    void duplicateReactivationConflictsAndStatusNoOpDoesNotAudit() {
        var material = material(42, false);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(material));
        service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(false));
        verify(repository, never()).saveAndFlush(any());
        when(repository.existsOtherActiveIdentity(42, "pla", "FDM")).thenReturn(true);
        var error = assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(true)));
        assertEquals(409, error.getStatus().value());
        assertFalse(material.getIsActive());
        verifyNoInteractions(auditLogs);
    }

    @Test
    void deactivationChangesStatusAndAuditsOnlyOnce() {
        var material = material(42, true);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(material));
        assertFalse(service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(false)).isActive());
        assertFalse(service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(false)).isActive());
        verify(repository).saveAndFlush(material);
        var audit = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).saveAndFlush(audit.capture());
        assertEquals("MATERIAL_DEACTIVATED", audit.getValue().getActionType());
        assertTrue(audit.getValue().getMetadata().contains("\"isActive\":false"));
        verify(repository, never()).delete(any());
    }

    @Test
    void legacyIdentityCannotBeSilentlyRewrittenOnReactivation() {
        var material = material(42, false);
        ReflectionTestUtils.setField(material, "unit", null);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(material));
        assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> service.updateStatus(7, 42, new UpdatePrintMaterialStatusRequest(true)));
        assertFalse(material.getIsActive());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogs);
    }

    @Test
    void missingMaterialAndInvalidStatusAreControlled() {
        assertEquals(404, assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> service.updateCost(7, 99, new UpdatePrintMaterialCostRequest(null)))
                .getStatus().value());
        assertEquals(400, assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> service.updateStatus(7, 99, new UpdatePrintMaterialStatusRequest(null)))
                .getStatus().value());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogs);
    }

    @Test
    void auditFailureIsPropagated() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PrintMaterial material = call.getArgument(0);
            ReflectionTestUtils.setField(material, "materialId", 42);
            return material;
        });
        doThrow(new IllegalStateException("audit unavailable")).when(auditLogs).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.create(7,
                new CreatePrintMaterialRequest("PLA", "FDM", "GRAM", null)));
    }

    private static PrintMaterial material(int id, boolean active) {
        var material = new PrintMaterial("PLA", "FDM", "GRAM", new BigDecimal("350.00"), active);
        ReflectionTestUtils.setField(material, "materialId", id);
        return material;
    }
}
