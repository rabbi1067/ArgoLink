package com.agrolink.app.service;

import com.agrolink.app.model.PaymentMethod;

import java.math.BigDecimal;


public interface PaymentGatewayService {


    PaymentVerification verify(PaymentMethod method, String transactionId, BigDecimal expectedAmount, String reference);

    boolean isSslCommerzConfigured();
    boolean isVerificationRequired();


    record PaymentVerification(boolean verified, String gateway, boolean live, String message) {

        public static PaymentVerification success(String gateway, boolean live, String message) {
            return new PaymentVerification(true, gateway, live, message);
        }

        public static PaymentVerification failure(String gateway, String message) {
            return new PaymentVerification(false, gateway, false, message);
        }
    }
}
