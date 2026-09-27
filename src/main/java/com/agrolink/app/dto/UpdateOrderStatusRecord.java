package com.agrolink.app.dto;

import com.agrolink.app.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRecord(
        @NotNull(message = "New status is required")
        OrderStatus status
) {
}