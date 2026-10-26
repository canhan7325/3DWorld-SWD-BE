package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestShippingAddressResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestWorkflowPolicy;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestShippingAddressService {
    private final PrintRequestRepository requests;
    private final UserAddressRepository addresses;
    private final AuditLogRepository audits;
    private final ObjectMapper json;

    @Transactional
    public PrintRequestShippingAddressResponse select(Integer customerId, Integer requestId,
            Integer addressId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (addressId == null || addressId <= 0) {
            throw ApiException.invalidRequest("addressId must be positive.");
        }
        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (!PrintRequestWorkflowPolicy.isDraft(request) || request.getLockedAt() != null
                || request.getCancelledAt() != null || request.getCurrentQuoteId() != null
                || request.getHandledAt() != null || request.getHandledByUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Shipping address can only be selected on an unhandled Draft.");
        }
        if (addressId.equals(request.getShippingAddressId())
                && request.getShippingAddressSnapshot() != null) {
            return new PrintRequestShippingAddressResponse(requestId, addressId);
        }
        UserAddress address = addresses.findById(addressId)
                .filter(value -> customerId.equals(value.getUserId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Shipping address was not found."));
        if (blank(address.getRecipientName()) || blank(address.getPhone())
                || blank(address.getAddressLine()) || blank(address.getProvince())
                || blank(address.getDistrict()) || blank(address.getWard())) {
            throw ApiException.invalidRequest("Shipping address is incomplete.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recipientName", address.getRecipientName());
        snapshot.put("phone", address.getPhone());
        snapshot.put("addressLine", address.getAddressLine());
        snapshot.put("province", address.getProvince());
        snapshot.put("district", address.getDistrict());
        snapshot.put("ward", address.getWard());
        request.selectShippingAddress(addressId, json.writeValueAsString(snapshot));
        requests.saveAndFlush(request);
        audits.saveAndFlush(new AuditLog(customerId, "POD_SHIPPING_ADDRESS_SELECTED",
                "PRINT_REQUEST", requestId, null,
                json.writeValueAsString(Map.of("addressId", addressId))));
        return new PrintRequestShippingAddressResponse(requestId, addressId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
