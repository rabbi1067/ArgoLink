package com.agrolink.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record BkashCheckoutRequest(
        @NotBlank(message = "Shipping address is required")
        @Size(max = 300, message = "Shipping address is too long")
        String shippingAddress) {
}
