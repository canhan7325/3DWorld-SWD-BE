package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface PrintRequestRepository extends Repository<PrintRequest, Integer> {
    Page<PrintRequest> findByCustomerUserId(Integer customerUserId, Pageable pageable);

    Optional<PrintRequest> findByPrintRequestIdAndCustomerUserId(
            Integer printRequestId, Integer customerUserId);
}
