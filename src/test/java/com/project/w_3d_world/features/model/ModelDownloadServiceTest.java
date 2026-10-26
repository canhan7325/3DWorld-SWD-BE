package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.repository.*;
import com.project.w_3d_world.features.model.service.*;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.model.exception.ModelStorageException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelDownloadServiceTest {
    Model3DRepository models; ModelVersionRepository versions; ModelFileRepository files; ModelStorageService storage;
    ModelDownloadServiceImpl service;
    @BeforeEach void setup() {
        models = mock(Model3DRepository.class); versions = mock(ModelVersionRepository.class); files = mock(ModelFileRepository.class); storage = mock(ModelStorageService.class);
        service = new ModelDownloadServiceImpl(models, versions, files, storage);
    }
    @Test void dl01_dl08_ownerRelationshipThenStorage() throws Exception {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.of(file(101, "glb", "managed/key.glb")));
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version()));
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1, 2}); when(storage.open("managed/key.glb")).thenReturn(input);
        try (AuthorizedModelDownload result = service.openAuthorizedDownload(7, 10, 301)) {
            assertArrayEquals(new byte[]{1, 2}, result.inputStream().readAllBytes());
            assertEquals("model-10-file-301.glb", result.filename()); assertEquals("model/gltf-binary", result.contentType());
        }
        verify(storage).open("managed/key.glb"); verifyNoMoreInteractions(storage);
        verify(models).findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7);
    }
    @Test void dl02_dl03_dl04_modelAuthorizationStopsBeforeFile() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(anyInt(), anyInt())).thenReturn(Optional.empty());
        for (int user : List.of(7, 8)) assertThrows(ApiException.class, () -> service.openAuthorizedDownload(user, 10, 301));
        verifyNoInteractions(files, versions, storage);
    }
    @Test void dl05_missingFileAndDl06_wrongVersionReturn404WithoutStorage() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 10, 301)).getStatus().value());
        when(files.findById(302)).thenReturn(Optional.of(file(999, "glb", "bad")));
        when(versions.findByModelVersionIdAndModelId(999, 10)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 10, 302)).getStatus().value());
        verifyNoInteractions(storage);
    }
    @Test void dl07_missingVersionReturns404() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.of(file(101, "glb", "key")));
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 10, 301)).getStatus().value()); verifyNoInteractions(storage);
    }
    @Test void dl09_dl10_invalidIdsRejectedWithoutQueries() {
        for (Integer id : Arrays.asList(null, 0, -1)) assertThrows(ApiException.class, () -> service.openAuthorizedDownload(id, 10, 301));
        assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 0, 301)); assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 10, 0));
        verifyNoInteractions(models, files, versions, storage);
    }
    @Test void dl11_dl12_dl13_storageFailureIsControlledAndNoPathLeaks() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.of(file(101, "glb", "../../secret")));
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version()));
        when(storage.open("../../secret")).thenThrow(new ModelStorageException(ModelStorageException.Code.UNSAFE_PATH, "internal path"));
        var error = assertThrows(ApiException.class, () -> service.openAuthorizedDownload(7, 10, 301));
        assertEquals(500, error.getStatus().value()); assertFalse(error.getMessage().contains("secret"));
    }
    @Test void dl14_dl15_unknownFormatUsesBinAndSafeIds() throws Exception {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.of(file(101, "exe\"\r\n", "key")));
        when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version()));
        when(storage.open("key")).thenReturn(new ByteArrayInputStream(new byte[0]));
        try (var result = service.openAuthorizedDownload(7, 10, 301)) { assertEquals("model-10-file-301.bin", result.filename()); assertEquals("application/octet-stream", result.contentType()); }
    }
    @Test void dl16_dl17_noWritesOrDeletes() {
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(10, 7)).thenReturn(Optional.of(model()));
        when(files.findById(301)).thenReturn(Optional.of(file(101, "glb", "key"))); when(versions.findByModelVersionIdAndModelId(101, 10)).thenReturn(Optional.of(version())); when(storage.open("key")).thenReturn(new ByteArrayInputStream(new byte[0]));
        assertDoesNotThrow(() -> service.openAuthorizedDownload(7, 10, 301)); verify(models, never()).save(any()); verify(files, never()).delete(any()); verify(storage, never()).deleteForCleanup(anyString());
    }
    @Test void handleCloseIsExactlyOnce() throws Exception {
        AtomicCloseInputStream input = new AtomicCloseInputStream(); var handle = new AuthorizedModelDownload(input, "a.bin", "application/octet-stream", null); handle.close(); handle.close(); assertEquals(1, input.closes);
    }
    private static Model3D model() { Model3D m = new Model3D(7, "m", null, ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE"); ReflectionTestUtils.setField(m, "modelId", 10); return m; }
    private static ModelVersion version() { ModelVersion v = new ModelVersion(10, null, null, 7, 1, ModelSourceType.UPLOAD, null); ReflectionTestUtils.setField(v, "modelVersionId", 101); return v; }
    private static ModelFile file(int version, String format, String key) { return new ModelFile(version, ModelFileRole.SOURCE_MODEL, format, key, null, "hash", null, true); }
    static class AtomicCloseInputStream extends ByteArrayInputStream { int closes; AtomicCloseInputStream() { super(new byte[0]); } @Override public void close() { closes++; } }
}
