package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileDownloadService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestSpecificationService;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestFileServiceTest {
    @Test
    void createUploadsOpaqueFileThenCreatesOneMaterialItemInSameTransactionBoundary() throws Exception {
        ModelUploadService uploader = mock(ModelUploadService.class);
        PrintRequestService requests = mock(PrintRequestService.class);
        var versionsService = mock(PrintRequestFileVersionService.class);
        var specs = mock(PrintRequestSpecificationService.class);
        var service = new PrintRequestFileCreateService(uploader, requests, versionsService, specs);
        byte[] bytes = {1, 2, 3, 4};
        var file = new MockMultipartFile("file", "shape.glb", "application/octet-stream", bytes);
        when(uploader.uploadModel(eq(7), eq(new CreateModelRequest("shape", null)), eq(file)))
                .thenReturn(new ModelUploadResponse(5, 12, 21, "shape", 1, "glb", 4L, "PRIVATE", Instant.now()));
        when(requests.createDraft(eq(7), any())).thenReturn(
                new PrintRequestResponse(44, "DRAFT", null, Instant.now(), null, null, List.of()));

        assertEquals(44, service.create(7, 3, true, file).printRequestId());
        var input = org.mockito.ArgumentCaptor.forClass(CreatePrintRequestRequest.class);
        verify(requests).createDraft(eq(7), input.capture());
        verify(versionsService).recordInitial(eq(7), eq(44), any(), eq("shape.glb"));
        verify(specs).recordInitial(7, 44);
        assertTrue(input.getValue().rightsDeclarationAccepted());
        assertEquals(1, input.getValue().items().size());
        assertEquals(12, input.getValue().items().getFirst().modelVersionId());
        assertEquals(3, input.getValue().items().getFirst().materialId());
        assertEquals(1, input.getValue().items().getFirst().quantity());
        assertTrue(PrintRequestFileCreateService.class.getMethod("create", Integer.class,
                Integer.class, Boolean.class, org.springframework.web.multipart.MultipartFile.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void invalidInputsAndFailedUploadNeverCreateRequest() {
        ModelUploadService uploader = mock(ModelUploadService.class);
        PrintRequestService requests = mock(PrintRequestService.class);
        var service = new PrintRequestFileCreateService(uploader, requests,
                mock(PrintRequestFileVersionService.class),
                mock(PrintRequestSpecificationService.class));
        var file = new MockMultipartFile("file", "shape.glb", null, new byte[] {1});
        assertThrows(ApiException.class, () -> service.create(7, null, true, file));
        assertThrows(ApiException.class, () -> service.create(7, 3, false, file));
        assertThrows(ApiException.class, () -> service.create(7, 3, true, null));
        verifyNoInteractions(uploader, requests);
        when(uploader.uploadModel(any(), any(), any())).thenThrow(new IllegalStateException("storage failed"));
        assertThrows(IllegalStateException.class, () -> service.create(7, 3, true, file));
        verifyNoInteractions(requests);
    }

    @Test
    void downloadChecksRequestOwnerBeforeResolvingFileAndReturnsOriginalStream() throws Exception {
        PrintRequestRepository requests = mock(PrintRequestRepository.class);
        PrintRequestItemRepository items = mock(PrintRequestItemRepository.class);
        ModelVersionRepository versions = mock(ModelVersionRepository.class);
        ModelFileRepository files = mock(ModelFileRepository.class);
        ModelDownloadService downloader = mock(ModelDownloadService.class);
        var service = new PrintRequestFileDownloadService(requests, items, versions, files,
                downloader, mock(PrintRequestFileVersionService.class));
        when(requests.findByPrintRequestIdAndCustomerUserId(44, 7))
                .thenReturn(Optional.of(new PrintRequest(7, null)));
        var item = new PrintRequestItem(44, 12, 3, null, null, null, null, null, null, 1, null);
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        var version = new ModelVersion(5, null, null, 7, 1, ModelSourceType.UPLOAD, null);
        when(versions.findById(12)).thenReturn(Optional.of(version));
        var modelFile = new ModelFile(12, ModelFileRole.SOURCE_MODEL, "glb", "private-key",
                "model/gltf-binary", "hash", 4L, true);
        ReflectionTestUtils.setField(modelFile, "fileId", 21);
        when(files.findPrimaryByModelVersionId(12)).thenReturn(Optional.of(modelFile));
        byte[] bytes = {1, 2, 3, 4};
        when(downloader.openAuthorizedDownload(7, 5, 21)).thenReturn(
                new AuthorizedModelDownload(new ByteArrayInputStream(bytes), "shape.glb", "model/gltf-binary", 4L));

        try (var download = service.open(7, 44)) {
            assertArrayEquals(bytes, download.inputStream().readAllBytes());
            assertEquals("shape.glb", download.filename());
        }
        var order = inOrder(requests, items, versions, files, downloader);
        order.verify(requests).findByPrintRequestIdAndCustomerUserId(44, 7);
        order.verify(items).findByPrintRequestIdOrderByPrintRequestItemIdAsc(44);
        order.verify(versions).findById(12);
        order.verify(files).findPrimaryByModelVersionId(12);
        order.verify(downloader).openAuthorizedDownload(7, 5, 21);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.open(8, 44)).getStatus());
        verifyNoMoreInteractions(items, versions, files, downloader);
    }

    @Test
    void initialAndCorrectionKeepHistoricalFileAndAdvancePointer() throws Exception {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var history = mock(PrintRequestFileVersionRepository.class);
        var modelFiles = mock(ModelFileRepository.class);
        var uploader = mock(ModelUploadService.class);
        var downloader = mock(ModelDownloadService.class);
        var service = new PrintRequestFileVersionService(requests, items, history,
                modelFiles, uploader, downloader, mock(PrintRequestReviewPolicy.class));
        var request = new PrintRequest(7, null);
        ReflectionTestUtils.setField(request, "printRequestId", 44);
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(request));
        when(requests.findByPrintRequestIdAndCustomerUserId(44, 7)).thenReturn(Optional.of(request));
        var firstUpload = new ModelUploadResponse(5, 12, 21, "shape", 1, "stl", 4L,
                "PRIVATE", Instant.now());
        var secondUpload = new ModelUploadResponse(6, 13, 22, "corrected", 1, "stl", 3L,
                "PRIVATE", Instant.now());
        var firstModelFile = new ModelFile(12, ModelFileRole.SOURCE_MODEL, "stl", "old-key",
                "model/stl", "old-hash", 4L, true);
        var secondModelFile = new ModelFile(13, ModelFileRole.SOURCE_MODEL, "stl", "new-key",
                "model/stl", "new-hash", 3L, true);
        when(modelFiles.findByFileIdAndModelVersionId(21, 12)).thenReturn(Optional.of(firstModelFile));
        when(modelFiles.findByFileIdAndModelVersionId(22, 13)).thenReturn(Optional.of(secondModelFile));
        var saved = new ArrayList<PrintRequestFileVersion>();
        when(history.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequestFileVersion value = call.getArgument(0);
            saved.add(value);
            return value;
        });

        service.recordInitial(7, 44, firstUpload, "shape.stl");
        request.useSpecVersion(1);
        assertEquals(1, request.getCurrentFileVersion());
        assertFalse(request.isPreviewConfirmed());
        assertNull(request.getSubmittedAt());
        assertEquals("/api/models/5/files/21/download", saved.getFirst().getFileUrl());
        ReflectionTestUtils.setField(request, "previewConfirmed", true);

        when(history.findByPrintRequestIdAndVersion(44, 1))
                .thenReturn(Optional.of(saved.getFirst()));
        var item = new PrintRequestItem(44, 12, 3, null, null, null,
                null, null, null, 1, null);
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        var corrected = new MockMultipartFile("file", "corrected.stl", "model/stl",
                new byte[]{5, 6, 7});
        when(uploader.uploadModel(eq(7), any(), eq(corrected))).thenReturn(secondUpload);

        var response = service.correct(7, 44, 1, corrected);
        assertEquals(2, response.version());
        assertTrue(response.current());
        assertEquals(2, request.getCurrentFileVersion());
        assertEquals(1, request.getCurrentSpecVersion());
        assertFalse(request.isPreviewConfirmed());
        assertEquals(13, item.getModelVersionId());
        assertEquals(1, saved.getFirst().getVersion());
        assertEquals("/api/models/5/files/21/download", saved.getFirst().getFileUrl());
        assertEquals("/api/models/6/files/22/download", saved.get(1).getFileUrl());
        when(history.findByPrintRequestIdOrderByVersionDesc(44))
                .thenReturn(List.of(saved.get(1), saved.getFirst()));
        var versions = service.list(7, 44);
        assertEquals(List.of(2, 1), versions.stream().map(value -> value.version()).toList());
        assertTrue(versions.getFirst().current());
        assertFalse(versions.get(1).current());

        when(downloader.openAuthorizedDownload(7, 5, 21)).thenReturn(
                new AuthorizedModelDownload(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                        "shape.stl", "model/stl", 4L));
        try (var old = service.download(7, 44, 1)) {
            assertArrayEquals(new byte[]{1, 2, 3, 4}, old.inputStream().readAllBytes());
        }
        verify(downloader).openAuthorizedDownload(7, 5, 21);
    }

    @Test
    void correctionRejectsOtherOwnerLegacyDraftStaleAndLockedBeforeUpload() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var history = mock(PrintRequestFileVersionRepository.class);
        var modelFiles = mock(ModelFileRepository.class);
        var uploader = mock(ModelUploadService.class);
        var downloader = mock(ModelDownloadService.class);
        var service = new PrintRequestFileVersionService(requests, items, history,
                modelFiles, uploader, downloader, mock(PrintRequestReviewPolicy.class));
        var file = new MockMultipartFile("file", "shape.stl", "model/stl", new byte[]{1});
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.correct(8, 44, 1, file)).getStatus().value());
        var request = new PrintRequest(7, null);
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(request));
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.correct(7, 44, 1, file)).getStatus().value());
        request.useFileVersion(1);
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.correct(7, 44, 2, file)).getStatus().value());
        ReflectionTestUtils.setField(request, "lockedAt", Instant.now());
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.correct(7, 44, 1, file)).getStatus().value());
        verifyNoInteractions(uploader, items, modelFiles);
    }

    @Test
    void staffAuthorizedCorrectionPreservesOldFileAndRejectsSecondUnreviewedUpload() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var files = mock(PrintRequestFileVersionRepository.class);
        var modelFiles = mock(ModelFileRepository.class);
        var uploader = mock(ModelUploadService.class);
        var policy = mock(PrintRequestReviewPolicy.class);
        var service = new PrintRequestFileVersionService(requests, items, files,
                modelFiles, uploader, mock(ModelDownloadService.class), policy);
        var request = new PrintRequest(7, null);
        ReflectionTestUtils.setField(request, "printRequestId", 44);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        var upload = new MockMultipartFile("file", "fixed.stl", "model/stl", new byte[]{1, 2, 3});
        when(requests.findOwnedByIdForUpdate(44, 7)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.correct(7, 44, 1, upload)).getStatus());
        verifyNoInteractions(uploader);

        request.requireRevision();
        when(policy.requirePendingCorrection(request)).thenReturn(
                new PrintRequestReviewPolicy.Decision("CORRECTION_REQUESTED", 1, 1));
        var oldFile = new PrintRequestFileVersion(44, 1, "old.stl", 3L,
                "model/stl", "/api/models/5/files/21/download", 7);
        when(files.findByPrintRequestIdAndVersion(44, 1)).thenReturn(Optional.of(oldFile));
        var item = new PrintRequestItem(44, 12, 3, null, null, null,
                null, null, null, 1, null);
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(44)).thenReturn(List.of(item));
        var uploaded = new ModelUploadResponse(6, 13, 22, "fixed", 1, "stl", 3L,
                "PRIVATE", Instant.now());
        when(uploader.uploadModel(eq(7), any(), eq(upload))).thenReturn(uploaded);
        when(modelFiles.findByFileIdAndModelVersionId(22, 13)).thenReturn(Optional.of(
                new ModelFile(13, ModelFileRole.SOURCE_MODEL, "stl", "new-key",
                        "model/stl", null, 3L, true)));
        when(files.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals(2, service.correct(7, 44, 1, upload).version());
        assertEquals(1, oldFile.getVersion());
        assertEquals(2, request.getCurrentFileVersion());
        assertEquals(1, request.getCurrentSpecVersion());
        assertFalse(request.isPreviewConfirmed());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.correct(7, 44, 2, upload)).getStatus());
        verify(uploader, times(1)).uploadModel(eq(7), any(), eq(upload));
        verify(files, times(1)).saveAndFlush(any());
    }

    @Test
    void currentFileDownloadUsesVersionPointerWhenPresent() throws Exception {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var versions = mock(ModelVersionRepository.class);
        var files = mock(ModelFileRepository.class);
        var downloader = mock(ModelDownloadService.class);
        var fileVersions = mock(PrintRequestFileVersionService.class);
        var request = new PrintRequest(7, null);
        request.useFileVersion(2);
        when(requests.findByPrintRequestIdAndCustomerUserId(44, 7))
                .thenReturn(Optional.of(request));
        when(fileVersions.download(7, 44, 2)).thenReturn(new AuthorizedModelDownload(
                new ByteArrayInputStream(new byte[]{5, 6, 7}), "new.stl", "model/stl", 3L));

        var service = new PrintRequestFileDownloadService(requests, items, versions,
                files, downloader, fileVersions);
        try (var current = service.open(7, 44)) {
            assertArrayEquals(new byte[]{5, 6, 7}, current.inputStream().readAllBytes());
        }
        verify(fileVersions).download(7, 44, 2);
        verifyNoInteractions(items, versions, files, downloader);
    }
}
