package com.agrolink.app.dto;

import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.ProduceListing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProduceListingResponse(
        String id,
        String farmerId,
        String farmerName,
        String cropName,
        String category,
        String description,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal remainingQuantity,
        String unit,
        BigDecimal pricePerUnit,
        String imageUrl,
        String location,
        String district,
        String division,
        LocalDate harvestDate,
        ListingStatus status,
        Instant createdAt) {

    public static ProduceListingResponse from(ProduceListing listing, String farmerName) {
        BigDecimal available = listing.getAvailableQuantity() == null ? BigDecimal.ZERO : listing.getAvailableQuantity();
        BigDecimal reserved = listing.getReservedQuantity() == null ? BigDecimal.ZERO : listing.getReservedQuantity();
        BigDecimal remaining = available.subtract(reserved).max(BigDecimal.ZERO);
        return new ProduceListingResponse(
                listing.getId(),
                listing.getFarmerId(),
                farmerName,
                listing.getCropName(),
                listing.getCategory(),
                listing.getDescription(),
                available,
                reserved,
                remaining,
                listing.getUnit(),
                listing.getPricePerUnit(),
                listing.getImageUrl(),
                listing.getLocation(),
                listing.getDistrict(),
                listing.getDivision(),
                listing.getHarvestDate(),
                listing.getStatus(),
                listing.getCreatedAt());
    }
}