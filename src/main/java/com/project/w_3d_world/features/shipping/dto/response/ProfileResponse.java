package com.project.w_3d_world.features.shipping.dto.response;

import java.math.BigDecimal;

public record ProfileResponse(int id, String code, String name, BigDecimal lengthCm, BigDecimal widthCm,
                                  BigDecimal heightCm, int maxProductWeightGrams, boolean supportsFragile,
                                  String allowedPackingGroups) {}
