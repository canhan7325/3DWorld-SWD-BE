package com.project.w_3d_world.features.user.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.auth.service.SellerVerificationService;
import com.project.w_3d_world.features.auth.service.impl.AccountValidation;
import com.project.w_3d_world.features.user.dto.UserDataResponses.BankResponse;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankVerificationService {
    private final UserBankAccountRepository banks;
    private final UserRepository users;
    private final AccountAuthorization authorization;
    private final SellerVerificationService sellerVerification;
    private final AuditLogRepository audit;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankResponse> queue(int reviewer, String status, int page, int size) {
        authorization.requireRole(reviewer, RoleCode.ADMIN);
        if (!java.util.Set.of("PENDING", "VERIFIED", "REJECTED").contains(status)
                || page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid verification status or pagination.");
        }
        return banks.findByVerificationStatusAndActiveTrue(status, org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("id").descending())).map(BankResponse::from);
    }

    @Transactional
    public BankResponse review(int reviewer, int id, boolean approved, String reason,
                               String evidenceReference, String expectedFingerprint) {
        authorization.requireRole(reviewer, RoleCode.ADMIN);
        Integer owner = banks.ownerId(id)
                .orElseThrow(() -> ApiException.invalidRequest("Bank account does not exist."));
        if (reviewer == owner) {
            throw ApiException.invalidRequest("You cannot verify your own bank account.");
        }
        var user = users.lockById(owner).filter(value -> value.isActive())
                .orElseThrow(ApiException::invalidCredentials);
        var bank = banks.lockById(id).filter(value -> value.isActive())
                .orElseThrow(() -> ApiException.invalidRequest("Bank account is inactive."));
        if (!bank.reviewFingerprint().equals(expectedFingerprint)) {
            throw ApiException.invalidRequest("Bank account changed. Refresh its verification fingerprint.");
        }
        String decision = AccountValidation.required(reason, 1000, "Review reason");
        String evidence = AccountValidation.required(evidenceReference, 500, "Evidence reference");
        bank.review(approved);
        if (!approved) sellerVerification.invalidate(user, "Bank verification rejected");
        audit.save(new AuditLog(reviewer, approved ? "BANK_ACCOUNT_VERIFIED" : "BANK_ACCOUNT_REJECTED",
                "USER_BANK_ACCOUNT", id, decision, JsonValues.write(Map.of(
                        "mode", "MANUAL_SANDBOX", "evidenceReference", evidence,
                        "accountFingerprint", expectedFingerprint, "status", bank.getVerificationStatus()))));
        return BankResponse.from(bank);
    }

    @Transactional(readOnly = true)
    public VerificationResponse get(int reviewer, int id) {
        authorization.requireRole(reviewer, RoleCode.ADMIN);
        var bank = banks.findById(id).orElseThrow(() -> ApiException.invalidRequest("Bank account does not exist."));
        var records = audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("USER_BANK_ACCOUNT", id,
                org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .map(value -> new ReviewEvidence(value.getActorUserId(), value.getActionType(),
                        value.getReason(), value.getMetadata(), value.getCreatedAt())).toList();
        return new VerificationResponse(BankResponse.from(bank), records);
    }

    public record ReviewEvidence(Integer actorId, String action, String reason, String evidence,
                                 Instant createdAt) {}
    public record VerificationResponse(BankResponse bank, List<ReviewEvidence> history) {}
}
