package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> summary(
            @AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        Role role = requireRole(user);
        try {
            return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary(user.getId(), role)));
        } catch (RuntimeException error) {
            log.error("Dashboard summary failed for user {} ({})", user.getId(), role, error);
            return ResponseEntity.ok(ApiResponse.ok(empty(role, scopeOf(role))));
        }
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> analytics(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "6") int months) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(dashboardService.analytics(months)));
        } catch (RuntimeException error) {
            log.error("Platform analytics failed for a {}-month window", months, error);
            return ResponseEntity.ok(ApiResponse.ok(empty(Role.ADMIN, "platform")));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> dashboard(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = currentUser(principal);
        Role role = requireRole(user);
        try {
            return ResponseEntity.ok(
                    ApiResponse.ok(dashboardService.dashboard(user.getId(), role, from, to)));
        } catch (RuntimeException error) {
            log.error("Dashboard build failed for user {} ({}), range {}..{}", user.getId(), role, from, to, error);
            return ResponseEntity.ok(ApiResponse.ok(empty(role, scopeOf(role))));
        }
    }


    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Role requireRole(User user) {
        if (user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
        return user.getRole();
    }

    private static String scopeOf(Role role) {
        return switch (role) {
            case FARMER -> "farmer";
            case ADMIN, SUPER_ADMIN -> "platform";
            default -> "buyer";
        };
    }

    private static DashboardSummaryDTO empty(Role role, String scope) {
        return DashboardSummaryDTO.of(role.name(), scope, List.of(), List.of(), List.of(), List.of());
    }
}