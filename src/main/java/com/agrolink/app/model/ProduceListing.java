package com.agrolink.app.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "produce_listings")
@CompoundIndexes({
        @CompoundIndex(name = "listings_category_status", def = "{'category': 1, 'status': 1}"),
        @CompoundIndex(name = "listings_district_status", def = "{'district': 1, 'status': 1}"),
        @CompoundIndex(name = "listings_division_status", def = "{'division': 1, 'status': 1}")
})
public class ProduceListing {

    @Id
    private String id;

    @Indexed
    private String farmerId;

    @NotBlank(message = "Crop name is required")
    private String cropName;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Available quantity is required")
    @DecimalMin(value = "0.0", message = "Available quantity cannot be negative")
    private BigDecimal availableQuantity;

    @DecimalMin(value = "0.0", message = "Reserved quantity cannot be negative")
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Price per unit is required")
    @DecimalMin(value = "0.0", message = "Price per unit cannot be negative")
    private BigDecimal pricePerUnit;

    @NotBlank(message = "Location is required")
    private String location;

    private LocalDate harvestDate;

    @Indexed
    @Builder.Default
    private ListingStatus status = ListingStatus.DRAFT;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    // ---- added fields (nullable, so existing documents keep working) ----

    @Size(max = 1000)
    private String description;

    @Size(max = 500)
    private String imageUrl;

    private String imagePublicId;

    private String imageFormat;

    @Indexed
    private String district;

    private String division;
}