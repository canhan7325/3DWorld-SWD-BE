package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.features.auth.enums.Capability;
import com.project.w_3d_world.features.user.entity.User;
import java.util.Set;

public interface CapabilityService {
    Set<Capability> getCapabilities(User user);
}
