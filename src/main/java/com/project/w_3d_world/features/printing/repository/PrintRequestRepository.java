package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PrintRequestRepository extends Repository<PrintRequest, Integer> {
    PrintRequest saveAndFlush(PrintRequest request);

    Page<PrintRequest> findByCustomerUserId(Integer customerUserId, Pageable pageable);

    Optional<PrintRequest> findByPrintRequestIdAndCustomerUserId(
            Integer printRequestId, Integer customerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PrintRequest request where request.printRequestId = :requestId "
            + "and request.customerUserId = :customerId")
    Optional<PrintRequest> findOwnedByIdForUpdate(
            @Param("requestId") Integer requestId, @Param("customerId") Integer customerId);
}
