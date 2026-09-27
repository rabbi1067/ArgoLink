package com.agrolink.app.service.impl;

import com.agrolink.app.dto.ChartSpecDTO;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.Role;
import com.agrolink.app.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration checks for {@link DashboardServiceImpl} against the real database.
 *
 * <p>These exist because every aggregation bug found while building the dashboards was
 * <em>silent</em>: the API returned 200 with plausible looking zeros rather than an error.
 * A unit test with a mocked {@code MongoTemplate} cannot catch any of them, because the
 * failures were all about how MongoDB treats the stored data:</p>
 * <ul>
 *   <li>{@code BigDecimal} persisted as a string made every {@code $sum} return 0;</li>
 *   <li>{@code $sort} and range filters compared values as text;</li>
 *   <li>references stored as strings could never match an {@code ObjectId} {@code _id} in a
 *       {@code $lookup}, so every join silently produced an empty chart;</li>
 *   <li>a projection stage dropped the summed field before the group stage could see it.</li>
 * </ul>
 *
 * <p>The assertions recompute the expected totals straight from BSON instead of hard-coding
 * them, so they keep working as the data changes.</p>
 */
@SpringBootTest
class DashboardServiceImplTest {

    private static final List<OrderStatus> LIVE = List.of(
            OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID_CONFIRMED,
            OrderStatus.ESCROW_HELD, OrderStatus.PROCESSING, OrderStatus.CONFIRMED,
            OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongo;

    @Test
    void platformGrossValueMatchesRawDatabaseSum() {
        BigDecimal expected = rawSum("orders", "totalAmount",
                new Document("deleted", new Document("$ne", true))
                        .append("orderStatus", new Document("$in", LIVE.stream().map(Enum::name).toList())));

        DashboardSummaryDTO dto = dashboardService.analytics(6);
        String gmv = dto.cards().stream()
                .filter(c -> c.key().equals("gmv"))
                .findFirst().orElseThrow().value();

        System.out.println("@@ platform gmv=" + gmv + " rawSum=" + expected);
        assertTrue(expected.compareTo(BigDecimal.ZERO) > 0,
                "fixture should contain live orders for this assertion to mean anything");
        // A "$sum" over string-stored money returns 0, and a projection that drops the summed
        // field before $group also returns 0. Comparing the rendered figure to the raw sum
        // catches both, where a "not zero" check alone would not.
        assertEquals(expected.setScale(0, java.math.RoundingMode.HALF_UP),
                renderedAmount(gmv),
                "gross trade value must equal the raw live-order sum, got: " + gmv);
        assertTrue(gmv.startsWith("৳") || gmv.contains("৳"),
                "money values must be formatted as currency, got: " + gmv);
    }

    @Test
    void noChartIsSilentlyZeroWhenTheDataSupportsIt() {
        BigDecimal platformSum = rawSum("orders", "totalAmount",
                new Document("deleted", new Document("$ne", true)));
        assertTrue(platformSum.compareTo(BigDecimal.ZERO) > 0, "expected live order value in fixtures");

        DashboardSummaryDTO dto = dashboardService.analytics(6);
        for (ChartSpecDTO chart : dto.charts()) {
            boolean hasValue = chart.datasets() != null && chart.datasets().stream()
                    .anyMatch(ds -> ds.data() != null && ds.data().stream()
                            .anyMatch(v -> v != null && v.doubleValue() != 0d));
            if (!hasValue) {
                System.out.println("@@ empty chart " + chart.key() + " (" + chart.type() + ")");
            }
        }

        // Charts that are backed by the same data as platformSum must not come back empty.
        for (String key : List.of("tradeTrend", "orderStatusMix", "topCrops")) {
            ChartSpecDTO chart = dto.charts().stream()
                    .filter(c -> c.key().equals(key)).findFirst().orElseThrow();
            assertFalse(chart.isEmpty(), key + " must render data while live orders exist");
        }
    }

    @Test
    void listingJoinsResolveInsteadOfSilentlyDropping() {
        long orders = mongo.getCollection("orders")
                .countDocuments(new Document("deleted", new Document("$ne", true)));
        long matched = mongo.getCollection("orders").aggregate(List.of(
                        new Document("$lookup", new Document("from", "offers")
                                .append("localField", "offerId")
                                .append("foreignField", "_id")
                                .append("as", "offer")),
                        new Document("$unwind", "$offer"),
                        new Document("$count", "n")))
                .first() == null ? 0 : 1;

        System.out.println("@@ raw lookup without conversion matched=" + matched + " of " + orders);
        // Raw $lookup cannot match String references against ObjectId _id. The dashboard works
        // around it in AnalyticsAggregations#objectIdRef, so district/category charts must have data.
        DashboardSummaryDTO dto = dashboardService.analytics(6);
        ChartSpecDTO district = dto.charts().stream()
                .filter(c -> c.key().equals("districtReach")).findFirst().orElseThrow();
        assertFalse(district.labels().isEmpty(),
                "district reach needs the offer and listing joins to resolve");
    }

    @Test
    void everyRoleGetsAPlausibleDashboard() {
        for (Role role : Role.values()) {
            var user = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == role).findFirst();
            if (user.isEmpty()) {
                continue;
            }
            DashboardSummaryDTO dto = dashboardService.summary(user.get().getId(), role);
            System.out.println("@@ " + role + " cards=" + dto.cards().size()
                    + " charts=" + dto.charts().size() + " attention=" + dto.attention().size()
                    + " activity=" + dto.activity().size());

            assertFalse(dto.cards().isEmpty(), role + " must have headline metrics");
            assertFalse(dto.charts().isEmpty(), role + " must have charts");
            assertNotNull(dto.generatedAt(), role + " snapshot must be timestamped");
            for (var card : dto.cards()) {
                assertNotNull(card.key(), "card key");
                assertNotNull(card.label(), "card label");
                assertNotNull(card.value(), "card value for " + card.key());
                assertFalse(card.value().isBlank(), "card " + card.key() + " must not be blank");
            }
            for (var chart : dto.charts()) {
                assertNotNull(chart.datasets(), "chart datasets for " + chart.key());
                assertFalse(chart.datasets().isEmpty(), "chart " + chart.key() + " needs a dataset");
                assertTrue(chart.height() > 0, "chart " + chart.key() + " needs a height");
            }
        }
    }

    @Test
    void analyticsWindowIsClampedInsteadOfErroring() {
        assertNotNull(dashboardService.analytics(1), "minimum window");
        assertNotNull(dashboardService.analytics(120), "oversized window should clamp, not fail");
        int clamped = dashboardService.analytics(120).charts().stream()
                .filter(c -> c.key().equals("tradeTrend")).findFirst().orElseThrow()
                .labels().size();
        assertEquals(24, clamped, "months should be clamped to 24");
    }

    @Test
    void softDeletedOrdersNeverReachTheNumbers() {
        BigDecimal allSum = rawSum("orders", "totalAmount", new Document());
        BigDecimal notDeletedSum = rawSum("orders", "totalAmount",
                new Document("deleted", new Document("$ne", true)));
        System.out.println("@@ all=" + allSum + " notDeleted=" + notDeletedSum);

        DashboardSummaryDTO dto = dashboardService.analytics(6);
        String ordersCard = dto.cards().stream()
                .filter(c -> c.key().equals("orders")).findFirst().orElseThrow().value();
        String expected = String.valueOf(mongo.getCollection("orders")
                .countDocuments(new Document("deleted", new Document("$ne", true))));
        assertEquals(expected, ordersCard, "order count must exclude soft-deleted orders");
    }

    /** Recomputes a sum straight from BSON so the dashboard cannot agree with a broken pipeline. */
    /**
     * Sums {@code field} straight from the collection, honouring {@code match}.
     * The match is applied deliberately: an earlier version ignored it, which made the
     * expected value cover every order including disputed ones and the assertion vacuous.
     */
    private BigDecimal rawSum(String collection, String field, Document match) {
        List<Document> pipeline = new ArrayList<>();
        if (match != null && !match.isEmpty()) {
            pipeline.add(new Document("$match", match));
        }
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", "$" + field))));
        var row = mongo.getCollection(collection)
                .aggregate(pipeline)
                .into(new java.util.ArrayList<Document>())
                .stream().filter(d -> d.get("_id") == null).findFirst().orElse(null);
        if (row == null || row.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = row.get("total");
        return total instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(String.valueOf(total));
    }

    /** Reads the numeric part back out of a rendered money string such as "৳ 4,624". */
    private BigDecimal renderedAmount(String formatted) {
        String digits = formatted.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digits);
    }
}
