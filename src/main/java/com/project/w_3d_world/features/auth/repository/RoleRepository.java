package com.project.w_3d_world.features.auth.repository;

import com.project.w_3d_world.features.auth.entity.Role;
import com.project.w_3d_world.features.auth.enums.RoleCode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByCode(RoleCode code);
}
