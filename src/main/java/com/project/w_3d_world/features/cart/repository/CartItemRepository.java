package com.project.w_3d_world.features.cart.repository;

import com.project.w_3d_world.features.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    List<CartItem> findByCartIdOrderByIdAsc(Integer cartId);

    Optional<CartItem> findByCartIdAndVariantId(Integer cartId, Integer variantId);
}
