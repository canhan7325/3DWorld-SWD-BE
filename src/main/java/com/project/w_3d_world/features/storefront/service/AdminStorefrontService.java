package com.project.w_3d_world.features.storefront.service;

import static com.project.w_3d_world.features.storefront.dto.AdminStorefrontDtos.*;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.storefront.repository.AdminStorefrontQueryRepository;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStorefrontService {
    private static final Set<String> SHOP_STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED");
    private static final Set<String> PLAN_STATUSES = Set.of("ACTIVE", "EXPIRED", "PENDING_PAYMENT", "NONE");
    private static final int EXPIRING_DAYS = 7;

    private final AdminStorefrontQueryRepository repository;
    private final PlatformConfigService platformConfig;

    public ListResponse list(String keyword, String status, String plan, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid page or size.");
        }
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (kw != null && kw.length() > 120) {
            throw ApiException.invalidRequest("Keyword is too long.");
        }
        String shopStatus = normalize(status, SHOP_STATUSES, "status");
        String planStatus = normalize(plan, PLAN_STATUSES, "plan");
        String order = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase();
        if (!AdminStorefrontQueryRepository.validSort(order)) {
            throw ApiException.invalidRequest("Unsupported sort.");
        }

        var rows = repository.page(kw, shopStatus, planStatus, order, page, size);
        long total = rows.isEmpty()
                ? (page == 0 ? 0 : repository.count(kw, shopStatus, planStatus))
                : ((Number) rows.get(0).get("total_rows")).longValue();
        var items = rows.stream().map(AdminStorefrontService::toRow).toList();
        var s = repository.summary(EXPIRING_DAYS);
        var summary = new Summary(num(s, "total"), num(s, "active"), num(s, "expired"),
                num(s, "pending_payment"), num(s, "none"), num(s, "suspended"), num(s, "expiring_soon"));
        int pages = (int) Math.ceil(total / (double) size);
        return new ListResponse(items, page, size, total, pages, summary,
                platformConfig.decimal(PlatformConfigService.STOREFRONT_MONTHLY_FEE, new BigDecimal("99000")));
    }

    public List<SubscriptionRow> subscriptions(int storefrontId) {
        if (!repository.exists(storefrontId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Storefront does not exist.");
        }
        return repository.subscriptions(storefrontId).stream()
                .map(r -> new SubscriptionRow(((Number) r.get("subscription_id")).intValue(),
                        (BigDecimal) r.get("fee_snapshot"), (String) r.get("status"), instant(r.get("start_at")),
                        instant(r.get("end_at")), instant(r.get("paid_at")), instant(r.get("created_at"))))
                .toList();
    }

    private static Row toRow(Map<String, Object> r) {
        String planStatus = (String) r.get("plan_status");
        Instant expiresAt = instant(r.get("expires_at"));
        Long daysLeft = "ACTIVE".equals(planStatus) && expiresAt != null
                ? Math.max(0, (long) Math.ceil(Duration.between(Instant.now(), expiresAt).toHours() / 24.0))
                : null;
        Integer currentId = r.get("cur_id") == null ? null : ((Number) r.get("cur_id")).intValue();
        var plan = new Plan(PLAN_CODE, PLAN_NAME, PLAN_DAYS, planStatus, (BigDecimal) r.get("cur_fee"), currentId,
                instant(r.get("cur_start")), instant(r.get("cur_end")), instant(r.get("cur_paid")), daysLeft);
        var pending = r.get("pend_id") == null ? null
                : new PendingSubscription(((Number) r.get("pend_id")).intValue(), (BigDecimal) r.get("pend_fee"),
                        instant(r.get("pend_created")));
        var owner = new Owner(((Number) r.get("owner_user_id")).intValue(), (String) r.get("full_name"),
                (String) r.get("email"), (String) r.get("phone"));
        return new Row(((Number) r.get("storefront_id")).intValue(), (String) r.get("storefront_name"),
                (String) r.get("status"), (String) r.get("seller_verification_status"), expiresAt,
                instant(r.get("created_at")), owner, plan, pending, num(r, "paid_count"),
                (BigDecimal) r.get("total_paid"), instant(r.get("first_paid_at")));
    }

    private static String normalize(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw ApiException.invalidRequest("Unsupported " + field + ".");
        }
        return upper;
    }

    private static long num(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof Instant i) return i;
        throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
    }
}
