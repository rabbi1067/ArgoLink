package com.agrolink.app.service;

import com.agrolink.app.dto.AuthResponseRecord;
import com.agrolink.app.dto.CreateUserRecord;
import com.agrolink.app.dto.LoginRequestRecord;
import com.agrolink.app.dto.RegisterRequestRecord;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;

import java.util.List;

public interface AuthService {

    AuthResponseRecord register(RegisterRequestRecord request);

    AuthResponseRecord login(LoginRequestRecord request);

    void requestPasswordResetCode(String email);

    void confirmPasswordResetCode(String email, String code, String newPassword);

    AuthResponseRecord createUser(CreateUserRecord request, Role requesterRole);

    List<User> getAllUsers(Role requesterRole);

    List<User> getUsersByRole(Role role, Role requesterRole);

    User findByEmail(String email);

    User updateUserRole(String userId, Role role, Role requesterRole);

    void deleteUser(String userId, Role requesterRole);

    void bulkDeleteUsers(List<String> userIds, Role requesterRole);
}