package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
    void requestMapsOnlyTheApprovedTenColumns() throws Exception {
        assertEquals("print_requests", PrintRequest.class.getAnnotation(Table.class).name());
        Map<String, String> columns = Map.of(
                "printRequestId", "print_request_id", "customerUserId", "customer_user_id",
                "shippingAddressId", "shipping_address_id", "shippingAddressSnapshot", "shipping_address_snapshot",
                "status", "status", "customerNotes", "customer_notes", "lockedAt", "locked_at",
                "createdAt", "created_at", "updatedAt", "updated_at", "completedAt", "completed_at");
        assertColumns(PrintRequest.class, columns);
        assertEquals(Integer.class, PrintRequest.class.getDeclaredField("printRequestId").getType());
        assertEquals(Integer.class, PrintRequest.class.getDeclaredField("customerUserId").getType());
        assertEquals(Instant.class, PrintRequest.class.getDeclaredField("createdAt").getType());
        assertEquals(30, PrintRequest.class.getDeclaredField("status").getAnnotation(Column.class).length());
        assertFalse(PrintRequest.class.getDeclaredField("status").getAnnotation(Column.class).nullable());
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
    void repositoriesExposeOnlyReadsAndControllerOnlyTwoGetMappings() {
        assertTrue(Repository.class.isAssignableFrom(PrintRequestRepository.class));
        assertTrue(Repository.class.isAssignableFrom(PrintRequestItemRepository.class));
        assertEquals(Set.of("findByCustomerUserId", "findByPrintRequestIdAndCustomerUserId"),
                methodNames(PrintRequestRepository.class));
        assertEquals(Set.of("findByPrintRequestIdOrderByPrintRequestItemIdAsc"),
                methodNames(PrintRequestItemRepository.class));
        assertEquals("/api/print/requests", PrintRequestController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(Set.of("/getAll", "/getId/{printRequestId}"),
                Arrays.stream(PrintRequestController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class))
                        .map(method -> method.getAnnotation(GetMapping.class).value()[0])
                        .collect(Collectors.toSet()));
        assertEquals(2, PrintRequestController.class.getDeclaredMethods().length);
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
