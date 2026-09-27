package com.agrolink.app.demo;

import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAuthFilterTest {

    private static final String SECRET = "demo-test-secret-that-is-long-enough-for-hs512-0123456789";

    private DemoAuthFilter filter;
    private Optional<User> repoAnswer;

    @BeforeEach
    void setUp() {
        repoAnswer = Optional.empty();
        UserRepository users = (UserRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) {
                        return repoAnswer;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        filter = new DemoAuthFilter(new JwtUtils(SECRET, 3_600_000L), users, null, null);
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    private MockHttpServletRequest json(String method, String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        request.setRemoteAddr("9.9.9.9");
        return request;
    }

    private String demoLogin() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(json("POST", "/api/v1/auth/login",
                "{\"email\":\"demo-farmer@agrolink.demo\",\"password\":\"Demo1234!\"}"),
                response, chain);
        assertEquals(200, response.getStatus());
        assertEquals(0, passed.get());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"role\":\"FARMER\""));
        int start = body.indexOf("\"token\":\"") + 9;
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    void demoLogin_returnsFarmerToken() throws Exception {
        String token = demoLogin();
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void demoLogin_wrongPassword_is401() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(json("POST", "/api/v1/auth/login",
                "{\"email\":\"demo-farmer@agrolink.demo\",\"password\":\"nope\"}"),
                response, chain);
        assertEquals(401, response.getStatus());
        assertEquals(0, passed.get());
    }

    @Test
    void demoProfile_returnsSyntheticUser() throws Exception {
        String token = demoLogin();
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletRequest request = json("GET", "/api/v1/users/me", null);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals(0, passed.get());
        assertTrue(response.getContentAsString().contains("Demo Farmer"));
    }

    @Test
    void demoWrite_isBlockedWith403() throws Exception {
        String token = demoLogin();
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletRequest request = json("POST", "/api/v1/produce", "{\"cropName\":\"X\"}");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertEquals(0, passed.get());
        assertTrue(response.getContentAsString().contains("read-only"));
    }

    @Test
    void demoRead_passesThroughWithAuth() throws Exception {
        String token = demoLogin();
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletRequest request = json("GET", "/api/v1/produce/listings", null);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(1, passed.get());
    }

    @Test
    void disabledDemoLogin_is403() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(json("POST", "/api/v1/auth/login",
                "{\"email\":\"demo-farmer@agrolink.demo\",\"password\":\"Demo1234!\"}"),
                response, chain);
        assertEquals(403, response.getStatus());
        assertEquals(0, passed.get());
    }

    @Test
    void realUserWithDemoAddress_winsOverDemo() throws Exception {
        User real = User.builder().id("real1").email("demo-farmer@agrolink.demo")
                .role(Role.FARMER).build();
        repoAnswer = Optional.of(real);
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(json("POST", "/api/v1/auth/login",
                "{\"email\":\"demo-farmer@agrolink.demo\",\"password\":\"Demo1234!\"}"),
                response, chain);
        assertEquals(1, passed.get());
    }

    @Test
    void realLogin_bodyStillReadableDownstream() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> seen =
                new java.util.concurrent.atomic.AtomicReference<>();
        FilterChain chain = (req, res) -> {
            byte[] bytes = req.getInputStream().readAllBytes();
            seen.set(new String(bytes, StandardCharsets.UTF_8));
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        String payload = "{\"email\":\"real@x.com\",\"password\":\"Secret123\"}";
        filter.doFilter(json("POST", "/api/v1/auth/login", payload), response, chain);
        assertTrue(seen.get() != null && seen.get().contains("real@x.com"));
    }

    @Test
    void demoAssistantChat_answersWithoutTouchingChain() throws Exception {
        com.agrolink.app.service.AssistantService assistant =
                (com.agrolink.app.service.AssistantService) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{com.agrolink.app.service.AssistantService.class},
                        (proxy, method, args) ->
                                new com.agrolink.app.dto.AssistantChatResponse("demo answer"));
        DemoReadService reads = new DemoReadService(
                null, null, null, null, null, null, null, null, assistant, null);
        DemoAuthFilter demoFilter = new DemoAuthFilter(
                new JwtUtils(SECRET, 3_600_000L), userRepository(), reads,
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        ReflectionTestUtils.setField(demoFilter, "enabled", true);

        String token = demoLogin();
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();
        MockHttpServletRequest request =
                json("POST", "/api/v1/assistant/chat", "{\"message\":\"hi\"}");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        demoFilter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(0, passed.get());
        assertTrue(response.getContentAsString().contains("demo answer"));
    }

    private UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) {
                        return repoAnswer;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
