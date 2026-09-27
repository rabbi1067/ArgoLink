package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.AcceptOfferRecord;
import com.agrolink.app.dto.BkashCheckoutRequest;
import com.agrolink.app.dto.BkashCheckoutResponse;
import com.agrolink.app.dto.ConfirmPaymentRequestDTO;
import com.agrolink.app.dto.SimulatedPaymentRequest;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.OrderStatsRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.EscrowStatus;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.OfferStatus;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.PaymentMethod;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.model.Role;
import com.agrolink.app.repository.OfferRepository;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.service.BkashCheckoutService;
import com.agrolink.app.service.InvoiceService;
import com.agrolink.app.service.OrderService;
import com.agrolink.app.service.PaymentGatewayService;
import com.agrolink.app.service.PaymentGatewayService.PaymentVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal PRICE_SCALE = new BigDecimal("0.01");
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final int MAX_USER_PAGE_SIZE = 50;
    private static final int MAX_ADMIN_PAGE_SIZE = 200;
    private static final double MIN_LATITUDE = -90;
    private static final double MAX_LATITUDE = 90;
    private static final double MIN_LONGITUDE = -180;
    private static final double MAX_LONGITUDE = 180;

    private static final Set<OrderStatus> PAYABLE_STAGES =
            Set.of(OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING);

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private final OrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final ProduceListingRepository produceListingRepository;
    private final InvoiceService invoiceService;
    private final PaymentGatewayService paymentGatewayService;
    private final BkashCheckoutService bkashCheckoutService;

    @Override
    @CacheEvict(cacheNames = {CacheConfig.CACHE_PUBLIC_LISTINGS, CacheConfig.CACHE_DASHBOARD_STATS}, allEntries = true)
    public OrderResponseRecord acceptOffer(String userId, AcceptOfferRecord request) {
        Offer offer = offerRepository.findById(request.offerId())
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", request.offerId()));

        boolean involved = offer.getBuyerId().equals(userId) || offer.getFarmerId().equals(userId);
        if (!involved) {
            throw new BusinessRuleException("You are not a party to this offer", 403);
        }
        if (offer.getStatus() != OfferStatus.OFFERED) {
            throw new BusinessRuleException("Offer is no longer valid (status: " + offer.getStatus() + ")", 409);
        }

        ProduceListing listing = produceListingRepository.findById(offer.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("ProduceListing", "id", offer.getListingId()));

        ProduceListing reserved = atomicallyReserveQuantity(listing.getId(), offer.getOfferedQuantity());

        offer.setStatus(OfferStatus.ACCEPTED);
        offerRepository.save(offer);
        rejectCompetingOffers(listing.getId(), offer.getId());

        Order order = Order.builder()
                .offerId(offer.getId())
                .buyerId(offer.getBuyerId())
                .farmerId(offer.getFarmerId())
                .cropName(reserved.getCropName())
                .agreedQuantity(offer.getOfferedQuantity())
                .agreedPricePerUnit(offer.getOfferedPrice())
                .totalAmount(offer.getOfferedQuantity()
                        .multiply(offer.getOfferedPrice())
                        .setScale(PRICE_SCALE.scale(), RoundingMode.HALF_UP))
                .orderStatus(OrderStatus.PENDING)
                .deliveryAddress(blankToNull(request.deliveryAddress()))
                .escrowStatus(EscrowStatus.NONE)
                .build();

        Order saved = orderRepository.save(order);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    @Override
    public OrderResponseRecord getOrderById(String orderId, String userId, boolean isAdmin) {
        Order order = findOrder(orderId);
        if (!isAdmin && !order.getBuyerId().equals(userId) && !order.getFarmerId().equals(userId)) {
            throw new BusinessRuleException("You are not allowed to view this order", 403);
        }
        return OrderResponseRecord.from(order);
    }

    @Override
    public List<OrderResponseRecord> getBuyerOrders(String buyerId) {
        return orderRepository.findByBuyerId(buyerId).stream()
                .filter(this::isVisible)
                .filter(order -> order.getOrderStatus() != OrderStatus.DELIVERED)
                .map(OrderResponseRecord::from)
                .toList();
    }

    @Override
    public List<OrderResponseRecord> getFarmerOrders(String farmerId) {
        return orderRepository.findByFarmerId(farmerId).stream()
                .filter(this::isVisible)
                .filter(order -> order.getOrderStatus() != OrderStatus.DELIVERED)
                .map(OrderResponseRecord::from)
                .toList();
    }

    @Override
    public List<OrderResponseRecord> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByDeletedFalseAndOrderStatus(status).stream()
                .map(OrderResponseRecord::from)
                .toList();
    }

    @Override
    public PageResponse<OrderResponseRecord> getBuyerOrdersPage(String buyerId, OrderStatus statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, MAX_USER_PAGE_SIZE));
        Page<Order> result;
        if (statusFilter == null) {
            result = orderRepository.findByBuyerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                    buyerId, OrderStatus.DELIVERED, pageable);
        } else if (statusFilter == OrderStatus.DELIVERED) {
            return PageResponse.from(Page.empty(pageable));
        } else {
            result = orderRepository.findByBuyerIdAndDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
                    buyerId, OrderStatus.DELIVERED, statusFilter, pageable);
        }
        return PageResponse.from(result.map(OrderResponseRecord::from));
    }

    @Override
    public PageResponse<OrderResponseRecord> getFarmerOrdersPage(String farmerId, OrderStatus statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, MAX_USER_PAGE_SIZE));
        Page<Order> result;
        if (statusFilter == null) {
            result = orderRepository.findByFarmerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                    farmerId, OrderStatus.DELIVERED, pageable);
        } else if (statusFilter == OrderStatus.DELIVERED) {
            return PageResponse.from(Page.empty(pageable));
        } else {
            result = orderRepository.findByFarmerIdAndDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
                    farmerId, OrderStatus.DELIVERED, statusFilter, pageable);
        }
        return PageResponse.from(result.map(OrderResponseRecord::from));
    }

    private int clampSize(int size, int max) {
        if (size <= 0) {
            return 5;
        }
        return Math.min(size, max);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord updateOrderStatus(String orderId, String userId, boolean isAdmin, OrderStatus newStatus) {
        Order order = findOrder(orderId);
        boolean buyerSide = order.getBuyerId().equals(userId);
        boolean farmerSide = order.getFarmerId().equals(userId);
        if (!isAdmin && !buyerSide && !farmerSide) {
            throw new BusinessRuleException("You are not allowed to update this order", 403);
        }
        Set<OrderStatus> allowed = TRANSITIONS.get(order.getOrderStatus());
        if (allowed == null || !allowed.contains(newStatus)) {
            throw transitionRefused(order.getOrderStatus(), newStatus, buyerSide);
        }
        // Admins keep the manual override; everyone else is held to their side of the workflow so
        // a paid order cannot be cancelled or fulfilled by the wrong party.
        if (!isAdmin && !isAllowedFor(order.getOrderStatus(), newStatus, buyerSide)) {
            throw transitionRefused(order.getOrderStatus(), newStatus, buyerSide);
        }
        Order saved = applyStatus(order, newStatus);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    // ------------------------------------------------------------------ order workflow

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord acceptOfferByFarmer(String orderId, String farmerId) {
        Order order = findOrder(orderId);
        if (!order.getFarmerId().equals(farmerId)) {
            throw new BusinessRuleException("Only the farmer of this order can accept the offer", 403);
        }
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException(
                    "Offer can no longer be accepted (order status: " + order.getOrderStatus() + ")", 409);
        }
        order.setOrderStatus(OrderStatus.OFFER_ACCEPTED);
        Order saved = orderRepository.save(order);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord confirmBuyerPayment(String buyerId, ConfirmPaymentRequestDTO request) {
        Order order = findOrder(request.orderId());
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleException("Only the buyer of this order can confirm its payment", 403);
        }

        EscrowStatus escrow = order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus();
        if (escrow == EscrowStatus.ESCROW_HELD || escrow == EscrowStatus.RELEASED) {
            if (request.transactionId().trim().equals(order.getTransactionId())) {
                return OrderResponseRecord.from(order);
            }
            throw new BusinessRuleException("This order is already paid (escrow: " + escrow + ")", 409);
        }
        if (order.getOrderStatus() == null || !PAYABLE_STAGES.contains(order.getOrderStatus())) {
            throw new BusinessRuleException(
                    "Payment cannot be confirmed while the order is " + order.getOrderStatus(), 409);
        }

        BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        if (request.totalAmount() == null
                || request.totalAmount().subtract(due).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            throw new BusinessRuleException(
                    "Amount mismatch: the order total is " + due.setScale(2, RoundingMode.HALF_UP), 409);
        }
        rejectDuplicateTransaction(request.transactionId(), order.getId());

        PaymentVerification verification = paymentGatewayService.verify(
                request.paymentMethod(), request.transactionId(), due, order.getId());
        if (!verification.verified()) {
            order.setEscrowStatus(EscrowStatus.FAILED);
            order.setPaymentMethod(request.paymentMethod());
            order.setTransactionId(request.transactionId().trim());
            order.setPaymentGateway(verification.gateway());
            order.setPaymentVerified(false);
            orderRepository.save(order);
            throw new BusinessRuleException("Payment was not accepted: " + verification.message(), 422);
        }

        order.setDeliveryAddress(request.shippingAddress().trim());
        return settleIntoEscrow(order, request.paymentMethod(), request.transactionId().trim(),
                verification.gateway(), verification.live(), due);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord confirmSimulatedPayment(String buyerId, String orderId,
                                                      SimulatedPaymentRequest request) {
        Order order = findOrder(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleException("Only the buyer of this order can pay for it", 403);
        }
        if (paymentGatewayService.isVerificationRequired()) {
            throw new BusinessRuleException(
                    "In-app number and PIN payments are disabled because gateway verification is required on this server", 409);
        }
        requirePayable(order);

        // The number has to match the rail the buyer picked, otherwise a card number could be
        // posted to a wallet gateway later on.
        String numberProblem = request.numberError();
        if (numberProblem != null) {
            throw new BusinessRuleException(numberProblem, 422);
        }
        String pinProblem = request.pinError();
        if (pinProblem != null) {
            throw new BusinessRuleException(pinProblem, 422);
        }

        // The PIN is used to satisfy the "number + PIN" flow and is deliberately dropped here:
        // nothing about it is written to the database or the logs.
        BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();

        return settleIntoEscrow(order, request.paymentMethod(), request.reference(),
                request.maskedNumber() + " (sandbox)", false, due,
                request.shippingAddress().trim());
    }

    @Override
    public BkashCheckoutResponse createBkashCheckout(String buyerId, String orderId, BkashCheckoutRequest request) {
        Order order = findOrder(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleException("Only the buyer of this order can pay for it", 403);
        }
        requirePayable(order);

        BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        BkashCheckoutService.CheckoutSession session = bkashCheckoutService.createCheckout(order.getId(), due);

        // The address is stored before the redirect so the farmer sees the destination even if the
        // buyer completes the payment in a separate tab.
        order.setDeliveryAddress(request.shippingAddress().trim());
        order.setPaymentMethod(PaymentMethod.BKASH);
        order.setTransactionId(session.paymentId());
        order.setPaymentGateway("bKash");
        order.setPaymentVerified(false);
        orderRepository.save(order);

        return BkashCheckoutResponse.of(order.getId(), session.paymentId(), session.bkashUrl(), due);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord confirmBkashCallback(String orderId, String paymentId) {
        Order order = findOrder(orderId);
        if (paymentId == null || paymentId.isBlank()) {
            throw new BusinessRuleException("bKash did not return a payment id", 422);
        }
        if (order.getTransactionId() != null && order.getTransactionId().equals(paymentId.trim())
                && order.getEscrowStatus() == EscrowStatus.ESCROW_HELD) {
            return OrderResponseRecord.from(order);
        }
        requirePayable(order);

        BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        PaymentVerification verification =
                bkashCheckoutService.verifyPayment(paymentId, order.getId(), due);
        if (!verification.verified()) {
            order.setEscrowStatus(EscrowStatus.FAILED);
            order.setPaymentVerified(false);
            order.setPaymentGateway(verification.gateway());
            orderRepository.save(order);
            throw new BusinessRuleException("Payment was not accepted: " + verification.message(), 422);
        }
        return settleIntoEscrow(order, PaymentMethod.BKASH, paymentId.trim(),
                verification.gateway(), true, due);
    }

    @Override
    public PaymentCapabilities getPaymentCapabilities() {
        return new PaymentCapabilities(
                bkashCheckoutService.isConfigured(),
                paymentGatewayService.isSslCommerzConfigured(),
                paymentGatewayService.isVerificationRequired(),
                bkashCheckoutService.callbackBaseUrl());
    }

    @Override
    public OrderStatsRecord getOrderStats(String userId, Role role) {
        boolean asFarmer = role == Role.FARMER;
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        long total = 0;
        for (OrderStatus status : OrderStatus.values()) {
            long count = asFarmer
                    ? orderRepository.countByFarmerIdAndDeletedFalseAndOrderStatus(userId, status)
                    : orderRepository.countByBuyerIdAndDeletedFalseAndOrderStatus(userId, status);
            byStatus.put(status, count);
            total += count;
        }

        long awaitingPayment = byStatus.getOrDefault(OrderStatus.OFFER_ACCEPTED, 0L)
                + byStatus.getOrDefault(OrderStatus.PAYMENT_PENDING, 0L);
        long inEscrow = byStatus.getOrDefault(OrderStatus.ESCROW_HELD, 0L);
        long inTransit = byStatus.getOrDefault(OrderStatus.PAID_CONFIRMED, 0L)
                + byStatus.getOrDefault(OrderStatus.PROCESSING, 0L)
                + byStatus.getOrDefault(OrderStatus.IN_TRANSIT, 0L);
        long delivered = byStatus.getOrDefault(OrderStatus.DELIVERED, 0L);
        long cancelled = byStatus.getOrDefault(OrderStatus.CANCELLED, 0L)
                + byStatus.getOrDefault(OrderStatus.DISPUTED, 0L);
        long needsAction = asFarmer
                ? byStatus.getOrDefault(OrderStatus.PENDING, 0L)
                : awaitingPayment;

        return new OrderStatsRecord(total, needsAction, awaitingPayment, inEscrow, inTransit,
                delivered, cancelled, byStatus);
    }

    private void requirePayable(Order order) {
        if (order.getOrderStatus() == null || !PAYABLE_STAGES.contains(order.getOrderStatus())) {
            throw new BusinessRuleException(
                    "Payment cannot be confirmed while the order is " + order.getOrderStatus(), 409);
        }
    }

    /**
     * Single place where a verified payment turns into an escrowed order, so the manual rails and
     * the bKash callback can never drift apart.
     */
    private OrderResponseRecord settleIntoEscrow(Order order, PaymentMethod method, String transactionId,
                                                 String gateway, boolean live, BigDecimal due) {
        return settleIntoEscrow(order, method, transactionId, gateway, live, due, null);
    }

    private OrderResponseRecord settleIntoEscrow(Order order, PaymentMethod method, String transactionId,
                                                 String gateway, boolean live, BigDecimal due,
                                                 String shippingAddress) {
        rejectDuplicateTransaction(transactionId, order.getId());

        if (shippingAddress != null && !shippingAddress.isBlank()) {
            order.setDeliveryAddress(shippingAddress);
        }
        BigDecimal paid = due.setScale(2, RoundingMode.HALF_UP);
        order.setPaymentMethod(method);
        order.setTransactionId(transactionId);
        order.setPaymentGateway(gateway);
        order.setPaymentVerified(live);
        order.setPaidAmount(paid);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        order.setPaidAt(Instant.now());
        order.setOrderStatus(OrderStatus.PAID_CONFIRMED);

        Order saved = orderRepository.save(order);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    @Override
    public OrderResponseRecord updateLiveTracking(String orderId, String actorId, boolean isAdmin,
                                                  Double latitude, Double longitude) {
        Order order = findOrder(orderId);
        if (!isAdmin && !order.getFarmerId().equals(actorId)) {
            throw new BusinessRuleException("Only the assigned farmer or an admin can update the delivery position", 403);
        }
        requireCoordinate(latitude, "latitude", MIN_LATITUDE, MAX_LATITUDE);
        requireCoordinate(longitude, "longitude", MIN_LONGITUDE, MAX_LONGITUDE);
        if (order.getOrderStatus() == null || !order.getOrderStatus().isLiveTrackingStage()) {
            throw new BusinessRuleException(
                    "Live tracking opens once the buyer payment is confirmed (current status: "
                            + order.getOrderStatus() + ")", 409);
        }
        order.setDriverLatitude(round(latitude));
        order.setDriverLongitude(round(longitude));
        order.setTrackingUpdatedAt(Instant.now());
        return OrderResponseRecord.from(orderRepository.save(order));
    }

    // ------------------------------------------------------------------ admin control panel

    @Override
    public PageResponse<OrderResponseRecord> getAllOrdersForAdmin(OrderStatus statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, MAX_ADMIN_PAGE_SIZE));
        Page<Order> result;
        if (statusFilter == null) {
            result = orderRepository.findByDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                    OrderStatus.DELIVERED, pageable);
        } else if (statusFilter == OrderStatus.DELIVERED) {
            return PageResponse.from(Page.empty(pageable));
        } else {
            result = orderRepository.findByDeletedFalseAndOrderStatusNotAndOrderStatusOrderByCreatedAtDesc(
                    OrderStatus.DELIVERED, statusFilter, pageable);
        }
        return PageResponse.from(result.map(OrderResponseRecord::from));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, allEntries = true)
    public OrderResponseRecord adminUpdateOrderStatus(String orderId, OrderStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessRuleException("New status is required", 400);
        }
        Order order = findOrder(orderId);
        if (order.getOrderStatus() == newStatus) {
            return OrderResponseRecord.from(order);
        }
        Order saved = applyStatus(order, newStatus);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    @Override
    @CacheEvict(cacheNames = {CacheConfig.CACHE_PUBLIC_LISTINGS, CacheConfig.CACHE_DASHBOARD_STATS}, allEntries = true)
    public OrderResponseRecord adminDeleteOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order.isDeleted()) {
            throw new BusinessRuleException("This order has already been removed", 409);
        }
        order.setDeleted(true);
        order.setDeletedAt(Instant.now());
        Order saved = applyStatus(order, OrderStatus.CANCELLED);
        invoiceService.syncForOrder(saved);
        return OrderResponseRecord.from(saved);
    }

    // ------------------------------------------------------------------ internals

    /** Sets the status and keeps the escrow position consistent with it. */
    private Order applyStatus(Order order, OrderStatus newStatus) {
        EscrowStatus escrow = order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus();
        if (newStatus == OrderStatus.DELIVERED && escrow == EscrowStatus.ESCROW_HELD) {
            order.setEscrowStatus(EscrowStatus.RELEASED);
        } else if (newStatus == OrderStatus.CANCELLED && escrow == EscrowStatus.ESCROW_HELD) {
            order.setEscrowStatus(EscrowStatus.REFUNDED);
        }
        order.setOrderStatus(newStatus);
        return orderRepository.save(order);
    }

    private void rejectDuplicateTransaction(String transactionId, String orderId) {
        String reference = transactionId == null ? "" : transactionId.trim();
        orderRepository.findByTransactionId(reference)
                .filter(existing -> !existing.getId().equals(orderId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "This transaction id is already used by order " + existing.getId(), 409);
                });
    }

    private void requireCoordinate(Double value, String label, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite() || value < min || value > max) {
            throw new BusinessRuleException(
                    "Invalid " + label + ": expected a number between " + min + " and " + max, 422);
        }
    }

    private double round(Double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private boolean isVisible(Order order) {
        return !order.isDeleted();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    private ProduceListing atomicallyReserveQuantity(String listingId, BigDecimal quantity) {
        ProduceListing listing = produceListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("ProduceListing", "id", listingId));

        if (listing.getStatus() != ListingStatus.ACTIVE && listing.getStatus() != ListingStatus.RESERVED) {
            throw new BusinessRuleException("Listing is no longer available for orders", 409);
        }

        BigDecimal available = listing.getAvailableQuantity();
        if (available == null || available.compareTo(quantity) < 0) {
            throw new BusinessRuleException(
                    "Insufficient stock (available: " + (available == null ? 0 : available) + ")", 409);
        }

        listing.setAvailableQuantity(available.subtract(quantity));
        BigDecimal reserved = listing.getReservedQuantity();
        listing.setReservedQuantity(reserved == null ? quantity : reserved.add(quantity));
        return produceListingRepository.save(listing);
    }

    private void rejectCompetingOffers(String listingId, String acceptedOfferId) {
        offerRepository.findByListingIdAndStatus(listingId, OfferStatus.OFFERED)
                .stream()
                .filter(other -> !other.getId().equals(acceptedOfferId))
                .forEach(other -> {
                    other.setStatus(OfferStatus.REJECTED);
                    offerRepository.save(other);
                });
    }

    private Order findOrder(String orderId) {
        return orderRepository.findByIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.PENDING,
                Set.of(OrderStatus.OFFER_ACCEPTED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.OFFER_ACCEPTED,
                Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID_CONFIRMED, OrderStatus.ESCROW_HELD,
                        OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.PAYMENT_PENDING,
                Set.of(OrderStatus.PAID_CONFIRMED, OrderStatus.ESCROW_HELD, OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.PAID_CONFIRMED,
                Set.of(OrderStatus.ESCROW_HELD, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT,
                        OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.ESCROW_HELD,
                Set.of(OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED,
                        OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.PROCESSING,
                Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.CONFIRMED, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED, OrderStatus.DISPUTED));
        transitions.put(OrderStatus.DELIVERED, Set.of(OrderStatus.DISPUTED));
        transitions.put(OrderStatus.CANCELLED, Set.of(OrderStatus.DISPUTED));
        transitions.put(OrderStatus.DISPUTED, Set.of());
        return transitions;
    }


    private static boolean isAllowedFor(OrderStatus current, OrderStatus target, boolean buyerSide) {
        if (current == null) {
            return false;
        }
        if (buyerSide) {
            return switch (current) {
                // Nothing paid yet, so walking away costs nobody anything.
                case PENDING, OFFER_ACCEPTED, PAYMENT_PENDING ->
                        target == OrderStatus.CANCELLED || target == OrderStatus.DISPUTED;
                // Money is secured: the buyer may raise a dispute but never cancel, and never
                // advances the farmer's fulfilment stages.
                case PAID_CONFIRMED, ESCROW_HELD, PROCESSING, CONFIRMED, IN_TRANSIT, DELIVERED ->
                        target == OrderStatus.DISPUTED;
                case CANCELLED, DISPUTED -> false;
            };
        }
        return switch (current) {
            case PENDING, OFFER_ACCEPTED, PAYMENT_PENDING ->
                    target == OrderStatus.CANCELLED || target == OrderStatus.DISPUTED;
            case PAID_CONFIRMED, ESCROW_HELD, CONFIRMED ->
                    target == OrderStatus.PROCESSING || target == OrderStatus.IN_TRANSIT
                            || target == OrderStatus.DELIVERED || target == OrderStatus.DISPUTED;
            case PROCESSING ->
                    target == OrderStatus.IN_TRANSIT || target == OrderStatus.DISPUTED;
            case IN_TRANSIT ->
                    target == OrderStatus.DELIVERED || target == OrderStatus.DISPUTED;
            case DELIVERED -> target == OrderStatus.DISPUTED;
            case CANCELLED, DISPUTED -> false;
        };
    }

    private static boolean isPostPayment(OrderStatus status) {
        return status == OrderStatus.PAID_CONFIRMED || status == OrderStatus.ESCROW_HELD
                || status == OrderStatus.PROCESSING || status == OrderStatus.CONFIRMED
                || status == OrderStatus.IN_TRANSIT || status == OrderStatus.DELIVERED;
    }

    private static boolean isFulfilmentStep(OrderStatus status) {
        return status == OrderStatus.PROCESSING || status == OrderStatus.IN_TRANSIT
                || status == OrderStatus.DELIVERED;
    }

    /** Explains the refusal in the words the actor can act on. */
    private static BusinessRuleException transitionRefused(OrderStatus current, OrderStatus target, boolean buyerSide) {
        if (target == OrderStatus.CANCELLED && isPostPayment(current)) {
            return new BusinessRuleException(
                    "This order is already paid and the money is held in escrow, so it cannot be cancelled. "
                            + "Raise a dispute instead and an admin will review the refund.", 409);
        }
        if (buyerSide && isFulfilmentStep(target)) {
            return new BusinessRuleException(
                    "Only the farmer can move this order to " + target + ".", 409);
        }
        if (!buyerSide && isFulfilmentStep(target) && isPostPayment(current) && !current.isTerminal()) {
            return new BusinessRuleException(
                    "This order cannot move straight to " + target + ".", 409);
        }
        return new BusinessRuleException("Cannot transition order from " + current + " to " + target, 409);
    }
}
