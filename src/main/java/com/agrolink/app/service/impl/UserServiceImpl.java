package com.agrolink.app.service.impl;

import com.agrolink.app.dto.ChangePasswordRecord;
import com.agrolink.app.dto.UpdateProfileRecord;
import com.agrolink.app.dto.UserProfileDTO;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.PasswordResetToken;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.PasswordResetTokenRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.CloudinaryService;
import com.agrolink.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final long RESET_TOKEN_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService storageService;

    @Override
    public UserProfileDTO getProfile(String userId) {
        return profileDto(findUserById(userId));
    }

    @Override
    public UserProfileDTO updateProfile(String userId, UpdateProfileRecord request) {
        User user = findUserById(userId);
        user.setName(request.name().trim());
        user.setPhone(request.phone());
        user.setLocation(request.location());
        return profileDto(userRepository.save(user));
    }

    @Override
    public UserProfileDTO adminUpdateProfile(String userId, UpdateProfileRecord request, Role requesterRole) {
        User user = requireManageable(userId, requesterRole);
        user.setName(request.name().trim());
        user.setPhone(request.phone());
        user.setLocation(request.location());
        // Email is intentionally never touched here - it is fixed once the account is created.
        return profileDto(userRepository.save(user));
    }

    @Override
    public UserProfileDTO updateAvatar(String userId, byte[] content, String contentType) {
        User user = findUserById(userId);
        CloudinaryService.UploadResult result = storageService.uploadAvatar(content, contentType);
        user.setProfileImagePublicId(result.publicId());
        user.setProfileImageFormat(result.format());
        user.setProfileImageUrl(result.secureUrl());
        return profileDto(userRepository.save(user));
    }

    @Override
    public void changePassword(String userId, ChangePasswordRecord request) {
        User user = findUserById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect", 400);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("New password must be different from the current password", 400);
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    public User setActive(String userId, boolean active, Role requesterRole) {
        User user = requireManageable(userId, requesterRole);
        user.setActive(active);
        return userRepository.save(user);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    public String initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        passwordResetTokenRepository.deleteByUserId(user.getId());
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(RESET_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .build());
        return token;
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired reset token", 400));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Reset token has expired", 400);
        }

        User user = findUserById(resetToken.getUserId());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenRepository.delete(resetToken);
    }


    private User requireManageable(String userId, Role requesterRole) {
        User user = findUserById(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessRuleException("Cannot modify a SUPER_ADMIN account", 403);
        }
        if (requesterRole != Role.SUPER_ADMIN && user.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Only a Super Admin can modify an Admin account", 403);
        }
        return user;
    }

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserProfileDTO profileDto(User user) {
        String signedImageUrl = user.getProfileImagePublicId() == null
                ? null
                : storageService.signedUrl(user.getProfileImagePublicId(), user.getProfileImageFormat());
        return UserProfileDTO.of(user, signedImageUrl);
    }
}