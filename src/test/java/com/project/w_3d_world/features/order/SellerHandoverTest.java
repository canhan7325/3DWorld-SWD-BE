package com.project.w_3d_world.features.order;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderItemRepository;
import com.project.w_3d_world.features.order.repository.OrderStatusHistoryRepository;
import com.project.w_3d_world.features.order.service.impl.OrderServiceImpl;
import com.project.w_3d_world.features.shipping.service.ShipmentAutoCreationService;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerHandoverTest {
    @Mock PhysicalOrderRepository orders;
    @Mock PhysicalOrderItemRepository orderItems;
    @Mock StorefrontRepository storefronts;
    @Mock UserRepository users;
    @Mock CommissionWalletService wallet;
    @Mock ShipmentAutoCreationService shipmentAutoCreation;
    @Mock OrderStatusHistoryRepository history;
    @InjectMocks OrderServiceImpl service;

    @Test void processingOrderShipsWithoutRequiringShipmentIdAndReplayDoesNotRepeat() throws Exception {
        try (var mocks = MockitoAnnotations.openMocks(this)) {
            var order = fixture();
            assertEquals("SHIPPED", service.updateStatus(9, 37, "SHIPPED").status());
            assertEquals("SHIPPED", order.getStatus());
            service.updateStatus(9, 37, "SHIPPED");
            verify(shipmentAutoCreation, times(1)).handOver(order, 9);
            verify(history, times(1)).save(any());
            verify(wallet).requireNoActiveRefund(37);
            verify(wallet).requireNoShippingDebt(9);
        }
    }
    @Test void unpaidOrUnconsumedOrderCannotShip() throws Exception {
        try (var mocks = MockitoAnnotations.openMocks(this)) {
            var order = fixture();
            ReflectionTestUtils.setField(order, "reservationStatus", "RESERVED");
            assertThrows(ApiException.class, () -> service.updateStatus(9,37,"SHIPPED"));
            verifyNoInteractions(shipmentAutoCreation);
        }
    }
    private PhysicalOrder fixture() {
        var order = new PhysicalOrder(1, 2, "{}", UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ZERO);
        ReflectionTestUtils.setField(order,"id",37);
        ReflectionTestUtils.setField(order,"status","PROCESSING");
        ReflectionTestUtils.setField(order,"reservationStatus","CONSUMED");
        var store = new Storefront(9,"Shop",null);
        ReflectionTestUtils.setField(store,"id",2);
        when(orders.lockById(37)).thenReturn(Optional.of(order));
        when(storefronts.findByOwnerUserId(9)).thenReturn(Optional.of(store));
        when(users.findWithRolesById(9)).thenReturn(Optional.empty());
        when(orderItems.findByOrderId(37)).thenReturn(List.of());
        return order;
    }
}
