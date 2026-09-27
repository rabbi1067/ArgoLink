package com.agrolink.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustStockRecord(
        @NotNull(message = "New available quantity is required")
        @DecimalMin(value = "0.0", message = "Available quantity cannot be negative")
        BigDecimal newAvailableQuantity
) {
}