package com.agrolink.app.service;

import com.agrolink.app.dto.AcceptOfferRecord;
import com.agrolink.app.dto.BkashCheckoutRequest;
import com.agrolink.app.dto.BkashCheckoutResponse;
import com.agrolink.app.dto.ConfirmPaymentRequestDTO;
import com.agrolink.app.dto.SimulatedPaymentRequest;
import com.agrolink.app.dto.OrderResponseRecord;
import com.agrolink.app.dto.OrderStatsRecord;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.Role;

import java.util.List;

public interface OrderService {

    OrderResponseRecord acceptOffer(String userId, AcceptOfferRecord request);

    OrderResponseRecord getOrderById(String orderId, String userId, boolean isAdmin);

    List<OrderResponseRecord> getBuyerOrders(String buyerId);

    List<OrderResponseRecord> getFarmerOrders(String farmerId);

    List<OrderResponseRecord> getOrdersByStatus(OrderStatus status);

    OrderResponseRecord updateOrderStatus(String orderId, String userId, boolean isAdmin, OrderStatus newStatus);

    PageResponse<OrderResponseRecord> getBuyerOrdersPage(String buyerId, OrderStatus statusFilter, int page, int size);

    PageResponse<OrderResponseRecord> getFarmerOrdersPage(String farmerId, OrderStatus statusFilter, int page, int size);


    OrderResponseRecord acceptOfferByFarmer(String orderId, String farmerId);

    OrderResponseRecord confirmBuyerPayment(String buyerId, ConfirmPaymentRequestDTO request);

    OrderResponseRecord updateLiveTracking(String orderId, String actorId, boolean isAdmin, Double latitude, Double longitude);


    BkashCheckoutResponse createBkashCheckout(String buyerId, String orderId, BkashCheckoutRequest request);


    OrderResponseRecord confirmSimulatedPayment(String buyerId, String orderId,
                                                SimulatedPaymentRequest request);


    OrderResponseRecord confirmBkashCallback(String orderId, String paymentId);

    PaymentCapabilities getPaymentCapabilities();

    OrderStatsRecord getOrderStats(String userId, Role role);
    record PaymentCapabilities(boolean bkashConfigured, boolean sslCommerzConfigured,
                               boolean requireVerification, String callbackBaseUrl) {
    }

    PageResponse<OrderResponseRecord> getAllOrdersForAdmin(OrderStatus statusFilter, int page, int size);

    OrderResponseRecord adminUpdateOrderStatus(String orderId, OrderStatus newStatus);

    OrderResponseRecord adminDeleteOrder(String orderId);
}
