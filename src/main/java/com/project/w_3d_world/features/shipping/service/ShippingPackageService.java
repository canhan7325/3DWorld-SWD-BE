package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.order.repository.*;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.shipping.dto.ShippingPackageDtos.*;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingPackageService {
    private final JdbcTemplate jdbc;
    private final PhysicalOrderRepository orders;
    private final PhysicalOrderItemRepository items;
    private final ProductVariantRepository variants;
    private final StorefrontRepository storefronts;
    private final AccountAuthorization authorization;
    private final OrderService orderService;
    private final AuditLogRepository audit;
    private final ObjectMapper mapper;

    public List<ProfileResponse> profiles(int actor) {
        authorization.requireRole(actor, RoleCode.SELLER, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        return jdbc.query("""
                SELECT packaging_profile_id,code,name,inner_length_cm,inner_width_cm,inner_height_cm,
                max_product_weight_grams,supports_fragile,allowed_packing_groups::text
                FROM packaging_profiles WHERE status='ACTIVE' ORDER BY priority,packaging_profile_id
                """, (rs, row) -> new ProfileResponse(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getBoolean(8), rs.getString(9)));
    }

    public List<PackageResponse> list(int actor, int orderId) {
        orderService.detail(actor, orderId);
        var allocations = jdbc.query("""
                SELECT shipping_package_id,physical_order_item_id,variant_id,quantity
                FROM order_shipping_package_items WHERE physical_order_id=? ORDER BY shipping_package_item_id
                """, (rs, row) -> Map.entry(rs.getInt(1), new ItemResponse(rs.getInt(2), rs.getInt(3), rs.getInt(4))), orderId);
        Map<Integer, List<ItemResponse>> grouped = new HashMap<>();
        allocations.forEach(entry -> grouped.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue()));
        return jdbc.query("""
                SELECT shipping_package_id,package_code,package_index,profile_code,product_weight_grams,
                packaging_weight_grams,package_weight_grams,internal_packaging_cost_vnd
                FROM order_shipping_packages WHERE physical_order_id=? ORDER BY package_index
                """, (rs, row) -> new PackageResponse(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getBigDecimal(8),
                grouped.getOrDefault(rs.getInt(1), List.of())), orderId);
    }

    @Transactional
    public List<PackageResponse> save(int actor, int orderId, SaveRequest request) {
        var order = orders.lockById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        if (storefronts.findByOwnerUserId(actor).filter(store -> store.getId().equals(order.getStorefrontId())).isEmpty()) {
            authorization.requireRole(actor, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        }
        if (!"PROCESSING".equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Packages may be prepared only before shipment of a processing order.");
        }
        var ordered = items.findByOrderId(orderId).stream().collect(
                Collectors.toMap(item -> item.getId(), item -> item));
        Map<Integer, Long> assigned = new HashMap<>();
        for (PackageRequest pack : request.packages()) {
            for (ItemRequest allocation : pack.items()) {
                if (!ordered.containsKey(allocation.orderItemId())) {
                    throw ApiException.invalidRequest("Every package item must belong to this order.");
                }
                assigned.merge(allocation.orderItemId(), (long) allocation.quantity(), Long::sum);
            }
        }
        if (ordered.values().stream().anyMatch(item -> assigned.getOrDefault(item.getId(), 0L) != item.getQuantity())) {
            throw ApiException.invalidRequest("Packages must allocate every ordered unit exactly once.");
        }
        jdbc.update("DELETE FROM order_shipping_package_items WHERE physical_order_id=?", orderId);
        jdbc.update("DELETE FROM order_shipping_packages WHERE physical_order_id=?", orderId);
        int index = 0, totalWeight = 0, totalProductWeight = 0, totalPackagingWeight = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (PackageRequest pack : request.packages()) {
            var rows = jdbc.queryForList("SELECT * FROM packaging_profiles WHERE packaging_profile_id=? AND status='ACTIVE' FOR SHARE",
                    pack.packagingProfileId());
            if (rows.isEmpty()) {
                throw ApiException.invalidRequest("Select an active packaging profile.");
            }
            var profile = rows.getFirst();
            BigDecimal[] box = sortedDimensions((BigDecimal) profile.get("inner_length_cm"),
                    (BigDecimal) profile.get("inner_width_cm"), (BigDecimal) profile.get("inner_height_cm"));
            BigDecimal stackHeight = BigDecimal.ZERO;
            int weight = 0;
            boolean fragile = false;
            var allowed = mapper.readTree(profile.get("allowed_packing_groups").toString());
            for (var allocation : pack.items()) {
                var item = ordered.get(allocation.orderItemId());
                var variant = variants.findById(item.getVariantId()).orElseThrow();
                if (variant.getProductWeightGrams() == null || variant.getProductLengthCm() == null
                        || variant.getProductWidthCm() == null || variant.getProductHeightCm() == null) {
                    throw ApiException.invalidRequest("Product weight and dimensions are required to prepare packages.");
                }
                if (allowed.size() > 0 && (variant.getPackingGroup() == null
                        || !StreamSupport.stream(allowed.spliterator(), false)
                        .anyMatch(value -> value.asText().equals(variant.getPackingGroup())))) {
                    throw ApiException.invalidRequest("Packaging profile does not support this product packing group.");
                }
                var dimensions = sortedDimensions(variant.getProductLengthCm(), variant.getProductWidthCm(), variant.getProductHeightCm());
                stackHeight = stackHeight.add(dimensions[2].multiply(BigDecimal.valueOf(allocation.quantity())));
                // Conservative straight-stack packing; no unverified volume-only fit assumption.
                if (dimensions[0].compareTo(box[0]) > 0 || dimensions[1].compareTo(box[1]) > 0
                        || stackHeight.compareTo(box[2]) > 0) {
                    throw ApiException.invalidRequest("Items do not fit this packaging profile. Split them into more packages.");
                }
                long nextWeight = weight + (long) variant.getProductWeightGrams() * allocation.quantity();
                if (nextWeight > ((Number) profile.get("max_product_weight_grams")).intValue()) {
                    throw ApiException.invalidRequest("Package exceeds maximum product weight.");
                }
                weight = (int) nextWeight;
                fragile |= variant.isFragile();
            }
            if (fragile && !Boolean.TRUE.equals(profile.get("supports_fragile"))) {
                throw ApiException.invalidRequest("Packaging profile does not support fragile products.");
            }
            int padding = fragile ? ((Number) profile.get("fragile_padding_weight_grams")).intValue() : 0;
            int packagingWeight = Math.addExact(((Number) profile.get("package_weight_grams")).intValue(), padding);
            int packageWeight = Math.addExact(weight, packagingWeight);
            BigDecimal boxCost = (BigDecimal) profile.get("box_cost_vnd");
            BigDecimal paddingCost = (BigDecimal) profile.get("padding_cost_vnd");
            BigDecimal cost = boxCost.add(paddingCost);
            int packageId = jdbc.queryForObject("""
                    INSERT INTO order_shipping_packages(physical_order_id,packaging_profile_id,package_code,package_index,
                    profile_code,product_weight_grams,packaging_weight_grams,package_weight_grams,max_product_weight_grams,
                    box_cost_vnd,padding_cost_vnd,internal_packaging_cost_vnd,fragile_padding_weight_grams,
                    fragile_padding_applied_grams,inner_length_cm,inner_width_cm,inner_height_cm)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING shipping_package_id
                    """, Integer.class, orderId, pack.packagingProfileId(), "PKG-" + UUID.randomUUID(), ++index,
                    profile.get("code"), weight, packagingWeight, packageWeight, profile.get("max_product_weight_grams"),
                    boxCost, paddingCost, cost, profile.get("fragile_padding_weight_grams"), padding,
                    profile.get("inner_length_cm"), profile.get("inner_width_cm"), profile.get("inner_height_cm"));
            for (var allocation : pack.items()) {
                var item = ordered.get(allocation.orderItemId());
                var variant = variants.findById(item.getVariantId()).orElseThrow();
                jdbc.update("""
                        INSERT INTO order_shipping_package_items(shipping_package_id,physical_order_item_id,
                        physical_order_id,product_id,variant_id,quantity,size_snapshot,product_length_cm,product_width_cm,product_height_cm)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """, packageId, item.getId(), orderId, variant.getProductId(), variant.getId(), allocation.quantity(),
                        sizeSnapshot(item.getVariantSnapshot()), variant.getProductLengthCm(), variant.getProductWidthCm(), variant.getProductHeightCm());
            }
            totalProductWeight = Math.addExact(totalProductWeight, weight);
            totalPackagingWeight = Math.addExact(totalPackagingWeight, packagingWeight);
            totalWeight = Math.addExact(totalWeight, packageWeight);
            totalCost = totalCost.add(cost);
        }
        // These are internal fulfillment snapshots; do not reprice an already paid order.
        order.snapshotPackages(index, totalCost, totalProductWeight, totalPackagingWeight, totalWeight);
        audit.save(new AuditLog(actor, "ORDER_PACKAGES_PREPARED", "PHYSICAL_ORDER", orderId,
                "Packaging snapshots prepared", "Package count " + index));
        return list(actor, orderId);
    }

    private String sizeSnapshot(String attributes) {
        if (attributes == null) {
            return null;
        }
        var size = mapper.readTree(attributes).get("size");
        if (size == null || size.isNull()) {
            return null;
        }
        String value = size.asText();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private BigDecimal[] sortedDimensions(BigDecimal length, BigDecimal width, BigDecimal height) {
        var result = new BigDecimal[] {length, width, height};
        Arrays.sort(result, Comparator.reverseOrder());
        return result;
    }
}
