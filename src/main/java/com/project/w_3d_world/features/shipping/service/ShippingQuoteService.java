package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.entity.PhysicalOrderItem;
import com.project.w_3d_world.features.shipping.dto.request.QuoteRequest;
import com.project.w_3d_world.features.shipping.dto.response.QuoteResponse;
import com.project.w_3d_world.features.shipping.dto.response.ShopQuote;
import java.util.List;
import java.util.Map;

public interface ShippingQuoteService {
    String VERSION = "GHTK_FLOOR_PACK_V1";
    boolean enabled();
    QuoteResponse quote(int userId, QuoteRequest request);
    Map<Integer, ShopQuote> validate(int userId, List<Integer> ids, int addressId, String token);
    void persist(PhysicalOrder order, ShopQuote quote, Map<Integer, PhysicalOrderItem> itemsByCartId);
}
