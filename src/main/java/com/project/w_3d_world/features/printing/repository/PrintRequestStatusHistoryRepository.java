package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PrintRequestStatusHistoryRepository extends Repository<PrintRequestStatusHistory, Integer> {
    PrintRequestStatusHistory saveAndFlush(PrintRequestStatusHistory history);

    List<PrintRequestStatusHistory> findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(
            Integer printRequestId);
}
