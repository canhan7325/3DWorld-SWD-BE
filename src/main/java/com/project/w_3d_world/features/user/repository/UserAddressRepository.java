package com.project.w_3d_world.features.user.repository;

import com.project.w_3d_world.features.user.entity.UserAddress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Integer> {
    List<UserAddress> findByUserIdOrderByIdAsc(Integer userId);
}
