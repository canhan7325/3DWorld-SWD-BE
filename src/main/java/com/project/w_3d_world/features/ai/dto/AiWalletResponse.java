package com.project.w_3d_world.features.ai.dto;

public record AiWalletResponse(
        int availableCredits,
        long totalTasksRun,
        long successfulTasks
) {}
