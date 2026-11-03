package com.project.w_3d_world.features.wallet.service;

import java.util.Map;

public interface EarningReconciliationService {
    Map<String,Object> status(int admin, int orderId);
    Map<String,Object> reconcile(int admin, int orderId);
}
