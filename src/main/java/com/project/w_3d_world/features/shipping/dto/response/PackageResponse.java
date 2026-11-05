package com.project.w_3d_world.features.shipping.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PackageResponse(int id, String code, int index, String profileCode, int productWeightGrams,
                                  int packagingWeightGrams, int packageWeightGrams, BigDecimal packagingCost,
                                  List<ItemResponse> items) {}
