package com.agrolink.app.demo;

import com.agrolink.app.dto.AdminConversationDTO;
import com.agrolink.app.dto.ConversationDTO;
import com.agrolink.app.dto.OfferDTO;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.OrderStatsRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.InvoiceService;
import com.agrolink.app.service.MessageService;
import com.agrolink.app.service.OfferService;
import com.agrolink.app.service.OrderService;
import com.agrolink.app.service.ProduceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide read models for demo visitors. A demo account owns nothing,
 * so every "my ..." endpoint would come back empty; instead the demo sees
 * the live shared data (listings, orders, offers, conversations, invoices)
 * exactly as it is right now - an admin edit or delete shows up here
 * immediately because nothing is copied. Everything stays read-only: writes
 * are still rejected by {@link DemoAuthFilter}.
 */
@Component
@RequiredArgsConstructor
public class DemoReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProduceService produceService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OfferService offerService;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final InvoiceService invoiceService;
    private final com.agrolink.app.service.DashboardService dashboardService;

    // ------------------------------------------------------------ dashboard

    /**
     * A demo account owns nothing, so its personal dashboard would be all
     * zeros. Instead the demo showcases the live dashboard of the first real
     * account of the same side - every number, table and activity row is
     * genuine database data, and an admin edit shows up here immediately.
     * Falls back to the empty demo id when no such account exists yet.
     */
    public Object showcaseDashboard(DemoAccounts.Entry demo,
                                    java.time.LocalDate from, java.time.LocalDate to) {
        String showcaseId = demo.userId();
        if (demo.role() == Role.FARMER || demo.role() == Role.BUYER) {
            showcaseId = userRepository.findByRole(demo.role()).stream()
                    .map(User::getId)
                    .findFirst()
                    .orElse(demo.userId());
        }
        return dashboardService.dashboard(showcaseId, demo.role(), from, to);
    }

    // ------------------------------------------------------------- produce

    public Object allListings() {
        return produceService.listAllForManagement();
    }

    public Object visibleListing(String id, DemoAccounts.Entry demo) {
        return produceService.getVisibleById(id, demo.userId(), isPrivileged(demo));
    }

    // --------------------------------------------------------------- orders

    public PageResponse<OrderResponseRecord> allOrders(int page, int size) {
        return orderService.getAllOrdersForAdmin(null, page, clamp(size));
    }

    public PageResponse<OrderResponseRecord> allOrdersFiltered(String status, int page, int size) {
        return orderService.getAllOrdersForAdmin(parseStatus(status), page, clamp(size));
    }

    public Object orderDetail(String orderId, DemoAccounts.Entry demo) {
        return orderService.getOrderById(orderId, demo.userId(), true);
    }

    /**
     * Platform-wide order counters, same card shape as a personal stats call.
     */
    public OrderStatsRecord platformStats() {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        long total = 0;
        for (OrderStatus status : OrderStatus.values()) {
            long count = orderRepository.findByDeletedFalseAndOrderStatus(status).size();
            byStatus.put(status, count);
            total += count;
        }
        long awaitingPayment = byStatus.getOrDefault(OrderStatus.OFFER_ACCEPTED, 0L)
                + byStatus.getOrDefault(OrderStatus.PAYMENT_PENDING, 0L);
        return new OrderStatsRecord(
                total,
                byStatus.getOrDefault(OrderStatus.PENDING, 0L),
                awaitingPayment,
                byStatus.getOrDefault(OrderStatus.ESCROW_HELD, 0L),
                byStatus.getOrDefault(OrderStatus.PAID_CONFIRMED, 0L)
                        + byStatus.getOrDefault(OrderStatus.PROCESSING, 0L)
                        + byStatus.getOrDefault(OrderStatus.IN_TRANSIT, 0L),
                byStatus.getOrDefault(OrderStatus.DELIVERED, 0L),
                byStatus.getOrDefault(OrderStatus.CANCELLED, 0L)
                        + byStatus.getOrDefault(OrderStatus.DISPUTED, 0L),
                byStatus);
    }

    // --------------------------------------------------------------- offers

    /**
     * Every offer on the given side of the trade. There is no global offers
     * query, so the union is built across all accounts of that side.
     */
    public List<OfferDTO> allOffers(String side) {
        boolean farmerSide = "FARMER".equalsIgnoreCase(side);
        List<OfferDTO> all = new ArrayList<>();
        for (User user : userRepository.findByRole(farmerSide ? Role.FARMER : Role.BUYER)) {
            all.addAll(farmerSide
                    ? offerService.getOffersByFarmer(user.getId())
                    : offerService.getOffersByBuyer(user.getId()));
        }
        return all;
    }

    public PageResponse<OfferDTO> allOffersPage(String side, int page, int size) {
        List<OfferDTO> all = allOffers(side);
        int safeSize = clamp(size);
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil(all.size() / (double) safeSize);
        return new PageResponse<>(all.subList(from, to), safePage, safeSize,
                all.size(), totalPages, to < all.size(), from > 0);
    }

    // --------------------------------------------------------------- messages

    public List<ConversationDTO> allConversations(DemoAccounts.Entry demo) {
        boolean buyerView = demo.role() == Role.BUYER;
        List<ConversationDTO> result = new ArrayList<>();
        for (AdminConversationDTO admin : messageService.listAllForAdmin(0, MAX_PAGE_SIZE).content()) {
            result.add(new ConversationDTO(
                    admin.id(), admin.orderId(), admin.listingId(),
                    buyerView ? admin.farmerId() : admin.buyerId(),
                    buyerView ? admin.farmerName() : admin.buyerName(),
                    buyerView ? Role.FARMER.name() : Role.BUYER.name(),
                    admin.subject(), admin.lastMessage(), admin.lastMessageAt(),
                    0L, admin.moderated()));
        }
        return result;
    }

    public Object conversationMessages(String conversationId) {
        return messageService.listMessagesForAdmin(conversationId);
    }

    // --------------------------------------------------------------- invoices

    public Object allInvoices() {
        return invoiceService.listAll(0, MAX_PAGE_SIZE).content();
    }

    public Object invoiceDetail(String invoiceId, DemoAccounts.Entry demo) {
        return invoiceService.getById(invoiceId, demo.userId(), true);
    }

    public Object invoiceForOrder(String orderId, DemoAccounts.Entry demo) {
        return invoiceService.generateOrGetByOrder(orderId, demo.userId(), true);
    }

    // ----------------------------------------------------------------- users

    /**
     * The user directory, honouring the same visibility rule as the real
     * endpoint (plain admins never see admin accounts). Reads the repository
     * directly instead of AuthService: going through the auth service would
     * loop back into SecurityConfig and stop the app from starting.
     */
    public Object allUsers(DemoAccounts.Entry demo) {
        java.util.List<User> all = userRepository.findAll();
        if (demo.role() == Role.SUPER_ADMIN) {
            return all;
        }
        return all.stream()
                .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.SUPER_ADMIN)
                .toList();
    }

    public Object usersByRole(String role, DemoAccounts.Entry demo) {
        Role requested = parseRole(role);
        if (demo.role() != Role.SUPER_ADMIN
                && (requested == Role.ADMIN || requested == Role.SUPER_ADMIN)) {
            throw new com.agrolink.app.exception.BusinessRuleException(
                    "Only a Super Admin can view admin accounts", 403);
        }
        return userRepository.findByRole(requested);
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(String.valueOf(role).trim().toUpperCase());
        } catch (Exception ex) {
            throw new com.agrolink.app.exception.BusinessRuleException("Invalid role: " + role, 400);
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean isPrivileged(DemoAccounts.Entry demo) {
        return demo.role() == Role.ADMIN || demo.role() == Role.SUPER_ADMIN;
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int clamp(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
