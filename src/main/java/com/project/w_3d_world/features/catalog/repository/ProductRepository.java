package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByStorefrontId(Integer storefrontId);
}
