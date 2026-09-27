package com.agrolink.app.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "offers")
@CompoundIndex(name = "offers_listing_status", def = "{'listingId': 1, 'status': 1}")
public class Offer {

    @Id
    private String id;

    @Indexed
    private String requestId;

    @Indexed
    private String listingId;

    @Indexed
    private String farmerId;

    @Indexed
    private String buyerId;

    @NotNull(message = "Offered quantity is required")
    @DecimalMin(value = "0.01", message = "Offered quantity must be greater than zero")
    private BigDecimal offeredQuantity;

    @NotNull(message = "Offered price is required")
    @DecimalMin(value = "0.0", message = "Offered price cannot be negative")
    private BigDecimal offeredPrice;

    private OfferStatus status = OfferStatus.OFFERED;

    @CreatedDate
    private Instant createdAt;
}