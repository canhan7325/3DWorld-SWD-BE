package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.impl.AdminPrintMaterialServiceImpl;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPrintMaterialServiceTest {
    private PrintMaterialRepository repository;
    private AdminPrintMaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PrintMaterialRepository.class);
        service = new AdminPrintMaterialServiceImpl(repository);
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

    private static PrintMaterial material(int id, boolean active) {
        var material = new PrintMaterial("PLA", "FDM", "GRAM", new BigDecimal("350.00"), active);
        ReflectionTestUtils.setField(material, "materialId", id);
        return material;
    }
}
