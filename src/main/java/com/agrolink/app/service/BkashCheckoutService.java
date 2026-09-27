package com.agrolink.app.service;

import com.agrolink.app.service.PaymentGatewayService.PaymentVerification;

import java.math.BigDecimal;

public interface BkashCheckoutService {

    boolean isConfigured();

    String callbackBaseUrl();

    CheckoutSession createCheckout(String orderId, BigDecimal amount);

    PaymentVerification verifyPayment(String paymentId, String orderId, BigDecimal expectedAmount);

    record CheckoutSession(String paymentId, String bkashUrl) {
    }
}
