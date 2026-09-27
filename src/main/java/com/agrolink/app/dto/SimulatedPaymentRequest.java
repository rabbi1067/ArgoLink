package com.agrolink.app.dto;

import com.agrolink.app.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SimulatedPaymentRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotBlank(message = "Account number is required")
        String number,

        @NotBlank(message = "PIN is required")
        String pin,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress
) {
    private static final java.util.regex.Pattern MOBILE_WALLET = java.util.regex.Pattern.compile("^01[3-9][0-9]{8}$");
    private static final java.util.regex.Pattern CARD_NUMBER = java.util.regex.Pattern.compile("^[0-9]{13,19}$");

    public boolean expectsMobileWalletNumber() {
        return paymentMethod == PaymentMethod.BKASH || paymentMethod == PaymentMethod.NAGAD;
    }

    public String numberError() {
        if (number == null || number.isBlank() || paymentMethod == null) {
            return "Account number is required";
        }
        String digits = number.replaceAll("[\\s-]", "");
        if (expectsMobileWalletNumber()) {
            return MOBILE_WALLET.matcher(digits).matches()
                    ? null
                    : "Enter a valid wallet number, e.g. 01712345678";
        }
        return CARD_NUMBER.matcher(digits).matches()
                ? null
                : "Enter a valid card number (13 to 19 digits)";
    }

    public String pinError() {
        if (pin == null || pin.isBlank()) {
            return "PIN is required";
        }
        String pattern = expectsMobileWalletNumber() ? "^[0-9]{4,6}$" : "^[0-9]{3,6}$";
        return pin.matches(pattern)
                ? null
                : expectsMobileWalletNumber()
                        ? "PIN must be 4 to 6 digits"
                        : "CVV must be 3 to 6 digits";
    }

    public String digitsOnly() {
        return number == null ? "" : number.replaceAll("[\\s-]", "");
    }

    public String maskedNumber() {
        if (number == null) {
            return paymentMethod == null ? "" : paymentMethod.name();
        }
        String digits = digitsOnly();
        String tail = digits.length() < 4 ? digits : digits.substring(digits.length() - 4);
        return paymentMethod + " ***" + tail;
    }

    public String reference() {
        String prefix = switch (paymentMethod) {
            case BKASH -> "BKS";
            case NAGAD -> "NGD";
            case CARD -> "CRD";
            case SSLCOMMERZ -> "SSL";
        };
        return prefix + "-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID()
                .toString().substring(0, 6).toUpperCase();
    }
}
