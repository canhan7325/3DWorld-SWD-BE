package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintQuoteItem;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import com.project.w_3d_world.features.printing.repository.PrintQuoteItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PrintRequestMappingTest {
    @Test
    void requestMapsTheApprovedV2Columns() throws Exception {
        assertEquals("print_requests", PrintRequest.class.getAnnotation(Table.class).name());
        Map<String, String> columns = Map.ofEntries(
                Map.entry("printRequestId", "print_request_id"),
                Map.entry("customerUserId", "customer_user_id"),
                Map.entry("shippingAddressId", "shipping_address_id"),
                Map.entry("shippingAddressSnapshot", "shipping_address_snapshot"),
                Map.entry("status", "status"), Map.entry("customerNotes", "customer_notes"),
                Map.entry("lockedAt", "locked_at"), Map.entry("createdAt", "created_at"),
                Map.entry("updatedAt", "updated_at"), Map.entry("completedAt", "completed_at"),
                Map.entry("cancellationReason", "cancellation_reason"),
                Map.entry("cancelledAt", "cancelled_at"),
                Map.entry("currentFileVersion", "current_file_version"),
                Map.entry("currentQuoteId", "current_quote_id"),
                Map.entry("currentSpecVersion", "current_spec_version"),
                Map.entry("submittedAt", "submitted_at"),
                Map.entry("handledAt", "handled_at"),
                Map.entry("handledByUserId", "handled_by_user_id"),
                Map.entry("copyrightConfirmed", "copyright_confirmed"),
                Map.entry("previewConfirmed", "preview_confirmed"));
        assertColumns(PrintRequest.class, columns);
        assertEquals(Integer.class, PrintRequest.class.getDeclaredField("printRequestId").getType());
        assertEquals(Integer.class, PrintRequest.class.getDeclaredField("customerUserId").getType());
        assertEquals(Instant.class, PrintRequest.class.getDeclaredField("createdAt").getType());
        for (String field : Set.of("cancelledAt", "submittedAt", "handledAt")) {
            assertEquals(Instant.class, PrintRequest.class.getDeclaredField(field).getType());
        }
        for (String field : Set.of("currentFileVersion", "currentSpecVersion", "currentQuoteId",
                "handledByUserId")) {
            assertEquals(Integer.class, PrintRequest.class.getDeclaredField(field).getType());
        }
        var address = PrintRequest.class.getDeclaredField("shippingAddressSnapshot");
        assertEquals(String.class, address.getType());
        assertEquals("jsonb", address.getAnnotation(Column.class).columnDefinition());
        assertEquals(SqlTypes.JSON, address.getAnnotation(JdbcTypeCode.class).value());
        assertEquals(30, PrintRequest.class.getDeclaredField("status").getAnnotation(Column.class).length());
        assertFalse(PrintRequest.class.getDeclaredField("status").getAnnotation(Column.class).nullable());
        for (String field : Set.of("copyrightConfirmed", "previewConfirmed")) {
            assertEquals(boolean.class, PrintRequest.class.getDeclaredField(field).getType());
            assertFalse(PrintRequest.class.getDeclaredField(field).getAnnotation(Column.class).nullable());
        }
        var draft = new PrintRequest(7, null);
        assertEquals("DRAFT", draft.getStatus());
        assertFalse(draft.isCopyrightConfirmed());
        assertFalse(draft.isPreviewConfirmed());
        assertNull(draft.getSubmittedAt());
        assertNull(draft.getCurrentFileVersion());
        assertIdentity(PrintRequest.class, "printRequestId");
    }

    @Test
    void itemMapsOnlyTheApprovedFourteenColumnsWithScalarReferences() throws Exception {
        assertEquals("print_request_items", PrintRequestItem.class.getAnnotation(Table.class).name());
        Map<String, String> columns = Map.ofEntries(
                Map.entry("printRequestItemId", "print_request_item_id"),
                Map.entry("printRequestId", "print_request_id"),
                Map.entry("modelVersionId", "model_version_id"),
                Map.entry("uploadedFileKey", "uploaded_file_key"),
                Map.entry("materialId", "material_id"),
                Map.entry("color", "color"), Map.entry("quality", "quality"),
                Map.entry("infillPercent", "infill_percent"),
                Map.entry("widthMm", "width_mm"), Map.entry("heightMm", "height_mm"),
                Map.entry("depthMm", "depth_mm"), Map.entry("quantity", "quantity"),
                Map.entry("notes", "notes"), Map.entry("productionStatus", "production_status"));
        assertColumns(PrintRequestItem.class, columns);
        assertIdentity(PrintRequestItem.class, "printRequestItemId");
        for (String name : Set.of("printRequestId", "modelVersionId", "materialId")) {
            assertEquals(Integer.class, PrintRequestItem.class.getDeclaredField(name).getType());
        }
        for (String name : Set.of("widthMm", "heightMm", "depthMm")) {
            var column = PrintRequestItem.class.getDeclaredField(name).getAnnotation(Column.class);
            assertEquals(BigDecimal.class, PrintRequestItem.class.getDeclaredField(name).getType());
            assertEquals(12, column.precision());
            assertEquals(2, column.scale());
        }
        assertEquals(100, PrintRequestItem.class.getDeclaredField("color").getAnnotation(Column.class).length());
        assertEquals(40, PrintRequestItem.class.getDeclaredField("quality").getAnnotation(Column.class).length());
        assertEquals(30, PrintRequestItem.class.getDeclaredField("productionStatus").getAnnotation(Column.class).length());
    }

    @Test
    void v2HistoryAndReviewEntitiesMapExportedColumns() throws Exception {
        assertEquals("print_request_file_versions",
                PrintRequestFileVersion.class.getAnnotation(Table.class).name());
        assertColumns(PrintRequestFileVersion.class, Map.ofEntries(
                Map.entry("fileVersionId", "file_version_id"),
                Map.entry("printRequestId", "print_request_id"),
                Map.entry("version", "version"), Map.entry("fileName", "file_name"),
                Map.entry("fileSize", "file_size"), Map.entry("fileType", "file_type"),
                Map.entry("fileUrl", "file_url"), Map.entry("uploadedAt", "uploaded_at"),
                Map.entry("uploadedByUserId", "uploaded_by_user_id")));
        assertIdentity(PrintRequestFileVersion.class, "fileVersionId");
        assertEquals(Long.class, PrintRequestFileVersion.class.getDeclaredField("fileSize").getType());
        assertEquals(255, PrintRequestFileVersion.class.getDeclaredField("fileName")
                .getAnnotation(Column.class).length());
        assertEquals(100, PrintRequestFileVersion.class.getDeclaredField("fileType")
                .getAnnotation(Column.class).length());

        assertEquals("print_request_spec_versions",
                PrintRequestSpecVersion.class.getAnnotation(Table.class).name());
        assertColumns(PrintRequestSpecVersion.class, Map.of(
                "specVersionId", "spec_version_id", "printRequestId", "print_request_id",
                "version", "version", "specificationData", "specification_data",
                "createdAt", "created_at", "createdByUserId", "created_by_user_id"));
        assertIdentity(PrintRequestSpecVersion.class, "specVersionId");
        assertEquals(SqlTypes.JSON, PrintRequestSpecVersion.class
                .getDeclaredField("specificationData").getAnnotation(JdbcTypeCode.class).value());

        assertEquals("print_request_status_history",
                PrintRequestStatusHistory.class.getAnnotation(Table.class).name());
        assertColumns(PrintRequestStatusHistory.class, Map.of(
                "printStatusHistoryId", "print_status_history_id",
                "printRequestId", "print_request_id", "fromStatus", "from_status",
                "toStatus", "to_status", "changedAt", "changed_at",
                "changedByUserId", "changed_by_user_id", "reason", "reason",
                "sourceEventId", "source_event_id"));
        assertIdentity(PrintRequestStatusHistory.class, "printStatusHistoryId");

        assertEquals("print_request_technical_reviews",
                PrintRequestTechnicalReview.class.getAnnotation(Table.class).name());
        assertColumns(PrintRequestTechnicalReview.class, Map.of(
                "technicalReviewId", "technical_review_id",
                "printRequestId", "print_request_id",
                "reviewerUserId", "reviewer_user_id", "reviewData", "review_data",
                "createdAt", "created_at", "updatedAt", "updated_at"));
        assertIdentity(PrintRequestTechnicalReview.class, "technicalReviewId");
        assertEquals(SqlTypes.JSON, PrintRequestTechnicalReview.class
                .getDeclaredField("reviewData").getAnnotation(JdbcTypeCode.class).value());

        assertEquals("print_quotes", PrintQuote.class.getAnnotation(Table.class).name());
        assertColumns(PrintQuote.class, Map.ofEntries(
                Map.entry("printQuoteId", "print_quote_id"),
                Map.entry("printRequestId", "print_request_id"),
                Map.entry("quotedByUserId", "quoted_by_user_id"),
                Map.entry("versionNumber", "version_number"),
                Map.entry("fileVersionSnapshot", "file_version_snapshot"),
                Map.entry("specificationVersionSnapshot", "specification_version_snapshot"),
                Map.entry("totalAmount", "total_amount"),
                Map.entry("depositAmount", "deposit_amount"),
                Map.entry("remainingAmount", "remaining_amount"),
                Map.entry("etaAt", "eta_at"),
                Map.entry("expiresAt", "expires_at"),
                Map.entry("quoteStatus", "quote_status"),
                Map.entry("issuedAt", "issued_at"),
                Map.entry("acceptedAt", "accepted_at")));
        assertIdentity(PrintQuote.class, "printQuoteId");
        for (String amountField : Set.of("totalAmount", "depositAmount", "remainingAmount")) {
            var col = PrintQuote.class.getDeclaredField(amountField).getAnnotation(Column.class);
            assertEquals(18, col.precision());
            assertEquals(2, col.scale());
        }

        assertEquals("print_quote_items", PrintQuoteItem.class.getAnnotation(Table.class).name());
        assertColumns(PrintQuoteItem.class, Map.ofEntries(
                Map.entry("printQuoteItemId", "print_quote_item_id"),
                Map.entry("printQuoteId", "print_quote_id"),
                Map.entry("printRequestItemId", "print_request_item_id"),
                Map.entry("fileVersionSnapshot", "file_version_snapshot"),
                Map.entry("specificationVersionSnapshot", "specification_version_snapshot"),
                Map.entry("fileReferenceSnapshot", "file_reference_snapshot"),
                Map.entry("specificationSnapshot", "specification_snapshot"),
                Map.entry("quantitySnapshot", "quantity_snapshot"),
                Map.entry("materialCostSnapshot", "material_cost_snapshot"),
                Map.entry("laborCostSnapshot", "labor_cost_snapshot"),
                Map.entry("optionFeeSnapshot", "option_fee_snapshot"),
                Map.entry("lineTotal", "line_total")));
        assertIdentity(PrintQuoteItem.class, "printQuoteItemId");
        assertEquals(SqlTypes.JSON, PrintQuoteItem.class
                .getDeclaredField("specificationSnapshot").getAnnotation(JdbcTypeCode.class).value());
    }

    @Test
    void repositoriesExposeOwnedReadsAndDraftWritesWithThreeRoutes() {
        assertTrue(Repository.class.isAssignableFrom(PrintRequestRepository.class));
        assertTrue(Repository.class.isAssignableFrom(PrintRequestItemRepository.class));
        assertTrue(Repository.class.isAssignableFrom(PrintQuoteRepository.class));
        assertTrue(Repository.class.isAssignableFrom(PrintQuoteItemRepository.class));
        assertEquals(Set.of("findByCustomerUserId", "findByPrintRequestIdAndCustomerUserId",
                        "findOwnedByIdForUpdate", "findById", "findByIdForUpdate",
                        "findUnacceptedQuoteExpirationCandidates", "findStaffReviewQueue",
                        "findProductionBoard",
                        "saveAndFlush"),
                methodNames(PrintRequestRepository.class));
        assertEquals(Set.of("findByPrintRequestIdOrderByPrintRequestItemIdAsc",
                        "findByPrintRequestIdInOrderByPrintRequestIdAscPrintRequestItemIdAsc", "saveAndFlush"),
                methodNames(PrintRequestItemRepository.class));
        assertEquals("/api/print/requests", PrintRequestController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(Set.of("/getAll", "/getId/{printRequestId}", "/getId/{printRequestId}/file",
                        "/getId/{printRequestId}/file/versions/getAll",
                        "/getId/{printRequestId}/file/versions/{version}/download",
                        "/getId/{printRequestId}/specifications/versions/getAll",
                        "/getId/{printRequestId}/status-history/getAll",
                        "/getId/{printRequestId}/quotes/getAll"),
                Arrays.stream(PrintRequestController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class))
                        .map(method -> method.getAnnotation(GetMapping.class).value()[0])
                        .collect(Collectors.toSet()));
        assertEquals(Set.of("/create", "/getId/{printRequestId}/file/versions/create",
                        "/getId/{printRequestId}/preview/confirm",
                        "/getId/{printRequestId}/submit",
                        "/getId/{printRequestId}/cancel",
                        "/getId/{printRequestId}/quotes/{quoteId}/accept",
                        "/getId/{printRequestId}/quotes/{quoteId}/request-revision"),
                Arrays.stream(PrintRequestController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(PostMapping.class))
                        .map(method -> method.getAnnotation(PostMapping.class).value()[0])
                        .collect(Collectors.toSet()));
        assertEquals(Set.of("/getId/{printRequestId}/updateSpecification",
                        "/getId/{printRequestId}/updateShippingAddress"),
                Arrays.stream(PrintRequestController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(PatchMapping.class))
                        .map(method -> method.getAnnotation(PatchMapping.class).value()[0])
                        .collect(Collectors.toSet()));
        assertEquals(17, Arrays.stream(PrintRequestController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)).count());
    }

    private static void assertColumns(Class<?> entity, Map<String, String> expected) throws Exception {
        Set<String> mapped = Arrays.stream(entity.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(field -> field.getName()).collect(Collectors.toSet());
        assertEquals(expected.keySet(), mapped);
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), entity.getDeclaredField(entry.getKey())
                    .getAnnotation(Column.class).name());
        }
    }

    private static void assertIdentity(Class<?> entity, String field) throws Exception {
        var id = entity.getDeclaredField(field);
        assertTrue(id.isAnnotationPresent(Id.class));
        assertEquals(GenerationType.IDENTITY, id.getAnnotation(GeneratedValue.class).strategy());
    }

    private static Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName)
                .collect(Collectors.toSet());
    }
}
