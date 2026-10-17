package com.project.w_3d_world.features.order.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.cart.entity.CartItem;
import com.project.w_3d_world.features.cart.repository.CartItemRepository;
import com.project.w_3d_world.features.cart.repository.CartRepository;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.order.dto.OrderRequests.CheckoutRequest;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderItemResponse;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderResponse;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.entity.PhysicalOrderItem;
import com.project.w_3d_world.features.order.repository.PhysicalOrderItemRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final StorefrontRepository storefronts;
    private final PhysicalOrderRepository orders;
    private final PhysicalOrderItemRepository orderItems;

    @Value("${app.order.reservation-ttl-minutes:15}")
    private long reservationTtlMinutes;

    @Override
    @Transactional
    public List<OrderResponse> checkout(int userId, CheckoutRequest request) {
        if (request.cartItemIds().stream().distinct().count() != request.cartItemIds().size()) {
            throw ApiException.invalidRequest("Duplicate cart item IDs are not allowed.");
        }
        Integer cartId = carts.findByUserId(userId)
                .orElseThrow(() -> ApiException.invalidRequest("Cart does not exist.")).getId();
        List<CartItem> selected = request.cartItemIds().stream()
                .map(id -> ownedCartItem(cartId, id))
                .toList();
        Map<Integer, List<CartItem>> byStorefront = selected.stream()
                .collect(Collectors.groupingBy(item -> storefrontId(item.getVariantId())));
        UUID checkoutGroup = UUID.randomUUID();

        return byStorefront.entrySet().stream()
                .map(entry -> createOrder(userId, request, checkoutGroup, entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> buyerOrders(int userId) {
        return orders.findByBuyerUserIdOrderByCreatedAtDesc(userId).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> sellerOrders(int userId) {
        return storefronts.findByOwnerUserId(userId)
                .map(storefront -> orders.findByStorefrontIdOrderByCreatedAtDesc(storefront.getId())
                        .stream()
                        .map(this::response)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(int sellerId, int orderId, String status) {
        PhysicalOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        storefronts.findByOwnerUserId(sellerId)
                .filter(storefront -> storefront.getId().equals(order.getStorefrontId()))
                .orElseThrow(() -> ApiException.invalidRequest("You do not own this storefront."));
        try {
            order.transition(status);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRequest(exception.getMessage());
        }
        if ("CANCELLED".equals(status)) {
            releaseReservedItems(order.getId());
        }
        return response(order);
    }

    @Override
    @Transactional
    public void releaseExpiredReservations() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(reservationTtlMinutes));
        for (String status : List.of("CREATED", "PENDING_PAYMENT")) {
            for (PhysicalOrder order : orders.findByStatusAndCreatedAtBefore(status, cutoff)) {
                releaseReservedItems(order.getId());
                order.transition("CANCELLED");
            }
        }
    }

    private OrderResponse createOrder(
            int userId, CheckoutRequest request, UUID checkoutGroup, int storefrontId, List<CartItem> cartItems) {
        BigDecimal subtotal = cartItems.stream().map(item -> {
            ProductVariant variant = reserve(item.getVariantId(), item.getQuantity());
            return variant.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
        PhysicalOrder order = new PhysicalOrder(
                userId, storefrontId, request.shippingAddressSnapshot(), checkoutGroup,
                subtotal, BigDecimal.ZERO);
        order.setShippingAddressId(request.shippingAddressId());
        order = orders.saveAndFlush(order);
        order.transition("PENDING_PAYMENT");
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = variants.findById(cartItem.getVariantId()).orElseThrow();
            orderItems.save(new PhysicalOrderItem(order.getId(), variant.getId(), variant.getSku(),
                    variant.getVariantAttributes(), cartItem.getQuantity(), variant.getUnitPrice()));
            this.cartItems.delete(cartItem);
        }
        return response(order);
    }

    private ProductVariant reserve(int variantId, int quantity) {
        ProductVariant variant = variants.findByIdForUpdate(variantId)
                .orElseThrow(() -> ApiException.invalidRequest("Variant does not exist."));
        Product product = products.findById(variant.getProductId())
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .orElseThrow(() -> ApiException.invalidRequest("Product is not available."));
        boolean storefrontActive = storefronts.findById(product.getStorefrontId())
                .map(storefront -> storefront.canPublish())
                .orElse(false);
        if (!variant.isActive() || !storefrontActive) {
            throw ApiException.invalidRequest(
                    "Product is not available for checkout.");
        }
        try {
            variant.reserve(quantity);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRequest("Requested quantity exceeds available stock.");
        }
        return variant;
    }

    private int storefrontId(int variantId) {
        ProductVariant variant = variants.findById(variantId).orElseThrow();
        return products.findById(variant.getProductId()).orElseThrow().getStorefrontId();
    }

    private CartItem ownedCartItem(int cartId, int itemId) {
        CartItem item = cartItems.findById(itemId)
                .orElseThrow(() -> ApiException.invalidRequest("Cart item does not exist."));
        if (!item.getCartId().equals(cartId)) {
            throw ApiException.invalidRequest("Cart item does not belong to the current user.");
        }
        return item;
    }

    private OrderResponse response(PhysicalOrder order) {
        List<OrderItemResponse> items = orderItems.findByOrderId(order.getId()).stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getVariantId(), item.getSkuSnapshot(),
                        item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return OrderResponse.from(order, items);
    }

    private void releaseReservedItems(int orderId) {
        orderItems.findByOrderId(orderId).forEach(item ->
                variants.findByIdForUpdate(item.getVariantId())
                        .ifPresent(variant -> variant.release(item.getQuantity())));
    }
}
