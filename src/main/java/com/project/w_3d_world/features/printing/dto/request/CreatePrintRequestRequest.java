package com.project.w_3d_world.features.printing.dto.request;

import java.util.List;

public record CreatePrintRequestRequest(
        String customerNotes, Boolean rightsDeclarationAccepted,
        List<CreatePrintRequestItemRequest> items) {}
