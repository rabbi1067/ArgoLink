package com.agrolink.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptOfferRecord(
        @NotNull(message = "Offer id is required")
        String offerId,
        String deliveryAddress
) {
}
