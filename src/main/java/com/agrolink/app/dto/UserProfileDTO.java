package com.agrolink.app.dto;

import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;

import java.time.Instant;

public record UserProfileDTO(
        String id,
        String name,
        String email,
        Role role,
        String phone,
        String location,
        String profileImageUrl,
        String profileImagePublicId,
        boolean isActive,
        Instant createdAt
) {

    public static UserProfileDTO of(User user, String signedImageUrl) {
        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getPhone(),
                user.getLocation(),
                signedImageUrl,
                user.getProfileImagePublicId(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}