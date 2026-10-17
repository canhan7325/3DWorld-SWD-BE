package com.project.w_3d_world.features.cart.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.cart.dto.CartRequests.ItemRequest;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartItemResponse;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartResponse;
import com.project.w_3d_world.features.cart.entity.Cart;
import com.project.w_3d_world.features.cart.entity.CartItem;
import com.project.w_3d_world.features.cart.repository.CartItemRepository;
import com.project.w_3d_world.features.cart.repository.CartRepository;
import com.project.w_3d_world.features.cart.service.CartService;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final StorefrontRepository storefronts;

    @Override
    @Transactional
    public CartResponse get(int userId) {
        return response(findOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse add(int userId, ItemRequest request) {
        ProductVariant variant = purchasableVariant(userId, request.variantId());
        Cart cart = findOrCreate(userId);
        CartItem item = items.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElseGet(() -> new CartItem(cart.getId(), variant.getId(), 0));
        int quantity = item.getQuantity() + request.quantity();
        ensureAvailable(variant, quantity);
        item.changeQuantity(quantity);
        items.save(item);
        cart.touch();
        return response(cart);
    }

    @Override
    @Transactional
    public CartResponse update(int userId, int itemId, int quantity) {
        if (quantity < 1) {
            throw ApiException.invalidRequest("Quantity must be positive.");
        }
        CartItem item = ownedItem(userId, itemId);
        ProductVariant variant = variants.findById(item.getVariantId())
                .orElseThrow(() -> ApiException.invalidRequest("Variant is unavailable."));
        ensureAvailable(variant, quantity);
        item.changeQuantity(quantity);
        return response(findOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse remove(int userId, int itemId) {
        CartItem item = ownedItem(userId, itemId);
        items.delete(item);
        return response(findOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse revalidate(int userId) {
        return response(findOrCreate(userId));
    }

    private Cart findOrCreate(int userId) {
        return carts.findByUserId(userId).orElseGet(() -> carts.save(new Cart(userId)));
    }

    private CartItem ownedItem(int userId, int itemId) {
        Cart cart = findOrCreate(userId);
        CartItem item = items.findById(itemId)
                .orElseThrow(() -> ApiException.invalidRequest("Cart item does not exist."));
        if (!cart.getId().equals(item.getCartId())) {
            throw ApiException.invalidRequest("You do not have permission to access this cart item.");
        }
        return item;
    }

    private ProductVariant purchasableVariant(int userId, int variantId) {
        ProductVariant variant = variants.findById(variantId)
                .orElseThrow(() -> ApiException.invalidRequest("Variant does not exist."));
        Product product = products.findById(variant.getProductId())
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .orElseThrow(() -> ApiException.invalidRequest("Product is not available."));
        var storefront = storefronts.findById(product.getStorefrontId())
                .orElseThrow(() -> ApiException.invalidRequest("Storefront is not available."));
        if (!variant.isActive() || !storefront.canPublish()) {
            throw ApiException.invalidRequest("This product is currently unavailable.");
        }
        if (storefront.getOwnerUserId().equals(userId)) {
            throw ApiException.invalidRequest("You cannot buy your own product.");
        }
        return variant;
    }

    private void ensureAvailable(ProductVariant variant, int quantity) {
        int available = variant.getStockQuantity() - variant.getReservedQuantity();
        if (quantity > available) {
            throw ApiException.invalidRequest("Requested quantity exceeds available stock.");
        }
    }

    private CartResponse response(Cart cart) {
        List<CartItemResponse> result = items.findByCartIdOrderByIdAsc(cart.getId()).stream()
                .map(this::itemResponse)
                .toList();
        BigDecimal total = result.stream()
                .filter(item -> !item.unavailable())
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), result, total);
    }

    private CartItemResponse itemResponse(CartItem item) {
        ProductVariant variant = variants.findById(item.getVariantId()).orElse(null);
        Product product = variant == null ? null : products.findById(variant.getProductId()).orElse(null);
        boolean unavailable = variant == null || product == null || !"PUBLISHED".equals(product.getStatus())
                || !variant.isActive()
                || variant.getStockQuantity() - variant.getReservedQuantity() < item.getQuantity();
        BigDecimal price = variant == null ? BigDecimal.ZERO : variant.getUnitPrice();
        return new CartItemResponse(
                item.getId(), item.getVariantId(), product == null ? null : product.getTitle(),
                variant == null ? null : variant.getSku(), price, item.getQuantity(),
                price.multiply(BigDecimal.valueOf(item.getQuantity())), unavailable);
    }
}
