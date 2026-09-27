package com.agrolink.app.dto;

import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.ProduceListing;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProduceListingDTO(
        String id,

        String farmerId,

        @NotBlank(message = "Crop name is required")
        String cropName,

        @NotBlank(message = "Category is required")
        String category,

        @NotNull(message = "Available quantity is required")
        @DecimalMin(value = "0.01", message = "Available quantity must be greater than zero")
        BigDecimal availableQuantity,

        @DecimalMin(value = "0.0", message = "Reserved quantity cannot be negative")
        BigDecimal reservedQuantity,

        @NotBlank(message = "Unit is required")
        String unit,

        @NotNull(message = "Price per unit is required")
        @DecimalMin(value = "0.01", message = "Price per unit must be greater than zero")
        BigDecimal pricePerUnit,

        @NotBlank(message = "Location is required")
        String location,

        String imagePublicId,

        String imageFormat,

        String imageUrl,

        LocalDate harvestDate,

        ListingStatus status,

        Long version,

        Instant createdAt
) {
    public static ProduceListingDTO from(ProduceListing listing) {
        return new ProduceListingDTO(
                listing.getId(),
                listing.getFarmerId(),
                listing.getCropName(),
                listing.getCategory(),
                listing.getAvailableQuantity(),
                listing.getReservedQuantity(),
                listing.getUnit(),
                listing.getPricePerUnit(),
                listing.getLocation(),
                listing.getImagePublicId(),
                listing.getImageFormat(),
                listing.getImageUrl(),
                listing.getHarvestDate(),
                listing.getStatus(),
                listing.getVersion(),
                listing.getCreatedAt()
        );
    }

    public static ProduceListingDTO withSignedImage(ProduceListingDTO dto, String signedImageUrl) {
        return new ProduceListingDTO(
                dto.id(),
                dto.farmerId(),
                dto.cropName(),
                dto.category(),
                dto.availableQuantity(),
                dto.reservedQuantity(),
                dto.unit(),
                dto.pricePerUnit(),
                dto.location(),
                dto.imagePublicId(),
                dto.imageFormat(),
                signedImageUrl,
                dto.harvestDate(),
                dto.status(),
                dto.version(),
                dto.createdAt()
        );
    }

    public ProduceListing toEntity() {
        return ProduceListing.builder()
                .farmerId(farmerId)
                .cropName(cropName)
                .category(category)
                .availableQuantity(availableQuantity)
                .reservedQuantity(reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity)
                .unit(unit)
                .pricePerUnit(pricePerUnit)
                .location(location)
                .imagePublicId(imagePublicId)
                .imageFormat(imageFormat)
                .imageUrl(imageUrl)
                .harvestDate(harvestDate)
                .status(status == null ? ListingStatus.DRAFT : status)
                .version(version)
                .build();
    }
}