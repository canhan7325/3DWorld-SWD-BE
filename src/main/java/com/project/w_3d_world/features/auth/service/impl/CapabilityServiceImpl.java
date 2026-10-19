package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.Capability;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.CapabilityService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.entity.User;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CapabilityServiceImpl implements CapabilityService {
    private final StorefrontRepository storefrontRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Capability> getCapabilities(User user) {
        if (!user.isActive()) {
            return Set.of();
        }

        Set<RoleCode> roles = user.getUserRoles().stream()
                .filter(UserRole::isActive)
                .map(role -> role.getRole().getCode())
                .collect(Collectors.toSet());

        EnumSet<Capability> capabilities = EnumSet.of(
                Capability.PROFILE_READ,
                Capability.PROFILE_UPDATE,
                Capability.ADDRESS_MANAGE,
                Capability.BANK_ACCOUNT_MANAGE);

        if (roles.contains(RoleCode.SELLER)) {
            storefrontRepository.findByOwnerUserId(user.getId()).ifPresent(storefront -> {
                capabilities.add(Capability.STOREFRONT_MANAGE_OWN);
                capabilities.add(Capability.ORDER_PROCESS_EXISTING);
                if (storefront.canPublish()) {
                    capabilities.add(Capability.CATALOG_PUBLISH);
                }
            });
        }

        if (roles.contains(RoleCode.ADMIN)) {
            capabilities.addAll(EnumSet.of(
                    Capability.USER_ROLE_MANAGE,
                    Capability.USER_STATUS_MANAGE,
                    Capability.REPORT_REVIEW,
                    Capability.PLATFORM_CONFIG_MANAGE));
        }

        return capabilities;
    }
}
