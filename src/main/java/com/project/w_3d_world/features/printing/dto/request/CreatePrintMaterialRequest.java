package com.project.w_3d_world.features.printing.dto.request;

import com.project.w_3d_world.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;

public record CreatePrintMaterialRequest(
        String materialName, String technologyCode, String unit, BigDecimal estimatedUnitCost) {
    private static final Set<String> FIELDS = Set.of(
            "materialName", "technologyCode", "unit", "estimatedUnitCost");

    public static CreatePrintMaterialRequest from(JsonNode json) {
        requireObjectWithOnly(json, FIELDS);
        return new CreatePrintMaterialRequest(text(json, "materialName"),
                text(json, "technologyCode"), text(json, "unit"),
                cost(json.get("estimatedUnitCost")));
    }

    static void requireObjectWithOnly(JsonNode json, Set<String> allowed) {
        if (json == null || !json.isObject() || json.properties().stream()
                .anyMatch(entry -> !allowed.contains(entry.getKey()))) {
            throw ApiException.invalidRequest("Unsupported or malformed Material request fields.");
        }
    }

    static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual()) {
            throw ApiException.invalidRequest(field + " must be a string.");
        }
        return value.textValue();
    }

    static BigDecimal cost(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw ApiException.invalidRequest("estimatedUnitCost must be a number or null.");
        return value.decimalValue();
    }
}
