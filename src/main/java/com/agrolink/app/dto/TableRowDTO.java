package com.agrolink.app.dto;

import java.util.List;
public record TableRowDTO(String key, List<String> cells, String route) {

    public TableRowDTO(String key, List<String> cells) {
        this(key, cells, null);
    }
}
