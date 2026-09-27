package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.InvoiceDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.service.InvoiceService;
import com.agrolink.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final UserService userService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<InvoiceDTO>>> myInvoices(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.listForUser(currentUser(authentication).getId())));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceDTO>>> allInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Invoices loaded", invoiceService.listAll(page, size)));
    }

    @DeleteMapping("/admin/{invoiceId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable String invoiceId) {
        invoiceService.deleteById(invoiceId);
        return ResponseEntity.ok(ApiResponse.ok("Invoice deleted", null));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InvoiceDTO>> invoiceForOrder(@PathVariable String orderId,
                                                                   Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Invoice ready",
                invoiceService.generateOrGetByOrder(orderId, user.getId(), isAdmin(user))));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoice(@PathVariable String invoiceId,
                                                              Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getById(invoiceId, user.getId(), isAdmin(user))));
    }

    private User currentUser(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName());
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }
}