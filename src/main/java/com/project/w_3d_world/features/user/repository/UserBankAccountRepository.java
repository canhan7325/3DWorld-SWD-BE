package com.project.w_3d_world.features.user.repository;

import com.project.w_3d_world.features.user.entity.UserBankAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBankAccountRepository extends JpaRepository<UserBankAccount, Integer> {
    List<UserBankAccount> findByUserIdAndActiveTrueOrderByIdAsc(Integer userId);
}
