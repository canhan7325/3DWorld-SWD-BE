package com.project.w_3d_world.features.cart.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.cart.dto.CartRequests.ItemRequest;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartItemResponse;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartResponse;
import com.project.w_3d_world.features.cart.dto.CartResponses.ShopGroup;
import com.project.w_3d_world.features.cart.entity.Cart;
import com.project.w_3d_world.features.cart.entity.CartItem;
import com.project.w_3d_world.features.cart.repository.CartItemRepository;
import com.project.w_3d_world.features.cart.repository.CartRepository;
import com.project.w_3d_world.features.cart.service.CartService;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import com.project.w_3d_world.features.catalog.repository.ProductMediaRepository;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final StorefrontRepository storefronts;
    private final ProductMediaRepository media;

    @Override
    @Transactional
    public CartResponse get(int userId) {
        return response(findOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse add(int userId, ItemRequest request) {
        Cart cart = findOrCreate(userId);
        ProductVariant variant = purchasableVariant(userId, request.variantId());
        CartItem item = items.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElseGet(() -> new CartItem(cart.getId(), variant.getId(), 0));
        int quantity;
        try {
            quantity = Math.addExact(item.getQuantity(), request.quantity());
        } catch (ArithmeticException exception) {
            throw ApiException.invalidRequest("Quantity exceeds the supported limit.");
        }
        if (item.getPriceAtAdded() == null) {
            item.acknowledgePrice(variant.getUnitPrice());
        }
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
        ProductVariant variant = purchasableVariant(userId, item.getVariantId());
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
        users.lockById(userId).filter(user -> user.isActive())
                .orElseThrow(ApiException::invalidCredentials);
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
        if (!variant.isActive() || !"ACTIVE".equals(variant.getStatus()) || !storefront.canPublish()) {
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
        var cartItems = items.findByCartIdOrderByIdAsc(cart.getId());
        var variantMap = variants.findAllById(cartItems.stream().map(CartItem::getVariantId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, value -> value));
        var productMap = products.findAllById(variantMap.values().stream().map(ProductVariant::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, value -> value));
        var storeMap = storefronts.findByIdIn(productMap.values().stream().map(Product::getStorefrontId).distinct().toList()).stream()
                .collect(Collectors.toMap(value -> value.getId(), value -> value));
        var thumbnailMap = new HashMap<Integer, String>();
        media.findByProductIdInOrderBySortOrderAscIdAsc(productMap.keySet())
                .forEach(image -> thumbnailMap.putIfAbsent(image.getProductId(), image.getMediaUrl()));
        List<CartItemResponse> result = cartItems.stream().map(item -> {
            ProductVariant variant = variantMap.get(item.getVariantId());
            Product product = variant == null ? null : productMap.get(variant.getProductId());
            var store = product == null ? null : storeMap.get(product.getStorefrontId());
            boolean storefrontAvailable = store != null && !store.getOwnerUserId().equals(cart.getUserId()) && store.canPublish();
            return itemResponse(item, variant, product, storefrontAvailable,
                    product == null ? null : thumbnailMap.get(product.getId()));
        }).toList();
        BigDecimal total = result.stream()
                .filter(item -> !item.unavailable())
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var grouped = result.stream().collect(Collectors.groupingBy(
                item -> item.storefrontId() == null ? 0 : item.storefrontId(), TreeMap::new,
                Collectors.toList()));
        var groups = grouped.entrySet().stream().map(entry ->
                new ShopGroup(entry.getKey(), entry.getValue(),
                        entry.getValue().stream().filter(item -> !item.unavailable()).map(CartItemResponse::lineTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
        return new CartResponse(cart.getId(), result, total, groups);
    }

    private CartItemResponse itemResponse(CartItem item, ProductVariant variant, Product product,
                                         boolean storefrontAvailable, String thumbnail) {
        boolean unavailable = !storefrontAvailable || variant == null || product == null
                || !"PUBLISHED".equals(product.getStatus())
                || !variant.isActive() || !"ACTIVE".equals(variant.getStatus())
                || variant.getStockQuantity() - variant.getReservedQuantity() < item.getQuantity();
        BigDecimal price = variant == null ? BigDecimal.ZERO : variant.getUnitPrice();
        return new CartItemResponse(
                item.getId(), item.getVariantId(), product == null ? null : product.getTitle(),
                variant == null ? null : variant.getSku(), price, item.getQuantity(),
                price.multiply(BigDecimal.valueOf(item.getQuantity())), unavailable,
                item.getPriceAtAdded(), item.getPriceAtAdded() == null
                        || item.getPriceAtAdded().compareTo(price) != 0,
                product == null ? null : product.getId(), product == null ? null : product.getStorefrontId(),
                thumbnail);
    }

    @Override
    @Transactional
    public CartResponse acknowledgePrice(int userId, int itemId) {
        CartItem item = ownedItem(userId, itemId);
        ProductVariant variant = purchasableVariant(userId, item.getVariantId());
        item.acknowledgePrice(variant.getUnitPrice());
        return response(findOrCreate(userId));
    }
}
