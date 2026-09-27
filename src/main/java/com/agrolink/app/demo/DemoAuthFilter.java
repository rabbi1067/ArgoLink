package com.agrolink.app.demo;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-contained demo mode: public demo logins with zero database footprint.
 *
 * <p>How it works, all inside this package:
 * <ul>
 *   <li>POST /api/v1/auth/login with a demo address is answered right here
 *       with a real signed JWT (same secret, same shape as a normal login),
 *       so the frontend needs no changes at all;</li>
 *   <li>later requests carrying a demo token get a demo authentication set
 *       before the JWT filter runs, which then leaves it alone;</li>
 *   <li>demo users are read-only: any POST/PUT/PATCH/DELETE (except the
 *       password-reset endpoints, which fail naturally with "Email not
 *       exist") and the chat websocket handshake get a 403 with a friendly
 *       message the UI shows as a popup;</li>
   *   <li>GET /api/v1/users/me is answered with a synthetic profile, because
 *       there is no database row to load;</li>
 *   <li>GET /api/v1/dashboard and /api/v1/dashboard/summary are served
 *       through the real dashboard builder with the demo id (it only
 *       aggregates by id, never loading the user), so demo visitors get a
 *       genuine empty-but-valid dashboard instead of an account lookup
 *       failure.</li>
 * </ul>
 *
 * <p>Everything else passes through untouched, so a demo visitor sees all the
 * real existing data and empty personal dashboards. If a real database user
 * ever owns a demo address, the real account always wins and this filter
 * steps aside. Set {@code agrolink.demo.enabled=false} on a public deploy to
 * switch every demo login off.
 *
 * <p>Wiring note: this filter must run <em>inside</em> the Spring Security
 * chain, right before {@code JwtAuthenticationFilter}. A servlet-level filter
 * cannot carry login state because the chain's context filter wipes anything
 * set outside it. So {@code SecurityConfig} has exactly one line for it
 * ({@code addFilterBefore(demoAuthFilter, JwtAuthenticationFilter.class)}),
 * and the {@code FilterRegistrationBean} in {@link DemoConfig} stops Boot
 * from also running it at servlet level. Deleting this package plus that one
 * line restores the main project completely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final DemoReadService readService;
    private final ObjectMapper objectMapper;

    @Value("${agrolink.demo.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isDemoLoginAttempt(request)) {
            handleDemoLogin(request, response, chain);
            return;
        }

        DemoAccounts.Entry demo = extractDemo(request);
        if (demo == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!enabled) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Demo mode is disabled");
            return;
        }
        if (isBlockedForDemo(request)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "Demo mode is read-only. Explore everything, but changes are disabled.");
            return;
        }
        if (isProfileRequest(request)) {
            writeProfile(response, demo);
            return;
        }
        if (isDashboardRequest(request)) {
            writeDashboard(request, response, demo);
            return;
        }
        if (handleDemoView(request, response, demo)) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        demo.email(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + demo.role().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        org.springframework.security.core.context.SecurityContextHolder
                .getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private boolean isDemoLoginAttempt(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/login".equals(request.getRequestURI());
    }

    private void handleDemoLogin(HttpServletRequest request, HttpServletResponse response,
                                 FilterChain chain) throws ServletException, IOException {
        // The body must be readable twice: once here to spot a demo address,
        // once downstream by the real login (a consumed stream would turn
        // every real login into a 400). So it is snapshotted up front and
        // replayed for the rest of the chain.
        byte[] cachedBody;
        try {
            cachedBody = request.getInputStream().readAllBytes();
        } catch (Exception ex) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }
        Map<?, ?> body;
        try {
            body = MAPPER.readValue(cachedBody, Map.class);
        } catch (Exception ex) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }
        HttpServletRequest replay = new CachedBodyRequest(request, cachedBody);
        Object rawEmail = body == null ? null : body.get("email");
        String email = rawEmail == null ? "" : String.valueOf(rawEmail).toLowerCase().trim();
        DemoAccounts.Entry demo = DemoAccounts.entryOf(email);
        if (demo == null) {
            chain.doFilter(replay, response);
            return;
        }
        // A real account with the same address always wins over the demo.
        if (userRepository.findByEmail(email).isPresent()) {
            chain.doFilter(replay, response);
            return;
        }
        if (!enabled) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Demo mode is disabled");
            return;
        }
        Object rawPassword = body.get("password");
        String password = rawPassword == null ? "" : String.valueOf(rawPassword);
        if (!DemoAccounts.PASSWORD.equals(password)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid email or password");
            return;
        }

        String token = jwtUtils.generateToken(demo.email(), Map.of("role", demo.role().name()));
        log.info("Demo login as {}", email);
        String data = "{\"token\":\"" + token
                + "\",\"type\":\"Bearer\",\"userId\":\"" + demo.userId()
                + "\",\"email\":\"" + demo.email()
                + "\",\"role\":\"" + demo.role().name()
                + "\",\"name\":\"" + demo.name() + "\"}";
        writeOk(response, HttpServletResponse.SC_OK, "Login successful", data);
    }

    /**
     * Returns the demo account for a valid self-minted token, or null for
     * anything else (no token, a real user token, a forged token). Real
     * database owners of a demo address are never shadowed.
     */
    private DemoAccounts.Entry extractDemo(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = header.substring(7);
            String username = jwtUtils.extractUsername(token);
            DemoAccounts.Entry demo = DemoAccounts.entryOf(username);
            if (demo == null || jwtUtils.isTokenExpired(token)) {
                return null;
            }
            if (userRepository.findByEmail(demo.email()).isPresent()) {
                return null;
            }
            return demo;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isBlockedForDemo(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/ws-chat/")) {
            return true;
        }
        String method = request.getMethod();
        boolean mutating = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        if (!mutating) {
            return false;
        }
        // Password recovery fails naturally for demo addresses ("Email not
        // exist"), so it is left to behave exactly like the real flow.
        return !path.startsWith("/api/v1/auth/password-reset")
                && !path.startsWith("/api/v1/users/password-reset");
    }

    private boolean isProfileRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && "/api/v1/users/me".equals(request.getRequestURI());
    }

    /**
     * Serves the dashboard through the real builder with the demo id. The
     * builder never loads the user row, only aggregates by id, so a demo
     * visitor gets a genuine empty-but-valid dashboard. This bypasses the
     * controller's account lookup, which has no demo row to find.
     */
    private boolean isDashboardRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/dashboard".equals(path) || "/api/v1/dashboard/summary".equals(path);
    }

    private void writeDashboard(HttpServletRequest request, HttpServletResponse response,
                                DemoAccounts.Entry demo) throws IOException {
        java.time.LocalDate from = parseDate(request.getParameter("from"));
        java.time.LocalDate to = parseDate(request.getParameter("to"));
        Object dto = readService.showcaseDashboard(demo, from, to);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.ok(dto));
    }

    private java.time.LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Demo visitors own nothing, so every "my ..." endpoint would come back
     * empty or fail on the missing account. Instead each one is served the
     * live platform-wide equivalent (same DTO the page already renders), so
     * the full project is browsable and an admin edit/delete shows up here
     * immediately. Anything not listed passes through to the real chain.
     */
    private boolean handleDemoView(HttpServletRequest request, HttpServletResponse response,
                                   DemoAccounts.Entry demo) throws IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        Object data;
        try {
            data = demoViewData(request, demo);
        } catch (com.agrolink.app.exception.ResourceNotFoundException ex) {
            // Thrown before the DispatcherServlet, where @ControllerAdvice
            // cannot translate it, so map it here to the same 404 JSON.
            reject(response, HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
            return true;
        } catch (com.agrolink.app.exception.BusinessRuleException ex) {
            int status = ex.getStatus() >= 400 && ex.getStatus() < 600
                    ? ex.getStatus() : HttpServletResponse.SC_BAD_REQUEST;
            reject(response, status, ex.getMessage());
            return true;
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(DemoAuthFilter.class)
                    .warn("Demo view failed for {}", path, ex);
            reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not load this for the demo account");
            return true;
        }
        if (data == null && !matchedDetail(path)) {
            return false;
        }
        writeData(response, data);
        return true;
    }

    private Object demoViewData(HttpServletRequest request, DemoAccounts.Entry demo) {
        String path = request.getRequestURI();
        Object data;
        if ("/api/v1/produce/my".equals(path)) {
            data = readService.allListings();
        } else if ("/api/v1/orders/my".equals(path)) {
            data = readService.allOrders(0, 50).content();
        } else if ("/api/v1/orders/my/page".equals(path)) {
            data = readService.allOrdersFiltered(request.getParameter("status"),
                    readService.parseInt(request.getParameter("page"), 0),
                    readService.parseInt(request.getParameter("size"), 5));
        } else if ("/api/v1/orders/my/stats".equals(path)) {
            data = readService.platformStats();
        } else if ("/api/v1/offers/my".equals(path)) {
            data = readService.allOffers(demoSide(request, demo));
        } else if ("/api/v1/offers/my/page".equals(path)) {
            data = readService.allOffersPage(demoSide(request, demo),
                    readService.parseInt(request.getParameter("page"), 0),
                    readService.parseInt(request.getParameter("size"), 6));
        } else if ("/api/v1/messages/conversations".equals(path)) {
            data = readService.allConversations(demo);
        } else if ("/api/v1/invoices/my".equals(path)) {
            data = readService.allInvoices();
        } else if ("/api/v1/users".equals(path)) {
            data = readService.allUsers(demo);
        } else if (path.startsWith("/api/v1/users/by-role/")) {
            data = readService.usersByRole(path.substring("/api/v1/users/by-role/".length()), demo);
        } else {
            data = demoDetail(request, demo);
        }
        return data;
    }

    private boolean matchedDetail(String path) {
        return isProduceDetail(path) || isOrderDetail(path)
                || isConversationMessages(path) || isInvoiceDetail(path)
                || isInvoiceForOrder(path);
    }

    private Object demoDetail(HttpServletRequest request, DemoAccounts.Entry demo) {
        String path = request.getRequestURI();
        String[] seg = path.split("/", -1);
        if (isProduceDetail(path)) {
            return readService.visibleListing(seg[4], demo);
        }
        if (isOrderDetail(path)) {
            return readService.orderDetail(seg[4], demo);
        }
        if (isConversationMessages(path)) {
            return readService.conversationMessages(seg[5]);
        }
        if (isInvoiceForOrder(path)) {
            return readService.invoiceForOrder(seg[5], demo);
        }
        if (isInvoiceDetail(path)) {
            return readService.invoiceDetail(seg[4], demo);
        }
        return null;
    }

    private boolean singleSegment(String path, String resource, java.util.Set<String> excluded) {
        String[] seg = path.split("/", -1);
        return seg.length == 5 && "api".equals(seg[1]) && "v1".equals(seg[2])
                && resource.equals(seg[3]) && !seg[4].isBlank() && !excluded.contains(seg[4]);
    }

    private boolean isProduceDetail(String path) {
        return singleSegment(path, "produce",
                java.util.Set.of("my", "manage", "listings", "categories", "search"));
    }

    private boolean isOrderDetail(String path) {
        return singleSegment(path, "orders", java.util.Set.of("my", "status", "payment", "admin"));
    }

    private boolean isInvoiceDetail(String path) {
        return singleSegment(path, "invoices", java.util.Set.of("my", "admin", "order"));
    }

    private boolean isInvoiceForOrder(String path) {
        String[] seg = path.split("/", -1);
        return seg.length == 6 && "api".equals(seg[1]) && "v1".equals(seg[2])
                && "invoices".equals(seg[3]) && "order".equals(seg[4]) && !seg[5].isBlank();
    }

    private boolean isConversationMessages(String path) {
        String[] seg = path.split("/", -1);
        return seg.length == 7 && "api".equals(seg[1]) && "v1".equals(seg[2])
                && "messages".equals(seg[3]) && "conversations".equals(seg[4])
                && !seg[5].isBlank() && "messages".equals(seg[6]);
    }

    private String demoSide(HttpServletRequest request, DemoAccounts.Entry demo) {
        String as = request.getParameter("as");
        if (as != null && !as.isBlank()) {
            return as;
        }
        return demo.role() == com.agrolink.app.model.Role.FARMER ? "FARMER" : "BUYER";
    }

    private void writeData(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.ok(data));
    }

    private void writeProfile(HttpServletResponse response, DemoAccounts.Entry demo) throws IOException {
        String data = "{\"id\":\"" + demo.userId()
                + "\",\"name\":\"" + demo.name()
                + "\",\"email\":\"" + demo.email()
                + "\",\"role\":\"" + demo.role().name()
                + "\",\"location\":\"Dhaka, Bangladesh\""
                + ",\"active\":true,\"createdAt\":\"" + Instant.now() + "\"}";
        writeOk(response, HttpServletResponse.SC_OK, "Success", data);
    }

    private void writeOk(HttpServletResponse response, int status, String message, String dataJson)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true,\"message\":\"" + message
                + "\",\"data\":" + dataJson
                + ",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message
                + "\",\"data\":null,\"timestamp\":\"" + Instant.now() + "\"}");
    }

    /**
     * Replays an already-read body for the rest of the chain, with a fresh
     * stream (and reader) per call.
     */
    private static class CachedBodyRequest extends jakarta.servlet.http.HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream source = new java.io.ByteArrayInputStream(cachedBody);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
