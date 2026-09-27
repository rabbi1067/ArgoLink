package com.agrolink.app.dto;

import java.time.LocalDate;
public record DailyForecastDTO(
        LocalDate date,
        Double tempMaxC,
        Double tempMinC,
        Double apparentMaxC,
        Double apparentMinC,
        Integer rainProbabilityPct,
        Double rainSumMm,
        Double precipitationHours,
        Double windMaxKmh,
        Double windGustMaxKmh,
        Integer windDirectionDeg,
        Double snowfallCm,
        Double uvIndexMax,
        Double sunshineHours,
        Double solarRadiationMjM2,
        Integer weatherCode,
        String description,
        String icon,
        String sunrise,
        String sunset,
        String advisoryLabel,
        String advisoryLevel) {
}
