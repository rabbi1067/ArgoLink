package com.agrolink.app.dto;

import java.util.List;

public record TableSpecDTO(
        String key,
        String title,
        String subtitle,
        List<TableColumnDTO> columns,
        List<TableRowDTO> rows,
        boolean searchable,
        String searchHint,
        String emptyMessage,
        String rowLimitNote,
        String viewMoreLabel,
        String viewMoreRoute) {

    public static TableSpecDTO of(String key, String title, String subtitle,
                                  List<TableColumnDTO> columns, List<TableRowDTO> rows,
                                  String emptyMessage) {
        return new TableSpecDTO(key, title, subtitle,
                columns == null ? List.of() : List.copyOf(columns),
                rows == null ? List.of() : List.copyOf(rows),
                false, null, emptyMessage, null, null, null);
    }

    public TableSpecDTO searchable(String hint) {
        return new TableSpecDTO(key, title, subtitle, columns, rows, true, hint,
                emptyMessage, rowLimitNote, viewMoreLabel, viewMoreRoute);
    }

    public TableSpecDTO limited(String note) {
        return new TableSpecDTO(key, title, subtitle, columns, rows, searchable,
                searchHint, emptyMessage, note, viewMoreLabel, viewMoreRoute);
    }

    public TableSpecDTO viewMore(String label, String route) {
        return new TableSpecDTO(key, title, subtitle, columns, rows, searchable,
                searchHint, emptyMessage, rowLimitNote, label, route);
    }
}
