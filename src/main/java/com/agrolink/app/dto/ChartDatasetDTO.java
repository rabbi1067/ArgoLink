package com.agrolink.app.dto;

import java.util.List;

public record ChartDatasetDTO(
        String label,
        List<Number> data,
        String color,
        boolean fill,
        String stack,
        String axis) {

    public static ChartDatasetDTO of(String label, List<Number> data) {
        return new ChartDatasetDTO(label, data, null, false, null, null);
    }

    public ChartDatasetDTO withColor(String color) {
        return new ChartDatasetDTO(label, data, color, fill, stack, axis);
    }

    public ChartDatasetDTO withFill(boolean fill) {
        return new ChartDatasetDTO(label, data, color, fill, stack, axis);
    }

    public ChartDatasetDTO withStack(String stack) {
        return new ChartDatasetDTO(label, data, color, fill, stack, axis);
    }

    public ChartDatasetDTO withAxis(String axis) {
        return new ChartDatasetDTO(label, data, color, fill, stack, axis);
    }
}
