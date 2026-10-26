package com.project.w_3d_world.features.storefront.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "storefront_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorefrontSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Integer id;
    @Column(name = "storefront_id", nullable = false)
    private Integer storefrontId;
    @Column(name = "fee_snapshot", nullable = false)
    private BigDecimal feeSnapshot;
    @Column(name = "start_at")
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    @Column(nullable = false)
    private String status = "PENDING_PAYMENT";
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public StorefrontSubscription(int storefrontId, BigDecimal fee) {
        this.storefrontId = storefrontId;
        feeSnapshot = fee;
    }

    public void activate(Instant start, Instant end) {
        startAt = start;
        endAt = end;
        paidAt = Instant.now();
        status = "ACTIVE";
    }
}
