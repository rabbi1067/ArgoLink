package com.agrolink.app.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "purchase_requests")
@CompoundIndex(name = "purchase_requests_buyer_status", def = "{'buyerId': 1, 'status': 1}")
public class PurchaseRequest {

    @Id
    private String id;

    @Indexed
    private String buyerId;

    @NotBlank(message = "Crop name is required")
    private String cropName;

    @NotNull(message = "Required quantity is required")
    @DecimalMin(value = "0.01", message = "Required quantity must be greater than zero")
    private BigDecimal requiredQuantity;

    private String deliveryLocation;

    private LocalDate targetDate;

    @Indexed
    private PurchaseRequestStatus status = PurchaseRequestStatus.PENDING;

    @CreatedDate
    private Instant createdAt;
}