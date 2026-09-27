package com.agrolink.app.model;

import java.time.LocalDate;

public record ForecastPoint(
        LocalDate date,
        double temperatureMaxC,
        double temperatureMinC,
        int precipitationChance,
        double precipitationMm,
        int weatherCode,
        String summary) {
}
