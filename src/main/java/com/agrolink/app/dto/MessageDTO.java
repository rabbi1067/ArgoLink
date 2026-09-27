package com.agrolink.app.dto;

import java.time.Instant;

public record MessageDTO(
        String id,
        String conversationId,
        String senderId,
        String senderRole,
        String body,
        boolean readByBuyer,
        boolean readByFarmer,
        Instant createdAt
) {
}