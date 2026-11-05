package com.project.w_3d_world.features.wallet.service;

import java.math.BigDecimal;
import java.util.Map;

public interface PlatformConfigService {
    String COMMISSION_RATE = "COMMISSION_RATE";
    String STOREFRONT_MONTHLY_FEE = "STOREFRONT_MONTHLY_FEE";
    BigDecimal decimal(String key, BigDecimal fallback);
    Map<String, BigDecimal> get(int admin);
    Map<String, BigDecimal> update(int admin, BigDecimal commission, BigDecimal monthlyFee);
}
