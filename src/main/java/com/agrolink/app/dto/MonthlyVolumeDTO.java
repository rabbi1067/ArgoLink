package com.agrolink.app.dto;

import java.util.List;

public record MonthlyVolumeDTO(
        List<String> labels,
        List<Long> data
) {
}