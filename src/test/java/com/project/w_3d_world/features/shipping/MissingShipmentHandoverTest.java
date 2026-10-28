package com.project.w_3d_world.features.shipping;

import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.shipping.entity.Shipment;
import com.project.w_3d_world.features.shipping.entity.ShipmentStatus;
import com.project.w_3d_world.features.shipping.repository.ShipmentRepository;
import com.project.w_3d_world.features.shipping.service.ShippingProvider;
import com.project.w_3d_world.features.shipping.service.impl.ShipmentAutoCreationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MissingShipmentHandoverTest {
    @Test void createsMissingShipmentAndMarksItShipped() {
        var repository = mock(ShipmentRepository.class);
        var provider = mock(ShippingProvider.class);
        var audit = mock(AuditLogRepository.class);
        var service = new ShipmentAutoCreationServiceImpl(repository, provider, audit);
        var order = new PhysicalOrder(1,2,"{}",UUID.randomUUID(),BigDecimal.TEN,BigDecimal.ZERO);
        ReflectionTestUtils.setField(order,"id",37);
        when(repository.findByPhysicalOrderId(37)).thenReturn(Optional.empty());
        when(provider.createTrackingNumber("GHTK",37)).thenReturn("DEMO-37");
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            Shipment shipment = call.getArgument(0);
            ReflectionTestUtils.setField(shipment,"id",7);
            return shipment;
        });
        service.handOver(order,9);
        var captured = org.mockito.ArgumentCaptor.forClass(Shipment.class);
        verify(repository).save(captured.capture());
        assertEquals(ShipmentStatus.SHIPPED,captured.getValue().getStatus());
        assertEquals(37,captured.getValue().getPhysicalOrderId());
        assertNotNull(captured.getValue().getShippedAt());
    }
}
