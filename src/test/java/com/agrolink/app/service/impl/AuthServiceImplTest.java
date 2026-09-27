package com.agrolink.app.service.impl;

import com.agrolink.app.dto.AuthResponseRecord;
import com.agrolink.app.dto.RegisterRequestRecord;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.EntityConflictException;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.PasswordResetCodeRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private com.agrolink.app.service.JwtUtils jwtUtils;
    @Mock
    private PasswordResetCodeRepository passwordResetCodeRepository;
    @Mock
    private EmailService emailService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager,
                jwtUtils, passwordResetCodeRepository, emailService);
    }

    @Test
    void register_shouldEncodePasswordAndReturnToken() {
        when(userRepository.existsByEmail("farmer@agrolink.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateToken(anyString(), anyMap())).thenReturn("jwt-token");

        RegisterRequestRecord request = new RegisterRequestRecord(
                "Green Valley Farms", "farmer@agrolink.com", "password123",
                Role.FARMER, "+1-555-0100", "Nairobi");

        AuthResponseRecord response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        assertEquals("Bearer", response.type());
        assertEquals("farmer@agrolink.com", response.email());
        assertEquals(Role.FARMER, response.role());
        assertEquals("Green Valley Farms", response.name());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("farmer@agrolink.com")).thenReturn(true);
        RegisterRequestRecord request = new RegisterRequestRecord(
                "Green Valley Farms", "farmer@agrolink.com", "password123",
                Role.FARMER, null, null);

        assertThrows(EntityConflictException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldRejectSelfRegistrationAsAdmin() {
        RegisterRequestRecord request = new RegisterRequestRecord(
                "Admin Wannabe", "admin@agrolink.com", "password123",
                Role.ADMIN, null, null);

        assertThrows(BusinessRuleException.class, () -> authService.register(request));
    }

    @Test
    void requestPasswordResetCode_shouldRejectUnknownEmail() {
        when(userRepository.findByEmail("ghost@nope.com")).thenReturn(java.util.Optional.empty());

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> authService.requestPasswordResetCode("ghost@nope.com"));

        assertEquals("Email not exist", error.getMessage());
        assertEquals(404, error.getStatus());
        verify(passwordResetCodeRepository, never()).save(any());
    }

    @Test
    void requestPasswordResetCode_shouldStoreHashedCodeAndEmailIt() {
        User user = User.builder().id("u1").email("farmer@agrolink.com")
                .name("Green Valley Farms").role(Role.FARMER).build();
        when(userRepository.findByEmail("farmer@agrolink.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));

        authService.requestPasswordResetCode("farmer@agrolink.com");

        var codeCaptor = org.mockito.ArgumentCaptor.forClass(com.agrolink.app.model.PasswordResetCode.class);
        verify(passwordResetCodeRepository).save(codeCaptor.capture());
        assertEquals("farmer@agrolink.com", codeCaptor.getValue().getEmail());
        assertEquals(5, codeCaptor.getValue().getAttemptsLeft());
        verify(emailService).sendPasswordResetCode(
                org.mockito.ArgumentMatchers.eq("farmer@agrolink.com"),
                org.mockito.ArgumentMatchers.eq("Green Valley Farms"), anyString());
    }

    @Test
    void confirmPasswordResetCode_shouldSetNewPasswordOnCorrectCode() {
        com.agrolink.app.model.PasswordResetCode reset =
                com.agrolink.app.model.PasswordResetCode.builder()
                        .email("farmer@agrolink.com").codeHash("hash:123456")
                        .expiresAt(java.time.Instant.now().plusSeconds(600)).attemptsLeft(5).build();
        User user = User.builder().id("u1").email("farmer@agrolink.com").role(Role.FARMER).build();
        when(passwordResetCodeRepository.findByEmail("farmer@agrolink.com"))
                .thenReturn(java.util.Optional.of(reset));
        when(passwordEncoder.matches("123456", "hash:123456")).thenReturn(true);
        when(userRepository.findByEmail("farmer@agrolink.com")).thenReturn(java.util.Optional.of(user));

        authService.confirmPasswordResetCode("farmer@agrolink.com", "123456", "NewPass123");

        verify(passwordEncoder).encode("NewPass123");
        verify(userRepository).save(user);
        verify(passwordResetCodeRepository).delete(reset);
    }

    @Test
    void confirmPasswordResetCode_shouldBurnAttemptOnWrongCode() {
        com.agrolink.app.model.PasswordResetCode reset =
                com.agrolink.app.model.PasswordResetCode.builder()
                        .email("farmer@agrolink.com").codeHash("hash:123456")
                        .expiresAt(java.time.Instant.now().plusSeconds(600)).attemptsLeft(5).build();
        when(passwordResetCodeRepository.findByEmail("farmer@agrolink.com"))
                .thenReturn(java.util.Optional.of(reset));
        when(passwordEncoder.matches("000000", "hash:123456")).thenReturn(false);

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> authService.confirmPasswordResetCode("farmer@agrolink.com", "000000", "NewPass123"));

        assertEquals("Wrong code. 4 attempts left.", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}