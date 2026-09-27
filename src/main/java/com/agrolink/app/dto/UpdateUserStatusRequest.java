package com.agrolink.app.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "active flag is required")
        Boolean active
) {
}