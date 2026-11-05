package com.project.w_3d_world.features.catalog.dto.response;

import com.project.w_3d_world.features.catalog.entity.ProductMedia;

public record MediaResponse(int id, Integer variantId, String url, int sortOrder) {
        public static MediaResponse from(ProductMedia media) {
            return new MediaResponse(media.getId(), media.getVariantId(), media.getMediaUrl(), media.getSortOrder());
        }
    }
