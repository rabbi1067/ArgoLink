package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.AuthResponseRecord;
import com.agrolink.app.dto.LoginRequestRecord;
import com.agrolink.app.dto.PasswordResetConfirmRecord;
import com.agrolink.app.dto.PasswordResetRequestRecord;
import com.agrolink.app.dto.RegisterRequestRecord;
import com.agrolink.app.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<AuthResponseRecord>> register(@Valid @RequestBody RegisterRequestRecord request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", authService.register(request)));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<AuthResponseRecord>> login(@Valid @RequestBody LoginRequestRecord request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(request)));
    }

    @PostMapping("/password-reset/request")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRecord request) {
        authService.requestPasswordResetCode(request.email());
        return ResponseEntity.ok(ApiResponse.ok("Code sent to your email", null));
    }

    @PostMapping("/password-reset/confirm")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRecord request) {
        authService.confirmPasswordResetCode(request.email(), request.code(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful. Please log in.", null));
    }
}