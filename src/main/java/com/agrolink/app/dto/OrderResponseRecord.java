package com.agrolink.app.dto;

import com.agrolink.app.model.EscrowStatus;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponseRecord(
        String id,
        String offerId,
        String buyerId,
        String farmerId,
        String cropName,
        BigDecimal agreedQuantity,
        BigDecimal agreedPricePerUnit,
        BigDecimal totalAmount,
        OrderStatus orderStatus,
        String deliveryAddress,
        String paymentStatus,
        Instant createdAt,
        PaymentMethod paymentMethod,
        String paymentMethodLabel,
        String transactionId,
        String paymentGateway,
        boolean paymentVerified,
        BigDecimal paidAmount,
        EscrowStatus escrowStatus,
        String escrowStatusLabel,
        Instant paidAt,
        Double driverLatitude,
        Double driverLongitude,
        boolean trackingActive,
        Instant trackingUpdatedAt,
        boolean deleted
) {
    public static OrderResponseRecord from(Order order) {
        return new OrderResponseRecord(
                order.getId(),
                order.getOfferId(),
                order.getBuyerId(),
                order.getFarmerId(),
                order.getCropName(),
                order.getAgreedQuantity(),
                order.getAgreedPricePerUnit(),
                order.getTotalAmount(),
                order.getOrderStatus(),
                order.getDeliveryAddress(),
                paymentStatusFor(order),
                order.getCreatedAt(),
                order.getPaymentMethod(),
                order.getPaymentMethod() == null ? null : order.getPaymentMethod().displayName(),
                order.getTransactionId(),
                order.getPaymentGateway(),
                order.isPaymentVerified(),
                order.getPaidAmount(),
                order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus(),
                (order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus()).displayName(),
                order.getPaidAt(),
                order.getDriverLatitude(),
                order.getDriverLongitude(),
                order.getOrderStatus() != null && order.getOrderStatus().isLiveTrackingStage(),
                order.getTrackingUpdatedAt(),
                order.isDeleted()
        );
    }


    public static String paymentStatusFor(Order order) {
        EscrowStatus escrow = order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus();
        return switch (escrow) {
            case ESCROW_HELD, RELEASED -> "PAID";
            case REFUNDED, FAILED -> "VOID";
            case NONE -> order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.DISPUTED
                    ? "VOID" : "UNPAID";
        };
    }
}
