package com.project.w_3d_world.features.ai.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "System")
public class HealthController {

    private final DataSource dataSource;
    private final Environment environment;

    @GetMapping("/health")
    @Operation(summary = "Kiểm tra trạng thái hoạt động của hệ thống (Health Check)")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("timestamp", Instant.now().toString());

        boolean dbOk = false;
        try (Connection c = dataSource.getConnection()) {
            dbOk = c.isValid(2);
        } catch (Exception ignored) {}

        data.put("database", dbOk ? "CONNECTED" : "UNAVAILABLE");
        data.put("modules", Map.of(
                "auth", "UP",
                "modelLibrary", "UP",
                "aiStudio", "UP",
                "studio3D", "UP"
        ));

        String[] activeProfiles = environment.getActiveProfiles();
        data.put("profiles", activeProfiles.length > 0 ? activeProfiles : new String[]{"default"});

        return ApiResponse.success(data);
    }
}
