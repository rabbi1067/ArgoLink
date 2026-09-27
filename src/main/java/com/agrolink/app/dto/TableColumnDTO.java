package com.agrolink.app.dto;

public record TableColumnDTO(String key, String label, String align) {

    public static TableColumnDTO of(String key, String label) {
        return new TableColumnDTO(key, label, "left");
    }

    public static TableColumnDTO right(String key, String label) {
        return new TableColumnDTO(key, label, "right");
    }

    public static TableColumnDTO center(String key, String label) {
        return new TableColumnDTO(key, label, "center");
    }
}
