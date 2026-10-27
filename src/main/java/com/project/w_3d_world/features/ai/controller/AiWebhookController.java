package com.project.w_3d_world.features.ai.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "AI Webhooks")
public class AiWebhookController {

    private final AiGenerationService aiGenerationService;

    @PostMapping("/meshy")
    @Operation(summary = "Nhận webhook thông báo kết quả tác vụ từ Meshy AI")
    public ApiResponse<Map<String, Object>> handleMeshyWebhook(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret
    ) {
        return ApiResponse.success(aiGenerationService.handleMeshyWebhook(payload, secret));
    }
}
