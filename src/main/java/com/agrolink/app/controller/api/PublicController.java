package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Role;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final UserRepository userRepository;
    private final ProduceListingRepository listingRepository;

    @GetMapping("/stats")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        Map<String, Long> data = Map.of(
                "farmers", userRepository.countByRole(Role.FARMER),
                "buyers", userRepository.countByRole(Role.BUYER),
                "listings", listingRepository.countByStatus(ListingStatus.ACTIVE));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
