package com.project.w_3d_world.features.shipping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.shipping.ghtk.GhtkAddressService;
import com.project.w_3d_world.features.shipping.ghtk.GhtkAddressService.*;
import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Address;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class GhtkAddressTests {
    UserAddressRepository addresses = mock(UserAddressRepository.class);
    AccountAuthorization authorization = mock(AccountAuthorization.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AuditLogRepository audit = mock(AuditLogRepository.class);
    ObjectMapper mapper = new ObjectMapper();
    GhtkAddressService service = new GhtkAddressService(addresses, authorization, jdbc, mapper, audit);
    UserAddress address;
    Address source;

    @BeforeEach
    void setup() {
        // Fictional names deliberately avoid implying an authoritative nationwide geography dataset.
        address = new UserAddress(7, "Buyer", "0901234567", "1 Main Street", "Current province");
        address.updateRegion("Current province", null, "Merged ward");
        ReflectionTestUtils.setField(address, "id", 10);
        source = new Address("1 Main Street", "Current province", "", "Merged ward");
        when(addresses.findById(10)).thenReturn(Optional.of(address));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("GHTK_ADDRESS_MAPPING:10"))).thenReturn(List.of());
    }

    @Test
    void originalTwoLevelAddressRemainsSupportedWithoutInventedDistrict() {
        var result = service.preview(7, 10, false);
        assertEquals("ORIGINAL", result.status());
        assertEquals("", result.carrierAddress().district());
        assertEquals("Merged ward", address.getWard());
        verifyNoInteractions(authorization);
    }

    @Test
    void explicitCarrierMappingPreservesStoredAddressAndStreet() {
        mapping(new Mapping(source, "Carrier province", "Legacy district", "Legacy ward"));
        var result = service.preview(7, 10, false);
        assertEquals("MAPPED", result.status());
        assertEquals("Legacy district", result.carrierAddress().district());
        assertEquals("Legacy ward", result.carrierAddress().ward());
        assertEquals("1 Main Street", result.carrierAddress().address());
        assertEquals(source, result.original());
        assertEquals(source, service.originalForQuote(address));
        assertNull(address.getDistrict());
        verify(addresses, never()).save(any());
    }

    @Test
    void changedSourceRejectsStaleMappingInsteadOfSendingWrongGeography() {
        mapping(new Mapping(source, "Carrier province", "Legacy district", "Legacy ward"));
        address.update("Buyer", "0901234567", "Different street", "Current province");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.resolve(address)).getStatus());
        assertEquals("Different street", service.originalForQuote(address).address());
        assertEquals("Merged ward", service.originalForQuote(address).ward());
    }

    @Test
    void usersCannotInspectOtherAddressesAndAdminWritesAreAudited() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.preview(9, 10, false)).getStatus());
        var request = new MappingRequest("Carrier province", "Legacy district", "Legacy ward", "Checked carrier geography");
        service.save(1, 10, request);
        verify(authorization).requireRole(1, RoleCode.ADMIN);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("INSERT INTO platform_configs"), eq("GHTK_ADDRESS_MAPPING:10"), json.capture(), eq(1));
        var saved = mapper.readValue(json.getValue(), Mapping.class);
        assertEquals(source, saved.source());
        assertEquals("Legacy district", saved.district());
        verify(audit).save(any());
        verify(addresses, never()).save(any());
    }

    @Test
    void malformedMappingFailsClosedAndNormalizationDoesNotGuessAliases() {
        mapping(new Mapping(new Address(" 1 Main Street ", "CURRENT PROVINCE", "", "merged ward"),
                "Carrier province", "Legacy district", "Legacy ward"));
        assertEquals("MAPPED", service.resolve(address).status());
        when(jdbc.query(anyString(), any(RowMapper.class), eq("GHTK_ADDRESS_MAPPING:10")))
                .thenReturn(List.of("{not-json}"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> service.resolve(address)).getStatus());
    }

    private void mapping(Mapping value) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("GHTK_ADDRESS_MAPPING:10")))
                .thenReturn(List.of(mapper.writeValueAsString(value)));
    }
}
