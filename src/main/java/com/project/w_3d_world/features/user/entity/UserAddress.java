package com.project.w_3d_world.features.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(length = 30)
    private String phone;

    @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    private String city;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "province", length = 255)
    private String province;

    @Column(name = "district", length = 255)
    private String district;

    @Column(name = "ward", length = 255)
    private String ward;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UserAddress(
            Integer userId,
            String recipientName,
            String phone,
            String addressLine,
            String city) {
        this.userId = userId;
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine = addressLine;
        this.city = city;
    }

    public void update(String recipientName, String phone, String addressLine, String city) {
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine = addressLine;
        this.city = city;
        updatedAt = Instant.now();
    }

    public void updateRegion(String province, String district, String ward) {
        this.province = province;
        this.district = district;
        this.ward = ward;
        updatedAt = Instant.now();
    }

    public void markDefault(boolean value) {
        this.defaultAddress = value;
    }
}
