package com.agrolink.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AbuseProtectionFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;

    private final com.agrolink.app.service.JwtUtils jwtUtils;
    private final com.agrolink.app.repository.UserRepository userRepository;

    @Value("${agrolink.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${agrolink.security.rate-limit.auth-per-minute:10}")
    private int authPerMinute;

    @Value("${agrolink.security.rate-limit.public-per-minute:60}")
    private int publicPerMinute;

    @Value("${agrolink.security.rate-limit.api-per-minute:120}")
    private int apiPerMinute;

    @Value("${agrolink.security.rate-limit.global-per-minute:600}")
    private int globalPerMinute;

    @Value("${agrolink.security.max-json-bytes:262144}")
    private long maxJsonBytes;

    @Value("${agrolink.security.auto-suspend.enabled:true}")
    private boolean autoSuspendEnabled;

    @Value("${agrolink.security.auto-suspend.strikes:5}")
    private int suspendStrikes;

    @Value("${agrolink.security.auto-suspend.strike-window-minutes:30}")
    private int suspendWindowMinutes;

    @Value("${agrolink.security.auto-suspend.writes-per-hour:60}")
    private int writesPerHour;

    @Value("${agrolink.security.auto-suspend.registers-per-hour-per-ip:20}")
    private int registersPerHour;

    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();
    /** email -> [windowStartMillis, strikes]. */
    private final Map<String, long[]> strikes = new ConcurrentHashMap<>();
    /** email -> [hourStartMillis, writes]. */
    private final Map<String, long[]> writeCounters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        if (isOversizedJson(request)) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }

        String path = request.getRequestURI();
        int tierLimit = tierLimit(path);
        String clientIp = clientIp(request);

        if (isRegisterAttempt(request) && !takeHour("reg:" + clientIp, registersPerHour)) {
            log.warn("Registration flood from {}", clientIp);
            response.setHeader("Retry-After", "3600");
            reject(response, 429, "Too many accounts created from this address. Try again later.");
            return;
        }

        if (!take("g", globalPerMinute) || !take("ip:" + clientIp + tierName(path), tierLimit)) {
            log.warn("Rate limit hit for {} on {}", clientIp, path);
            strike(authenticatedEmail(request));
            response.setHeader("Retry-After", "60");
            reject(response, 429, "Too many requests. Slow down and try again in a minute.");
            return;
        }

        countWrite(request);
        chain.doFilter(request, response);
    }

    private boolean isRegisterAttempt(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/register".equals(request.getRequestURI());
    }

    /**
     * Counts spammy creations per account: dozens of listings/orders/offers
     * per hour is not normal trading. Hit the ceiling and the account is
     * suspended on the spot.
     */
    private void countWrite(HttpServletRequest request) {
        if (!autoSuspendEnabled) {
            return;
        }
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            return;
        }
        String email = authenticatedEmail(request);
        if (email == null) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        long[] counter = writeCounters.computeIfAbsent(email, k -> new long[]{now, 0});
        synchronized (counter) {
            if (now - counter[0] >= HOUR_MS) {
                counter[0] = now;
                counter[1] = 0;
            }
            counter[1]++;
            if (counter[1] > writesPerHour) {
                suspend(email, "write flood (" + counter[1] + " writes in an hour)");
            }
        }
    }

    private void strike(String email) {
        if (!autoSuspendEnabled || email == null) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        long windowMs = Math.max(1, suspendWindowMinutes) * 60_000L;
        long[] record = strikes.computeIfAbsent(email, k -> new long[]{now, 0});
        synchronized (record) {
            if (now - record[0] >= windowMs) {
                record[0] = now;
                record[1] = 0;
            }
            record[1]++;
            if (record[1] >= Math.max(1, suspendStrikes)) {
                suspend(email, record[1] + " rate-limit strikes");
            }
        }
    }

    /**
     * Deactivates the account so the next login fails and existing tokens
     * stop working (JwtUtils also checks the active flag). Staff accounts
     * are never touched; only an admin re-activating the account (User
     * Management) brings it back.
     */
    private void suspend(String email, String reason) {
        try {
            com.agrolink.app.model.User user = userRepository.findByEmail(email).orElse(null);
            strikes.remove(email);
            writeCounters.remove(email);
            if (user == null || !user.isActive()) {
                return;
            }
            if (user.getRole() == com.agrolink.app.model.Role.ADMIN
                    || user.getRole() == com.agrolink.app.model.Role.SUPER_ADMIN) {
                log.warn("Auto-suspend skipped for staff account {} ({})", email, reason);
                return;
            }
            user.setActive(false);
            userRepository.save(user);
            log.warn("Auto-suspended account {} ({})", email, reason);
        } catch (Exception ex) {
            log.warn("Auto-suspend failed for {}", email, ex);
        }
    }

    /**
     * Who called, without touching the database: the Bearer subject of a
     * well-formed token. Only used for abuse accounting, never for access
     * decisions. Null for anonymous, forged or unreadable tokens.
     */
    private String authenticatedEmail(HttpServletRequest request) {
        try {
            if (jwtUtils == null || userRepository == null) {
                return null;
            }
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                return null;
            }
            String subject = jwtUtils.extractUsername(header.substring(7));
            return subject == null || subject.isBlank() ? null : subject;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isOversizedJson(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method))) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("json")) {
            return false;
        }
        return request.getContentLengthLong() > maxJsonBytes;
    }

    private int tierLimit(String path) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/password-reset")
                || path.startsWith("/api/v1/users/password-reset")) {
            return authPerMinute;
        }
        if (path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/produce/listings")
                || path.startsWith("/api/v1/produce/categories")
                || path.startsWith("/api/v1/weather/")
                || path.startsWith("/api/v1/locations/")) {
            return publicPerMinute;
        }
        return apiPerMinute;
    }

    private String tierName(String path) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/password-reset")
                || path.startsWith("/api/v1/users/password-reset")) {
            return ":auth";
        }
        if (path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/produce/listings")
                || path.startsWith("/api/v1/produce/categories")
                || path.startsWith("/api/v1/weather/")
                || path.startsWith("/api/v1/locations/")) {
            return ":public";
        }
        return ":api";
    }

    private boolean take(String key, int limit) {
        return takeWindow(key, limit, WINDOW_MS);
    }

    private boolean takeHour(String key, int limit) {
        return takeWindow(key, limit, HOUR_MS);
    }

    private boolean takeWindow(String key, int limit, long windowMs) {
        if (limit <= 0) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        long[] bucket = buckets.computeIfAbsent(key, k -> new long[]{now, 0});
        synchronized (bucket) {
            if (now - bucket[0] >= windowMs) {
                bucket[0] = now;
                bucket[1] = 0;
            }
            // Opportunistic cleanup so dead IPs do not grow the map forever.
            if (buckets.size() > 20_000 && (now & 0xFF) == 0) {
                long cutoff = now - WINDOW_MS;
                buckets.entrySet().removeIf(entry -> entry.getValue()[0] < cutoff);
            }
            if (bucket[1] >= limit) {
                return false;
            }
            bucket[1]++;
            return true;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String body = "{\"success\":false,\"message\":\"" + message
                + "\",\"data\":null,\"timestamp\":\"" + Instant.now() + "\"}";
        response.getWriter().write(body);
    }
}
