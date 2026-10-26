package com.project.w_3d_world.features.printing.dto.response;

public record PrintProductionSummaryResponse(
        int totalItems,
        int queuedItems,
        int printingItems,
        int completedItems,
        String productionDisplayStatus) {
    public static PrintProductionSummaryResponse fromStatuses(java.util.List<String> statuses) {
        int queued = 0;
        int printing = 0;
        int completed = 0;
        for (String status : statuses) {
            if ("QUEUED".equals(status)) queued++;
            else if ("PRINTING".equals(status)) printing++;
            else if ("COMPLETED".equals(status)) completed++;
        }
        int total = statuses.size();
        String display = total > 0 && completed == total ? "COMPLETED"
                : printing > 0 || completed > 0 ? "PRINTING"
                : queued > 0 ? "QUEUED" : "PENDING";
        return new PrintProductionSummaryResponse(total, queued, printing, completed, display);
    }
}
