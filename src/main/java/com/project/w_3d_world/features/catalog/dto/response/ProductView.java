package com.project.w_3d_world.features.catalog.dto.response;

import java.time.Instant;
import java.util.List;

public record ProductView(int id, int storefrontId, String title, String description, String category,
                              String specifications, String shippingClass, String status, String moderationReason,
                              String revision, Instant updatedAt, List<MediaResponse> media, List<VariantView> variants) {}
