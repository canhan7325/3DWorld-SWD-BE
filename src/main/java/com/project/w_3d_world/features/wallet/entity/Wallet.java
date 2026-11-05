package com.project.w_3d_world.features.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="wallets") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Wallet {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="wallet_id") private Integer id;
 @Column(name="user_id",nullable=false,unique=true) private Integer userId;
 @Column(nullable=false) private String environment="SANDBOX";
 @Column(name="pending_balance",nullable=false) private BigDecimal pendingBalance=BigDecimal.ZERO;
 @Column(name="available_balance",nullable=false) private BigDecimal availableBalance=BigDecimal.ZERO;
 @Column(name="held_balance",nullable=false) private BigDecimal heldBalance=BigDecimal.ZERO;
 @Column(name="return_shipping_debt",nullable=false) private BigDecimal returnShippingDebt=BigDecimal.ZERO;
 @Column(name="shipping_debt_since") private Instant shippingDebtSince;
 @Column(name="updated_at") private Instant updatedAt;
 public Wallet(int userId){this.userId=userId;}
 /** Reserve stays inside available balance; it is never a platform fee or extra debit. */
 public BigDecimal reserveBalance(BigDecimal target){return availableBalance.min(target);}
 public BigDecimal reserveShortfall(BigDecimal target){return target.subtract(reserveBalance(target));}
 public BigDecimal withdrawableBalance(BigDecimal target){return returnShippingDebt.signum()>0?BigDecimal.ZERO:availableBalance.subtract(target).max(BigDecimal.ZERO);}
 public void creditAvailable(BigDecimal v){availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void addPending(BigDecimal v){pendingBalance=pendingBalance.add(v);updatedAt=Instant.now();}
 public void addReturnShippingDebt(BigDecimal v){if(v.signum()<0)throw new IllegalArgumentException("Debt cannot be negative.");if(v.signum()>0&&returnShippingDebt.signum()==0)shippingDebtSince=Instant.now();returnShippingDebt=returnShippingDebt.add(v);updatedAt=Instant.now();}
 public BigDecimal settleReturnShippingDebt(BigDecimal available){BigDecimal paid=available.min(returnShippingDebt);returnShippingDebt=returnShippingDebt.subtract(paid);if(returnShippingDebt.signum()==0)shippingDebtSince=null;updatedAt=Instant.now();return paid;}
 public void releasePending(BigDecimal v){pendingBalance=pendingBalance.subtract(v);availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void removePending(BigDecimal v){if(v.compareTo(pendingBalance)>0)throw new IllegalStateException("Insufficient pending balance.");pendingBalance=pendingBalance.subtract(v);updatedAt=Instant.now();}
 public void debitAvailable(BigDecimal v){if(v.compareTo(availableBalance)>0)throw new IllegalStateException("Insufficient available balance.");availableBalance=availableBalance.subtract(v);updatedAt=Instant.now();}
 public void hold(BigDecimal v){availableBalance=availableBalance.subtract(v);heldBalance=heldBalance.add(v);updatedAt=Instant.now();}
 public void releaseHold(BigDecimal v){heldBalance=heldBalance.subtract(v);availableBalance=availableBalance.add(v);updatedAt=Instant.now();}
 public void spendHold(BigDecimal v){heldBalance=heldBalance.subtract(v);updatedAt=Instant.now();}
}
