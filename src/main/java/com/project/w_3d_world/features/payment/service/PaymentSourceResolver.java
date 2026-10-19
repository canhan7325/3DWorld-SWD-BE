package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.common.exception.ApiException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSourceResolver {
    private final JdbcTemplate jdbc;

    public BigDecimal resolve(
            int userId,
            String sourceType,
            int sourceId,
            String stage,
            BigDecimal requestedAmount) {
        return switch (sourceType) {
            case "PHYSICAL_ORDER" -> physicalOrder(userId, sourceId, stage);
            case "RENTAL_CONTRACT" -> rental(userId, sourceId, stage);
            case "PRINT_REQUEST" -> printRequest(userId, sourceId, stage);
            case "PRINT_QUOTE" -> printQuote(userId, sourceId, stage);
            case "SUBSCRIPTION" -> subscription(userId, sourceId, stage);
            case "WALLET" -> topUp(stage, requestedAmount);
            default -> throw ApiException.invalidRequest("Unsupported payment source.");
        };
    }

    private BigDecimal physicalOrder(int userId, int id, String stage) {
        requireStage(stage, "FULL");
        return one("SELECT total_amount FROM physical_orders "
                + "WHERE physical_order_id = ? AND buyer_user_id = ? "
                + "AND order_status IN ('CREATED', 'PENDING_PAYMENT')", id, userId);
    }

    private BigDecimal rental(int userId, int id, String stage) {
        requireStage(stage, "FULL");
        return one("SELECT price_snapshot FROM rental_contracts "
                + "WHERE rental_contract_id = ? AND renter_user_id = ? "
                + "AND status = 'PENDING_PAYMENT'", id, userId);
    }

    private BigDecimal printRequest(int userId, int id, String stage) {
        String column = switch (stage) {
            case "DEPOSIT" -> "deposit_amount";
            case "REMAINING" -> "remaining_amount";
            case "FULL" -> "total_amount";
            default -> throw ApiException.invalidRequest("Unsupported print payment stage.");
        };
        return one("SELECT q." + column + " FROM print_quotes q "
                + "JOIN print_requests r ON r.print_request_id = q.print_request_id "
                + "WHERE q.print_request_id = ? AND r.customer_user_id = ? "
                + "AND q.quote_status = 'ACCEPTED' ORDER BY q.version_number DESC LIMIT 1", id, userId);
    }

    private BigDecimal printQuote(int userId, int id, String stage) {
        String column = switch (stage) {
            case "DEPOSIT" -> "deposit_amount";
            case "REMAINING" -> "remaining_amount";
            case "FULL" -> "total_amount";
            default -> throw ApiException.invalidRequest("Unsupported quote payment stage.");
        };
        return one("SELECT q." + column + " FROM print_quotes q "
                + "JOIN print_requests r ON r.print_request_id = q.print_request_id "
                + "WHERE q.print_quote_id = ? AND r.customer_user_id = ? "
                + "AND q.quote_status = 'ACCEPTED'", id, userId);
    }

    private BigDecimal subscription(int userId, int id, String stage) {
        requireStage(stage, "SUBSCRIPTION");
        return one("SELECT s.fee_snapshot FROM storefront_subscriptions s "
                + "JOIN storefronts f ON f.storefront_id = s.storefront_id "
                + "WHERE s.subscription_id = ? AND f.owner_user_id = ? "
                + "AND s.status = 'PENDING_PAYMENT'", id, userId);
    }

    private BigDecimal topUp(String stage, BigDecimal amount) {
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
