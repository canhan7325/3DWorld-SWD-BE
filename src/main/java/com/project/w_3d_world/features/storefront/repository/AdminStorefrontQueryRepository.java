package com.project.w_3d_world.features.storefront.repository;

import java.util.List;
import java.util.Map;

public interface AdminStorefrontQueryRepository {
    boolean validSort(String sort);
    List<Map<String, Object>> page(String keyword, String status, String plan, String sort, int page, int size);
    long count(String keyword, String status, String plan);
    Map<String, Object> summary(int expiringDays);
    boolean exists(int storefrontId);
    List<Map<String, Object>> subscriptions(int storefrontId);
}
