package com.agrolink.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 1000, message = "Message must be at most 1000 characters")
        String message,

        @Size(max = 12, message = "Conversation history is too long")
        @Valid
        List<ChatTurn> history
) {
    public record ChatTurn(String role, String text) {
    }
}