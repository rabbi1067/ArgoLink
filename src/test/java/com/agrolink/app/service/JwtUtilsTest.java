package com.agrolink.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String SECRET = "jwt-test-secret-that-is-long-enough-for-hs512-0123456789";

    private UserDetails user(String email, boolean enabled) {
        return new User(email, "encoded", enabled, true, true, true, List.of());
    }

    @Test
    void activeUser_tokenValid() {
        JwtUtils utils = new JwtUtils(SECRET, 3_600_000L);
        String token = utils.generateToken("a@x.com", Map.of("role", "FARMER"));
        assertTrue(utils.isTokenValid(token, user("a@x.com", true)));
    }

    @Test
    void deactivatedUser_tokenDiesImmediately() {
        JwtUtils utils = new JwtUtils(SECRET, 3_600_000L);
        String token = utils.generateToken("a@x.com", Map.of("role", "FARMER"));
        assertFalse(utils.isTokenValid(token, user("a@x.com", false)));
    }

    @Test
    void forgedToken_rejected() {
        JwtUtils utils = new JwtUtils(SECRET, 3_600_000L);
        JwtUtils other = new JwtUtils("a-different-secret-that-is-also-long-enough-0123456789", 3_600_000L);
        String forged = other.generateToken("a@x.com", Map.of("role", "FARMER"));
        assertFalse(utils.isTokenValid(forged, user("a@x.com", true)));
    }
}
