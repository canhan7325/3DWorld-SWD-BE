package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.*;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read projections keep paging and all product cards in two queries, independent of page size. */
@Repository
@RequiredArgsConstructor
public class CatalogReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String FROM = """
            FROM products p JOIN storefronts s ON s.storefront_id=p.storefront_id
            JOIN users u ON u.user_id=s.owner_user_id
            LEFT JOIN LATERAL (
                SELECT min(unit_price) price, max(unit_price) max_price,
                       bool_or(stock_quantity > reserved_quantity) available
                FROM product_variants v WHERE v.product_id=p.product_id
                AND v.is_active AND v.status='ACTIVE'
            ) v ON true
            WHERE p.status='PUBLISHED' AND s.status='ACTIVE'
              AND s.seller_verification_status='VERIFIED' AND s.expires_at > now()
              AND u.account_status='ACTIVE' AND u.email_verified AND u.phone_verified
              AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.role_id=ur.role_id
                          WHERE ur.user_id=u.user_id AND ur.approval_status='ACTIVE' AND r.role_code='SELLER')
            """;
    private static final String SELECT = """
            SELECT p.product_id,p.storefront_id,p.title,p.category,p.average_rating,p.review_count,
                   p.sales_count,p.badge_type,v.price,v.max_price,coalesce(v.available,false) available,
                   (SELECT m.media_url FROM product_media m WHERE m.product_id=p.product_id
                    ORDER BY m.sort_order,m.media_id LIMIT 1) thumbnail_url
            """;

    public CatalogPage search(String keyword, String category, BigDecimal minPrice, BigDecimal maxPrice,
                              String sort, Integer storefrontId, boolean featured, int page, int size) {
        MarketplacePage.pageable(page, size);
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw ApiException.invalidRequest("Invalid price range.");
        }
        String ordering = switch (sort) {
            case "newest" -> "p.created_at DESC,p.product_id DESC";
            case "price_asc" -> "v.price ASC NULLS LAST,p.product_id DESC";
            case "price_desc" -> "v.price DESC NULLS LAST,p.product_id DESC";
            case "rating" -> "p.average_rating DESC,p.product_id DESC";
            case "best_selling" -> "p.sales_count DESC,p.product_id DESC";
            default -> throw ApiException.invalidRequest("Unsupported catalog sort.");
        };
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(FROM);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.title ILIKE :keyword ESCAPE '\\' OR p.description ILIKE :keyword ESCAPE '\\')");
            params.put("keyword", "%" + keyword.trim().replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND lower(p.category)=lower(:category)");
            params.put("category", category.trim());
        }
        if (minPrice != null) {
            where.append(" AND v.price >= :minPrice");
            params.put("minPrice", minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND v.price <= :maxPrice");
            params.put("maxPrice", maxPrice);
        }
        if (storefrontId != null) {
            where.append(" AND p.storefront_id=:storefrontId");
            params.put("storefrontId", storefrontId);
        }
        if (featured) {
            where.append(" AND p.badge_type IS NOT NULL AND p.badge_type<>'' AND p.badge_type<>'NONE'");
        }
        long total = jdbc.queryForObject("SELECT count(*) " + where, params, Long.class);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<ProductSummary> items = jdbc.query(SELECT + where + " ORDER BY " + ordering
                + " LIMIT :limit OFFSET :offset", params, (rs, index) -> new ProductSummary(
                rs.getInt("product_id"), rs.getInt("storefront_id"), rs.getString("title"),
                rs.getString("category"), rs.getString("thumbnail_url"), rs.getBigDecimal("price"),
                rs.getBigDecimal("max_price"), rs.getBigDecimal("average_rating"), rs.getInt("review_count"),
                rs.getInt("sales_count"), rs.getString("badge_type"), rs.getBoolean("available")));
        return new CatalogPage(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public ProductSummary summary(int id) {
        return jdbc.query(SELECT + FROM + " AND p.product_id=:id", Map.of("id", id), (rs, index) ->
                new ProductSummary(rs.getInt("product_id"), rs.getInt("storefront_id"), rs.getString("title"),
                        rs.getString("category"), rs.getString("thumbnail_url"), rs.getBigDecimal("price"),
                        rs.getBigDecimal("max_price"), rs.getBigDecimal("average_rating"), rs.getInt("review_count"),
                        rs.getInt("sales_count"), rs.getString("badge_type"), rs.getBoolean("available")))
                .stream().findFirst().orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Product is not publicly available."));
    }

    public MarketplacePage<ReviewResponse> reviews(int id, int page, int size) {
        MarketplacePage.pageable(page, size);
        summary(id);
        var args = Map.of("id", id, "limit", size, "offset", (long) page * size);
        long count = jdbc.queryForObject(
                "SELECT count(*) FROM reviews WHERE product_id=:id AND status='PUBLISHED'", args, Long.class);
        var items = jdbc.query("""
                SELECT review_id,rating,comment,images::text,created_at FROM reviews
                WHERE product_id=:id AND status='PUBLISHED'
                ORDER BY created_at DESC,review_id DESC LIMIT :limit OFFSET :offset
                """, args, (rs, index) -> new ReviewResponse(rs.getInt(1), rs.getInt(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()));
        return new MarketplacePage<>(items, page, size, count, (int) ((count + size - 1) / size));
    }
}
