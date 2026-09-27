package com.agrolink.app.dto;

import java.time.Instant;
public record ActivityItemDTO(
        String id,
        String kind,
        String title,
        String subtitle,
        String amount,
        String status,
        String actor,
        Instant at,
        String route) {
}
