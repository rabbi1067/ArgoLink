package com.agrolink.app.dto;

import java.util.List;

public record CropDistributionDTO(
        String cropName,
        long count
) {
    public static CropDistributionDTO of(String cropName, long count) {
        return new CropDistributionDTO(cropName, count);
    }
}