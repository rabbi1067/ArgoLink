package com.agrolink.app.dto;


public record AttentionItemDTO(
        String key,
        String label,
        long count,
        String tone,
        String action,
        String route) {

    public static AttentionItemDTO of(String key, String label, long count, String tone, String action, String route) {
        return new AttentionItemDTO(key, label, count, tone, action, route);
    }
}
