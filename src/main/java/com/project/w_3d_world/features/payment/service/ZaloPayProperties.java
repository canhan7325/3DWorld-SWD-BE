package com.project.w_3d_world.features.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.zalopay")
public record ZaloPayProperties(
        int appId,
        String key1,
        String key2,
        String createUrl,
        String queryUrl,
        String refundUrl,
        String callbackUrl) {}
