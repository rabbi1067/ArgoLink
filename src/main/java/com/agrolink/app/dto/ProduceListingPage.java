package com.agrolink.app.dto;

import java.util.List;

public record ProduceListingPage(
        List<ProduceListingResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}