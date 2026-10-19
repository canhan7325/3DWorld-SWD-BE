package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.entity.ProductMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Integer> {
    List<ProductMedia> findByProductIdOrderBySortOrderAscIdAsc(Integer productId);
}
