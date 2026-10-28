package com.project.w_3d_world.features.wallet.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="wallets") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Wallet {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="wallet_id") private Integer id;
 @Column(name="user_id",nullable=false,unique=true) private Integer userId;
 @Column(nullable=false) private String environment="SANDBOX";
 @Column(name="pending_balance",nullable=false) private BigDecimal pendingBalance=BigDecimal.ZERO;
 @Column(name="available_balance",nullable=false) private BigDecimal availableBalance=BigDecimal.ZERO;
 @Column(name="held_balance",nullable=false) private BigDecimal heldBalance=BigDecimal.ZERO;
 @Column(name="return_shipping_debt",nullable=false) private BigDecimal returnShippingDebt=BigDecimal.ZERO;
 @Column(name="updated_at") private Instant updatedAt;
 public Wallet(int userId){this.userId=userId;}
 public void creditAvailable(BigDecimal v){availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void addPending(BigDecimal v){pendingBalance=pendingBalance.add(v);updatedAt=Instant.now();}
 public void addReturnShippingDebt(BigDecimal v){returnShippingDebt=returnShippingDebt.add(v);updatedAt=Instant.now();}
 public BigDecimal settleReturnShippingDebt(BigDecimal available){BigDecimal paid=available.min(returnShippingDebt);returnShippingDebt=returnShippingDebt.subtract(paid);return paid;}
 public void releasePending(BigDecimal v){pendingBalance=pendingBalance.subtract(v);availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void removePending(BigDecimal v){if(v.compareTo(pendingBalance)>0)throw new IllegalStateException("Insufficient pending balance.");pendingBalance=pendingBalance.subtract(v);updatedAt=Instant.now();}
 public void debitAvailable(BigDecimal v){if(v.compareTo(availableBalance)>0)throw new IllegalStateException("Insufficient available balance.");availableBalance=availableBalance.subtract(v);updatedAt=Instant.now();}
 public void hold(BigDecimal v){availableBalance=availableBalance.subtract(v);heldBalance=heldBalance.add(v);updatedAt=Instant.now();}
 public void releaseHold(BigDecimal v){heldBalance=heldBalance.subtract(v);availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void spendHold(BigDecimal v){heldBalance=heldBalance.subtract(v);updatedAt=Instant.now();}
}
