package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByStorefrontId(Integer storefrontId);

    @Query("""
            select p from Product p
            where p.status = 'PUBLISHED'
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')))
              and (:category is null or lower(p.category) = lower(:category))
            """)
    Page<Product> searchPublic(
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable);

    java.util.Optional<Product> findByIdAndStatus(Integer id, String status);
}
