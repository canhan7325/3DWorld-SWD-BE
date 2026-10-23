package com.project.w_3d_world.features.printing.dto.request;

import com.project.w_3d_world.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;

public record UpdatePrintMaterialCostRequest(BigDecimal estimatedUnitCost) {
    public static UpdatePrintMaterialCostRequest from(JsonNode json) {
        CreatePrintMaterialRequest.requireObjectWithOnly(json, Set.of("estimatedUnitCost"));
        if (!json.has("estimatedUnitCost")) {
            throw ApiException.invalidRequest("estimatedUnitCost is required, even when clearing it to null.");
        }
        return new UpdatePrintMaterialCostRequest(CreatePrintMaterialRequest.cost(json.get("estimatedUnitCost")));
    }
}
