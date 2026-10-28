package com.project.w_3d_world.features.shipping.ghtk;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Address;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Provider-specific geography lives in existing platform_configs; original user addresses stay intact. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GhtkAddressService {
    private static final String PREFIX = "GHTK_ADDRESS_MAPPING:";
    private final UserAddressRepository addresses;
    private final AccountAuthorization authorization;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;

    public record MappingRequest(
            @NotBlank @Size(max = 255) String province,
            @NotBlank @Size(max = 255) String district,
            @NotBlank @Size(max = 255) String ward,
            @NotBlank @Size(max = 1000) String reason) {}
    public record Mapping(Address source, String province, String district, String ward) {}
    public record Resolution(int addressId, Address original, Address carrierAddress, String status) {}

    public Resolution preview(int actor, int addressId, boolean admin) {
        if (admin) authorization.requireRole(actor, RoleCode.ADMIN);
        UserAddress address = addresses.findById(addressId)
                .filter(value -> admin || value.getUserId().equals(actor))
                .orElseThrow(this::missing);
        return resolve(address);
    }

    public Address originalForQuote(UserAddress address) {
        // Fee quotes use current two-level geography; legacy mappings are not applied.
        return original(address);
    }

    public Resolution resolve(UserAddress address) {
        Address original = original(address);
        List<String> values = jdbc.query("SELECT config_value FROM platform_configs WHERE config_key=?",
                (rs, row) -> rs.getString(1), PREFIX + address.getId());
        if (values.isEmpty()) {
            // Missing district alone is not proof of an invalid route: GHTK documents it as optional.
            return new Resolution(address.getId(), original, original, "ORIGINAL");
        }
        Mapping mapping;
        try {
            mapping = mapper.readValue(values.getFirst(), Mapping.class);
            if (mapping.source() == null || blank(mapping.province()) || blank(mapping.district()) || blank(mapping.ward())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GHTK address mapping is invalid. Contact an administrator.");
        }
        if (!same(original, mapping.source())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Address " + address.getId() + " changed after its GHTK mapping was configured. An administrator must update the mapping.");
        }
        Address carrier = new Address(original.address(), clean(mapping.province()), clean(mapping.district()), clean(mapping.ward()));
        return new Resolution(address.getId(), original, carrier, "MAPPED");
    }

    @Transactional
    public Resolution save(int admin, int addressId, MappingRequest request) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        // Lock the same row profile edits update, so the saved source snapshot cannot race an address edit.
        jdbc.queryForList("SELECT address_id FROM user_addresses WHERE address_id=? FOR UPDATE", addressId);
        UserAddress address = addresses.findById(addressId).orElseThrow(this::missing);
        Address source = original(address);
        if (blank(request.province()) || blank(request.district()) || blank(request.ward()) || blank(request.reason())) {
            throw ApiException.invalidRequest("Carrier province, district, ward and mapping reason are required.");
        }
        var mapping = new Mapping(source, clean(request.province()), clean(request.district()), clean(request.ward()));
        jdbc.update("""
                INSERT INTO platform_configs(config_key,config_value,updated_by_user_id,updated_at)
                VALUES (?,?,?,now()) ON CONFLICT (config_key) DO UPDATE
                SET config_value=excluded.config_value,updated_by_user_id=excluded.updated_by_user_id,updated_at=now()
                """, PREFIX + addressId, mapper.writeValueAsString(mapping), admin);
        audit.save(new AuditLog(admin, "GHTK_ADDRESS_MAPPING_UPDATED", "USER_ADDRESS", addressId,
                request.reason(), JsonValues.write(mapping)));
        return resolve(address);
    }

    @Transactional
    public void remove(int admin, int addressId) {
        authorization.requireRole(admin, RoleCode.ADMIN);
        if (!addresses.existsById(addressId)) throw missing();
        jdbc.update("DELETE FROM platform_configs WHERE config_key=?", PREFIX + addressId);
        audit.save(new AuditLog(admin, "GHTK_ADDRESS_MAPPING_REMOVED", "USER_ADDRESS", addressId,
                "Carrier address override removed; use original address", null));
    }

    private Address original(UserAddress address) {
        if (blank(address.getAddressLine()) || blank(address.getProvince()) || blank(address.getWard())) {
            throw ApiException.invalidRequest("Address " + address.getId() + " requires address line, province and ward for GHTK.");
        }
        return new Address(clean(address.getAddressLine()), clean(address.getProvince()), clean(address.getDistrict()), clean(address.getWard()));
    }

    private boolean same(Address a, Address b) {
        return List.of(key(a.address()), key(a.province()), key(a.district()), key(a.ward()))
                .equals(List.of(key(b.address()), key(b.province()), key(b.district()), key(b.ward())));
    }

    private String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
    private String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "Address does not exist.");
    }
}
