package com.agrolink.app.dto;

public record ChatMessageWsPayload(
        String conversationId,
        String senderId,
        String body
) {
}