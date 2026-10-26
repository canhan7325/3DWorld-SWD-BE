package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSourceResolver {
    private final JdbcTemplate jdbc;

    public record PodSource(Integer printRequestId, Integer printQuoteId, BigDecimal amount) {}

    public BigDecimal resolve(
            int userId,
            String sourceType,
            int sourceId,
            String stage,
            BigDecimal requestedAmount) {
        return switch (sourceType) {
            case "PHYSICAL_ORDER" -> physicalOrder(userId, sourceId, stage);
            case "RENTAL_CONTRACT" -> rental(userId, sourceId, stage);
            case "PRINT_REQUEST", "PRINT_QUOTE" -> resolvePod(userId, sourceType, sourceId, stage).amount();
            case "SUBSCRIPTION" -> subscription(userId, sourceId, stage);
            case "WALLET" -> topUp(userId, sourceId, stage, requestedAmount);
            default -> throw ApiException.invalidRequest("Unsupported payment source.");
        };
    }

    private BigDecimal physicalOrder(int userId, int id, String stage) {
        requireStage(stage, "FULL");
        return one("SELECT total_amount FROM physical_orders "
                + "WHERE physical_order_id = ? AND buyer_user_id = ? "
                + "AND order_status IN ('CREATED', 'PENDING_PAYMENT') "
                + "AND reservation_status='RESERVED' AND reservation_expires_at > now()", id, userId);
    }

    private BigDecimal rental(int userId, int id, String stage) {
        requireStage(stage, "FULL");
        return one("SELECT price_snapshot FROM rental_contracts "
                + "WHERE rental_contract_id = ? AND renter_user_id = ? "
                + "AND status = 'PENDING_PAYMENT'", id, userId);
    }

    public PodSource resolvePod(int userId, String sourceType, int sourceId, String stage) {
        requireStage(stage, "FULL");
        if (sourceId <= 0 || !("PRINT_REQUEST".equals(sourceType)
                || "PRINT_QUOTE".equals(sourceType))) {
            throw ApiException.invalidRequest("Invalid POD payment source.");
        }
        String sourceColumn = "PRINT_REQUEST".equals(sourceType)
                ? "r.print_request_id" : "q.print_quote_id";
        List<PodSource> matches = jdbc.query(
                "SELECT r.print_request_id, q.print_quote_id, q.total_amount FROM print_quotes q "
                + "JOIN print_requests r ON r.print_request_id = q.print_request_id "
                + "WHERE " + sourceColumn + " = ? AND r.customer_user_id = ? "
                + "AND r.current_quote_id = q.print_quote_id "
                + "AND r.status = 'QUOTE_ACCEPTED' AND r.cancelled_at IS NULL "
                + "AND r.locked_at IS NULL AND q.quote_status = 'ACCEPTED' "
                + "AND q.accepted_at IS NOT NULL "
                + "AND q.file_version_snapshot = r.current_file_version "
                + "AND q.specification_version_snapshot = r.current_spec_version "
                + "AND q.total_amount > 0 AND q.deposit_amount = q.total_amount "
                + "AND q.remaining_amount = 0 FOR UPDATE OF r",
                (result, row) -> new PodSource(result.getInt(1), result.getInt(2),
                        result.getBigDecimal(3)), sourceId, userId);
        if (matches.size() != 1) {
            throw ApiException.invalidRequest("POD quote is not the current payable quote.");
        }
        return matches.getFirst();
    }

    private BigDecimal subscription(int userId, int id, String stage) {
        requireStage(stage, "SUBSCRIPTION");
        return one("SELECT s.fee_snapshot FROM storefront_subscriptions s "
                + "JOIN storefronts f ON f.storefront_id = s.storefront_id "
                + "WHERE s.subscription_id = ? AND f.owner_user_id = ? "
                + "AND s.status = 'PENDING_PAYMENT'", id, userId);
    }

    private BigDecimal topUp(int userId, int walletId, String stage, BigDecimal amount) {
        Integer owner = jdbc.queryForObject("SELECT user_id FROM wallets WHERE wallet_id = ?", Integer.class, walletId);
        if (!Integer.valueOf(userId).equals(owner)) {
            throw ApiException.invalidRequest("Wallet does not belong to the current user.");
        }
        requireStage(stage, "TOPUP");
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.invalidRequest("A positive top-up amount is required.");
        }
        return amount;
    }

    private BigDecimal one(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, BigDecimal.class, args);
        } catch (Exception exception) {
            throw ApiException.invalidRequest("Payment source does not exist or is not payable.");
        }
    }

    private void requireStage(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw ApiException.invalidRequest("Payment stage does not match the source.");
        }
    }
}
