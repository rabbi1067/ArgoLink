package com.agrolink.app.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkDeleteUsersRequest(
        @NotEmpty(message = "At least one user id is required")
        List<String> ids
) {
}