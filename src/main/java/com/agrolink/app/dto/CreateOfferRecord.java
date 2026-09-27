package com.agrolink.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOfferRecord(
        @NotNull(message = "Listing id is required")
        String listingId,

        String requestId,

        @NotNull(message = "Offered quantity is required")
        @DecimalMin(value = "0.01", message = "Offered quantity must be greater than zero")
        BigDecimal offeredQuantity,

        @NotNull(message = "Offered price is required")
        @DecimalMin(value = "0.01", message = "Offered price must be greater than zero")
        BigDecimal offeredPrice
) {
}