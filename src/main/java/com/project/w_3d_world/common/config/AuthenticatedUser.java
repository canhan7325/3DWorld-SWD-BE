package com.project.w_3d_world.common.config;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(int userId, Set<String> roles, UUID sessionId) {}
