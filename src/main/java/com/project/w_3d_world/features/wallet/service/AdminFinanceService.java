package com.project.w_3d_world.features.wallet.service;

import java.util.List;
import java.util.Map;

public interface AdminFinanceService {
    Map<String,Object> order(int actor, int id);
    List<Map<String,Object>> orders(int actor, int page, int size);
    List<Map<String,Object>> packages(int actor);
    Map<String,Object> cash(int actor);
}
