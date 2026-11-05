package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.storefront.enums.StorefrontStatus;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.wallet.dto.request.PayoutDecision;
import com.project.w_3d_world.features.wallet.dto.request.PayoutRequest;
import com.project.w_3d_world.features.wallet.dto.response.EarningResponse;
import com.project.w_3d_world.features.wallet.dto.response.PayoutResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletResponse;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.entity.WalletLedger;
import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.repository.WalletLedgerRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor public class WalletServiceImpl implements WalletService {
 private final StorefrontRepository stores;
 private final CommissionWalletService commission; private final WalletRepository wallets; private final SellerEarningRepository earnings; private final WalletLedgerRepository ledgers; private final UserBankAccountRepository banks; private final AccountAuthorization authorization; private final JdbcTemplate jdbc;
 @Transactional public WalletResponse mine(int user){var w=commission.wallet(user);var es=earnings.findAll().stream().filter(e->e.getSellerUserId().equals(user)).map(e->new EarningResponse(e.getOrderId(),e.getGrossAmount(),e.getCommissionRateSnapshot(),e.getCommissionAmount(),e.getNetAmount(),e.getStatus(),e.getReleaseEligibleAt(),e.getReleasedAt())).toList();return new WalletResponse(user,w.getId(),CommissionWalletService.MINIMUM_RESERVE,w.withdrawableBalance(CommissionWalletService.MINIMUM_RESERVE),w.getPendingBalance(),w.getAvailableBalance(),w.getHeldBalance(),w.getReturnShippingDebt(),es,w.reserveBalance(CommissionWalletService.MINIMUM_RESERVE),w.reserveShortfall(CommissionWalletService.MINIMUM_RESERVE));}
 @Transactional public PayoutResponse request(int user,PayoutRequest req){var bank=banks.findById(req.bankAccountId()).filter(b->b.getUserId().equals(user)&&b.isActive()&&"VERIFIED".equals(b.getVerificationStatus())).orElseThrow(()->ApiException.invalidRequest("An active VERIFIED bank account is required."));var w=wallets.lockByUserId(user).orElseThrow(()->ApiException.invalidRequest("Wallet does not exist."));if(stores.existsByOwnerUserIdAndStatus(user,StorefrontStatus.SUSPENDED))throw new ApiException(HttpStatus.CONFLICT,"Suspended shop cannot withdraw.");if(w.getReturnShippingDebt().signum()>0)throw new ApiException(HttpStatus.CONFLICT,"Outstanding return shipping debt must be settled before payout.");if(w.getAvailableBalance().subtract(req.amount()).compareTo(CommissionWalletService.MINIMUM_RESERVE)<0)throw new ApiException(HttpStatus.CONFLICT,"Payout must leave at least 100000 VND in available balance.");String key="PAYOUT:"+user+":"+UUID.randomUUID();int id=jdbc.queryForObject("INSERT INTO transactions(transaction_type,purpose,operation_key,payee_user_id,payee_bank_account_id,wallet_id,payment_stage,settlement_mode,amount,status,requested_by_user_id,refund_reason) VALUES ('PAYOUT','WALLET',?,?,?,?,?,'PLATFORM',?,'REQUESTED',?,?) RETURNING transaction_id",Integer.class,key,user,bank.getId(),w.getId(),"PAYOUT",req.amount(),user,req.reason());w.hold(req.amount());wallets.save(w);ledgers.save(new WalletLedger(w.getId(),"PAYOUT_HOLD","HELD",req.amount(),w.getHeldBalance(),id,null,"PAYOUT_HOLD:"+id));return new PayoutResponse(id,req.amount(),"REQUESTED",bank.getId(),null);}
 @Transactional public PayoutResponse decide(int admin,int id,PayoutDecision req){authorization.requireRole(admin,RoleCode.ADMIN);var row=jdbc.queryForMap("SELECT transaction_id,payee_user_id,payee_bank_account_id,amount,status FROM transactions WHERE transaction_id=? FOR UPDATE",id);if(!"REQUESTED".equals(row.get("status")))throw new ApiException(HttpStatus.CONFLICT,"Payout is already decided.");int user=((Number)row.get("payee_user_id")).intValue();BigDecimal amount=(BigDecimal)row.get("amount");var w=wallets.lockByUserId(user).orElseThrow();int bank=((Number)row.get("payee_bank_account_id")).intValue();if(Boolean.TRUE.equals(req.approved())){if(stores.existsByOwnerUserIdAndStatus(user,StorefrontStatus.SUSPENDED))throw new ApiException(HttpStatus.CONFLICT,"Suspended shop cannot withdraw.");if(w.getReturnShippingDebt().signum()>0||w.getAvailableBalance().compareTo(CommissionWalletService.MINIMUM_RESERVE)<0)throw new ApiException(HttpStatus.CONFLICT,"Payout is blocked by shipping debt or minimum reserve.");String ref=req.transferReference()==null?"MANUAL-"+id:req.transferReference();jdbc.update("UPDATE transactions SET status='SUCCESS',completed_at=now(),updated_at=now(),active_key=? WHERE transaction_id=?",ref,id);w.spendHold(amount);wallets.save(w);ledgers.save(new WalletLedger(w.getId(),"PAYOUT_COMPLETED","HELD",amount.negate(),w.getHeldBalance(),id,null,"PAYOUT_COMPLETED:"+id));return new PayoutResponse(id,amount,"SUCCESS",bank,ref);}jdbc.update("UPDATE transactions SET status='REJECTED',updated_at=now(),refund_reason=? WHERE transaction_id=?",req.reason(),id);w.releaseHold(amount);BigDecimal debtPaid=w.settleReturnShippingDebt(w.getAvailableBalance());if(debtPaid.signum()>0){w.debitAvailable(debtPaid);commission.recordDebtPayments(user,debtPaid,"PAYOUT_REJECTED:"+id);ledgers.save(new WalletLedger(w.getId(),"RETURN_SHIPPING_DEBT_SETTLED","AVAILABLE",debtPaid.negate(),w.getAvailableBalance(),id,null,"PAYOUT_REJECTED_DEBT:"+id));}wallets.save(w);ledgers.save(new WalletLedger(w.getId(),"PAYOUT_REJECTED","HELD",amount.negate(),w.getHeldBalance(),id,null,"PAYOUT_REJECTED:"+id));return new PayoutResponse(id,amount,"REJECTED",bank,null);}
}
