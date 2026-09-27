package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.CounterOfferRecord;
import com.agrolink.app.dto.CreateOfferRecord;
import com.agrolink.app.dto.OfferDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.model.User;
import com.agrolink.app.service.OfferService;
import com.agrolink.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('BUYER', 'FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OfferDTO>> submit(@Valid @RequestBody CreateOfferRecord request,
                                                        Authentication authentication) {
        OfferDTO offer = offerService.submit(currentUser(authentication).getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Offer submitted", offer));
    }

    @PostMapping("/{offerId}/counter")
    @PreAuthorize("hasAnyRole('BUYER', 'FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OfferDTO>> counter(@PathVariable String offerId,
                                                         @Valid @RequestBody CounterOfferRecord request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Counter offer submitted",
                offerService.counter(currentUser(authentication).getId(), offerId, request)));
    }

    @PostMapping("/{offerId}/withdraw")
    @PreAuthorize("hasAnyRole('BUYER', 'FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OfferDTO>> withdraw(@PathVariable String offerId,
                                                          Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Offer withdrawn",
                offerService.withdraw(currentUser(authentication).getId(), offerId)));
    }

    @PostMapping("/{offerId}/reject")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OfferDTO>> reject(@PathVariable String offerId,
                                                        Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Offer rejected",
                offerService.rejectByFarmer(currentUser(authentication).getId(), offerId)));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OfferDTO>>> myOffers(
            @RequestParam(defaultValue = "BUYER") String as,
            Authentication authentication) {
        User user = currentUser(authentication);
        List<OfferDTO> offers = as.equalsIgnoreCase("FARMER")
                ? offerService.getOffersByFarmer(user.getId())
                : offerService.getOffersByBuyer(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(offers));
    }

    @GetMapping("/my/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OfferDTO>>> myOffersPage(
            @RequestParam(defaultValue = "BUYER") String as,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            Authentication authentication) {
        User user = currentUser(authentication);
        PageResponse<OfferDTO> result = as.equalsIgnoreCase("FARMER")
                ? offerService.getFarmerOffersPage(user.getId(), page, size)
                : offerService.getBuyerOffersPage(user.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OfferDTO>>> offersForListing(
            @RequestParam String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.getOffersForListing(listingId)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName());
    }
}