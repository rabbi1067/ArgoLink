package com.agrolink.app.dto;

import com.agrolink.app.model.OrderStatus;

public record OrderStatsRecord(
        long total,
        long needsAction,
        long awaitingPayment,
        long inEscrow,
        long inTransit,
        long delivered,
        long cancelled,
        java.util.Map<OrderStatus, Long> byStatus) {
}
