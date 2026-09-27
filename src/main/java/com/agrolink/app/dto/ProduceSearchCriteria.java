package com.agrolink.app.dto;

import java.math.BigDecimal;
import java.util.Set;

public record ProduceSearchCriteria(
        String q,
        String category,
        String district,
        String division,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minQuantity,
        String sort,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;
    private static final Set<String> SORTS = Set.of("newest", "price_asc", "price_desc", "quantity_desc");

    public ProduceSearchCriteria normalized() {
        BigDecimal min = nonNegative(minPrice);
        BigDecimal max = nonNegative(maxPrice);
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        String cleanSort = sort == null ? "newest" : sort.trim().toLowerCase();
        if (!SORTS.contains(cleanSort)) {
            cleanSort = "newest";
        }
        int cleanSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new ProduceSearchCriteria(
                blankToNull(q), blankToNull(category), blankToNull(district), blankToNull(division),
                min, max, nonNegative(minQuantity), cleanSort, Math.max(page, 0), cleanSize);
    }

    public String cacheKey() {
        ProduceSearchCriteria n = normalized();
        return String.join("|",
                lower(n.q), lower(n.category), lower(n.district), lower(n.division),
                plain(n.minPrice), plain(n.maxPrice), plain(n.minQuantity),
                n.sort, String.valueOf(n.page), String.valueOf(n.size));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? null : value;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}