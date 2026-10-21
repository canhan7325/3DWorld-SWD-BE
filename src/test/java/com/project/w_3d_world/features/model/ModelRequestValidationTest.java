package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModelRequestValidationTest {
    @Test
    void principalAndPaginationKeepTheirHttpContract() {
        assertEquals(7, ModelRequestValidation.requireUserId(
                new AuthenticatedUser(7, Set.of("CUSTOMER"), UUID.randomUUID())));
        assertEquals(401, assertThrows(ApiException.class,
                () -> ModelRequestValidation.requireUserId(null)).getStatus().value());
        assertEquals(0, ModelRequestValidation.parsePage("0"));
        assertEquals(50, ModelRequestValidation.parsePageSize("50"));
        for (String value : new String[] {"-1", "not-a-page"}) {
            assertEquals(400, assertThrows(ApiException.class,
                    () -> ModelRequestValidation.parsePage(value)).getStatus().value());
        }
        for (String value : new String[] {"0", "51", "not-a-size"}) {
            assertEquals(400, assertThrows(ApiException.class,
                    () -> ModelRequestValidation.parsePageSize(value)).getStatus().value());
        }
    }

    @Test
    void modelAndDownloadIdentifiersKeepExistingErrorMessages() {
        assertEquals(12, ModelRequestValidation.parsePositive("12", "modelId"));
        assertEquals(12, ModelRequestValidation.parseDownloadId("12", "Model ID"));
        assertEquals("modelId must be a valid integer.", assertThrows(ApiException.class,
                () -> ModelRequestValidation.parsePositive("bad", "modelId")).getMessage());
        assertEquals("Model ID must be a positive integer.", assertThrows(ApiException.class,
                () -> ModelRequestValidation.parseDownloadId("bad", "Model ID")).getMessage());
        assertEquals("Model ID must be a positive integer.", assertThrows(ApiException.class,
                () -> ModelRequestValidation.parseDownloadId("0", "Model ID")).getMessage());
    }
}
