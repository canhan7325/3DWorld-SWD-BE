package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.features.shipping.dto.request.SaveRequest;
import com.project.w_3d_world.features.shipping.dto.response.PackageResponse;
import com.project.w_3d_world.features.shipping.dto.response.ProfileResponse;
import java.util.List;

public interface ShippingPackageService {

    List<ProfileResponse> profiles(int actor);
    List<PackageResponse> list(int actor, int orderId);
    List<PackageResponse> save(int actor, int orderId, SaveRequest request);
}
