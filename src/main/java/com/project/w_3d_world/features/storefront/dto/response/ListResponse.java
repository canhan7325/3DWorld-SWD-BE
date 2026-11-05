package com.project.w_3d_world.features.storefront.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ListResponse(List<Row> items, int page, int size, long totalElements, int totalPages,
                               Summary summary, BigDecimal currentMonthlyFee) {}
