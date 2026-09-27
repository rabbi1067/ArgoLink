package com.agrolink.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EscrowStatus {
    NONE("Unpaid"),
    ESCROW_HELD("Escrow Held"),
    RELEASED("Released"),
    REFUNDED("Refunded"),
    FAILED("Failed");

    private final String displayName;

    EscrowStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static EscrowStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String needle = value.trim();
        for (EscrowStatus status : values()) {
            if (status.name().equalsIgnoreCase(needle) || status.displayName.equalsIgnoreCase(needle)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported escrow status: " + value);
    }
}
