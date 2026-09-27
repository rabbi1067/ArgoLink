package com.agrolink.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record WeatherForecastDTO(
        String district,
        String division,
        BigDecimal latitude,
        BigDecimal longitude,
        int days,
        String units,
        Current current,
        List<DailyForecastDTO> daily,
        Instant generatedAt,
        String provider,
        boolean fallback,
        boolean stale,
        String disclaimer) {

    public record Current(
            Double temperatureC,
            Double apparentTemperatureC,
            Double windKmh,
            Double windGustKmh,
            Integer windDirectionDeg,
            Double precipitationMm,
            Double rainMm,
            Double humidityPct,
            Double cloudCoverPct,
            Double pressureHpa,
            Double snowfallCm,
            Integer weatherCode,
            String description,
            String icon,
            Boolean day,
            String observedAt,
            String sunrise,
            String sunset) {

        public String windDirectionLabel() {
            if (windDirectionDeg == null) {
                return null;
            }
            String[] points = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
            int index = (int) Math.round((((windDirectionDeg % 360) + 360) % 360) / 22.5) % 16;
            return points[index];
        }
    }
}
