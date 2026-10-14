package com.project.w_3d_world.common.config;

import java.util.Set;

public record AuthenticatedUser(int userId, Set<String> roles) {}
