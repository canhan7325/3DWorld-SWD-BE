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
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileDownloadService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestFileServiceTest {
    @Test
    void createUploadsOpaqueFileThenCreatesOneMaterialItemInSameTransactionBoundary() throws Exception {
        ModelUploadService uploader = mock(ModelUploadService.class);
        PrintRequestService requests = mock(PrintRequestService.class);
        var service = new PrintRequestFileCreateService(uploader, requests);
        byte[] bytes = {1, 2, 3, 4};
        var file = new MockMultipartFile("file", "shape.glb", "application/octet-stream", bytes);
        when(uploader.uploadModel(eq(7), eq(new CreateModelRequest("shape", null)), eq(file)))
                .thenReturn(new ModelUploadResponse(5, 12, 21, "shape", 1, "glb", 4L, "PRIVATE", Instant.now()));
        when(requests.createDraft(eq(7), any())).thenReturn(
                new PrintRequestResponse(44, "DRAFT", null, Instant.now(), null, null, List.of()));

        assertEquals(44, service.create(7, 3, true, file).printRequestId());
        var input = org.mockito.ArgumentCaptor.forClass(CreatePrintRequestRequest.class);
        verify(requests).createDraft(eq(7), input.capture());
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
        var service = new PrintRequestFileCreateService(uploader, requests);
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
        var service = new PrintRequestFileDownloadService(requests, items, versions, files, downloader);
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
}
