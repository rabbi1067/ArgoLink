package com.agrolink.app.dto;

public record CreateConversationRecord(
        String orderId,
        String listingId,
        String subject
) {
    public boolean hasTarget() {
        return (orderId != null && !orderId.isBlank()) || (listingId != null && !listingId.isBlank());
    }
}