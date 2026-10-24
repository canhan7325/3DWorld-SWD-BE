package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestFileDownloadService {
    private final PrintRequestRepository requestRepository;
    private final PrintRequestItemRepository itemRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;
    private final ModelDownloadService modelDownloadService;
    private final PrintRequestFileVersionService fileVersionService;

    public AuthorizedModelDownload open(Integer customerUserId, Integer printRequestId) {
        var request = requestRepository.findByPrintRequestIdAndCustomerUserId(printRequestId, customerUserId)
                .orElseThrow(PrintRequestFileDownloadService::notFound);
        if (request.getCurrentFileVersion() != null) {
            return fileVersionService.download(customerUserId, printRequestId,
                    request.getCurrentFileVersion());
        }
        var items = itemRepository.findByPrintRequestIdOrderByPrintRequestItemIdAsc(printRequestId);
        if (items.isEmpty() || items.getFirst().getModelVersionId() == null) throw notFound();
        Integer versionId = items.getFirst().getModelVersionId();
        if (items.stream().anyMatch(item -> !versionId.equals(item.getModelVersionId()))) throw notFound();
        var version = versionRepository.findById(versionId).orElseThrow(PrintRequestFileDownloadService::notFound);
        var file = fileRepository.findPrimaryByModelVersionId(versionId)
                .orElseThrow(PrintRequestFileDownloadService::notFound);
        return modelDownloadService.openAuthorizedDownload(customerUserId, version.getModelId(), file.getFileId());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Print Request file was not found.");
    }
}
