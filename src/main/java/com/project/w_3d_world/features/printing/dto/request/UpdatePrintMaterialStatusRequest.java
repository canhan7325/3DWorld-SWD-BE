package com.project.w_3d_world.features.printing.dto.request;

import com.project.w_3d_world.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

import java.util.Set;

public record UpdatePrintMaterialStatusRequest(Boolean isActive) {
    public static UpdatePrintMaterialStatusRequest from(JsonNode json) {
        CreatePrintMaterialRequest.requireObjectWithOnly(json, Set.of("isActive"));
        JsonNode value = json.get("isActive");
        if (value == null || !value.isBoolean()) {
            throw ApiException.invalidRequest("isActive must be true or false.");
        }
        return new UpdatePrintMaterialStatusRequest(value.booleanValue());
    }
}
