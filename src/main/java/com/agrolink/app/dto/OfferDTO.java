package com.agrolink.app.dto;

import com.agrolink.app.model.Offer;
import com.agrolink.app.model.OfferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferDTO(
        String id,
        String requestId,
        String listingId,
        String farmerId,
        String buyerId,
        BigDecimal offeredQuantity,
        BigDecimal offeredPrice,
        OfferStatus status,
        Instant createdAt
) {
    public static OfferDTO from(Offer offer) {
        return new OfferDTO(
                offer.getId(),
                offer.getRequestId(),
                offer.getListingId(),
                offer.getFarmerId(),
                offer.getBuyerId(),
                offer.getOfferedQuantity(),
                offer.getOfferedPrice(),
                offer.getStatus(),
                offer.getCreatedAt()
        );
    }
}