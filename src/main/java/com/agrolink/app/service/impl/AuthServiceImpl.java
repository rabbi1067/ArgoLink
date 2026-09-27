package com.agrolink.app.service.impl;

import com.agrolink.app.dto.AuthResponseRecord;
import com.agrolink.app.dto.CreateUserRecord;
import com.agrolink.app.dto.LoginRequestRecord;
import com.agrolink.app.dto.RegisterRequestRecord;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.EntityConflictException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.PasswordResetCode;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.PasswordResetCodeRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.AuthService;
import com.agrolink.app.service.EmailService;
import com.agrolink.app.service.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long RESET_CODE_TTL_MINUTES = 10;
    private static final int RESET_CODE_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final EmailService emailService;

    @Override
    public AuthResponseRecord register(RegisterRequestRecord request) {
        Role role = request.role() == null ? Role.BUYER : request.role();
        validateSelfRegistration(role);

        User user = buildAndSaveUser(
                request.name(), request.email(), request.password(),
                role, request.phone(), request.location()
        );
        return buildAuthResponse(issueToken(user), user);
    }

    @Override
    public AuthResponseRecord login(LoginRequestRecord request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException ex) {
            throw new BusinessRuleException(
                    "Your account has been deactivated. Please contact an administrator.", 403);
        } catch (Exception ex) {
            throw new BusinessRuleException("Invalid email or password", 401);
        }

        User user = findByEmail(request.email());
        return buildAuthResponse(issueToken(user), user);
    }


    @Override
    public void requestPasswordResetCode(String email) {
        String normalized = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessRuleException("Email not exist", 404));

        passwordResetCodeRepository.deleteByEmail(normalized);
        String code = String.format("%06d", SECURE_RANDOM.nextInt(900000) + 100000);
        passwordResetCodeRepository.save(PasswordResetCode.builder()
                .email(normalized)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(RESET_CODE_TTL_MINUTES, ChronoUnit.MINUTES))
                .attemptsLeft(RESET_CODE_ATTEMPTS)
                .build());
        emailService.sendPasswordResetCode(normalized, user.getName(), code);
    }


    @Override
    public void confirmPasswordResetCode(String email, String code, String newPassword) {
        String normalized = email.toLowerCase().trim();
        PasswordResetCode resetCode = passwordResetCodeRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessRuleException("No reset request found. Send a new code.", 400));

        if (resetCode.getExpiresAt().isBefore(Instant.now())) {
            passwordResetCodeRepository.delete(resetCode);
            throw new BusinessRuleException("Code expired. Send a new code.", 400);
        }
        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            int left = resetCode.getAttemptsLeft() - 1;
            if (left <= 0) {
                passwordResetCodeRepository.delete(resetCode);
                throw new BusinessRuleException("Too many wrong attempts. Send a new code.", 400);
            }
            resetCode.setAttemptsLeft(left);
            passwordResetCodeRepository.save(resetCode);
            throw new BusinessRuleException("Wrong code. " + left + " attempts left.", 400);
        }

        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessRuleException("Email not exist", 404));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetCodeRepository.delete(resetCode);
    }

    @Override
    public AuthResponseRecord createUser(CreateUserRecord request, Role requesterRole) {
        if (request.role() == Role.SUPER_ADMIN) {
            throw new BusinessRuleException("Cannot create a SUPER_ADMIN through the management API", 403);
        }
        if (requesterRole != Role.SUPER_ADMIN && request.role() == Role.ADMIN) {
            throw new BusinessRuleException("Only a Super Admin can create an Admin account", 403);
        }
        User user = buildAndSaveUser(
                request.name(), request.email(), request.password(),
                request.role(), request.phone(), request.location()
        );
        return buildAuthResponse(null, user);
    }

    @Override
    public List<User> getAllUsers(Role requesterRole) {
        if (requesterRole == Role.SUPER_ADMIN) {
            return userRepository.findAll();
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.SUPER_ADMIN)
                .toList();
    }

    @Override
    public List<User> getUsersByRole(Role role, Role requesterRole) {
        if (requesterRole != Role.SUPER_ADMIN && (role == Role.ADMIN || role == Role.SUPER_ADMIN)) {
            throw new BusinessRuleException("Only a Super Admin can view admin accounts", 403);
        }
        return userRepository.findByRole(role);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    public User updateUserRole(String userId, Role role, Role requesterRole) {
        if (role == Role.SUPER_ADMIN) {
            throw new BusinessRuleException("Cannot assign SUPER_ADMIN through the management API", 403);
        }
        User user = requireManageable(userId, requesterRole);
        user.setRole(role);
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(String userId, Role requesterRole) {
        User user = requireManageable(userId, requesterRole);
        userRepository.delete(user);
    }

    @Override
    public void bulkDeleteUsers(List<String> userIds, Role requesterRole) {
        for (String id : userIds) {
            deleteUser(id, requesterRole);
        }
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

    private void validateSelfRegistration(Role role) {
        if (role == Role.ADMIN || role == Role.SUPER_ADMIN) {
            throw new BusinessRuleException("Self-registration as " + role + " is not allowed", 403);
        }
    }

    private User buildAndSaveUser(String name, String email, String password,
                                  Role role, String phone, String location) {
        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EntityConflictException("An account with email " + normalizedEmail + " already exists");
        }
        User user = User.builder()
                .name(name.trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(role)
                .phone(phone)
                .location(location)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    private String issueToken(User user) {
        return jwtUtils.generateToken(user.getEmail(), Map.of("role", user.getRole().name()));
    }

    private AuthResponseRecord buildAuthResponse(String token, User user) {
        return new AuthResponseRecord(
                token,
                TOKEN_TYPE,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getName()
        );
    }

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}