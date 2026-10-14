package com.project.w_3d_world.features.auth.entity;

import com.project.w_3d_world.features.auth.enums.RoleCode;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, unique = true, length = 40)
    private RoleCode code;

    @Column(name = "role_name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
