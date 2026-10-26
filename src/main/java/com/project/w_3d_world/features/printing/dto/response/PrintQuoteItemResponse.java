package com.project.w_3d_world.features.printing.dto.response;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

public record PrintQuoteItemResponse(
        Integer printQuoteItemId,
        Integer printRequestItemId,
        Integer fileVersionSnapshot,
        Integer specificationVersionSnapshot,
        JsonNode specificationSnapshot,
        Integer quantitySnapshot,
        BigDecimal materialCostSnapshot,
        BigDecimal laborCostSnapshot,
        BigDecimal optionFeeSnapshot,
        BigDecimal lineTotal
) {}
