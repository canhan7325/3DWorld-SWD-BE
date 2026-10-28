package com.project.w_3d_world.features.wallet.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="seller_earnings") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SellerEarning {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="earning_id") private Integer id;
 @Column(name="physical_order_id",nullable=false,unique=true) private Integer orderId;
 @Column(name="seller_user_id",nullable=false) private Integer sellerUserId;
 @Column(name="wallet_id",nullable=false) private Integer walletId;
 @Column(name="gross_amount",nullable=false) private BigDecimal grossAmount;
 @Column(name="commission_rate_snapshot",nullable=false) private BigDecimal commissionRateSnapshot;
 @Column(name="commission_amount",nullable=false) private BigDecimal commissionAmount;
 @Column(name="net_amount",nullable=false) private BigDecimal netAmount;
 @Column(name="earning_status",nullable=false) private String status="AWAITING_FUNDS";
 @Column(name="platform_funds_received_at") private Instant fundsReceivedAt;
 @Column(name="pending_posted_at") private Instant pendingPostedAt;
 @Column(name="release_eligible_at") private Instant releaseEligibleAt;
 @Column(name="released_at") private Instant releasedAt;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 public SellerEarning(int orderId,int sellerId,int walletId,BigDecimal gross,BigDecimal rate,BigDecimal commission,BigDecimal net){this.orderId=orderId;sellerUserId=sellerId;this.walletId=walletId;grossAmount=gross;commissionRateSnapshot=rate;commissionAmount=commission;netAmount=net;}
 public void pending(Instant received, Instant eligible){status="PENDING";fundsReceivedAt=received;pendingPostedAt=Instant.now();releaseEligibleAt=eligible;}
 public void markReceived(Instant received){ if("PENDING".equals(status)) releaseEligibleAt=received.plusSeconds(72*3600); }
 public void voidForReturn(){status="VOID";commissionAmount=BigDecimal.ZERO;netAmount=grossAmount;}
 public void release(){status="RELEASED";releasedAt=Instant.now();}
}
