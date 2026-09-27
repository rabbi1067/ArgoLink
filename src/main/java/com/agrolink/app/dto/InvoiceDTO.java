package com.agrolink.app.dto;

import com.agrolink.app.model.Invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceDTO(
        String id,
        String invoiceNumber,
        String orderId,
        String buyerId,
        String farmerId,
        String buyerName,
        String farmerName,
        String buyerAddress,
        String cropName,
        BigDecimal quantity,
        BigDecimal unitPriceBt,
        BigDecimal subtotalBt,
        BigDecimal deliveryChargeBt,
        BigDecimal taxBt,
        BigDecimal discountBt,
        BigDecimal totalBt,
        String paymentStatus,
        String deliveryAddress,
        Instant dueDate,
        String qrCodeUrl,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceDTO from(Invoice invoice) {
        return new InvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getOrderId(),
                invoice.getBuyerId(),
                invoice.getFarmerId(),
                invoice.getBuyerName(),
                invoice.getFarmerName(),
                invoice.getBuyerAddress(),
                invoice.getCropName(),
                invoice.getQuantity(),
                invoice.getUnitPriceBt(),
                invoice.getSubtotalBt(),
                invoice.getDeliveryChargeBt(),
                invoice.getTaxBt(),
                invoice.getDiscountBt(),
                invoice.getTotalBt(),
                invoice.getPaymentStatus(),
                invoice.getDeliveryAddress(),
                invoice.getDueDate(),
                invoice.getQrCodeUrl(),
                invoice.getImageUrl(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}