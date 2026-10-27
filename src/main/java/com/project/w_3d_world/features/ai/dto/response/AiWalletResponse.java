package com.project.w_3d_world.features.ai.dto.response;

public record AiWalletResponse(
        int availableCredits,
        long totalTasksRun,
        long successfulTasks
) {}
