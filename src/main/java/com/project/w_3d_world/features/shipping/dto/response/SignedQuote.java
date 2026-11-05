package com.project.w_3d_world.features.shipping.dto.response;

import java.util.List;

public record SignedQuote(int userId, String fingerprint, long expiresAt, String version, List<ShopQuote> shops) {}
