package com.project.w_3d_world.features.payment.repository;

import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {
    Optional<PaymentTransaction> findByOperationKey(String operationKey);

    List<PaymentTransaction> findByPrintRequestIdAndPayerUserId(
            Integer printRequestId, Integer payerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> lockById(@Param("id") int id);
}
