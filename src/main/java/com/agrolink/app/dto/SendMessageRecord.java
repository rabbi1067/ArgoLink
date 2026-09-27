package com.agrolink.app.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRecord(
        @NotBlank(message = "Message cannot be empty")
        String content
) {
}