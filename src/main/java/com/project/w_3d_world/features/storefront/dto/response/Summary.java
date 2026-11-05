package com.project.w_3d_world.features.storefront.dto.response;



public record Summary(long total, long active, long expired, long pendingPayment, long none,
                          long suspended, long expiringSoon) {}
