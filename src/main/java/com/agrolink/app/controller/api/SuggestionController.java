package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.SuggestionDTO;
import com.agrolink.app.service.SuggestionService;
import com.agrolink.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Buyer-only "AI Suggest" picks, computed from the buyer's own orders and
 * offers plus live platform demand. An empty list means the buyer has no
 * history yet, and the frontend hides the section entirely.
 */
@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final UserService userService;

    @GetMapping("/buyer")
    @PreAuthorize("hasAnyRole('BUYER')")
    public ResponseEntity<ApiResponse<List<SuggestionDTO>>> buyerSuggestions(Authentication authentication) {
        String buyerId = userService.getUserByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(ApiResponse.ok(suggestionService.suggestForBuyer(buyerId)));
    }
}
