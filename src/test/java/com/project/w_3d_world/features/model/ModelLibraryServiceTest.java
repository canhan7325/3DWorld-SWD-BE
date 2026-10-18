package com.project.w_3d_world.features.model;

import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.repository.*;
import com.project.w_3d_world.features.model.service.impl.ModelLibraryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelLibraryServiceTest {
    Model3DRepository models;
    ModelVersionRepository versions;
    ModelLibraryServiceImpl service;

    @BeforeEach
    void setup() {
        models = mock(Model3DRepository.class);
        versions = mock(ModelVersionRepository.class);
        service = new ModelLibraryServiceImpl(models, versions);
    }

    @Test
    void lib01_returnsOwnedModelsWithStableSortAndPagination() {
        Model3D first = model(11, "new", Instant.parse("2026-01-02T00:00:00Z"));
        Model3D second = model(10, "old", Instant.parse("2026-01-01T00:00:00Z"));
        var page = new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 4);
        when(models.findByOwnerUserIdAndDeletedAtIsNull(eq(7), any(Pageable.class))).thenReturn(page);
        var response = service.getMyModels(7, 1, 2);
        assertEquals(List.of(11, 10), response.content().stream().map(x -> x.modelId()).toList());
        assertEquals(1, response.page()); assertEquals(2, response.size());
        assertEquals(4, response.totalElements()); assertEquals(2, response.totalPages());
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(models).findByOwnerUserIdAndDeletedAtIsNull(eq(7), captor.capture());
        var pageable = captor.getValue();
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "modelId")), pageable.getSort());
        assertEquals(1, pageable.getPageNumber()); assertEquals(2, pageable.getPageSize());
        verifyNoMoreInteractions(models); verifyNoInteractions(versions);
    }

    @Test
    void lib02_emptyLibraryReturnsEmptyPage() {
        when(models.findByOwnerUserIdAndDeletedAtIsNull(eq(7), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var response = service.getMyModels(7, 0, 20);
        assertTrue(response.content().isEmpty()); assertEquals(0, response.totalElements()); assertEquals(0, response.totalPages());
    }

    @Test
    void lib07_lib08_invalidPageAndSizeRejectedBeforeQuery() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getMyModels(7, -1, 20)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getMyModels(7, 0, 0)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getMyModels(7, 0, 51)).getStatus());
        verifyNoInteractions(models, versions);
    }

    @Test
    void lib17_invalidUserRejected() {
        for (Integer id : Arrays.asList(null, 0, -1)) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getMyModels(id, 0, 20)).getStatus());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getModelDetails(id, 1)).getStatus());
        }
        verifyNoInteractions(models, versions);
    }

    @Test
    void lib09_lib13_latestVersionUsesOneRowPage() {
        Model3D model = model(11, "owned", Instant.now());
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7)).thenReturn(Optional.of(model));
        ModelVersion latest = version(22, 11, 3, ModelSourceType.EDITOR_SAVE);
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(11), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(latest), PageRequest.of(0, 1), 3));
        var response = service.getModelDetails(7, 11);
        assertNotNull(response.latestVersion()); assertEquals(22, response.latestVersion().modelVersionId());
        assertEquals(3, response.latestVersion().versionNumber()); assertEquals(ModelSourceType.EDITOR_SAVE, response.latestVersion().sourceType());
        verify(versions).findByModelIdOrderByVersionNumberDesc(eq(11), argThat(p -> p.getPageSize() == 1 && p.getPageNumber() == 0));
    }

    @Test
    void lib15_modelWithoutVersionHasNullableLatestVersion() {
        Model3D model = model(11, "owned", Instant.now());
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(11), any())).thenReturn(Page.empty());
        assertNull(service.getModelDetails(7, 11).latestVersion());
    }

    @Test
    void lib10_lib11_lib12_missingForeignOrDeletedModelIsSameNotFoundQueryResult() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(anyInt(), anyInt())).thenReturn(Optional.empty());
        var missing = assertThrows(ApiException.class, () -> service.getModelDetails(7, 99));
        var foreign = assertThrows(ApiException.class, () -> service.getModelDetails(8, 99));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus()); assertEquals(missing.getStatus(), foreign.getStatus());
        assertEquals("Model was not found.", missing.getMessage()); assertEquals(missing.getMessage(), foreign.getMessage());
        verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(99, 7);
        verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(99, 8);
        verifyNoMoreInteractions(models);
        verifyNoInteractions(versions);
    }

    @Test
    void lib16_dtoDoesNotExposeOwnerOrStorageFields() {
        Model3D model = model(11, "owned", Instant.now());
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(11), any())).thenReturn(Page.empty());
        var details = service.getModelDetails(7, 11);
        assertEquals(Set.of("modelId", "modelName", "description", "modelType", "originType", "visibility", "latestVersion", "createdAt", "updatedAt"),
                Arrays.stream(details.getClass().getRecordComponents()).map(x -> x.getName()).collect(java.util.stream.Collectors.toSet()));
        verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7);
    }

    @Test
    void lib18_readOnlyServiceNeverCallsSaveDeleteOrStorage() {
        when(models.findByOwnerUserIdAndDeletedAtIsNull(eq(7), any())).thenReturn(Page.empty());
        service.getMyModels(7, 0, 20);
        verify(models, never()).save(any()); verify(models, never()).delete(any()); verifyNoInteractions(versions);
        verify(models).findByOwnerUserIdAndDeletedAtIsNull(eq(7), any()); verifyNoMoreInteractions(models);
        assertTrue(ModelLibraryServiceImpl.class.getAnnotation(Transactional.class).readOnly());
        assertEquals(Set.of(Model3DRepository.class, ModelVersionRepository.class),
                Arrays.stream(ModelLibraryServiceImpl.class.getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .map(java.lang.reflect.Field::getType).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void lib14_versionOneSummaryAndOwnedPublicModel() {
        Model3D model = model(11, "owned", Instant.now());
        ReflectionTestUtils.setField(model, "visibility", "PUBLIC");
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(11), any()))
                .thenReturn(new PageImpl<>(List.of(version(21, 11, 1, ModelSourceType.UPLOAD))));
        var detail = service.getModelDetails(7, 11);
        assertEquals("PUBLIC", detail.visibility()); assertEquals(1, detail.latestVersion().versionNumber());
        assertEquals(ModelSourceType.UPLOAD, detail.latestVersion().sourceType());
        verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(11, 7);
        verifyNoMoreInteractions(models);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-private", "foreign-public", "deleted"})
    void lib03_lib04_lib11_lib12_filteredRowsCannotBeAccessed(String scenario) {
        // Stub the existing repository contract, not its SQL implementation. An unrestricted lookup
        // would return the forbidden row; the service must never make that lookup.
        Model3D forbidden = model(99, scenario, Instant.now());
        if (scenario.startsWith("foreign")) ReflectionTestUtils.setField(forbidden, "ownerUserId", 8);
        if (scenario.endsWith("public")) ReflectionTestUtils.setField(forbidden, "visibility", "PUBLIC");
        if (scenario.equals("deleted")) ReflectionTestUtils.setField(forbidden, "deletedAt", Instant.now());
        when(models.findById(99)).thenReturn(Optional.of(forbidden));
        when(models.findByOwnerUserIdAndDeletedAtIsNull(eq(7), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(99, 7)).thenReturn(Optional.empty());
        assertTrue(service.getMyModels(7, 0, 20).content().isEmpty());
        var failure = assertThrows(ApiException.class, () -> service.getModelDetails(7, 99));
        assertEquals(HttpStatus.NOT_FOUND, failure.getStatus()); assertEquals("Model was not found.", failure.getMessage());
        verify(models, never()).findById(any()); verifyNoInteractions(versions);
    }

    @Test
    void invalidModelIdsAre400WithoutQueries() {
        for (Integer id : Arrays.asList(null, 0, -1)) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.getModelDetails(7, id)).getStatus());
        }
        verifyNoInteractions(models, versions);
    }

    private static Model3D model(int id, String name, Instant created) {
        Model3D model = new Model3D(7, name, "description", ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE");
        ReflectionTestUtils.setField(model, "modelId", id); ReflectionTestUtils.setField(model, "createdAt", created);
        return model;
    }

    private static ModelVersion version(int id, int modelId, int number, ModelSourceType source) {
        ModelVersion version = new ModelVersion(modelId, null, null, 7, number, source, null);
        ReflectionTestUtils.setField(version, "modelVersionId", id); return version;
    }
}
