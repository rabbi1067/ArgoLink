package com.agrolink.app.util;

public final class WeatherCodes {

    public static final String LEVEL_ALERT = "ALERT";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_GOOD = "GOOD";
    public static final String LEVEL_INFO = "INFO";

    private WeatherCodes() {
    }

    /** Human label for a WMO code. */
    public static String describe(Integer code) {
        if (code == null) {
            return "Variable";
        }
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Variable";
        };
    }

    public static String icon(Integer code, boolean day) {
        if (code == null) {
            return "cloud";
        }
        return switch (code) {
            case 0 -> day ? "sun" : "moon";
            case 1 -> day ? "sun-cloud" : "cloud-moon";
            case 2 -> day ? "sun-cloud" : "cloud-moon";
            case 3 -> "cloud";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57 -> "drizzle";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "rain";
            case 71, 73, 75, 77, 85, 86 -> "snow";
            case 95, 96, 99 -> "storm";
            default -> "cloud";
        };
    }


    public static Advisory advisory(Double rainSumMm, Double windMaxKmh, Double tempMaxC, Integer weatherCode) {
        if (rainSumMm == null && tempMaxC == null) {
            return new Advisory(null, null);
        }
        if ((weatherCode != null && weatherCode >= 95) || (windMaxKmh != null && windMaxKmh >= 40)) {
            return new Advisory("Storm alert - secure stored produce and delay field work", LEVEL_ALERT);
        }
        if (rainSumMm != null && rainSumMm > 5) {
            return new Advisory("Rain warning - drying and harvest windows will slip", LEVEL_WARNING);
        }
        if (tempMaxC != null && tempMaxC >= 36) {
            return new Advisory("Heat stress - irrigate in the early morning or evening", LEVEL_WARNING);
        }
        if (rainSumMm != null && rainSumMm <= 1) {
            return new Advisory("Good harvesting and drying weather", LEVEL_GOOD);
        }
        return new Advisory("Light rain likely - plan spraying and harvest around it", LEVEL_INFO);
    }


    public record Advisory(String label, String level) {

        public boolean present() {
            return label != null;
        }
    }
}
