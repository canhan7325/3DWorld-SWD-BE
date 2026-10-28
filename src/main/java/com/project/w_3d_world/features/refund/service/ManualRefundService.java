package com.project.w_3d_world.features.refund.service;

import com.project.w_3d_world.features.refund.dto.request.RefundRequests.*;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ManualRefundService {
    Map<String,Object> submit(int buyer, int orderId, int bankId, String accountNumber, String reason, List<MultipartFile> files);
    List<Map<String,Object>> queue(int actor, String status, int page, int size);
    Map<String,Object> detail(int actor, long id);
    Map<String,Object> review(int actor, long id, Review request);
    Map<String,Object> confirm(int admin, long id, Transfer request);
    void assessCancelled(int actor, int orderId, ShippingAssessment request);
    Map<String,String> transferDetails(int admin, long id, String reason);
}
