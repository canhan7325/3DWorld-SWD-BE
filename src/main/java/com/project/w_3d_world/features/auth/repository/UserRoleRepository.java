package com.project.w_3d_world.features.auth.repository;

import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.entity.UserRoleId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {}
