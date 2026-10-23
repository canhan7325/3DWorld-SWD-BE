package com.project.w_3d_world.features.user.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.auth.service.CapabilityService;
import com.project.w_3d_world.features.user.dto.ProfileRequests.AddressRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.BankRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.UpdateProfileRequest;
import com.project.w_3d_world.features.user.dto.UserDataResponses.AddressResponse;
import com.project.w_3d_world.features.user.dto.UserDataResponses.BankResponse;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.entity.UserBankAccount;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import com.project.w_3d_world.features.user.service.UserProfileService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final UserBankAccountRepository banks;
    private final CapabilityService capabilities;
    private final AuditLogRepository auditLogs;

    private User user(int id) {
        return users.lockById(id)
                .orElseThrow(() -> ApiException.invalidRequest("User does not exist."));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(int id, UpdateProfileRequest request) {
        User user = users.lockById(id).orElseThrow(ApiException::invalidCredentials);
        if (!Objects.equals(user.getPhone(), request.phone())) {
            for (UserBankAccount account : banks.findByUserIdAndActiveTrueOrderByIdAsc(id)) {
                account.invalidateVerification();
            }
            auditLogs.save(new AuditLog(id, "PHONE_CHANGED", "USER", id,
                    "Phone and bank verification invalidated", null));
        }
        user.updateProfile(request.fullName().trim(), request.phone());
        user.updateDateOfBirth(request.dateOfBirth());
        return UserResponse.from(user, capabilities.getCapabilities(user));
    }

    @Override
    @Transactional
    public List<AddressResponse> addresses(int id) {
        user(id);
        return addresses.findByUserIdOrderByIdAsc(id).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse addAddress(int id, AddressRequest request) {
        user(id);
        UserAddress address = new UserAddress(
                id,
                request.recipientName().trim(),
                request.phone(),
                request.addressLine().trim(),
                request.city());
        address.updateRegion(request.province(), request.district(), request.ward());
        addresses.save(address);
        if (addresses.findByUserIdOrderByIdAsc(id).size() == 1) {
            setDefaultAddress(id, address.getId());
        }
        return AddressResponse.from(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(int id, int addressId, AddressRequest request) {
        user(id);
        UserAddress address = ownedAddress(id, addressId);
        address.update(
                request.recipientName().trim(),
                request.phone(),
                request.addressLine().trim(),
                request.city());
        address.updateRegion(request.province(), request.district(), request.ward());
        return AddressResponse.from(address);
    }

    @Override
    @Transactional
    public void deleteAddress(int id, int addressId) {
        user(id);
        UserAddress address = ownedAddress(id, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        if (wasDefault) {
            addresses.findByUserIdOrderByIdAsc(id).stream()
                    .findFirst()
                    .ifPresent(item -> item.markDefault(true));
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(int id, int addressId) {
        user(id);
        UserAddress address = ownedAddress(id, addressId);
        addresses.findByUserIdOrderByIdAsc(id).forEach(item -> item.markDefault(false));
        addresses.flush();
        address.markDefault(true);
        return AddressResponse.from(address);
    }

    @Override
    @Transactional
    public List<BankResponse> banks(int id) {
        user(id);
        return banks.findByUserIdAndActiveTrueOrderByIdAsc(id).stream()
                .map(BankResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public BankResponse addBank(int id, BankRequest request) {
        user(id);
        UserBankAccount bank = newBank(id, request);
        banks.save(bank);
        if (banks.findByUserIdAndActiveTrueOrderByIdAsc(id).size() == 1) {
            setDefaultBank(id, bank.getId());
        }
        return BankResponse.from(bank);
    }

    @Override
    @Transactional
    public BankResponse updateBank(int id, int bankId, BankRequest request) {
        user(id);
        UserBankAccount bank = ownedBank(id, bankId);
        bank.update(
                request.bankCode().trim(),
                request.bankName().trim(),
                request.accountHolder().trim(),
                hash(request.accountNumber()),
                mask(request.accountNumber()));
        auditLogs.save(new AuditLog(
                id, "BANK_ACCOUNT_UPDATED", "USER_BANK_ACCOUNT", bankId,
                "Bank account details changed", "verification_status=PENDING"));
        return BankResponse.from(bank);
    }

    @Override
    @Transactional
    public void deleteBank(int id, int bankId) {
        user(id);
        UserBankAccount bank = ownedBank(id, bankId);
        boolean wasDefault = bank.isDefaultAccount();
        bank.deactivate();
        if (wasDefault) {
            banks.findByUserIdAndActiveTrueOrderByIdAsc(id).stream()
                    .findFirst()
                    .ifPresent(item -> item.markDefault(true));
        }
    }

    @Override
    @Transactional
    public BankResponse setDefaultBank(int id, int bankId) {
        user(id);
        UserBankAccount bank = ownedBank(id, bankId);
        banks.findByUserIdAndActiveTrueOrderByIdAsc(id).forEach(item -> item.markDefault(false));
        banks.flush();
        bank.markDefault(true);
        return BankResponse.from(bank);
    }

    @Override
    @Transactional
    public BankResponse reviewBank(int reviewerId, int bankId, boolean approved, String reason) {
        UserBankAccount bank = banks.findById(bankId)
                .orElseThrow(() -> ApiException.invalidRequest("Bank account does not exist."));
        bank.review(approved);
        auditLogs.save(new AuditLog(
                reviewerId,
                approved ? "BANK_ACCOUNT_VERIFIED" : "BANK_ACCOUNT_REJECTED",
                "USER_BANK_ACCOUNT",
                bankId,
                reason,
                "verification_status=" + bank.getVerificationStatus()));
        return BankResponse.from(bank);
    }

    private UserAddress ownedAddress(int userId, int addressId) {
        UserAddress address = addresses.findById(addressId)
                .orElseThrow(() -> ApiException.invalidRequest("Address does not exist."));
        if (!address.getUserId().equals(userId)) {
            throw ApiException.invalidRequest("You do not have permission to access this address.");
        }
        return address;
    }

    private UserBankAccount ownedBank(int userId, int bankId) {
        UserBankAccount bank = banks.findById(bankId)
                .orElseThrow(() -> ApiException.invalidRequest("Bank account does not exist."));
        if (!bank.getUserId().equals(userId) || !bank.isActive()) {
            throw ApiException.invalidRequest("You do not have permission to access this resource.");
        }
        return bank;
    }

    private UserBankAccount newBank(int userId, BankRequest request) {
        return new UserBankAccount(
                userId,
                request.bankCode().trim(),
                request.bankName().trim(),
                request.accountHolder().trim(),
                hash(request.accountNumber()),
                mask(request.accountNumber()));
    }

    private String mask(String accountNumber) {
        String value = accountNumber.trim();
        int visibleStart = Math.max(0, value.length() - 4);
        return "*".repeat(visibleStart) + value.substring(visibleStart);
    }

    private String hash(String accountNumber) {
        try {
            byte[] bytes = accountNumber.trim().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash bank account number", exception);
        }
    }
}
