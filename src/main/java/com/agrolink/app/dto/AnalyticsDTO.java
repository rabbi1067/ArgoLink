package com.agrolink.app.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record AnalyticsDTO(
        long totalListings,
        long activeListings,
        double activeRatioPercentage,
        long totalUsers,
        long totalCategories,
        long totalOrders,
        BigDecimal averagePricePerUnit,
        Instant generatedAt
) {
    public static AnalyticsDTO of(long totalListings,
                                  long activeListings,
                                  long totalUsers,
                                  long totalCategories,
                                  long totalOrders,
                                  BigDecimal averagePricePerUnit) {
        double activeRatio = totalListings == 0 ? 0.0
                : BigDecimal.valueOf((double) activeListings / totalListings * 100.0)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
        return new AnalyticsDTO(
                totalListings,
                activeListings,
                activeRatio,
                totalUsers,
                totalCategories,
                totalOrders,
                averagePricePerUnit,
                Instant.now()
        );
    }
}