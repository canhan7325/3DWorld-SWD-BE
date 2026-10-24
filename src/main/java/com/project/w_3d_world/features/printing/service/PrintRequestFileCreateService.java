package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintRequestFileCreateService {
    private final ModelUploadService modelUploadService;
    private final PrintRequestService printRequestService;
    private final PrintRequestFileVersionService fileVersionService;

    @Transactional
    public PrintRequestResponse create(Integer customerUserId, Integer materialId,
            Boolean rightsAccepted, MultipartFile file) {
        if (materialId == null || materialId <= 0) {
            throw ApiException.invalidRequest("materialId must be a positive integer.");
        }
        if (!Boolean.TRUE.equals(rightsAccepted)) {
            throw ApiException.invalidRequest("rightsDeclarationAccepted must be true.");
        }
        String name = ModelServiceValidation.modelNameFromFilename(
                file == null ? null : file.getOriginalFilename());
        var upload = modelUploadService.uploadModel(customerUserId, new CreateModelRequest(name, null), file);
        var item = new CreatePrintRequestItemRequest(upload.modelVersionId(), materialId,
                null, null, null, null, null, null, 1, null);
        var created = printRequestService.createDraft(customerUserId,
                new CreatePrintRequestRequest(null, true, List.of(item)));
        fileVersionService.recordInitial(customerUserId, created.printRequestId(),
                upload, file.getOriginalFilename());
        return created;
    }
}
