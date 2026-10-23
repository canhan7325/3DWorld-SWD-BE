package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.response.*;
import com.project.w_3d_world.features.model.entity.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.repository.*;
import com.project.w_3d_world.features.model.service.impl.ModelVersionHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelVersionHistoryServiceTest {
    Model3DRepository models; ModelVersionRepository versions; ModelFileRepository files;
    ModelVersionHistoryServiceImpl service;

    @BeforeEach void setup() {
        models = mock(Model3DRepository.class); versions = mock(ModelVersionRepository.class); files = mock(ModelFileRepository.class);
        service = new ModelVersionHistoryServiceImpl(models, versions, files);
    }

    @Test void ver01_ver07_ver08_historyMapsPageAndUsesRepositoryOrdering() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        var v3 = version(103, 10, 3, ModelSourceType.EDITOR_SAVE);
        var v1 = version(101, 10, 1, ModelSourceType.UPLOAD);
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(10), any())).thenReturn(
                new PageImpl<>(List.of(v3, v1), PageRequest.of(0, 2), 2));
        var result = service.getModelVersions(7, 10, 0, 2);
        assertEquals(List.of(3, 1), result.content().stream().map(ModelVersionSummaryResponse::versionNumber).toList());
        assertEquals(2, result.totalElements()); assertEquals(1, result.totalPages());
        verify(versions).findByModelIdOrderByVersionNumberDesc(eq(10), argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 2));
        verifyNoInteractions(files);
    }

    @Test void ver03_emptyHistoryIs200Compatible() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(10), any())).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var result = service.getModelVersions(7, 10, 0, 20);
        assertTrue(result.content().isEmpty()); assertEquals(0, result.totalElements()); assertEquals(0, result.totalPages());
    }

    @Test void ver04_ver05_ver06_modelOwnershipFailsBeforeVersionQuery() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(anyInt(), anyInt())).thenReturn(Optional.empty());
        var first = assertThrows(ApiException.class, () -> service.getModelVersions(7, 10, 0, 20));
        var second = assertThrows(ApiException.class, () -> service.getModelVersionDetails(7, 10, 101));
        assertEquals(404, first.getStatus().value()); assertEquals(first.getMessage(), second.getMessage());
        verifyNoInteractions(versions, files);
    }

    @Test void ver11_ver14_ver16_detailsMapsFilesWithoutSensitiveFields() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        var version = version(101, 10, 1, ModelSourceType.AI_GENERATED);
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version));
        var file = new ModelFile(101, ModelFileRole.SOURCE_MODEL, "glb", "secret/key", "model/gltf-binary", "hash", null, true);
        ReflectionTestUtils.setField(file, "fileId", 301);
        when(files.findByModelVersionIdOrderByFileIdAsc(101)).thenReturn(List.of(file));
        var result = service.getModelVersionDetails(7, 10, 101);
        assertEquals(101, result.modelVersionId()); assertEquals(ModelSourceType.AI_GENERATED, result.sourceType());
        assertEquals(1, result.files().size()); assertEquals(301, result.files().getFirst().fileId());
        assertNull(result.files().getFirst().fileSize()); assertTrue(result.files().getFirst().isPrimary());
        assertEquals(Set.of("fileId", "fileRole", "fileFormat", "fileSize", "isPrimary"),
                Arrays.stream(ModelFileInfoResponse.class.getRecordComponents()).map(x -> x.getName()).collect(java.util.stream.Collectors.toSet()));
        verify(files).findByModelVersionIdOrderByFileIdAsc(101);
    }

    @Test void ver12_ver13_versionOwnershipAndMissingVersionReturn404BeforeFiles() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        when(versions.findByModelVersionIdAndModelId(999, 10)).thenReturn(Optional.empty());
        var error = assertThrows(ApiException.class, () -> service.getModelVersionDetails(7, 10, 999));
        assertEquals(404, error.getStatus().value()); verifyNoInteractions(files);
    }

    @Test void ver15_versionWithoutFilesReturnsEmptyList() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version(101, 10, 1, ModelSourceType.UPLOAD)));
        when(files.findByModelVersionIdOrderByFileIdAsc(101)).thenReturn(List.of());
        assertTrue(service.getModelVersionDetails(7, 10, 101).files().isEmpty());
    }

    @Test void ver09_ver10_ver20_invalidInputsRejectedWithoutQueries() {
        for (Runnable action : List.<Runnable>of(
                () -> service.getModelVersions(7, 10, -1, 20),
                () -> service.getModelVersions(7, 10, 0, 0),
                () -> service.getModelVersions(7, 10, 0, 51),
                () -> service.getModelVersionDetails(7, 10, 0),
                () -> service.getModelVersionDetails(0, 10, 1),
                () -> service.getModelVersionDetails(7, null, 1))) {
            assertThrows(ApiException.class, action::run);
        }
        verifyNoInteractions(models, versions, files);
    }

    @Test void ver18_ver19_readOnlyNoWritesOrStorage() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model(10)));
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(10), any())).thenReturn(Page.empty());
        service.getModelVersions(7, 10, 0, 20);
        verify(models, never()).save(any()); verify(models, never()).delete(any()); verifyNoInteractions(files);
    }

    private static Model3D model(int id) { Model3D model = new Model3D(7, "model", null, ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE"); ReflectionTestUtils.setField(model, "modelId", id); return model; }
    private static ModelVersion version(int id, int modelId, int number, ModelSourceType source) { ModelVersion v = new ModelVersion(modelId, null, null, 7, number, source, number == 3 ? "change" : null); ReflectionTestUtils.setField(v, "modelVersionId", id); ReflectionTestUtils.setField(v, "createdAt", Instant.now()); return v; }
}
