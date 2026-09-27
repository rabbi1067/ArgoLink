package com.agrolink.app.controller.api;

import com.agrolink.app.dto.AdjustStockRecord;
import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.ProduceListingDTO;
import com.agrolink.app.dto.ProduceListingPage;
import com.agrolink.app.dto.ProduceListingRequest;
import com.agrolink.app.dto.ProduceListingResponse;
import com.agrolink.app.dto.ProduceSearchCriteria;
import com.agrolink.app.model.Category;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.service.ProduceService;
import com.agrolink.app.service.PublicCatalogService;
import com.agrolink.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/produce")
@RequiredArgsConstructor
public class ProduceController {

    private final ProduceService produceService;
    private final PublicCatalogService catalogService;
    private final UserService userService;

    @GetMapping("/listings")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<ProduceListingDTO>>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cropName) {
        return ResponseEntity.ok(ApiResponse.ok(produceService.searchActive(category, cropName)));
    }

    @GetMapping("/listings/search")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<ProduceListingPage>> searchPaged(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minQuantity,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProduceSearchCriteria criteria = new ProduceSearchCriteria(
                q, category, district, division, minPrice, maxPrice, minQuantity, sort, page, size);
        return ResponseEntity.ok(ApiResponse.ok(produceService.search(criteria)));
    }

    @GetMapping("/categories")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<Category>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getCategories()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProduceListingResponse>>> myListings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(produceService.getMyListings(currentUserId(authentication))));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProduceListingResponse>>> manage() {
        return ResponseEntity.ok(ApiResponse.ok(produceService.listAllForManagement()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> getById(@PathVariable String id,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                produceService.getVisibleById(id, currentUserId(authentication), isPrivileged(authentication))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> create(@Valid @RequestBody ProduceListingRequest request,
                                                                      Authentication authentication) {
        ProduceListingResponse created = produceService.create(currentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Listing created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> update(@PathVariable String id,
                                                                      @Valid @RequestBody ProduceListingRequest request,
                                                                      Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Listing updated",
                produceService.update(id, currentUserId(authentication), isPrivileged(authentication), request)));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> publish(@PathVariable String id,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Listing published",
                produceService.changeStatus(id, currentUserId(authentication), isPrivileged(authentication), ListingStatus.ACTIVE)));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> archive(@PathVariable String id,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Listing archived",
                produceService.changeStatus(id, currentUserId(authentication), isPrivileged(authentication), ListingStatus.ARCHIVED)));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProduceListingResponse>> adjustStock(@PathVariable String id,
                                                                           @Valid @RequestBody AdjustStockRecord request,
                                                                           Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Stock updated",
                produceService.adjustStock(id, currentUserId(authentication), isPrivileged(authentication), request.newAvailableQuantity())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id,
                                                    Authentication authentication) {
        produceService.delete(id, currentUserId(authentication), isPrivileged(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Listing deleted", null));
    }

    private String currentUserId(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).getId();
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}