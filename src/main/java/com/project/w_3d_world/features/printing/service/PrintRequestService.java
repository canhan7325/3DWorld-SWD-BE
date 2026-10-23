package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;

public interface PrintRequestService {
    PrintRequestResponse createDraft(Integer customerUserId, CreatePrintRequestRequest request);

    PagedPrintRequestResponse getMyRequests(Integer customerUserId, int page, int size);

    PrintRequestResponse getMyRequestById(Integer customerUserId, Integer printRequestId);
}
