package com.project.w_3d_world.features.wallet.repository;

import java.util.List;
import java.util.Map;

public interface EarningReleaseStatusRepository {
    List<Map<String,Object>> status(int orderId);
}
