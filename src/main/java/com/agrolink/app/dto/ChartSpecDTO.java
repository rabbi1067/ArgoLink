package com.agrolink.app.dto;

import java.util.List;
public record ChartSpecDTO(
        String key,
        String title,
        String subtitle,
        String type,
        List<String> labels,
        List<ChartDatasetDTO> datasets,
        int height,
        boolean currency,
        String unitLabel,
        Integer highlightIndex) {

    public static ChartSpecDTO of(String key, String title, String subtitle, String type,
                                  List<String> labels, List<ChartDatasetDTO> datasets) {
        return new ChartSpecDTO(key, title, subtitle, type, labels, datasets, 280, false, null, null);
    }

    public ChartSpecDTO withHeight(int height) {
        return new ChartSpecDTO(key, title, subtitle, type, labels, datasets, height, currency, unitLabel, highlightIndex);
    }

    public ChartSpecDTO withCurrency(boolean currency) {
        return new ChartSpecDTO(key, title, subtitle, type, labels, datasets, height, currency, unitLabel, highlightIndex);
    }

    public ChartSpecDTO withUnit(String unitLabel) {
        return new ChartSpecDTO(key, title, subtitle, type, labels, datasets, height, currency, unitLabel, highlightIndex);
    }

    /** Emphasises one bar, e.g. the best month in a revenue chart. */
    public ChartSpecDTO highlight(int index) {
        return new ChartSpecDTO(key, title, subtitle, type, labels, datasets, height, currency, unitLabel, index);
    }

    /** True when there is nothing to plot, so the UI can show a proper empty state. */
    public boolean isEmpty() {
        if (datasets == null || datasets.isEmpty() || labels == null || labels.isEmpty()) {
            return true;
        }
        return datasets.stream().allMatch(d -> d.data() == null
                || d.data().isEmpty()
                || d.data().stream().allMatch(v -> v == null || v.doubleValue() == 0d));
    }
}
