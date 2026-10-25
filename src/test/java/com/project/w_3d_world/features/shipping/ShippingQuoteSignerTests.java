package com.project.w_3d_world.features.shipping;

import static org.junit.jupiter.api.Assertions.*;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.shipping.dto.ShippingQuoteDtos.SignedQuote;
import com.project.w_3d_world.features.shipping.service.ShippingQuoteSigner;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ShippingQuoteSignerTests {
    @Test
    void signedQuoteRejectsTamperAndExpiry() {
        var signer = new ShippingQuoteSigner(Base64.getEncoder().encodeToString(new byte[32]), new ObjectMapper());
        var quote = new SignedQuote(1, "cart-fingerprint", System.currentTimeMillis() + 60000, "v1", List.of());
        String signed = signer.sign(quote);
        assertEquals(quote, signer.read(signed));
        assertThrows(ApiException.class, () -> signer.read("x" + signed));
        assertThrows(ApiException.class, () -> signer.read(signer.sign(new SignedQuote(1, "hash", 1, "v1", List.of()))));
    }
}
