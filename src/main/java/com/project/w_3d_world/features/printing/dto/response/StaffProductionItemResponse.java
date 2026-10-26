package com.project.w_3d_world.features.printing.dto.response;

public record StaffProductionItemResponse(
        PrintRequestItemResponse item,
        boolean canStart,
        boolean canComplete) {}
