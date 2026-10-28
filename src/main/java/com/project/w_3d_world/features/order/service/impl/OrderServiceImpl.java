package com.project.w_3d_world.features.order.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.cart.entity.CartItem;
import com.project.w_3d_world.features.cart.repository.CartItemRepository;
import com.project.w_3d_world.features.cart.repository.CartRepository;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.order.dto.OrderRequests.CheckoutRequest;
import com.project.w_3d_world.features.order.dto.OrderRequests.HistoryResponse;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderItemResponse;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderResponse;
import com.project.w_3d_world.features.order.entity.OrderStatusHistory;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.entity.PhysicalOrderItem;
import com.project.w_3d_world.features.order.repository.OrderStatusHistoryRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderItemRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.payment.service.CodPaymentService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.shipping.service.ShippingQuoteService;
import com.project.w_3d_world.features.shipping.dto.ShippingQuoteDtos.ShopQuote;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final OrderStatusHistoryRepository history;
    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final ShippingQuoteService shippingQuotes;
    private final CodPaymentService cod;
    private final com.project.w_3d_world.features.wallet.service.PlatformConfigService platformConfig;
    private final com.project.w_3d_world.features.wallet.service.CommissionWalletService wallet;
    private final com.project.w_3d_world.features.shipping.service.ShipmentAutoCreationService shipmentAutoCreation;

    @Value("${app.order.reservation-ttl-minutes:15}")
    private long reservationTtlMinutes;
    @Value("${app.order.shipping-fee:0}")
    private BigDecimal shippingFee;
    @Value("${app.order.commission-rate:0}")
    private BigDecimal commissionRate;
    @Value("${app.order.refund-window-hours:72}")
    private int refundHours;
    @Value("${app.order.auto-confirm-hours:24}")
    private int autoConfirmHours;
    @Value("${app.order.cod-acceptance-ttl-minutes:1440}")
    private long codAcceptanceTtlMinutes;

    @Override
    @Transactional
    public List<OrderResponse> checkout(int userId, CheckoutRequest request) {
        users.lockById(userId).filter(user -> user.isActive()).orElseThrow(ApiException::invalidCredentials);
        if (request.cartItemIds() == null || request.cartItemIds().isEmpty()
                || request.cartItemIds().stream().anyMatch(Objects::isNull)
                || request.cartItemIds().stream().distinct().count() != request.cartItemIds().size()) {
            throw ApiException.invalidRequest("Select distinct cart item IDs.");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 255 || request.shippingAddressId() == null) {
            throw ApiException.invalidRequest("Provide an address ID and a non-empty checkout idempotency key.");
        }
        if (!Set.of("COD", "GATEWAY").contains(request.paymentMethod())) {
            throw ApiException.invalidRequest("Payment method must be COD or GATEWAY/ZALOPAY.");
        }
        String fingerprint = fingerprint(request);
        var existing = orders.findByBuyerUserIdAndIdempotencyKeyOrderByIdAsc(userId, request.idempotencyKey());
        if (!existing.isEmpty()) {
            if (!fingerprint.equals(existing.getFirst().getRequestFingerprint())) {
                throw ApiException.invalidRequest("The checkout key was already used for another request.");
            }
            return existing.stream().map(this::response).toList();
        }
        var address = addresses.findById(request.shippingAddressId())
                .filter(value -> value.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.invalidRequest("Select an address owned by the current user."));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recipientName", address.getRecipientName());
        snapshot.put("phone", address.getPhone());
        snapshot.put("addressLine", address.getAddressLine());
        snapshot.put("city", address.getCity());
        snapshot.put("province", address.getProvince());
        snapshot.put("district", address.getDistrict());
        snapshot.put("ward", address.getWard());
        String addressJson = JsonValues.write(snapshot);
        int cartId = carts.findByUserId(userId)
                .orElseThrow(() -> ApiException.invalidRequest("Cart does not exist.")).getId();
        List<CartItem> selected = request.cartItemIds().stream().map(id -> ownedCartItem(cartId, id)).toList();
        // Lock variants globally in a stable order across all seller child orders.
        Map<Integer, ProductVariant> locked = new TreeMap<>();
        selected.stream().map(CartItem::getVariantId).distinct().sorted().forEach(id ->
                locked.put(id, variants.findByIdForUpdate(id)
                        .orElseThrow(() -> ApiException.invalidRequest("Variant does not exist."))));
        var quotedShops = shippingQuotes.enabled()
                ? shippingQuotes.validate(userId, request.cartItemIds(), request.shippingAddressId(), request.shippingQuoteToken())
                : Map.<Integer, ShopQuote>of();
        Map<Integer, List<CartItem>> groups = new TreeMap<>();
        for (CartItem item : selected) {
            ProductVariant variant = locked.get(item.getVariantId());
            Product product = products.findById(variant.getProductId()).orElseThrow();
            var storefront = storefronts.findById(product.getStorefrontId()).orElseThrow();
            if (!"PUBLISHED".equals(product.getStatus()) || !variant.isActive() || !"ACTIVE".equals(variant.getStatus())
                    || !storefront.canPublish() || storefront.getOwnerUserId().equals(userId)) {
                throw ApiException.invalidRequest("A selected product is unavailable for this buyer.");
            }
            if (item.getPriceAtAdded() == null || item.getPriceAtAdded().compareTo(variant.getUnitPrice()) != 0) {
                throw ApiException.invalidRequest("A cart price changed. Review and acknowledge it before checkout.");
            }
            if (item.getQuantity() < 1 || variant.availableQuantity() < item.getQuantity()) {
                throw ApiException.invalidRequest("Requested quantity exceeds available stock.");
            }
            variant.reserve(item.getQuantity());
            groups.computeIfAbsent(product.getStorefrontId(), key -> new ArrayList<>()).add(item);
        }
        if (reservationTtlMinutes < 1 || ("COD".equals(request.paymentMethod()) && codAcceptanceTtlMinutes < 1)
                || shippingFee.signum() < 0 || commissionRate.signum() < 0
                || commissionRate.compareTo(BigDecimal.ONE) > 0 || refundHours < 0 || autoConfirmHours < 0) {
            throw new IllegalStateException("Invalid checkout policy configuration.");
        }
        UUID checkoutGroup = UUID.randomUUID();
        BigDecimal appliedCommissionRate = platformConfig.decimal(
                com.project.w_3d_world.features.wallet.service.PlatformConfigService.COMMISSION_RATE, new BigDecimal("0.05"));
        if (appliedCommissionRate.signum() < 0 || appliedCommissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Invalid commission rate configuration.");
        }
        boolean codCheckout = "COD".equals(request.paymentMethod());
        Instant expiry = Instant.now().plusSeconds((codCheckout ? codAcceptanceTtlMinutes : reservationTtlMinutes) * 60);
        List<OrderResponse> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            BigDecimal subtotal = entry.getValue().stream()
                    .map(item -> locked.get(item.getVariantId()).getUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            PhysicalOrder order = new PhysicalOrder(userId, entry.getKey(), addressJson,
                    checkoutGroup, subtotal, quotedShops.containsKey(entry.getKey())
                            ? quotedShops.get(entry.getKey()).selected().shippingFee() : shippingFee);
            order.setShippingAddressId(address.getId());
            order.initializeCheckout(request.idempotencyKey(), fingerprint, expiry,
                    appliedCommissionRate, refundHours, autoConfirmHours);
            if (codCheckout) order.selectCod();
            orders.saveAndFlush(order);
            shipmentAutoCreation.createForOrder(order);
            if (codCheckout) {
                cod.create(order);
            } else {
                order.transition("PENDING_PAYMENT");
            }
            history.save(new OrderStatusHistory(order.getId(), null, order.getStatus(), userId,
                    codCheckout ? "COD checkout: awaiting seller acceptance, no online payment required" : "Checkout"));
            Map<Integer, PhysicalOrderItem> checkoutItems = new TreeMap<>();
            for (CartItem item : entry.getValue()) {
                ProductVariant variant = locked.get(item.getVariantId());
                PhysicalOrderItem orderItem = new PhysicalOrderItem(order.getId(), variant.getId(),
                        variant.getSku(), variant.getVariantAttributes(), item.getQuantity(), variant.getUnitPrice());
                orderItem.snapshotProductTitle(products.findById(variant.getProductId()).orElseThrow().getTitle());
                orderItems.saveAndFlush(orderItem);
                checkoutItems.put(item.getId(), orderItem);
                cartItems.delete(item);
            }
            if (quotedShops.containsKey(entry.getKey())) {
                shippingQuotes.persist(order, quotedShops.get(entry.getKey()), checkoutItems);
            }
            result.add(response(order));
        }
        return result;
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
                .map(store -> orders.findByStorefrontIdOrderByCreatedAtDesc(store.getId())
                        .stream().map(this::response).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(int sellerId, int orderId, String status) {
        PhysicalOrder order = orders.lockById(orderId)
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        boolean owner = storefronts.findByOwnerUserId(sellerId)
                .filter(store -> store.getId().equals(order.getStorefrontId())).isPresent();
        boolean operator = users.findWithRolesById(sellerId).filter(user -> user.isActive() && user.isEmailVerified())
                .map(user -> user.getUserRoles().stream().anyMatch(grant -> grant.isActive()
                        && (grant.getRole().getCode() == RoleCode.ADMIN
                            || grant.getRole().getCode() == RoleCode.PRINT_STAFF)))
                .orElse(false);
        if (!owner && !operator) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "You cannot process this order.");
        }
        if (order.getStatus().equals(status)) {
            return response(order);
        }
        wallet.requireNoActiveRefund(orderId);
        // COD acceptance does not fabricate a paid fact; online orders still require verified payment.
        boolean codReady = order.isCod() && "CREATED".equals(order.getStatus());
        if (!"PROCESSING".equals(status) || !("PAID".equals(order.getStatus()) || codReady)) {
            throw ApiException.invalidRequest("Processing requires a PAID online order or a newly CREATED COD order.");
        }
        if (codReady && order.reservationExpired(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "COD seller acceptance window expired. The buyer must checkout again.");
        }
        String from = order.getStatus();
        if (!order.hasReservation()) {
            throw ApiException.invalidRequest("Order has no stock reservation to consume.");
        }
        for (PhysicalOrderItem item : sortedItems(orderId)) {
            variants.findByIdForUpdate(item.getVariantId()).orElseThrow().consume(item.getQuantity());
        }
        order.consumed();
        order.transition(status);
        history.save(new OrderStatusHistory(orderId, from, status, sellerId,
                codReady ? "Seller accepted COD order; cash not yet collected" : "Seller processing"));
        return response(order);
    }

    @Override
    @Transactional
    public void releaseExpiredReservations() {
        Instant now = Instant.now();
        for (Integer id : orders.findExpiredReservationIds(now)) {
            PhysicalOrder order = orders.lockById(id).orElseThrow();
            if (!order.hasReservation() || !order.reservationExpired(now)
                    || !("CREATED".equals(order.getStatus()) || "PENDING_PAYMENT".equals(order.getStatus()))) {
                continue;
            }
            for (PhysicalOrderItem item : sortedItems(id)) {
                variants.findByIdForUpdate(item.getVariantId()).orElseThrow().release(item.getQuantity());
            }
            String from = order.getStatus();
            order.released();
            order.transition("CANCELLED");
            cod.cancel(order);
            history.save(new OrderStatusHistory(id, from, "CANCELLED", null, "Reservation expired"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse detail(int userId, int orderId) {
        return response(readable(userId, orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryResponse> history(int userId, int orderId) {
        readable(userId, orderId);
        return history.findByOrderIdOrderByChangedAtAscIdAsc(orderId).stream()
                .map(HistoryResponse::from).toList();
    }

    @Override
    @Transactional
    public OrderResponse cancel(int userId, int orderId, String reason) {
        PhysicalOrder order = orders.lockById(orderId).filter(value -> value.getBuyerUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        if ("CANCELLED".equals(order.getStatus())) {
            return response(order);
        }
        if (!Set.of("CREATED", "PENDING_PAYMENT").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only an unpaid order can be cancelled directly. Paid orders require the refund workflow.");
        }
        String from = order.getStatus();
        if (order.hasReservation()) {
            for (PhysicalOrderItem item : sortedItems(orderId)) {
                variants.findByIdForUpdate(item.getVariantId()).orElseThrow().release(item.getQuantity());
            }
            order.released();
        }
        order.cancel(reason);
        cod.cancel(order);
        history.save(new OrderStatusHistory(orderId, from, "CANCELLED", userId, reason));
        return response(order);
    }

    @Override
    @Transactional
    public OrderResponse confirmReceived(int userId, int orderId) {
        PhysicalOrder order = orders.lockById(orderId).filter(value -> value.getBuyerUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        if ("RECEIVED".equals(order.getStatus())) {
            return response(order);
        }
        if (!"DELIVERED".equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a delivered order can be confirmed.");
        }
        order.confirmReceived();
        history.save(new OrderStatusHistory(orderId, "DELIVERED", "RECEIVED", userId, "Buyer confirmed receipt"));
        wallet.received(orderId, order.getReceivedAt());
        wallet.releaseEligible(orderId);
        return response(order);
    }

    private PhysicalOrder readable(int userId, int orderId) {
        PhysicalOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        boolean buyer = order.getBuyerUserId().equals(userId);
        boolean seller = storefronts.findByOwnerUserId(userId)
                .filter(store -> store.getId().equals(order.getStorefrontId())).isPresent();
        boolean operator = users.findWithRolesById(userId).filter(user -> user.isActive() && user.isEmailVerified())
                .map(user -> user.getUserRoles().stream().anyMatch(role -> role.isActive()
                        && (role.getRole().getCode() == RoleCode.ADMIN || role.getRole().getCode() == RoleCode.PRINT_STAFF)))
                .orElse(false);
        if (!buyer && !seller && !operator) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order does not exist.");
        }
        return order;
    }

    private List<PhysicalOrderItem> sortedItems(int id) {
        return orderItems.findByOrderId(id).stream()
                .sorted(Comparator.comparing(PhysicalOrderItem::getVariantId)).toList();
    }

    private CartItem ownedCartItem(int cartId, int itemId) {
        return cartItems.findById(itemId).filter(item -> item.getCartId().equals(cartId))
                .orElseThrow(() -> ApiException.invalidRequest("Cart item does not belong to the current user."));
    }

    private String fingerprint(CheckoutRequest request) {
        try {
            String canonical = request.cartItemIds().stream().sorted().toList()
                    + "|" + request.shippingAddressId()
                    + ("COD".equals(request.paymentMethod()) ? "|COD" : "");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint checkout", exception);
        }
    }

    private OrderResponse response(PhysicalOrder order) {
        var items = orderItems.findByOrderId(order.getId()).stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getVariantId(), item.getSkuSnapshot(),
                        item.getQuantity(), item.getUnitPrice(), item.getLineTotal(), item.getProductTitleSnapshot(),
                        item.getVariantSnapshot())).toList();
        return OrderResponse.from(order, items);
    }
}
