package com.agrolink.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record SupplyDemandDTO(
        List<String> labels,
        List<BigDecimal> supply,
        List<BigDecimal> demand
) {
    public static SupplyDemandDTO of(List<String> labels, List<BigDecimal> supply, List<BigDecimal> demand) {
        return new SupplyDemandDTO(labels, supply, demand);
    }
}