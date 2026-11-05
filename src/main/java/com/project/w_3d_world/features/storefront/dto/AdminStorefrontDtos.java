package com.project.w_3d_world.features.storefront.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin view of every storefront together with the plan (subscription) it is renting. */
public final class AdminStorefrontDtos {
    private AdminStorefrontDtos() {}

    public static final String PLAN_CODE = "STOREFRONT_MONTHLY";
    public static final String PLAN_NAME = "Gói thuê gian hàng 30 ngày";
    public static final int PLAN_DAYS = 30;

    public record Owner(int userId, String fullName, String email, String phone) {}

    /**
     * status: ACTIVE (paid, not yet expired) · EXPIRED (paid before, now expired) ·
     * PENDING_PAYMENT (never paid, has an unpaid subscription) · NONE (never subscribed).
     */
    public record Plan(String code, String name, int durationDays, String status, BigDecimal fee,
                       Integer subscriptionId, Instant startAt, Instant endAt, Instant paidAt, Long daysLeft) {}

    public record PendingSubscription(int subscriptionId, BigDecimal fee, Instant createdAt) {}

    public record Row(int id, String name, String status, String verificationStatus, Instant expiresAt,
                      Instant createdAt, Owner owner, Plan plan, PendingSubscription pending,
                      long paidSubscriptions, BigDecimal totalPaid, Instant firstPaidAt) {}

    public record Summary(long total, long active, long expired, long pendingPayment, long none,
                          long suspended, long expiringSoon) {}

    public record ListResponse(List<Row> items, int page, int size, long totalElements, int totalPages,
                               Summary summary, BigDecimal currentMonthlyFee) {}

    public record SubscriptionRow(int subscriptionId, BigDecimal fee, String status, Instant startAt,
                                  Instant endAt, Instant paidAt, Instant createdAt) {}
}
