package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintMaterialServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintMaterialServiceTest {
    private PrintMaterialRepository repository;
    private PrintMaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PrintMaterialRepository.class);
        service = new PrintMaterialServiceImpl(repository);
    }

    @Test
    void activeListUsesRepositoryPaginationAndStableSort() {
        var material = material(4, true);
        when(repository.findByIsActiveTrue(any())).thenReturn(
                new PageImpl<>(List.of(material), PageRequest.of(1, 10), 11));

        var result = service.getActiveMaterials(null, 1, 10);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByIsActiveTrue(pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Order.asc("materialName"), Sort.Order.asc("materialId")),
                pageable.getValue().getSort());
        assertEquals(11, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(4, result.content().getFirst().materialId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void technologyFilterIsExactAndUnknownCodeCanReturnEmptyPage() {
        when(repository.findByIsActiveTrueAndTechnologyCode(eq("FDM"), any()))
                .thenReturn(new PageImpl<>(List.of(material(4, true)), PageRequest.of(0, 20), 1));
        when(repository.findByIsActiveTrueAndTechnologyCode(eq("UNKNOWN"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertEquals(1, service.getActiveMaterials("FDM", 0, 20).content().size());
        var empty = service.getActiveMaterials("UNKNOWN", 0, 20);
        assertTrue(empty.content().isEmpty());
        assertEquals(0, empty.totalElements());
        verify(repository).findByIsActiveTrueAndTechnologyCode(eq("FDM"), any());
        verify(repository).findByIsActiveTrueAndTechnologyCode(eq("UNKNOWN"), any());
        verify(repository, never()).findByIsActiveTrue(any());
    }

    @Test
    void emptyCatalogReturnsEmptyPage() {
        when(repository.findByIsActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var page = service.getActiveMaterials(null, 0, 20);
        assertTrue(page.content().isEmpty());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
    }

    @Test
    void invalidFiltersAndPaginationFailBeforeRepositoryAccess() {
        for (String code : List.of("", " ", " FDM", "FDM ", "x".repeat(31))) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ApiException.class,
                            () -> service.getActiveMaterials(code, 0, 20)).getStatus());
        }
        assertThrows(ApiException.class, () -> service.getActiveMaterials(null, -1, 20));
        assertThrows(ApiException.class, () -> service.getActiveMaterials(null, 0, 0));
        assertThrows(ApiException.class, () -> service.getActiveMaterials(null, 0, 51));
        verifyNoInteractions(repository);
    }

    @Test
    void activeDetailIsCustomerSafe() {
        when(repository.findById(4)).thenReturn(Optional.of(material(4, true)));
        var response = service.getActiveMaterialById(4);
        assertEquals(4, response.materialId());
        assertEquals("PLA", response.materialName());
        assertEquals("FDM", response.technologyCode());
        assertEquals("GRAM", response.unit());
        assertEquals(List.of("materialId", "materialName", "technologyCode", "unit"),
                java.util.Arrays.stream(response.getClass().getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void missingAndInactiveDetailHaveTheSameNotFoundResponse() {
        when(repository.findById(4)).thenReturn(Optional.empty());
        when(repository.findById(5)).thenReturn(Optional.of(material(5, false)));
        var missing = assertThrows(ApiException.class, () -> service.getActiveMaterialById(4));
        var inactive = assertThrows(ApiException.class, () -> service.getActiveMaterialById(5));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
        assertEquals(missing.getMessage(), inactive.getMessage());
    }

    @Test
    void invalidDetailIdAndReadOnlyContract() {
        assertThrows(ApiException.class, () -> service.getActiveMaterialById(null));
        assertThrows(ApiException.class, () -> service.getActiveMaterialById(0));
        assertThrows(ApiException.class, () -> service.getActiveMaterialById(-1));
        verifyNoInteractions(repository);
        assertTrue(PrintMaterialServiceImpl.class.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class).readOnly());
    }

    private static PrintMaterial material(int id, boolean active) {
        var material = new PrintMaterial("PLA", "FDM", "GRAM", new BigDecimal("350.00"), active);
        ReflectionTestUtils.setField(material, "materialId", id);
        return material;
    }
}
