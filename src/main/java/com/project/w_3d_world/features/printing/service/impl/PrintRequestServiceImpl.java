package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSummaryResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestServiceImpl implements PrintRequestService {
    private static final Sort REQUEST_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("printRequestId"));

    private final PrintRequestRepository requestRepository;
    private final PrintRequestItemRepository itemRepository;

    @Override
    public PagedPrintRequestResponse getMyRequests(Integer customerUserId, int page, int size) {
        PrintRequestValidation.validateCustomerId(customerUserId);
        PrintRequestValidation.validatePage(page, size);
        var requests = requestRepository.findByCustomerUserId(
                customerUserId, PageRequest.of(page, size, REQUEST_SORT));
        return new PagedPrintRequestResponse(
                requests.getContent().stream().map(PrintRequestServiceImpl::toSummary).toList(),
                requests.getNumber(), requests.getSize(),
                requests.getTotalElements(), requests.getTotalPages());
    }

    @Override
    public PrintRequestResponse getMyRequestById(Integer customerUserId, Integer printRequestId) {
        PrintRequestValidation.validateCustomerId(customerUserId);
        PrintRequestValidation.validatePrintRequestId(printRequestId);
        PrintRequest request = requestRepository
                .findByPrintRequestIdAndCustomerUserId(printRequestId, customerUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        var items = itemRepository.findByPrintRequestIdOrderByPrintRequestItemIdAsc(printRequestId)
                .stream().map(PrintRequestServiceImpl::toItem).toList();
        return new PrintRequestResponse(request.getPrintRequestId(), request.getStatus(),
                request.getCustomerNotes(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getLockedAt(), items);
    }

    private static PrintRequestSummaryResponse toSummary(PrintRequest request) {
        return new PrintRequestSummaryResponse(request.getPrintRequestId(), request.getStatus(),
                request.getCustomerNotes(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getLockedAt());
    }

    private static PrintRequestItemResponse toItem(PrintRequestItem item) {
        String sourceType = item.getModelVersionId() == null ? "UPLOADED_FILE" : "MODEL_VERSION";
        return new PrintRequestItemResponse(item.getPrintRequestItemId(), item.getMaterialId(),
                item.getModelVersionId(), sourceType, item.getColor(), item.getQuality(),
                item.getInfillPercent(), item.getWidthMm(), item.getHeightMm(), item.getDepthMm(),
                item.getQuantity(), item.getNotes(), item.getProductionStatus());
    }
}
