package com.project.w_3d_world.features.storefront.repository.impl;

import com.project.w_3d_world.features.storefront.repository.AdminStorefrontQueryRepository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only queries for the admin storefront list; joins each shop with its latest paid and pending plan. */
@Repository
@RequiredArgsConstructor
public class AdminStorefrontQueryRepositoryImpl implements AdminStorefrontQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE = """
            WITH base AS (
              SELECT s.storefront_id, s.storefront_name, s.status, s.seller_verification_status,
                s.expires_at, s.created_at, s.owner_user_id, u.full_name, u.email, u.phone,
                cur.subscription_id AS cur_id, cur.fee_snapshot AS cur_fee, cur.start_at AS cur_start,
                cur.end_at AS cur_end, cur.paid_at AS cur_paid,
                pend.subscription_id AS pend_id, pend.fee_snapshot AS pend_fee, pend.created_at AS pend_created,
                agg.paid_count, agg.total_paid, agg.first_paid_at,
                CASE
                  WHEN s.expires_at IS NOT NULL AND s.expires_at > now() AND agg.paid_count > 0 THEN 'ACTIVE'
                  WHEN agg.paid_count > 0 THEN 'EXPIRED'
                  WHEN pend.subscription_id IS NOT NULL THEN 'PENDING_PAYMENT'
                  ELSE 'NONE'
                END AS plan_status
              FROM storefronts s
              JOIN users u ON u.user_id = s.owner_user_id
              LEFT JOIN LATERAL (
                SELECT b.subscription_id, b.fee_snapshot, b.start_at, b.end_at, b.paid_at
                FROM storefront_subscriptions b
                WHERE b.storefront_id = s.storefront_id AND b.paid_at IS NOT NULL
                ORDER BY b.end_at DESC NULLS LAST, b.subscription_id DESC LIMIT 1
              ) cur ON true
              LEFT JOIN LATERAL (
                SELECT b.subscription_id, b.fee_snapshot, b.created_at
                FROM storefront_subscriptions b
                WHERE b.storefront_id = s.storefront_id AND b.status = 'PENDING_PAYMENT' AND b.paid_at IS NULL
                ORDER BY b.subscription_id DESC LIMIT 1
              ) pend ON true
              LEFT JOIN LATERAL (
                SELECT count(*) AS paid_count, coalesce(sum(b.fee_snapshot), 0) AS total_paid,
                  min(b.paid_at) AS first_paid_at
                FROM storefront_subscriptions b
                WHERE b.storefront_id = s.storefront_id AND b.paid_at IS NOT NULL
              ) agg ON true
            )
            """;

    private static final String FILTER = """
            WHERE (CAST(:keyword AS text) IS NULL
                OR storefront_name ILIKE :keyword OR full_name ILIKE :keyword OR email ILIKE :keyword
                OR coalesce(phone, '') ILIKE :keyword OR CAST(storefront_id AS text) ILIKE :keyword)
              AND (CAST(:status AS text) IS NULL OR status = :status)
              AND (CAST(:plan AS text) IS NULL OR plan_status = :plan)
            """;

    private static final Map<String, String> SORTS = Map.of(
            "newest", "created_at DESC, storefront_id DESC",
            "oldest", "created_at ASC, storefront_id ASC",
            "expiring", "expires_at ASC NULLS LAST, storefront_id DESC",
            "revenue", "total_paid DESC, storefront_id DESC",
            "name", "storefront_name ASC, storefront_id ASC");

    @Override
    public boolean validSort(String sort) {
        return SORTS.containsKey(sort);
    }

    @Override
    public List<Map<String, Object>> page(String keyword, String status, String plan, String sort, int page, int size) {
        var params = filters(keyword, status, plan).addValue("limit", size).addValue("offset", (long) page * size);
        return jdbc.queryForList(BASE + "SELECT *, count(*) OVER () AS total_rows FROM base " + FILTER
                + " ORDER BY " + SORTS.get(sort) + " LIMIT :limit OFFSET :offset", params);
    }

    @Override
    public long count(String keyword, String status, String plan) {
        Long total = jdbc.queryForObject(BASE + "SELECT count(*) FROM base " + FILTER,
                filters(keyword, status, plan), Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public Map<String, Object> summary(int expiringDays) {
        return jdbc.queryForMap(BASE + """
                SELECT count(*) AS total,
                  count(*) FILTER (WHERE plan_status = 'ACTIVE') AS active,
                  count(*) FILTER (WHERE plan_status = 'EXPIRED') AS expired,
                  count(*) FILTER (WHERE plan_status = 'PENDING_PAYMENT') AS pending_payment,
                  count(*) FILTER (WHERE plan_status = 'NONE') AS none,
                  count(*) FILTER (WHERE status = 'SUSPENDED') AS suspended,
                  count(*) FILTER (WHERE plan_status = 'ACTIVE'
                    AND expires_at <= now() + make_interval(days => :days)) AS expiring_soon
                FROM base
                """, new MapSqlParameterSource("days", expiringDays));
    }

    @Override
    public boolean exists(int storefrontId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM storefronts WHERE storefront_id = :id)",
                new MapSqlParameterSource("id", storefrontId), Boolean.class);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public List<Map<String, Object>> subscriptions(int storefrontId) {
        return jdbc.queryForList("""
                SELECT subscription_id, fee_snapshot, status, start_at, end_at, paid_at, created_at
                FROM storefront_subscriptions WHERE storefront_id = :id
                ORDER BY subscription_id DESC LIMIT 200
                """, new MapSqlParameterSource("id", storefrontId));
    }

    private static MapSqlParameterSource filters(String keyword, String status, String plan) {
        return new MapSqlParameterSource()
                .addValue("keyword", keyword == null ? null : "%" + escape(keyword) + "%", Types.VARCHAR)
                .addValue("status", status, Types.VARCHAR)
                .addValue("plan", plan, Types.VARCHAR);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
