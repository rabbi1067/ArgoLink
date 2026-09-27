package com.agrolink.app.dto;

import com.agrolink.app.model.Role;

public record AuthResponseRecord(
        String token,
        String type,
        String userId,
        String email,
        Role role,
        String name
) {
}