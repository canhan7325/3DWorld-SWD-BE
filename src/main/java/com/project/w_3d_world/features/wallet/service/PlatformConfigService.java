package com.project.w_3d_world.features.wallet.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformConfigService {
    public static final String COMMISSION_RATE = "COMMISSION_RATE";
    public static final String STOREFRONT_MONTHLY_FEE = "STOREFRONT_MONTHLY_FEE";
    private final JdbcTemplate jdbc;
    private final AccountAuthorization authorization;

    @Transactional(readOnly = true)
    public BigDecimal decimal(String key, BigDecimal fallback) {
        return jdbc.query("SELECT config_value FROM platform_configs WHERE config_key=?",
                (rs, row) -> rs.getString(1), key).stream().findFirst()
                .map(value -> new BigDecimal(value)).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> get(int admin) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        return Map.of(COMMISSION_RATE, decimal(COMMISSION_RATE, new BigDecimal("0.05")),
                STOREFRONT_MONTHLY_FEE, decimal(STOREFRONT_MONTHLY_FEE, new BigDecimal("99000")));
    }

    @Transactional
    public Map<String, BigDecimal> update(int admin, BigDecimal commission, BigDecimal monthlyFee) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        if (commission == null || commission.signum() < 0 || commission.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.invalidRequest("Commission rate must be between 0 and 1.");
        }
        if (monthlyFee == null || monthlyFee.signum() <= 0 || monthlyFee.stripTrailingZeros().scale() > 0) {
            throw ApiException.invalidRequest("Storefront monthly fee must be a positive whole VND amount.");
        }
        save(COMMISSION_RATE, commission, admin);
        save(STOREFRONT_MONTHLY_FEE, monthlyFee, admin);
        return get(admin);
    }

    private void save(String key, BigDecimal value, int admin) {
        jdbc.update("""
                INSERT INTO platform_configs(config_key,config_value,updated_by_user_id,updated_at)
                VALUES (?,?,?,now()) ON CONFLICT (config_key) DO UPDATE SET config_value=excluded.config_value,
                updated_by_user_id=excluded.updated_by_user_id,updated_at=now()
                """, key, value.toPlainString(), admin);
    }
}
