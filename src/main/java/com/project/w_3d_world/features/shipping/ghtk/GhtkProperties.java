package com.project.w_3d_world.features.shipping.ghtk;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ghtk")
public record GhtkProperties(boolean enabled, String token, String baseUrl, String partnerCode,
                             String transport, BigDecimal paddingCm, long quoteTtlSeconds) {}
