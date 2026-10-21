package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface PrintRequestItemRepository extends Repository<PrintRequestItem, Integer> {
    List<PrintRequestItem> findByPrintRequestIdOrderByPrintRequestItemIdAsc(Integer printRequestId);
}
