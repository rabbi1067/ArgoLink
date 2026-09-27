package com.agrolink.app.service;

import com.agrolink.app.dto.ChangePasswordRecord;
import com.agrolink.app.dto.UpdateProfileRecord;
import com.agrolink.app.dto.UserProfileDTO;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;

public interface UserService {

    UserProfileDTO getProfile(String userId);

    UserProfileDTO updateProfile(String userId, UpdateProfileRecord request);

    UserProfileDTO adminUpdateProfile(String userId, UpdateProfileRecord request, Role requesterRole);

    UserProfileDTO updateAvatar(String userId, byte[] content, String contentType);

    void changePassword(String userId, ChangePasswordRecord request);

    User setActive(String userId, boolean active, Role requesterRole);

    User getUserByEmail(String email);

    String initiatePasswordReset(String email);

    void resetPassword(String token, String newPassword);
}