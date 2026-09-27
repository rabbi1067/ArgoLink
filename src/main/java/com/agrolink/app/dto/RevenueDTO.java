package com.agrolink.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueDTO(
        List<String> labels,
        List<BigDecimal> data
) {
}