package com.project.w_3d_world.features.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer id;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "operation_key", nullable = false, unique = true)
    private String operationKey;

    @Column(name = "physical_order_id")
    private Integer physicalOrderId;
    @Column(name = "rental_contract_id")
    private Integer rentalContractId;
    @Column(name = "print_request_id")
    private Integer printRequestId;
    @Column(name = "storefront_subscription_id")
    private Integer subscriptionId;
    @Column(name = "wallet_id")
    private Integer walletId;
    @Column(name = "print_quote_id")
    private Integer printQuoteId;
    @Column(name = "payer_user_id")
    private Integer payerUserId;
    @Column(name = "payment_stage")
    private String paymentStage;
    @Column(name = "settlement_mode", nullable = false)
    private String settlementMode;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency = "VND";
    @Column(nullable = false)
    private String environment = "SANDBOX";
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "active_key", length = 255)
    private String activeKey;

    @Column(name = "fulfillment_status", length = 30, nullable = false)
    private String fulfillmentStatus = "PENDING";

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PaymentTransaction(
            String purpose,
            String operationKey,
            Integer payerUserId,
            String paymentStage,
            BigDecimal amount) {
        this.transactionType = "PAYMENT";
        this.purpose = purpose;
        this.operationKey = operationKey;
        this.payerUserId = payerUserId;
        this.paymentStage = paymentStage;
        this.settlementMode = "PLATFORM";
        this.amount = amount;
    }

    public void setSource(String sourceType, Integer sourceId) {
        switch (sourceType) {
            case "PHYSICAL_ORDER" -> physicalOrderId = sourceId;
            case "RENTAL_CONTRACT" -> {
                rentalContractId = sourceId;
                purpose = "RENTAL";
                settlementMode = "DIRECT_TO_DESIGNER";
            }
            case "PRINT_REQUEST" -> {
                printRequestId = sourceId;
                purpose = "POD";
            }
            case "SUBSCRIPTION" -> subscriptionId = sourceId;
            case "WALLET" -> {
                walletId = sourceId;
                transactionType = "TOPUP";
                purpose = "WALLET";
            }
            case "PRINT_QUOTE" -> printQuoteId = sourceId;
            default -> throw new IllegalArgumentException("Unsupported payment source.");
        }
    }

    public void markSuccess() {
        status = "SUCCESS";
        updatedAt = Instant.now();
        completedAt = Instant.now();
    }

    public void markFailed() {
        if (!"SUCCESS".equals(status)) {
            status = "FAILED";
            updatedAt = Instant.now();
        }
    }
    public void fulfillment(String result) {
        fulfillmentStatus = result;
        updatedAt = Instant.now();
    }

    public void initializeCod() {
        settlementMode = "COD";
        fulfillmentStatus = "PENDING_COLLECTION";
    }

    public void collectedCod() {
        status = "PROCESSING";
        fulfillmentStatus = "PENDING_SETTLEMENT";
        updatedAt = Instant.now();
    }

    public void cancelCod() {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only uncollected COD payments can be cancelled.");
        status = "CANCELLED";
        fulfillmentStatus = "CANCELLED";
        updatedAt = Instant.now();
    }
}
