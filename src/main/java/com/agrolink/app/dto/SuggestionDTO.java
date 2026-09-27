package com.agrolink.app.dto;

import java.math.BigDecimal;

/**
 * One "AI Suggest" card for a buyer. Every number is computed from live
 * database data: platform-wide ordered quantity, the crop's repeat-buyer
 * rate, and the actual listing price. No ratings exist in the system, so
 * none are shown.
 */
public record SuggestionDTO(
        String badge,
        String listingId,
        String cropName,
        String category,
        String imageUrl,
        BigDecimal pricePerUnit,
        String unit,
        String location,
        BigDecimal soldQuantity,
        int repurchaseRatePct
) {
}
