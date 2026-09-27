package com.agrolink.app.dto;

import java.time.Instant;
import java.util.List;
public record DashboardSummaryDTO(
        String role,
        String view,
        String scope,
        String title,
        String subtitle,
        List<MetricCardDTO> cards,
        List<ChartSpecDTO> charts,
        List<TableSpecDTO> tables,
        List<AttentionItemDTO> attention,
        List<ActivityItemDTO> activity,
        SystemHealthDTO health,
        String rangeLabel,
        boolean empty,
        Instant generatedAt) {

    public static DashboardSummaryDTO of(String role, String view, String scope, String title, String subtitle,
                                         List<MetricCardDTO> cards, List<ChartSpecDTO> charts,
                                         List<TableSpecDTO> tables, List<AttentionItemDTO> attention,
                                         List<ActivityItemDTO> activity, SystemHealthDTO health,
                                         String rangeLabel) {
        return new DashboardSummaryDTO(role, view, scope, title, subtitle,
                cards == null ? List.of() : List.copyOf(cards),
                charts == null ? List.of() : List.copyOf(charts),
                tables == null ? List.of() : List.copyOf(tables),
                attention == null ? List.of() : List.copyOf(attention),
                activity == null ? List.of() : List.copyOf(activity),
                health, rangeLabel,
                (activity == null || activity.isEmpty()) && (tables == null || tables.isEmpty()),
                Instant.now());
    }
    public DashboardSummaryDTO withQueryLatencyMs(double queryLatencyMs) {
        if (health == null) {
            return this;
        }
        return new DashboardSummaryDTO(role, view, scope, title, subtitle, cards, charts, tables,
                attention, activity,
                new SystemHealthDTO(health.databaseStatus(), health.databaseLatencyMs(), queryLatencyMs,
                        health.uptimeSeconds(), health.heapUsedMb(), health.heapMaxMb(),
                        health.activeUsers(), health.error(), health.checkedAt()),
                rangeLabel, empty, generatedAt);
    }

    public static DashboardSummaryDTO of(String role, String scope, List<MetricCardDTO> cards,
                                         List<ChartSpecDTO> charts, List<AttentionItemDTO> attention,
                                         List<ActivityItemDTO> activity) {
        boolean empty = (activity == null || activity.isEmpty());
        return new DashboardSummaryDTO(role, "CLASSIC", scope, null, null,
                cards == null ? List.of() : List.copyOf(cards),
                charts == null ? List.of() : List.copyOf(charts),
                List.of(),
                attention == null ? List.of() : List.copyOf(attention),
                activity == null ? List.of() : List.copyOf(activity),
                null, null, empty, Instant.now());
    }
}
