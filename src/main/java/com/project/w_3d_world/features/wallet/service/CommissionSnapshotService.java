package com.project.w_3d_world.features.wallet.service;

public interface CommissionSnapshotService {
    void reconcile(int admin, int orderId);
}
