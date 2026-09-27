package com.agrolink.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    BKASH("bKash"),
    NAGAD("Nagad"),
    CARD("Card"),
    SSLCOMMERZ("SSLCommerz");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static PaymentMethod from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String needle = value.trim();
        for (PaymentMethod method : values()) {
            if (method.name().equalsIgnoreCase(needle) || method.displayName.equalsIgnoreCase(needle)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported payment method: " + value);
    }
}
