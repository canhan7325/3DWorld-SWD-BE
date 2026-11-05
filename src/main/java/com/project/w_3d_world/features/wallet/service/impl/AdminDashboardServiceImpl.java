package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.enums.StorefrontStatus;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.repository.AdminDashboardRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.service.AdminDashboardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final AdminDashboardRepository dashboard;
    private final StorefrontRepository stores;
    private final WalletRepository wallets;
    private final AuditLogRepository audits;
    private final PhysicalOrderRepository orders;

    private Instant[] range(LocalDate from,LocalDate to) {
        if(from==null || to==null || from.isAfter(to) || to.isAfter(LocalDate.of(9999,12,30)))
            throw ApiException.invalidRequest("Supply from/to dates, inclusive, with from <= to.");
        return new Instant[]{from.atStartOfDay(ZONE).toInstant(),to.plusDays(1).atStartOfDay(ZONE).toInstant()};
    }
    private void page(int page,int size){if(page<0||size<1||size>100)throw ApiException.invalidRequest("Invalid pagination.");}
    @Override public Map<String,Object> metrics(LocalDate from,LocalDate to){var r=range(from,to);return dashboard.metrics(r[0],r[1]);}
    @Override public List<Map<String,Object>> commissions(LocalDate from,LocalDate to,Integer shop,int page,int size){page(page,size);var r=range(from,to);return dashboard.commissions(r[0],r[1],shop,page,size);}
    @Override public List<Map<String,Object>> subscriptions(LocalDate from,LocalDate to,Integer shop,Integer user,int page,int size){page(page,size);var r=range(from,to);return dashboard.subscriptions(r[0],r[1],shop,user,page,size);}
    @Override public List<Map<String,Object>> wallets(boolean debtOnly,boolean lockedOnly,int days,int page,int size){page(page,size);days(days);return dashboard.wallets(debtOnly,lockedOnly,days,page,size);}
    @Override public Map<String,Object> shop(int shop){var result=new LinkedHashMap<>(dashboard.shopTotals(shop));result.put("paymentHistory",dashboard.subscriptions(Instant.EPOCH,Instant.now().plusSeconds(1),shop,null,0,100));return result;}
    @Override public Map<String,Object> debt(int shop){
        var result=new LinkedHashMap<>(dashboard.shopTotals(shop));
        var payments=dashboard.debtPayments(((Number)result.get("owner_user_id")).intValue());
        BigDecimal paid=payments.stream().map(p->((BigDecimal)p.get("signed_amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add);
        var assessments=dashboard.debtDetails(shop);
        // Allocate recorded debt repayments oldest first; never mutate the audit history.
        for(var row:assessments){BigDecimal original=(BigDecimal)row.get("debt_assessed");BigDecimal applied=paid.min(original);row.put("debt_repaid",applied);row.put("outstanding_debt",original.subtract(applied));paid=paid.subtract(applied);}
        result.put("assessments",assessments);result.put("repayments",payments);return result;
    }
    private void days(int days){if(days<1||days>3650)throw ApiException.invalidRequest("inactiveDays must be 1–3650.");}
    private void reason(String reason){if(reason==null||reason.isBlank()||reason.length()>2000)throw ApiException.invalidRequest("A reason up to 2000 characters is required.");}
    @Override @Transactional public void suspend(int admin,int shop,int inactiveDays,String reason){
        days(inactiveDays);reason(reason);
        Storefront store=stores.lockById(shop).orElseThrow(()->ApiException.invalidRequest("Shop does not exist."));
        var wallet=wallets.lockByUserId(store.getOwnerUserId()).orElseThrow(()->ApiException.invalidRequest("Wallet does not exist."));
        Instant cutoff=Instant.now().minusSeconds(inactiveDays*86400L);
        if(wallet.getReturnShippingDebt().signum()<=0 || wallet.getShippingDebtSince()==null || wallet.getShippingDebtSince().isAfter(cutoff)
                || orders.existsRecentShopOrder(shop,cutoff))
            throw new ApiException(HttpStatus.CONFLICT,"Shop must have overdue shipping debt and no recent non-cancelled orders.");
        if(store.getStatus()==StorefrontStatus.SUSPENDED)throw new ApiException(HttpStatus.CONFLICT,"Shop is already suspended.");
        store.suspendTrading();
        audits.save(new AuditLog(admin,"SHOP_SUSPENDED_FOR_DEBT","STOREFRONT",shop,reason,
                JsonValues.write(Map.of("debt",wallet.getReturnShippingDebt(),"inactiveDays",inactiveDays))));
    }
    @Override @Transactional public void restore(int admin,int shop,String reason){
        reason(reason);var store=stores.lockById(shop).orElseThrow(()->ApiException.invalidRequest("Shop does not exist."));
        var wallet=wallets.lockByUserId(store.getOwnerUserId()).orElseThrow();
        if(wallet.getReturnShippingDebt().signum()>0)throw new ApiException(HttpStatus.CONFLICT,"Shipping debt must be cleared before restoring trading.");
        if(!audits.existsByActionTypeAndEntityTypeAndEntityId("SHOP_SUSPENDED_FOR_DEBT","STOREFRONT",shop))
            throw new ApiException(HttpStatus.CONFLICT,"This shop was not suspended through debt management.");
        if(store.getStatus()!=StorefrontStatus.SUSPENDED)throw new ApiException(HttpStatus.CONFLICT,"Shop is not suspended.");
        store.restoreTrading();
        audits.save(new AuditLog(admin,"SHOP_DEBT_SUSPENSION_RESTORED","STOREFRONT",shop,reason,null));
    }
}
