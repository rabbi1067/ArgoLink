package com.agrolink.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbuseProtectionFilterTest {

    private static final String SECRET = "abuse-test-secret-that-is-long-enough-for-hs512-0123456789";

    private AbuseProtectionFilter filter;
    private com.agrolink.app.model.User repoUser;

    @BeforeEach
    void setUp() {
        repoUser = null;
        com.agrolink.app.repository.UserRepository users =
                (com.agrolink.app.repository.UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{com.agrolink.app.repository.UserRepository.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("findByEmail")) {
                                return java.util.Optional.ofNullable(repoUser);
                            }
                            if (method.getName().equals("save")) {
                                repoUser = (com.agrolink.app.model.User) args[0];
                                return repoUser;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        filter = new AbuseProtectionFilter(
                new com.agrolink.app.service.JwtUtils(SECRET, 3_600_000L), users);
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authPerMinute", 10);
        ReflectionTestUtils.setField(filter, "publicPerMinute", 60);
        ReflectionTestUtils.setField(filter, "apiPerMinute", 10_000);
        ReflectionTestUtils.setField(filter, "globalPerMinute", 100_000);
        ReflectionTestUtils.setField(filter, "maxJsonBytes", 262144L);
        ReflectionTestUtils.setField(filter, "autoSuspendEnabled", true);
        ReflectionTestUtils.setField(filter, "suspendStrikes", 5);
        ReflectionTestUtils.setField(filter, "suspendWindowMinutes", 30);
        ReflectionTestUtils.setField(filter, "writesPerHour", 60);
        ReflectionTestUtils.setField(filter, "registersPerHour", 20);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType("application/json");
        request.setContent(new byte[]{'{', '}'});
        request.setRemoteAddr("9.9.9.9");
        return request;
    }

    @Test
    void authTier_allowsTenThenRejectsWith429() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        int rejected = 0;
        for (int i = 0; i < 12; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), response, chain);
            if (response.getStatus() == 429) {
                rejected++;
            }
        }

        assertEquals(10, passed.get());
        assertEquals(2, rejected);
    }

    @Test
    void oversizedJson_isRejectedWith413() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        MockHttpServletRequest request = loginRequest();
        request.setContent(new byte[300_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertEquals(0, passed.get());
    }

    @Test
    void nonApiPaths_skipTheFilter() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/main.css");
        request.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertEquals(1, passed.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectionBody_matchesApiErrorShape() throws Exception {
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletRequest request = loginRequest();
        request.setContent(new byte[300_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        String body = response.getContentAsString();
        assertTrue(body.contains("\"success\":false"));
        assertTrue(body.contains("too large"));
    }

    private String tokenFor(String email, String role) {
        return new com.agrolink.app.service.JwtUtils(SECRET, 3_600_000L)
                .generateToken(email, java.util.Map.of("role", role));
    }

    private MockHttpServletRequest authed(String method, String uri, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        request.setContent(new byte[]{'{', '}'});
        request.setRemoteAddr("9.9.9.9");
        request.addHeader("Authorization", "Bearer " + tokenFor(email, "FARMER"));
        return request;
    }

    private void activeFarmer(String email) {
        repoUser = com.agrolink.app.model.User.builder()
                .id("u1").email(email).role(com.agrolink.app.model.Role.FARMER)
                .isActive(true).build();
    }

    @Test
    void authedRateLimitStrikes_suspendAfterFive() throws Exception {
        activeFarmer("spam@x.com");
        FilterChain chain = (req, res) -> {
        };

        // api tier is wide open here; force breaches on the tight auth tier.
        for (int i = 0; i < 15; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockHttpServletRequest request = authed("POST", "/api/v1/auth/login", "spam@x.com");
            filter.doFilter(request, response, chain);
        }

        assertTrue(repoUser != null && !repoUser.isActive());
    }

    @Test
    void writeFlood_suspendsAccount() throws Exception {
        activeFarmer("flood@x.com");
        FilterChain chain = (req, res) -> {
        };

        for (int i = 0; i < 61; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authed("POST", "/api/v1/produce", "flood@x.com"), response, chain);
        }

        assertTrue(!repoUser.isActive());
    }

    @Test
    void staffAccounts_neverSuspended() throws Exception {
        repoUser = com.agrolink.app.model.User.builder()
                .id("a1").email("boss@x.com").role(com.agrolink.app.model.Role.ADMIN)
                .isActive(true).build();
        FilterChain chain = (req, res) -> {
        };

        String bossToken = tokenFor("boss@x.com", "ADMIN");
        for (int i = 0; i < 61; i++) {
            // Fresh instance per call: OncePerRequestFilter skips re-runs on
            // the same request object, which would hide the flood.
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/produce");
            request.setContentType("application/json");
            request.setContent(new byte[]{'{', '}'});
            request.setRemoteAddr("9.9.9.9");
            request.addHeader("Authorization", "Bearer " + bossToken);
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        assertTrue(repoUser.isActive());
    }

    @Test
    void registerFlood_perIpHourlyCap() throws Exception {
        FilterChain chain = (req, res) -> {
        };

        String lastBody = "";
        for (int i = 0; i < 21; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            request.setContentType("application/json");
            request.setContent(new byte[]{'{', '}'});
            request.setRemoteAddr("9.9.9.9");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            lastBody = response.getContentAsString();
        }

        assertTrue(lastBody.contains("accounts created"));
    }
}
