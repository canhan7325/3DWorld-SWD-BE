package com.project.w_3d_world.features.wallet.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AdminDashboardRepository {
    Map<String,Object> metrics(Instant from, Instant to);
    List<Map<String,Object>> commissions(Instant from, Instant to, Integer shop, int page, int size);
    List<Map<String,Object>> subscriptions(Instant from, Instant to, Integer shop, Integer user, int page, int size);
    List<Map<String,Object>> wallets(boolean debtOnly, boolean lockedOnly, int inactiveDays, int page, int size);
    List<Map<String,Object>> debtDetails(int shop);
    List<Map<String,Object>> debtPayments(int seller);
    Map<String,Object> shopTotals(int shop);
}
