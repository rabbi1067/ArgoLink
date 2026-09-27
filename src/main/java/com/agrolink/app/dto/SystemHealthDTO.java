package com.agrolink.app.dto;

import java.time.Instant;
public record SystemHealthDTO(
        String databaseStatus,
        double databaseLatencyMs,
        double queryLatencyMs,
        long uptimeSeconds,
        long heapUsedMb,
        long heapMaxMb,
        long activeUsers,
        String error,
        Instant checkedAt) {

    public static SystemHealthDTO down(String error) {
        return new SystemHealthDTO("DOWN", 0d, 0d, 0L, 0L, 0L, 0L, error, Instant.now());
    }

    public boolean healthy() {
        return "UP".equals(databaseStatus);
    }
}
