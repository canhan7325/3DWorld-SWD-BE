package com.project.w_3d_world.features.user.dto;

import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.entity.UserBankAccount;

public final class UserDataResponses {
    private UserDataResponses() {}

    public record AddressResponse(
            Integer id,
            String recipientName,
            String phone,
            String addressLine,
            String city,
            boolean isDefault) {
        public static AddressResponse from(UserAddress address) {
            return new AddressResponse(
                    address.getId(),
                    address.getRecipientName(),
                    address.getPhone(),
                    address.getAddressLine(),
                    address.getCity(),
                    address.isDefaultAddress());
        }
    }

    public record BankResponse(
            Integer id,
            String bankCode,
            String bankName,
            String accountHolder,
            String accountNumberMasked,
            String verificationStatus,
            boolean isDefault,
            boolean isActive) {
        public static BankResponse from(UserBankAccount bank) {
            return new BankResponse(
                    bank.getId(),
                    bank.getBankCode(),
                    bank.getBankName(),
                    bank.getAccountHolder(),
                    bank.getAccountNumberMasked(),
                    bank.getVerificationStatus(),
                    bank.isDefaultAccount(),
                    bank.isActive());
        }
    }
}
