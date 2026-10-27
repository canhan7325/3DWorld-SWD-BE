package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.cart.entity.CartItem;
import com.project.w_3d_world.features.cart.repository.*;
import com.project.w_3d_world.features.catalog.repository.*;
import com.project.w_3d_world.features.order.entity.*;
import com.project.w_3d_world.features.shipping.dto.ShippingQuoteDtos.*;
import com.project.w_3d_world.features.shipping.ghtk.*;
import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Address;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService.*;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingQuoteService {
    public static final String VERSION = "GHTK_FLOOR_PACK_V1";
    private final GhtkProperties config;
    private final GhtkClient carrier;
    private final GhtkAddressService carrierAddresses;
    private final BoxPackingService packing;
    private final ShippingQuoteSigner signer;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final StorefrontRepository stores;
    private final UserAddressRepository addresses;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private record Inputs(Map<Integer, List<Item>> shops, Map<Integer, Address> origins,
                          Address destination, List<Box> boxes, String fingerprint) {}

    public boolean enabled() { return config.enabled(); }

    public QuoteResponse quote(int userId, QuoteRequest request) {
        if (!enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GHTK shipping quotes are disabled.");
        Inputs input = inputs(userId, request.cartItemIds(), request.shippingAddressId());
        List<ShopQuote> shops = new ArrayList<>();
        // Same parcel/address/value combination is quoted once per request, even across candidate plans.
        Map<String, GhtkClient.Fee> cache = new HashMap<>();
        int[] calls = {0};
        for (var entry : input.shops().entrySet()) {
            List<PricedPlan> priced = new ArrayList<>();
            for (Plan plan : packing.suggest(entry.getValue(), input.boxes(), config.paddingCm())) {
                List<GhtkClient.Fee> fees = new ArrayList<>();
                for (Parcel parcel : plan.parcels()) {
                    String key = entry.getKey() + "|" + parcel.totalWeight() + "|" + parcel.declaredValue() + "|" + parcel.fragile();
                    GhtkClient.Fee fee = cache.get(key);
                    if (fee == null) {
                        if (++calls[0] > 20) throw ApiException.invalidRequest("Too many parcels to quote at once. Split this checkout.");
                        fee = carrier.quote(input.origins().get(entry.getKey()), input.destination(),
                                parcel.totalWeight(), parcel.declaredValue(), parcel.fragile());
                        cache.put(key, fee);
                    }
                    fees.add(fee);
                }
                BigDecimal shipping = fees.stream().map(GhtkClient.Fee::total).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal packaging = plan.parcels().stream().map(Parcel::packagingCost).reduce(BigDecimal.ZERO, BigDecimal::add);
                priced.add(new PricedPlan(plan, fees, shipping, packaging, shipping.add(packaging)));
            }
            BigDecimal minimumShipping = priced.stream().map(PricedPlan::shippingFee).min(BigDecimal::compareTo).orElseThrow();
            PricedPlan selected = priced.stream()
                    .filter(plan -> plan.shippingFee().compareTo(minimumShipping.add(new BigDecimal("6000"))) <= 0)
                    .min(Comparator.comparing(PricedPlan::combinedCost)
                            .thenComparingInt(plan -> plan.packing().parcels().size())).orElseThrow();
            shops.add(new ShopQuote(entry.getKey(), selected, List.copyOf(priced)));
        }
        long ttl = config.quoteTtlSeconds();
        if (ttl < 30 || ttl > 1800) throw new IllegalStateException("Shipping quote TTL must be 30-1800 seconds.");
        long expiry = System.currentTimeMillis() + ttl * 1000;
        // Only selected plans go into the checkout token, to keep it bounded.
        var selected = shops.stream().map(shop -> new ShopQuote(shop.storefrontId(), shop.selected(), List.of())).toList();
        String token = signer.sign(new SignedQuote(userId, input.fingerprint(), expiry, VERSION, selected));
        BigDecimal total = shops.stream().map(shop -> shop.selected().shippingFee()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new QuoteResponse(token, expiry, VERSION, "INDEPENDENT_PARCELS", total, shops);
    }

    /** Called after checkout locks variant rows; no external HTTP occurs while stock is locked. */
    public Map<Integer, ShopQuote> validate(int userId, List<Integer> ids, int addressId, String token) {
        SignedQuote quote = signer.read(token);
        Inputs current = inputs(userId, ids, addressId);
        if (quote.userId() != userId || !VERSION.equals(quote.version()) || !quote.fingerprint().equals(current.fingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cart, prices, addresses or packaging changed. Request a new shipping quote.");
        }
        Map<Integer, ShopQuote> result = new TreeMap<>();
        quote.shops().forEach(shop -> result.put(shop.storefrontId(), shop));
        if (!result.keySet().equals(current.shops().keySet())) throw ApiException.invalidRequest("Invalid shipping quote shops.");
        return result;
    }

    private Inputs inputs(int userId, List<Integer> ids, int addressId) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || ids.stream().distinct().count() != ids.size()) {
            throw ApiException.invalidRequest("Select 1-100 distinct cart items.");
        }
        UserAddress destinationAddress = addresses.findById(addressId).filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery address does not exist."));
        Address destination = address(destinationAddress, "Delivery");
        int cartId = carts.findByUserId(userId).orElseThrow(() -> ApiException.invalidRequest("Cart does not exist.")).getId();
        Map<Integer, List<Item>> groups = new TreeMap<>();
        Map<Integer, Address> origins = new TreeMap<>();
        Map<Integer, String> addressBasis = new TreeMap<>();
        addressBasis.put(destinationAddress.getId(), addressBasis(destinationAddress));
        for (int id : ids.stream().sorted().toList()) {
            CartItem cart = items.findById(id).filter(value -> value.getCartId().equals(cartId))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart item does not exist."));
            var variant = variants.findById(cart.getVariantId()).orElseThrow();
            var product = products.findById(variant.getProductId()).orElseThrow();
            var shop = stores.findById(product.getStorefrontId()).orElseThrow();
            if (!shop.canPublish() || shop.getOwnerUserId().equals(userId) || !"PUBLISHED".equals(product.getStatus())
                    || !variant.isActive() || !"ACTIVE".equals(variant.getStatus()) || cart.getQuantity() < 1
                    || cart.getQuantity() > variant.availableQuantity()) {
                throw ApiException.invalidRequest("A cart item is unavailable or has insufficient stock.");
            }
            if (cart.getPriceAtAdded() == null || cart.getPriceAtAdded().compareTo(variant.getUnitPrice()) != 0) {
                throw ApiException.invalidRequest("A cart price changed. Acknowledge the new price before requesting shipping.");
            }
            if (variant.getProductWeightGrams() == null) throw ApiException.invalidRequest("Variant shipping weight is missing.");
            variant.getUnitPrice().longValueExact();
            groups.computeIfAbsent(shop.getId(), key -> new ArrayList<>()).add(new Item(id, variant.getId(),
                    cart.getQuantity(), variant.getProductWeightGrams(), variant.getProductLengthCm(),
                    variant.getProductWidthCm(), variant.getProductHeightCm(), variant.isFragile(),
                    variant.getPackingGroup(), variant.getUnitPrice()));
            if (!origins.containsKey(shop.getId())) {
                UserAddress pickup = addresses.findByUserIdOrderByIdAsc(shop.getOwnerUserId()).stream()
                        .filter(UserAddress::isDefaultAddress).findFirst()
                        .orElseThrow(() -> ApiException.invalidRequest("Seller storefront " + shop.getId()
                                + " requires a default pickup address before GHTK quoting."));
                origins.put(shop.getId(), address(pickup, "Seller pickup"));
                addressBasis.put(pickup.getId(), addressBasis(pickup));
            }
        }
        List<Box> boxes = jdbc.query("SELECT * FROM packaging_profiles WHERE status='ACTIVE' ORDER BY packaging_profile_id",
                (rs, row) -> {
                    Set<String> allowed = new TreeSet<>();
                    mapper.readTree(rs.getString("allowed_packing_groups")).forEach(v -> allowed.add(v.asText()));
                    return new Box(rs.getInt("packaging_profile_id"), rs.getString("code"), rs.getBigDecimal("inner_length_cm"),
                            rs.getBigDecimal("inner_width_cm"), rs.getBigDecimal("inner_height_cm"),
                            rs.getInt("max_product_weight_grams"), rs.getInt("package_weight_grams"),
                            rs.getInt("fragile_padding_weight_grams"), rs.getBoolean("supports_fragile"), allowed,
                            rs.getBigDecimal("box_cost_vnd"), rs.getBigDecimal("padding_cost_vnd"));
                });
        String payload = JsonValues.write(List.of(userId, ids.stream().sorted().toList(), addressId, destination,
                groups, origins, addressBasis, boxes, config.paddingCm(), config.transport()));
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
            return new Inputs(groups, origins, destination, boxes, fingerprint);
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private Address address(UserAddress address, String label) {
        return carrierAddresses.resolve(address).carrierAddress();
    }

    private String addressBasis(UserAddress address) {
        return JsonValues.write(Arrays.asList(address.getId(), address.getAddressLine(), address.getProvince(),
                address.getDistrict(), address.getWard(), address.getRecipientName(), address.getPhone()));
    }

    @Transactional
    public void persist(PhysicalOrder order, ShopQuote quote, Map<Integer, PhysicalOrderItem> itemsByCartId) {
        var plan = quote.selected().packing();
        int index = 0, weight = 0, productWeight = 0, packagingWeight = 0;
        for (Parcel parcel : plan.parcels()) {
            Box box = parcel.box();
            int padding = parcel.fragile() ? box.paddingWeight() : 0;
            int packageId = jdbc.queryForObject("""
                    INSERT INTO order_shipping_packages(physical_order_id,packaging_profile_id,package_code,package_index,
                    profile_code,product_weight_grams,packaging_weight_grams,package_weight_grams,max_product_weight_grams,
                    box_cost_vnd,padding_cost_vnd,internal_packaging_cost_vnd,fragile_padding_weight_grams,
                    fragile_padding_applied_grams,inner_length_cm,inner_width_cm,inner_height_cm)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING shipping_package_id
                    """, Integer.class, order.getId(), box.id(), "PKG-" + UUID.randomUUID(), ++index, box.code(),
                    parcel.productWeight(), parcel.packagingWeight(), parcel.totalWeight(), box.maxProductWeight(),
                    box.boxCost(), box.paddingCost(), parcel.packagingCost(), box.paddingWeight(), padding,
                    box.length(), box.width(), box.height());
            var counts = new TreeMap<Integer, Integer>();
            parcel.placements().forEach(p -> counts.merge(p.cartItemId(), 1, Integer::sum));
            for (var count : counts.entrySet()) {
                PhysicalOrderItem item = itemsByCartId.get(count.getKey());
                var variant = variants.findById(item.getVariantId()).orElseThrow();
                jdbc.update("""
                        INSERT INTO order_shipping_package_items(shipping_package_id,physical_order_item_id,
                        physical_order_id,product_id,variant_id,quantity,product_length_cm,product_width_cm,product_height_cm)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """, packageId, item.getId(), order.getId(), variant.getProductId(), variant.getId(), count.getValue(),
                        variant.getProductLengthCm(), variant.getProductWidthCm(), variant.getProductHeightCm());
            }
            weight = Math.addExact(weight, parcel.totalWeight());
            productWeight = Math.addExact(productWeight, parcel.productWeight());
            packagingWeight = Math.addExact(packagingWeight, parcel.packagingWeight());
        }
        order.snapshotPackages(index, quote.selected().packagingCost(), productWeight, packagingWeight, weight);
        order.markShippingQuoteVersion(VERSION);
        // Existing JSONB audit log preserves quote, provider breakdown and 2D placements without a new table.
        jdbc.update("""
                INSERT INTO audit_logs(actor_user_id,action_type,entity_type,entity_id,reason,metadata)
                VALUES (?,'SHIPPING_QUOTE_ACCEPTED','PHYSICAL_ORDER',?,'GHTK independent parcel fee snapshot',cast(? as jsonb))
                """, order.getBuyerUserId(), order.getId(), JsonValues.write(quote));
    }
}
