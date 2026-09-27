package com.agrolink.app.dto;

import java.time.Instant;

public record AdminConversationDTO(
        String id,
        String orderId,
        String listingId,
        String buyerId,
        String buyerName,
        String farmerId,
        String farmerName,
        String subject,
        String lastMessage,
        Instant lastMessageAt,
        long messageCount,
        boolean reported,
        String reportReason,
        boolean moderated,
        Instant createdAt
) {
}
