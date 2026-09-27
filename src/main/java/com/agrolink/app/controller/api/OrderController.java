package com.agrolink.app.controller.api;

import com.agrolink.app.dto.AcceptOfferRecord;
import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.BkashCheckoutRequest;
import com.agrolink.app.dto.BkashCheckoutResponse;
import com.agrolink.app.dto.SimulatedPaymentRequest;
import com.agrolink.app.dto.ConfirmPaymentRequestDTO;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.OrderStatsRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.dto.UpdateOrderStatusRecord;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.service.OrderService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('BUYER', 'FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> acceptOffer(
            @Valid @RequestBody AcceptOfferRecord request,
            Authentication authentication) {
        OrderResponseRecord order = orderService.acceptOffer(currentUser(authentication).getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created", order));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> getOrder(@PathVariable String orderId,
                                                                     Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getOrderById(orderId, user.getId(), isAdmin(user))));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrderResponseRecord>>> myOrders(
            @RequestParam(defaultValue = "BUYER") String as,
            Authentication authentication) {
        User user = currentUser(authentication);
        List<OrderResponseRecord> orders = as.equalsIgnoreCase("FARMER")
                ? orderService.getFarmerOrders(user.getId())
                : orderService.getBuyerOrders(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }


    @GetMapping("/my/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponseRecord>>> myOrdersPage(
            @RequestParam(defaultValue = "BUYER") String as,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        User user = currentUser(authentication);
        PageResponse<OrderResponseRecord> result = as.equalsIgnoreCase("FARMER")
                ? orderService.getFarmerOrdersPage(user.getId(), status, page, size)
                : orderService.getBuyerOrdersPage(user.getId(), status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> updateStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRecord request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Order status updated",
                orderService.updateOrderStatus(orderId, user.getId(), isAdmin(user), request.status())));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponseRecord>>> ordersByStatus(
            @PathVariable OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersByStatus(status)));
    }


    @PostMapping("/{orderId}/accept-offer")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> acceptOfferByFarmer(
            @PathVariable String orderId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Offer accepted, awaiting buyer payment",
                orderService.acceptOfferByFarmer(orderId, user.getId())));
    }

    @PostMapping("/confirm-payment")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequestDTO request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Payment confirmed and held in escrow",
                orderService.confirmBuyerPayment(user.getId(), request)));
    }

    @PutMapping("/{orderId}/tracking")    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> updateTracking(
            @PathVariable String orderId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Live position updated",
                orderService.updateLiveTracking(orderId, user.getId(), isAdmin(user), latitude, longitude)));
    }


    @PostMapping("/{orderId}/payment/bkash")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BkashCheckoutResponse>> createBkashCheckout(
            @PathVariable String orderId,
            @Valid @RequestBody BkashCheckoutRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("bKash checkout created",
                orderService.createBkashCheckout(user.getId(), orderId, request)));
    }


    @PostMapping("/{orderId}/payment/simulate")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> payWithNumberAndPin(
            @PathVariable String orderId,
            @Valid @RequestBody SimulatedPaymentRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok(request.paymentMethod() + " sandbox payment recorded",
                orderService.confirmSimulatedPayment(user.getId(), orderId, request)));
    }


    @GetMapping(value = "/payment/bkash/callback", produces = "text/html")
    public ResponseEntity<String> bkashCallback(@RequestParam String orderId,
                                                 @RequestParam(required = false) String paymentID) {
        String message;
        try {
            OrderResponseRecord order = orderService.confirmBkashCallback(orderId, paymentID);
            message = "<h1>Payment successful</h1><p>Order <code>" + order.id()
                    + "</code> is paid and the amount is held in escrow until delivery.</p>";
        } catch (RuntimeException ex) {
            message = "<h1>Payment not completed</h1><p>" + escape(ex.getMessage()) + "</p>";
        }
        String body = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>bKash payment</title><style>"
                + "body{font-family:system-ui,sans-serif;background:#0b1220;color:#e2e8f0;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}"
                + "div{max-width:32rem;padding:2rem;background:#111c33;border:1px solid #1e293b;"
                + "border-radius:1rem}h1{margin-top:0;font-size:1.4rem}p{line-height:1.6;color:#cbd5e1}"
                + "code{color:#34d399}</style></head><body><div>" + message
                + "<p>You can close this tab and return to AgroLink.</p></div></body></html>";
        return ResponseEntity.ok(body);
    }

    @GetMapping("/payment/capabilities")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderService.PaymentCapabilities>> paymentCapabilities() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getPaymentCapabilities()));
    }

    @GetMapping("/my/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderStatsRecord>> myStats(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderStats(user.getId(), user.getRole())));
    }


    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponseRecord>>> allOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAllOrdersForAdmin(status, page, size)));
    }

    @PutMapping("/admin/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> adminUpdateStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRecord request) {
        return ResponseEntity.ok(ApiResponse.ok("Order status updated by admin",
                orderService.adminUpdateOrderStatus(orderId, request.status())));
    }

    @DeleteMapping("/admin/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseRecord>> adminDeleteOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled and removed",
                orderService.adminDeleteOrder(orderId)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName());
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    private String escape(String value) {
        return value == null ? "Unknown error" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}