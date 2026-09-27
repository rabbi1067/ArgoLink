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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
@CompoundIndex(name = "orders_buyer_status", def = "{'buyerId': 1, 'orderStatus': 1}")
@CompoundIndex(name = "orders_status_created", def = "{'orderStatus': 1, 'createdAt': -1}")
public class Order {

    @Id
    private String id;

    @Indexed
    private String offerId;

    @Indexed
    private String buyerId;

    @Indexed
    private String farmerId;

    @NotBlank(message = "Crop name is required")
    private String cropName;

    @NotNull(message = "Agreed quantity is required")
    @DecimalMin(value = "0.01", message = "Agreed quantity must be greater than zero")
    private BigDecimal agreedQuantity;

    @NotNull(message = "Agreed price per unit is required")
    @DecimalMin(value = "0.0", message = "Agreed price per unit cannot be negative")
    private BigDecimal agreedPricePerUnit;

    private BigDecimal totalAmount;

    @Indexed
    private OrderStatus orderStatus = OrderStatus.PENDING;

    private String deliveryAddress;

    private PaymentMethod paymentMethod;

    @Indexed
    private String transactionId;

    private String paymentGateway;

    private boolean paymentVerified;

    @DecimalMin(value = "0.0", message = "Paid amount cannot be negative")
    private BigDecimal paidAmount;

    @Builder.Default
    private EscrowStatus escrowStatus = EscrowStatus.NONE;

    private Instant paidAt;

    private Double driverLatitude;

    private Double driverLongitude;

    private Instant trackingUpdatedAt;

    private boolean deleted;

    private Instant deletedAt;

    @CreatedDate
    private Instant createdAt;

}
