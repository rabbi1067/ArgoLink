package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.AuthResponseRecord;
import com.agrolink.app.dto.BulkDeleteUsersRequest;
import com.agrolink.app.dto.ChangePasswordRecord;
import com.agrolink.app.dto.CreateUserRecord;
import com.agrolink.app.dto.PasswordResetRequestRecord;
import com.agrolink.app.dto.ResetPasswordRecord;
import com.agrolink.app.dto.UpdateProfileRecord;
import com.agrolink.app.dto.UpdateUserRoleRequest;
import com.agrolink.app.dto.UpdateUserStatusRequest;
import com.agrolink.app.dto.UserProfileDTO;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.service.AuthService;
import com.agrolink.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileDTO>> getProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(currentUserId(authentication))));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileDTO>> updateProfile(@Valid @RequestBody UpdateProfileRecord request,
                                                                     Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                userService.updateProfile(currentUserId(authentication), request)));
    }

    @PostMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRecord request,
                                                            Authentication authentication) {
        userService.changePassword(currentUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed", null));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePasswordEndpoint(@Valid @RequestBody ChangePasswordRecord request,
                                                                    Authentication authentication) {
        userService.changePassword(currentUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed", null));
    }

    @PostMapping("/upload-avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileDTO>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                                    Authentication authentication) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Avatar file is required", 400);
        }
        try {
            UserProfileDTO updated = userService.updateAvatar(
                    currentUserId(authentication), file.getBytes(), file.getContentType());
            return ResponseEntity.ok(ApiResponse.ok("Avatar updated", updated));
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not read avatar file", 400);
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponseRecord>> createUser(@Valid @RequestBody CreateUserRecord request,
                                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", authService.createUser(request, currentRole(authentication))));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getAllUsers(currentRole(authentication))));
    }

    @GetMapping("/by-role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByRole(@PathVariable Role role,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getUsersByRole(role, currentRole(authentication))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileDTO>> adminUpdateUser(@PathVariable String id,
                                                                       @Valid @RequestBody UpdateProfileRecord request,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("User updated",
                userService.adminUpdateProfile(id, request, currentRole(authentication))));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateStatus(@PathVariable String id,
                                                          @Valid @RequestBody UpdateUserStatusRequest request,
                                                          Authentication authentication) {
        User updated = userService.setActive(id, request.active(), currentRole(authentication));
        return ResponseEntity.ok(ApiResponse.ok("User status updated", updated));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateRole(@PathVariable String id,
                                                        @Valid @RequestBody UpdateUserRoleRequest request,
                                                        Authentication authentication) {
        User updated = authService.updateUserRole(id, request.role(), currentRole(authentication));
        return ResponseEntity.ok(ApiResponse.ok("User role updated", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id, Authentication authentication) {
        authService.deleteUser(id, currentRole(authentication));
        return ResponseEntity.ok(ApiResponse.ok("User deleted", null));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkDeleteUsers(@Valid @RequestBody BulkDeleteUsersRequest request,
                                                             Authentication authentication) {
        authService.bulkDeleteUsers(request.ids(), currentRole(authentication));
        return ResponseEntity.ok(ApiResponse.ok(request.ids().size() + " user(s) deleted", null));
    }

    @PostMapping("/password-reset/request")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestRecord request) {
        String token = userService.initiatePasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.ok("Password reset token generated", Map.of("resetToken", token)));
    }

    @PostMapping("/password-reset/confirm")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRecord request) {
        userService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully", null));
    }

    private String currentUserId(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).getId();
    }

    private Role currentRole(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).getRole();
    }
}