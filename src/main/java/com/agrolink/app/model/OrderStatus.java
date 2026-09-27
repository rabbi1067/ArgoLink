package com.agrolink.app.model;

import java.util.EnumSet;
import java.util.Set;


public enum OrderStatus {
    PENDING,
    OFFER_ACCEPTED,
    PAYMENT_PENDING,
    PAID_CONFIRMED,
    ESCROW_HELD,
    PROCESSING,
    CONFIRMED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    DISPUTED;

    private static final Set<OrderStatus> LIVE_TRACKING_STAGES = EnumSet.of(
            PAID_CONFIRMED, ESCROW_HELD, PROCESSING, IN_TRANSIT, DELIVERED);

    public boolean isLiveTrackingStage() {
        return LIVE_TRACKING_STAGES.contains(this);
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == DISPUTED;
    }
}
