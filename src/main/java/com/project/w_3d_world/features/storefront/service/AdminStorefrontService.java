package com.project.w_3d_world.features.storefront.service;

import com.project.w_3d_world.features.storefront.dto.response.ListResponse;
import com.project.w_3d_world.features.storefront.dto.response.SubscriptionRow;
import java.util.List;

public interface AdminStorefrontService {

    ListResponse list(String keyword, String status, String plan, String sort, int page, int size);
    List<SubscriptionRow> subscriptions(int storefrontId);
}
