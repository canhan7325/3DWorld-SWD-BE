package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import lombok.RequiredArgsConstructor;
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
                .orElseThrow(PrintingErrors::requestFileNotFound);
        if (request.getCurrentFileVersion() != null) {
            return fileVersionService.download(customerUserId, printRequestId,
                    request.getCurrentFileVersion());
        }
        var items = itemRepository.findByPrintRequestIdOrderByPrintRequestItemIdAsc(printRequestId);
        if (items.isEmpty() || items.getFirst().getModelVersionId() == null) throw PrintingErrors.requestFileNotFound();
        Integer versionId = items.getFirst().getModelVersionId();
        if (items.stream().anyMatch(item -> !versionId.equals(item.getModelVersionId()))) throw PrintingErrors.requestFileNotFound();
        var version = versionRepository.findById(versionId).orElseThrow(PrintingErrors::requestFileNotFound);
        var file = fileRepository.findPrimaryByModelVersionId(versionId)
                .orElseThrow(PrintingErrors::requestFileNotFound);
        return modelDownloadService.openAuthorizedDownload(customerUserId, version.getModelId(), file.getFileId());
    }

}
