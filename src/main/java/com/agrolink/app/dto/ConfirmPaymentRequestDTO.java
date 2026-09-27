package com.agrolink.app.dto;

import com.agrolink.app.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConfirmPaymentRequestDTO(
        @NotBlank(message = "Order id is required")
        String orderId,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotBlank(message = "Transaction id is required")
        String transactionId,

        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
        BigDecimal totalAmount
) {
}
