package com.agrolink.app.dto;
public record MetricCardDTO(
        String key,
        String label,
        String value,
        String hint,
        String icon,
        String tone,
        String badge,
        Double trendPercent,
        String trendCaption) {

    public static MetricCardDTO of(String key, String label, String value, String icon) {
        return new MetricCardDTO(key, label, value, null, icon, "neutral", null, null, null);
    }

    public MetricCardDTO withHint(String hint) {
        return new MetricCardDTO(key, label, value, hint, icon, tone, badge, trendPercent, trendCaption);
    }

    public MetricCardDTO withTone(String tone) {
        return new MetricCardDTO(key, label, value, hint, icon, tone, badge, trendPercent, trendCaption);
    }

    public MetricCardDTO withBadge(String badge) {
        return new MetricCardDTO(key, label, value, hint, icon, tone, badge, trendPercent, trendCaption);
    }

    /**
     * Attaches a real period-over-period change. {@code current} and {@code previous}
     * are the measured values for the two windows; a null {@code previous} means there
     * is nothing to compare against and no badge is produced.
     */
    public MetricCardDTO withTrend(BigDecimalPair current, BigDecimalPair previous, String caption) {
        if (current == null || previous == null || previous.value().signum() == 0) {
            return this;
        }
        double change = current.value().subtract(previous.value())
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(previous.value(), 4, java.math.RoundingMode.HALF_UP)
                .doubleValue();
        Double rounded = Math.round(change * 10d) / 10d;
        return new MetricCardDTO(key, label, value, hint, icon, tone, badge, rounded, caption);
    }

    /** A measured value that a trend can be derived from. */
    public record BigDecimalPair(java.math.BigDecimal value, boolean comparable) {

        public static BigDecimalPair of(java.math.BigDecimal value) {
            return new BigDecimalPair(value == null ? java.math.BigDecimal.ZERO : value, true);
        }

        public static BigDecimalPair none() {
            return new BigDecimalPair(java.math.BigDecimal.ZERO, false);
        }
    }
}
