package com.agrolink.app.dto;

import com.agrolink.app.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}