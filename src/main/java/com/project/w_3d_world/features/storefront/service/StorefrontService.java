package com.project.w_3d_world.features.storefront.service;

import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.storefront.dto.request.CreateRequest;
import com.project.w_3d_world.features.storefront.dto.request.UpdateRequest;
import com.project.w_3d_world.features.storefront.dto.response.ShopResponse;
import com.project.w_3d_world.features.storefront.dto.response.SubscriptionOverview;
import com.project.w_3d_world.features.storefront.dto.response.SubscriptionResponse;

public interface StorefrontService {

    ShopResponse publicShop(int id);
    ShopResponse mine(int userId);
    ShopResponse create(int userId, CreateRequest request);
    ShopResponse update(int userId, UpdateRequest request);
    SubscriptionOverview subscription(int userId);
    SubscriptionResponse subscribe(int userId);
    void activateVerifiedPayment(PaymentTransaction transaction);
}
