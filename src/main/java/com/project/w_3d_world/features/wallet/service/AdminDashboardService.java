package com.project.w_3d_world.features.wallet.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AdminDashboardService {
    Map<String,Object> metrics(LocalDate from, LocalDate to);
    List<Map<String,Object>> commissions(LocalDate from, LocalDate to, Integer shop, int page, int size);
    List<Map<String,Object>> subscriptions(LocalDate from, LocalDate to, Integer shop, Integer user, int page, int size);
    List<Map<String,Object>> wallets(boolean debtOnly, boolean lockedOnly, int inactiveDays, int page, int size);
    Map<String,Object> shop(int shop);
    Map<String,Object> debt(int shop);
    void suspend(int admin, int shop, int inactiveDays, String reason);
    void restore(int admin, int shop, String reason);
}
