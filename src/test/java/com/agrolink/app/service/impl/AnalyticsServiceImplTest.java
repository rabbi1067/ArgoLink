package com.agrolink.app.service.impl;

import com.agrolink.app.dto.AnalyticsDTO;
import com.agrolink.app.dto.ChartSpecDTO;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.dto.MonthlyVolumeDTO;
import com.agrolink.app.dto.RevenueDTO;
import com.agrolink.app.service.AnalyticsService;
import com.agrolink.app.service.DashboardService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI assistant still answers questions from the legacy {@link AnalyticsService}, so these
 * endpoints must report exactly the same numbers as the dashboards. They previously ran their
 * own monthly pipeline whose projection dropped the summed field before {@code $group}, which
 * made every revenue figure silently zero while the dashboards were correct.
 */
@SpringBootTest
class AnalyticsServiceImplTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MongoTemplate mongo;

    @Test
    void monthlyRevenueEqualsRawLiveOrderSum() {
        RevenueDTO revenue = analyticsService.monthlyRevenue(6);
        BigDecimal total = revenue.data().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expected = rawScopedSum();
        System.out.println("@@ legacy revenue total=" + total + " raw live sum=" + expected);
        assertTrue(expected.compareTo(BigDecimal.ZERO) > 0,
                "fixture should contain live orders for this assertion to mean anything");
        assertEquals(0, expected.compareTo(total),
                "legacy revenue must equal the raw live-order sum");
    }

    @Test
    void monthlyVolumeEqualsRawLiveOrderCount() {
        MonthlyVolumeDTO volume = analyticsService.monthlyOrderVolume(6);
        long total = volume.data().stream().mapToLong(Long::longValue).sum();

        long expected = mongo.getCollection("orders").countDocuments(new Document("deleted",
                new Document("$ne", true)).append("orderStatus", new Document("$nin", List.of("CANCELLED", "DISPUTED"))));
        System.out.println("@@ legacy volume total=" + total + " raw live count=" + expected);
        assertEquals(expected, total, "legacy order volume must exclude cancelled and disputed orders");
    }

    @Test
    void legacyRevenueAgreesWithDashboardCharts() {
        RevenueDTO legacyRevenue = analyticsService.monthlyRevenue(6);
        MonthlyVolumeDTO legacyVolume = analyticsService.monthlyOrderVolume(6);
        DashboardSummaryDTO dashboard = dashboardService.analytics(6);

        ChartSpecDTO tradeTrend = dashboard.charts().stream()
                .filter(c -> c.key().equals("tradeTrend"))
                .findFirst().orElseThrow();

        List<Number> value = tradeTrend.datasets().get(0).data();
        List<Number> counts = tradeTrend.datasets().get(1).data();

        assertEquals(legacyRevenue.labels(), tradeTrend.labels(),
                "both layers must bucket months identically");

        List<BigDecimal> dashboardMoney = value.stream()
                .map(v -> new BigDecimal(String.valueOf(v)))
                .toList();
        assertEquals(legacyRevenue.data(), dashboardMoney,
                "the assistant and the dashboard must not report different revenue");

        List<Long> dashboardCounts = counts.stream().map(v -> v.longValue()).toList();
        assertEquals(legacyVolume.data(), dashboardCounts,
                "the assistant and the dashboard must not report different order counts");
    }

    @Test
    void overviewCountsExcludeSoftDeletedRecords() {
        AnalyticsDTO overview = analyticsService.overview();
        long notDeletedOrders = mongo.getCollection("orders")
                .countDocuments(new Document("deleted", new Document("$ne", true)));
        long notDeletedListings = mongo.getCollection("produce_listings")
                .countDocuments(new Document("deleted", new Document("$ne", true)));

        System.out.println("@@ overview orders=" + overview.totalOrders() + " listings=" + overview.totalListings());
        assertEquals(notDeletedOrders, overview.totalOrders(),
                "overview order count must exclude soft-deleted orders");
        assertEquals(notDeletedListings, overview.totalListings(),
                "overview listing count must exclude soft-deleted listings");
    }

    @Test
    void overviewStillReportsANonZeroAveragePrice() {
        AnalyticsDTO overview = analyticsService.overview();
        assertTrue(overview.averagePricePerUnit().compareTo(BigDecimal.ZERO) > 0,
                "active listings exist, so the average unit price must not be zero: "
                        + overview.averagePricePerUnit());
    }

    private BigDecimal rawScopedSum() {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("deleted", new Document("$ne", true))
                .append("orderStatus", new Document("$nin", List.of("CANCELLED", "DISPUTED")))));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", "$totalAmount"))));
        Document row = mongo.getCollection("orders").aggregate(pipeline)
                .into(new ArrayList<Document>())
                .stream().filter(d -> d.get("_id") == null).findFirst().orElse(null);
        if (row == null || row.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = row.get("total");
        return total instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(String.valueOf(total));
    }
}
