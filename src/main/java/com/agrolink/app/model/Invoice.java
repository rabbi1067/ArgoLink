package com.agrolink.app.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invoices")
@CompoundIndex(name = "invoices_order", def = "{'orderId': 1}")
public class Invoice {

    @Id
    private String id;

    @Indexed(unique = true)
    private String invoiceNumber;

    @Indexed(name = "invoices_order")
    private String orderId;

    private String buyerId;   // Bill to
    private String farmerId;  // Bill from

    private String buyerName;
    private String farmerName     = "AgroLink Verified Farmer";
    private String buyerAddress;
    private String cropName;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.0", message = "Unit price cannot be negative")
    private BigDecimal unitPriceBt;

    private BigDecimal subtotalBt;
    private BigDecimal deliveryChargeBt;
    private BigDecimal taxBt;
    private BigDecimal discountBt;
    private BigDecimal totalBt;

    private String paymentStatus;

    private String deliveryAddress;

    private Instant dueDate;

    @Indexed
    private String verificationToken;

    private String qrCodeUrl;

    private String imageUrl;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
