package com.agrolink.app.dto;

import java.math.BigDecimal;

public record BkashCheckoutResponse(
        String orderId,
        String paymentId,
        String bkashUrl,
        BigDecimal amount,
        String currency,
        String merchantInvoiceNumber,
        String instructions) {

    public static BkashCheckoutResponse of(String orderId, String paymentId, String bkashUrl, BigDecimal amount) {
        return new BkashCheckoutResponse(orderId, paymentId, bkashUrl, amount, "BDT", orderId,
                "Enter your bKash number and PIN on the bKash page, then return here.");
    }
}
