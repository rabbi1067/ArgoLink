package com.agrolink.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;


public record ProduceListingRequest(
        @NotBlank(message = "Crop name is required")
        @Size(max = 100, message = "Crop name must be at most 100 characters")
        String cropName,

        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category must be at most 60 characters")
        String category,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "Available quantity is required")
        @DecimalMin(value = "0.01", message = "Available quantity must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Available quantity has too many digits")
        BigDecimal availableQuantity,

        @NotBlank(message = "Unit is required")
        @Size(max = 20, message = "Unit must be at most 20 characters")
        String unit,

        @NotNull(message = "Price per unit is required")
        @DecimalMin(value = "0.01", message = "Price per unit must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Price has too many digits")
        BigDecimal pricePerUnit,

        @Size(max = 500, message = "Image URL is too long")
        String imageUrl,

        @NotBlank(message = "District is required")
        @Size(max = 60, message = "District must be at most 60 characters")
        String district,

        @Size(max = 120, message = "Location detail must be at most 120 characters")
        String location,

        LocalDate harvestDate) {
}