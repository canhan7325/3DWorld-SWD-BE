package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PrintRequestTechnicalReviewRepository extends Repository<PrintRequestTechnicalReview, Integer> {
    PrintRequestTechnicalReview saveAndFlush(PrintRequestTechnicalReview review);

    List<PrintRequestTechnicalReview> findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(
            Integer printRequestId);
}
