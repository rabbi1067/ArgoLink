package com.agrolink.app.service.impl;

import com.agrolink.app.dto.AcceptOfferRecord;
import com.agrolink.app.dto.BkashCheckoutRequest;
import com.agrolink.app.dto.BkashCheckoutResponse;
import com.agrolink.app.dto.ConfirmPaymentRequestDTO;
import com.agrolink.app.dto.SimulatedPaymentRequest;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.OrderStatsRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.exception.BusinessRuleException;
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
import com.agrolink.app.service.PaymentGatewayService;
import com.agrolink.app.service.PaymentGatewayService.PaymentVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private ProduceListingRepository produceListingRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private BkashCheckoutService bkashCheckoutService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, offerRepository, produceListingRepository,
                invoiceService, paymentGatewayService, bkashCheckoutService);
    }

    @Test
    void acceptOffer_shouldReserveStockSnapshotAndComputeTotalAmount() {
        Offer offer = offer("offer-1", "buyer-1", "farmer-1", "listing-1",
                new BigDecimal("100"), new BigDecimal("10.00"), OfferStatus.OFFERED);
        ProduceListing listing = listing("listing-1", "Wheat");
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(offer));
        when(produceListingRepository.findById("listing-1")).thenReturn(Optional.of(listing));
        when(produceListingRepository.save(any(ProduceListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.findByListingIdAndStatus("listing-1", OfferStatus.OFFERED)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result = orderService.acceptOffer("buyer-1",
                new AcceptOfferRecord("offer-1", "Warehouse, Delhi"));

        assertEquals("buyer-1", result.buyerId());
        assertEquals("farmer-1", result.farmerId());
        assertEquals("Wheat", result.cropName());
        assertEquals(new BigDecimal("1000.00"), result.totalAmount());
        assertEquals(OrderStatus.PENDING, result.orderStatus());
        verify(offerRepository).save(offer);
        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
    }

    @Test
    void acceptOffer_shouldRejectCompetingOffers() {
        Offer offer = offer("offer-1", "buyer-1", "farmer-1", "listing-1",
                new BigDecimal("100"), new BigDecimal("10.00"), OfferStatus.OFFERED);
        Offer competitor = offer("offer-2", "buyer-2", "farmer-1", "listing-1",
                new BigDecimal("80"), new BigDecimal("9.00"), OfferStatus.OFFERED);
        ProduceListing listing = listing("listing-1", "Wheat");
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(offer));
        when(produceListingRepository.findById("listing-1")).thenReturn(Optional.of(listing));
        when(produceListingRepository.save(any(ProduceListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.findByListingIdAndStatus("listing-1", OfferStatus.OFFERED))
                .thenReturn(List.of(offer, competitor));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.acceptOffer("buyer-1", new AcceptOfferRecord("offer-1", "Warehouse, Delhi"));

        assertEquals(OfferStatus.REJECTED, competitor.getStatus());
    }

    @Test
    void acceptOffer_shouldRejectOfferPlacedByAnotherBuyer() {
        Offer offer = offer("offer-1", "other-buyer", "farmer-1", "listing-1",
                new BigDecimal("100"), new BigDecimal("10.00"), OfferStatus.OFFERED);
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(offer));

        assertThrows(BusinessRuleException.class,
                () -> orderService.acceptOffer("buyer-1", new AcceptOfferRecord("offer-1", "Address")));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void acceptOffer_shouldRejectOfferThatIsNoLongerOffered() {
        Offer offer = offer("offer-1", "buyer-1", "farmer-1", "listing-1",
                new BigDecimal("100"), new BigDecimal("10.00"), OfferStatus.ACCEPTED);
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(offer));

        assertThrows(BusinessRuleException.class,
                () -> orderService.acceptOffer("buyer-1", new AcceptOfferRecord("offer-1", "Address")));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void acceptOffer_shouldFailWhenStockIsInsufficient() {
        Offer offer = offer("offer-1", "buyer-1", "farmer-1", "listing-1",
                new BigDecimal("6000"), new BigDecimal("10.00"), OfferStatus.OFFERED);
        ProduceListing listing = listing("listing-1", "Wheat");
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(offer));
        when(produceListingRepository.findById("listing-1")).thenReturn(Optional.of(listing));

        assertThrows(BusinessRuleException.class,
                () -> orderService.acceptOffer("buyer-1", new AcceptOfferRecord("offer-1", "Address")));
        verify(produceListingRepository, never()).save(any(ProduceListing.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getOrderById_shouldForbidUnauthorizedAccess() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.getOrderById("order-1", "stranger", false));
    }

    @Test
    void getOrderById_shouldAllowAdminAccess() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        OrderResponseRecord result = orderService.getOrderById("order-1", "stranger", true);

        assertEquals("order-1", result.id());
    }

    @Test
    void createBkashCheckout_shouldStoreAddressAndReturnHostedPage() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bkashCheckoutService.createCheckout("order-1", new BigDecimal("1000.00")))
                .thenReturn(new BkashCheckoutService.CheckoutSession("pay-123", "https://sandbox.bka.sh/pay-123"));

        BkashCheckoutResponse result = orderService.createBkashCheckout("buyer-1", "order-1",
                new BkashCheckoutRequest("Gulshan 1, Dhaka"));

        assertEquals("pay-123", result.paymentId());
        assertEquals("https://sandbox.bka.sh/pay-123", result.bkashUrl());
        assertEquals("Gulshan 1, Dhaka", order.getDeliveryAddress());
        assertEquals(PaymentMethod.BKASH, order.getPaymentMethod());
        assertFalse(order.isPaymentVerified());
        // The order must not be marked paid before bKash confirms anything.
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
    }

    @Test
    void createBkashCheckout_shouldRejectAnotherBuyer() {
        Order order = order("order-1", "other-buyer", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.createBkashCheckout("buyer-1", "order-1",
                new BkashCheckoutRequest("Gulshan 1, Dhaka")));
        verify(bkashCheckoutService, never()).createCheckout(any(), any());
    }

    @Test
    void confirmBkashCallback_shouldEscrowWhenBkashConfirmsTheAmount() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bkashCheckoutService.verifyPayment("pay-123", "order-1", new BigDecimal("1000.00")))
                .thenReturn(PaymentVerification.success("bKash Sandbox", true, "Confirmed by bKash"));

        OrderResponseRecord result = orderService.confirmBkashCallback("order-1", "pay-123");

        assertEquals(OrderStatus.PAID_CONFIRMED, result.orderStatus());
        assertEquals(EscrowStatus.ESCROW_HELD, result.escrowStatus());
        assertEquals("pay-123", result.transactionId());
        assertTrue(result.paymentVerified());
    }

    @Test
    void confirmBkashCallback_shouldNotEscrowWhenBkashRejects() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(bkashCheckoutService.verifyPayment("pay-123", "order-1", new BigDecimal("1000.00")))
                .thenReturn(PaymentVerification.failure("bKash", "amount mismatch"));

        assertThrows(BusinessRuleException.class,
                () -> orderService.confirmBkashCallback("order-1", "pay-123"));

        assertEquals(EscrowStatus.FAILED, order.getEscrowStatus());
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
    }

    @Test
    void getOrderStats_shouldSplitCountersByRole() {
        when(orderRepository.countByBuyerIdAndDeletedFalseAndOrderStatus(eq("buyer-1"), any()))
                .thenAnswer(invocation -> {
                    OrderStatus status = invocation.getArgument(1);
                    return switch (status) {
                        case OFFER_ACCEPTED -> 2L;
                        case ESCROW_HELD -> 3L;
                        case DELIVERED -> 5L;
                        default -> 0L;
                    };
                });

        OrderStatsRecord stats = orderService.getOrderStats("buyer-1", Role.BUYER);

        assertEquals(10, stats.total());
        assertEquals(2, stats.needsAction());
        assertEquals(3, stats.inEscrow());
        assertEquals(5, stats.delivered());
        assertEquals(0, stats.inTransit());
    }

    @Test
    void updateOrderStatus_shouldForbidInvalidTransition() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateOrderStatus("order-1", "buyer-1", false, OrderStatus.IN_TRANSIT));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_shouldApplyValidTransition() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result =
                orderService.updateOrderStatus("order-1", "farmer-1", false, OrderStatus.IN_TRANSIT);

        assertEquals(OrderStatus.IN_TRANSIT, result.orderStatus());
    }

    // ------------------------------------------------------------------ paid orders are not the buyer's to unwind

    @Test
    void updateOrderStatus_shouldStopTheBuyerCancellingAPaidOrder() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        BusinessRuleException problem = assertThrows(BusinessRuleException.class,
                () -> orderService.updateOrderStatus("order-1", "buyer-1", false, OrderStatus.CANCELLED));

        assertTrue(problem.getMessage().contains("escrow"));
        assertTrue(problem.getMessage().contains("dispute"));
        assertEquals(OrderStatus.PAID_CONFIRMED, order.getOrderStatus());
        assertEquals(EscrowStatus.ESCROW_HELD, order.getEscrowStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_shouldStopTheBuyerCancellingWhileInTransit() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.IN_TRANSIT);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateOrderStatus("order-1", "buyer-1", false, OrderStatus.CANCELLED));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_shouldStopTheBuyerDrivingTheFulfilmentStages() {
        for (OrderStatus target : List.of(OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED)) {
            Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
            when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

            BusinessRuleException problem = assertThrows(BusinessRuleException.class,
                    () -> orderService.updateOrderStatus("order-1", "buyer-1", false, target));

            assertTrue(problem.getMessage().contains("Only the farmer"));
        }
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_shouldStopTheFarmerCancellingAPaidOrder() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PROCESSING);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateOrderStatus("order-1", "farmer-1", false, OrderStatus.CANCELLED));
        assertEquals(EscrowStatus.ESCROW_HELD, order.getEscrowStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_shouldLetTheFarmerFulfilAPaidOrder() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord processing =
                orderService.updateOrderStatus("order-1", "farmer-1", false, OrderStatus.PROCESSING);
        assertEquals(OrderStatus.PROCESSING, processing.orderStatus());

        OrderResponseRecord transit =
                orderService.updateOrderStatus("order-1", "farmer-1", false, OrderStatus.IN_TRANSIT);
        assertEquals(OrderStatus.IN_TRANSIT, transit.orderStatus());

        OrderResponseRecord delivered =
                orderService.updateOrderStatus("order-1", "farmer-1", false, OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, delivered.orderStatus());
        assertEquals(EscrowStatus.RELEASED, order.getEscrowStatus());
    }

    @Test
    void updateOrderStatus_shouldLetTheBuyerDisputeAPaidOrder() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result =
                orderService.updateOrderStatus("order-1", "buyer-1", false, OrderStatus.DISPUTED);

        assertEquals(OrderStatus.DISPUTED, result.orderStatus());
        assertEquals(EscrowStatus.ESCROW_HELD, order.getEscrowStatus());
    }

    @Test
    void updateOrderStatus_shouldLetTheBuyerCancelBeforePaying() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAYMENT_PENDING);
        order.setEscrowStatus(EscrowStatus.NONE);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result =
                orderService.updateOrderStatus("order-1", "buyer-1", false, OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, result.orderStatus());
        assertEquals(EscrowStatus.NONE, order.getEscrowStatus());
    }

    @Test
    void updateOrderStatus_shouldLetAnAdminCancelAPaidOrder() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result =
                orderService.updateOrderStatus("order-1", "admin-1", true, OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, result.orderStatus());
        assertEquals(EscrowStatus.REFUNDED, order.getEscrowStatus());
    }

    // ------------------------------------------------------------------ offer accept -> payment -> tracking

    @Test
    void acceptOfferByFarmer_shouldMoveOrderToOfferAccepted() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result = orderService.acceptOfferByFarmer("order-1", "farmer-1");

        assertEquals(OrderStatus.OFFER_ACCEPTED, result.orderStatus());
        assertEquals("UNPAID", result.paymentStatus());
    }

    @Test
    void acceptOfferByFarmer_shouldForbidAnotherFarmer() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.acceptOfferByFarmer("order-1", "farmer-2"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void acceptOfferByFarmer_shouldForbidDoubleAccept() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.acceptOfferByFarmer("order-1", "farmer-1"));
    }

    @Test
    void confirmBuyerPayment_shouldHoldFundsInEscrowAndOpenTracking() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.verify(eq(PaymentMethod.BKASH), eq("TXN12345"), any(), eq("order-1")))
                .thenReturn(PaymentVerification.success("bKash Sandbox", false, "sandbox accepted"));

        OrderResponseRecord result = orderService.confirmBuyerPayment("buyer-1",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.BKASH,
                        "TXN12345", new BigDecimal("1000.00")));

        assertEquals(OrderStatus.PAID_CONFIRMED, result.orderStatus());
        assertEquals(EscrowStatus.ESCROW_HELD, result.escrowStatus());
        assertEquals("PAID", result.paymentStatus());
        assertEquals("House 5, Dhaka", result.deliveryAddress());
        assertEquals("TXN12345", result.transactionId());
        assertEquals("bKash Sandbox", result.paymentGateway());
        assertEquals(new BigDecimal("1000.00"), result.paidAmount());
        assertNotNull(result.paidAt());
        assertTrue(result.trackingActive());
    }

    @Test
    void confirmBuyerPayment_shouldForbidNonBuyer() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.confirmBuyerPayment("stranger",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.CARD,
                        "TXN12345", new BigDecimal("1000.00"))));
        verify(paymentGatewayService, never()).verify(any(), any(), any(), any());
    }

    @Test
    void confirmBuyerPayment_shouldRejectAmountMismatch() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.confirmBuyerPayment("buyer-1",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.NAGAD,
                        "TXN12345", new BigDecimal("10.00"))));
        verify(paymentGatewayService, never()).verify(any(), any(), any(), any());
    }

    @Test
    void confirmBuyerPayment_shouldRejectPaymentBeforeOfferIsAccepted() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.confirmBuyerPayment("buyer-1",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.CARD,
                        "TXN12345", new BigDecimal("1000.00"))));
    }

    @Test
    void confirmBuyerPayment_shouldFlagFailedEscrowWhenGatewayRejects() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(paymentGatewayService.verify(any(), any(), any(), any()))
                .thenReturn(PaymentVerification.failure("SSLCommerz Sandbox", "invalid transaction"));

        assertThrows(BusinessRuleException.class, () -> orderService.confirmBuyerPayment("buyer-1",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.SSLCOMMERZ,
                        "TXN12345", new BigDecimal("1000.00"))));

        assertEquals(EscrowStatus.FAILED, order.getEscrowStatus());
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
        assertFalse(order.isPaymentVerified());
        verify(orderRepository).save(order);
    }

    @Test
    void confirmBuyerPayment_shouldBeIdempotentForSameTransaction() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        order.setTransactionId("TXN12345");
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        OrderResponseRecord result = orderService.confirmBuyerPayment("buyer-1",
                new ConfirmPaymentRequestDTO("order-1", "House 5, Dhaka", PaymentMethod.BKASH,
                        "TXN12345", new BigDecimal("1000.00")));

        assertEquals(EscrowStatus.ESCROW_HELD, result.escrowStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateLiveTracking_shouldStoreDriverCoordinates() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result =
                orderService.updateLiveTracking("order-1", "farmer-1", false, 23.8103d, 90.4125d);

        assertEquals(23.8103d, result.driverLatitude());
        assertEquals(90.4125d, result.driverLongitude());
        assertNotNull(result.trackingUpdatedAt());
        assertTrue(result.trackingActive());
    }

    @Test
    void updateLiveTracking_shouldForbidBeforePaymentIsConfirmed() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateLiveTracking("order-1", "farmer-1", false, 23.8103d, 90.4125d));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateLiveTracking_shouldRejectImpossibleCoordinates() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.IN_TRANSIT);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateLiveTracking("order-1", "farmer-1", false, 130d, 90.4125d));
    }

    // ------------------------------------------------------------------ admin / super admin

    @Test
    void adminUpdateOrderStatus_shouldReleaseEscrowOnDelivery() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.IN_TRANSIT);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        order.setPaidAmount(new BigDecimal("1000.00"));
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result = orderService.adminUpdateOrderStatus("order-1", OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, result.orderStatus());
        assertEquals(EscrowStatus.RELEASED, result.escrowStatus());
        assertEquals("PAID", result.paymentStatus());
    }

    @Test
    void adminUpdateOrderStatus_shouldOverrideAnIllegalTransition() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result = orderService.adminUpdateOrderStatus("order-1", OrderStatus.IN_TRANSIT);

        assertEquals(OrderStatus.IN_TRANSIT, result.orderStatus());
    }

    @Test
    void adminDeleteOrder_shouldRefundEscrowAndSoftDelete() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED);
        order.setEscrowStatus(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseRecord result = orderService.adminDeleteOrder("order-1");

        assertEquals(OrderStatus.CANCELLED, result.orderStatus());
        assertEquals(EscrowStatus.REFUNDED, result.escrowStatus());
        assertTrue(result.deleted());
        assertNotNull(order.getDeletedAt());
        assertEquals("VOID", result.paymentStatus());
    }

    @Test
    void adminDeleteOrder_shouldRefuseASecondDelete() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.CANCELLED);
        order.setDeleted(true);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.adminDeleteOrder("order-1"));
    }

    @Test
    void confirmSimulatedPayment_shouldSettleEscrowWithoutVerification() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        OrderResponseRecord result = orderService.confirmSimulatedPayment("buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.BKASH, "01712345678", "1234", "Gulshan 1, Dhaka"));

        assertEquals(OrderStatus.PAID_CONFIRMED, result.orderStatus());
        assertEquals(EscrowStatus.ESCROW_HELD, result.escrowStatus());
        assertEquals(PaymentMethod.BKASH, order.getPaymentMethod());
        assertEquals(new BigDecimal("1000.00"), order.getPaidAmount());
        assertEquals("Gulshan 1, Dhaka", order.getDeliveryAddress());
        assertFalse(order.isPaymentVerified());
        assertTrue(order.getTransactionId().startsWith("BKS-"));
        assertTrue(order.getPaymentGateway().contains("***5678"));
        verify(invoiceService).syncForOrder(order);
    }

    @Test
    void confirmSimulatedPayment_shouldSettleNagadWithAWalletNumber() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        orderService.confirmSimulatedPayment("buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.NAGAD, "01812345678", "4321", "Dhanmondi, Dhaka"));

        assertEquals(PaymentMethod.NAGAD, order.getPaymentMethod());
        assertEquals(OrderStatus.PAID_CONFIRMED, order.getOrderStatus());
        assertTrue(order.getTransactionId().startsWith("NGD-"));
        assertTrue(order.getPaymentGateway().contains("***5678"));
    }

    @Test
    void confirmSimulatedPayment_shouldSettleCardWithACardNumber() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        orderService.confirmSimulatedPayment("buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.CARD, "4242 4242 4242 4242", "123", "Mirpur, Dhaka"));

        assertEquals(PaymentMethod.CARD, order.getPaymentMethod());
        assertEquals(OrderStatus.PAID_CONFIRMED, order.getOrderStatus());
        assertTrue(order.getTransactionId().startsWith("CRD-"));
        assertTrue(order.getPaymentGateway().contains("***4242"));
    }

    @Test
    void confirmSimulatedPayment_shouldAcceptSpacedSslCommerzCardNumber() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        orderService.confirmSimulatedPayment("buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.SSLCOMMERZ, "4111-1111-1111-1111", "321", "Uttara, Dhaka"));

        assertEquals(PaymentMethod.SSLCOMMERZ, order.getPaymentMethod());
        assertTrue(order.getTransactionId().startsWith("SSL-"));
    }

    @Test
    void confirmSimulatedPayment_shouldRejectAWalletNumberOnACardRail() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        BusinessRuleException problem = assertThrows(BusinessRuleException.class,
                () -> orderService.confirmSimulatedPayment("buyer-1", "order-1",
                        new SimulatedPaymentRequest(PaymentMethod.CARD, "01712345678", "1234", "Dhaka")));

        assertTrue(problem.getMessage().contains("card number"));
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
    }

    @Test
    void confirmSimulatedPayment_shouldRejectACardNumberOnAWalletRail() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        BusinessRuleException problem = assertThrows(BusinessRuleException.class,
                () -> orderService.confirmSimulatedPayment("buyer-1", "order-1",
                        new SimulatedPaymentRequest(PaymentMethod.BKASH, "4242424242424242", "1234", "Dhaka")));

        assertTrue(problem.getMessage().contains("wallet number"));
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
    }

    @Test
    void confirmSimulatedPayment_shouldRejectATooShortPin() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> orderService.confirmSimulatedPayment(
                "buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.BKASH, "01712345678", "12", "Dhaka")));
    }

    @Test
    void confirmSimulatedPayment_shouldNeverKeepThePin() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(false);

        orderService.confirmSimulatedPayment("buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.BKASH, "01712345678", "9876", "Banani, Dhaka"));

        assertFalse(String.valueOf(order.getTransactionId()).contains("9876"));
        assertFalse(String.valueOf(order.getPaymentGateway()).contains("9876"));
        assertFalse(String.valueOf(order.getDeliveryAddress()).contains("9876"));
    }

    @Test
    void confirmSimulatedPayment_shouldForbidOtherBuyer() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.confirmSimulatedPayment(
                "stranger", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.BKASH, "01712345678", "1234", "Dhaka")));
    }

    @Test
    void confirmSimulatedPayment_shouldRefuseWhenServerRequiresVerification() {
        Order order = order("order-1", "buyer-1", "farmer-1", OrderStatus.OFFER_ACCEPTED);
        when(orderRepository.findByIdAndDeletedFalse("order-1")).thenReturn(Optional.of(order));
        when(paymentGatewayService.isVerificationRequired()).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> orderService.confirmSimulatedPayment(
                "buyer-1", "order-1",
                new SimulatedPaymentRequest(PaymentMethod.BKASH, "01712345678", "1234", "Dhaka")));
        assertEquals(OrderStatus.OFFER_ACCEPTED, order.getOrderStatus());
    }

    private Offer offer(String id, String buyerId, String farmerId, String listingId,
                        BigDecimal quantity, BigDecimal price, OfferStatus status) {
        return Offer.builder()
                .id(id)
                .listingId(listingId)
                .farmerId(farmerId)
                .buyerId(buyerId)
                .offeredQuantity(quantity)
                .offeredPrice(price)
                .status(status)
                .build();
    }

    private ProduceListing listing(String id, String cropName) {
        return ProduceListing.builder()
                .id(id)
                .cropName(cropName)
                .category("Grains")
                .availableQuantity(new BigDecimal("5000"))
                .reservedQuantity(BigDecimal.ZERO)
                .unit("kg")
                .pricePerUnit(new BigDecimal("10.00"))
                .location("Punjab")
                .status(ListingStatus.ACTIVE)
                .build();
    }

    private Order order(String id, String buyerId, String farmerId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .buyerId(buyerId)
                .farmerId(farmerId)
                .cropName("Wheat")
                .agreedQuantity(new BigDecimal("100"))
                .agreedPricePerUnit(new BigDecimal("10.00"))
                .totalAmount(new BigDecimal("1000.00"))
                .orderStatus(status)
                .build();
    }

    // ------------------------------------------------------------------ delivered orders leave the commitments list

    @Test
    void getBuyerOrdersPage_shouldExcludeDeliveredOrdersFromTheQuery() {
        when(orderRepository.findByBuyerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                eq("buyer-1"), eq(OrderStatus.DELIVERED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order("order-1", "buyer-1", "farmer-1", OrderStatus.IN_TRANSIT))));

        PageResponse<OrderResponseRecord> page = orderService.getBuyerOrdersPage("buyer-1", null, 0, 5);

        assertEquals(1, page.totalElements());
        assertEquals(OrderStatus.IN_TRANSIT, page.content().get(0).orderStatus());
    }

    @Test
    void getBuyerOrdersPage_shouldReturnNothingWhenDeliveredIsFilteredFor() {
        PageResponse<OrderResponseRecord> page =
                orderService.getBuyerOrdersPage("buyer-1", OrderStatus.DELIVERED, 0, 5);

        assertEquals(0, page.totalElements());
        assertTrue(page.content().isEmpty());
        verify(orderRepository, never())
                .findByBuyerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(anyString(), any(), any());
    }

    @Test
    void getFarmerOrdersPage_shouldExcludeDeliveredOrdersFromTheQuery() {
        when(orderRepository.findByFarmerIdAndDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                eq("farmer-1"), eq(OrderStatus.DELIVERED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order("order-1", "buyer-1", "farmer-1", OrderStatus.PROCESSING))));

        PageResponse<OrderResponseRecord> page = orderService.getFarmerOrdersPage("farmer-1", null, 0, 5);

        assertEquals(1, page.totalElements());
    }

    @Test
    void getAllOrdersForAdmin_shouldExcludeDeliveredOrdersFromTheQuery() {
        when(orderRepository.findByDeletedFalseAndOrderStatusNotOrderByCreatedAtDesc(
                eq(OrderStatus.DELIVERED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order("order-1", "buyer-1", "farmer-1", OrderStatus.PAID_CONFIRMED))));

        PageResponse<OrderResponseRecord> page = orderService.getAllOrdersForAdmin(null, 0, 20);

        assertEquals(1, page.totalElements());
    }

    @Test
    void getBuyerOrders_shouldDropDeliveredOrders() {
        when(orderRepository.findByBuyerId("buyer-1")).thenReturn(List.of(
                order("order-1", "buyer-1", "farmer-1", OrderStatus.DELIVERED),
                order("order-2", "buyer-1", "farmer-1", OrderStatus.IN_TRANSIT)));

        List<OrderResponseRecord> orders = orderService.getBuyerOrders("buyer-1");

        assertEquals(1, orders.size());
        assertEquals("order-2", orders.get(0).id());
    }
}