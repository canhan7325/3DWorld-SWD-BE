package com.project.w_3d_world.features.moderation.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.moderation.dto.ReportDtos.*;
import com.project.w_3d_world.features.moderation.entity.Report;
import com.project.w_3d_world.features.moderation.repository.ReportRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reports;
    private final ProductRepository products;
    private final UserRepository users;
    private final AccountAuthorization authorization;
    private final AuditLogRepository audit;

    @Transactional
    public ReportResponse create(int userId, CreateRequest request) {
        if (request.productId() == null && request.reportedUserId() == null) {
            throw ApiException.invalidRequest("Select a product or reported user.");
        }
        if (request.productId() != null && !products.existsById(request.productId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product does not exist.");
        }
        if (request.reportedUserId() != null && (!users.existsById(request.reportedUserId())
                || request.reportedUserId().equals(userId))) {
            throw ApiException.invalidRequest("Invalid reported user.");
        }
        Report report = reports.saveAndFlush(new Report(userId, request.productId(), request.reportedUserId(),
                request.reason(), request.description(), request.evidenceKey()));
        audit.save(new AuditLog(userId, "REPORT_SUBMITTED", "REPORT", report.getId(), request.reason(), null));
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public MarketplacePage<ReportResponse> list(int admin, String status, int page, int size) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        if (!Set.of("OPEN", "RESOLVED", "REJECTED").contains(status)) {
            throw ApiException.invalidRequest("Unsupported report status.");
        }
        return MarketplacePage.from(reports.findByStatus(status, MarketplacePage.pageable(page, size)).map(ReportResponse::from));
    }

    @Transactional
    public ReportResponse resolve(int admin, int id, ResolveRequest request) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        if (!Set.of("RESOLVED", "REJECTED").contains(request.status())) {
            throw ApiException.invalidRequest("Decision status must be RESOLVED or REJECTED.");
        }
        Report report = reports.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report does not exist."));
        if (report.getReporterUserId().equals(admin) || Integer.valueOf(admin).equals(report.getReportedUserId())) {
            throw ApiException.invalidRequest("You cannot adjudicate your own report.");
        }
        if (!"OPEN".equals(report.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Report has already been handled.");
        }
        if (request.hideProduct()) {
            if (!"RESOLVED".equals(request.status()) || report.getProductId() == null) {
                throw ApiException.invalidRequest("Only a resolved product report can hide a listing.");
            }
            var product = products.lockById(report.getProductId()).orElseThrow();
            product.changeStatus("HIDDEN", request.reason());
            audit.save(new AuditLog(admin, "PRODUCT_HIDDEN", "PRODUCT", product.getId(), request.reason(), null));
        }
        report.resolve(request.status(), admin, request.reason());
        audit.save(new AuditLog(admin, "REPORT_HANDLED", "REPORT", id, request.reason(), request.status()));
        return ReportResponse.from(report);
    }
}
