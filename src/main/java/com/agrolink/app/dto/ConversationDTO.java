package com.agrolink.app.dto;

import java.time.Instant;

public record ConversationDTO(
        String id,
        String orderId,
        String listingId,
        String otherPartyId,
        String otherPartyName,
        String otherPartyRole,
        String subject,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        boolean moderated
) {
}