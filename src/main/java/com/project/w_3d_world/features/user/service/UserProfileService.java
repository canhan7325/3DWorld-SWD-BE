package com.project.w_3d_world.features.user.service;

import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.user.dto.ProfileRequests.AddressRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.BankRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.UpdateProfileRequest;
import com.project.w_3d_world.features.user.dto.UserDataResponses.AddressResponse;
import com.project.w_3d_world.features.user.dto.UserDataResponses.BankResponse;
import java.util.List;

public interface UserProfileService {
    UserResponse updateProfile(int userId, UpdateProfileRequest request);

    List<AddressResponse> addresses(int userId);

    AddressResponse addAddress(int userId, AddressRequest request);

    AddressResponse updateAddress(int userId, int addressId, AddressRequest request);

    void deleteAddress(int userId, int addressId);

    AddressResponse setDefaultAddress(int userId, int addressId);

    List<BankResponse> banks(int userId);

    BankResponse addBank(int userId, BankRequest request);

    BankResponse updateBank(int userId, int bankId, BankRequest request);

    void deleteBank(int userId, int bankId);

    BankResponse setDefaultBank(int userId, int bankId);

    BankResponse reviewBank(int reviewerId, int bankId, boolean approved, String reason);
}
