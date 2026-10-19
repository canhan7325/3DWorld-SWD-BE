package com.project.w_3d_world.features.payment.repository;

import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {
    Optional<PaymentTransaction> findByOperationKey(String operationKey);
}
