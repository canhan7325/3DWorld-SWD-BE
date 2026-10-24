package com.project.w_3d_world.features.catalog.dto;

import com.project.w_3d_world.common.exception.ApiException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record MarketplacePage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> MarketplacePage<T> from(Page<T> page) {
        return new MarketplacePage<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid page or size.");
        }
        return PageRequest.of(page, size,
                Sort.by("id").descending());
    }
}
