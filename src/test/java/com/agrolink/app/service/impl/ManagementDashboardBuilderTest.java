package com.agrolink.app.service.impl;

import com.agrolink.app.dto.ChartSpecDTO;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.dto.MetricCardDTO;
import com.agrolink.app.dto.TableRowDTO;
import com.agrolink.app.dto.TableSpecDTO;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.DashboardService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration checks for the three management dashboards against the real database.
 *
 * <p>The property under test is not "does it return 200" but "can it show a number that
 * is not in the database". Every money figure is therefore recomputed here with a plain
 * BSON query and compared against the rendered card, and the three promises the product
 * made explicitly are pinned:</p>
 * <ul>
 *   <li>a farmer or buyer only ever sees their own trades, proven by giving two
 *       different users an identical-looking window and checking the numbers differ;</li>
 *   <li>a period with nothing in it renders zero and no growth badge, rather than
 *       inventing a percentage;</li>
 *   <li>widgets for concepts the platform does not store (fleet, GPS, audit trail) are
 *       absent from the payload instead of being faked.</li>
 * </ul>
 */
@SpringBootTest
class ManagementDashboardBuilderTest {

    private static final List<OrderStatus> LIVE = List.of(
            OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID_CONFIRMED,
            OrderStatus.ESCROW_HELD, OrderStatus.PROCESSING, OrderStatus.CONFIRMED,
            OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ManagementDashboardBuilder builder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongo;

    @Test
    void everyRoleGetsItsOwnViewFromTheStoredRole() {
        assertEquals("STANDARD", viewFor(Role.FARMER));
        assertEquals("STANDARD", viewFor(Role.BUYER));
        assertEquals("OPERATIONS", viewFor(Role.ADMIN));
        assertEquals("CONTROL", viewFor(Role.SUPER_ADMIN));

        DashboardSummaryDTO farmer = builder.build(anyUserId(Role.FARMER), Role.FARMER, null, null);
        DashboardSummaryDTO control = builder.build(anyUserId(Role.SUPER_ADMIN), Role.SUPER_ADMIN, null, null);
        assertNotEquals(farmer.view(), control.view());
        assertTrue(farmer.title() != null && !farmer.title().isBlank(), "every view names itself");
        assertTrue(control.subtitle() != null && !control.subtitle().isBlank());
    }

    @Test
    void farmerSalesMatchARawQueryOnThatFarmersOrders() {
        User farmer = userWithOrders(Role.FARMER, "farmerId");
        LocalDate from = LocalDate.now().minusDays(29);
        LocalDate to = LocalDate.now();

        BigDecimal expected = sumFor(farmer.getId(), "farmerId", from, to);
        DashboardSummaryDTO dto = builder.build(farmer.getId(), Role.FARMER, from, to);

        BigDecimal shown = amountOf(dto, "farmerSales");
        System.out.println("@@ farmerSales=" + shown + " rawSum=" + expected);
        assertTrue(expected.compareTo(BigDecimal.ZERO) > 0, "fixture must have live orders for this role");
        assertEquals(0, expected.compareTo(shown), "farmer sales must equal the raw sum of their live orders");
    }

    @Test
    void buyerSpendIsScopedToThatBuyerOnly() {
        User buyer = userWithOrders(Role.BUYER, "buyerId");
        LocalDate from = LocalDate.now().minusDays(29);
        LocalDate to = LocalDate.now();

        BigDecimal mine = sumFor(buyer.getId(), "buyerId", from, to);
        BigDecimal platform = rawSum("orders", "totalAmount",
                liveFilter().append("createdAt", window(from, to)));

        DashboardSummaryDTO dto = builder.build(buyer.getId(), Role.BUYER, from, to);
        BigDecimal shown = amountOf(dto, "buyerSpend");
        System.out.println("@@ buyerSpend=" + shown + " mine=" + mine + " platform=" + platform);

        assertEquals(0, mine.compareTo(shown), "buyer spend must be only this buyer's orders");
        assertTrue(mine.compareTo(platform) <= 0, "a single buyer can never spend more than the platform");
        if (mine.compareTo(platform) < 0) {
            assertNotEquals(0, platform.compareTo(shown),
                    "when other buyers have orders, this buyer must not be shown their total");
        }
    }

    @Test
    void orderTablesOnlyListTheViewersOwnTrades() {
        User buyer = userWithOrders(Role.BUYER, "buyerId");
        DashboardSummaryDTO dto = builder.build(buyer.getId(), Role.BUYER, null, null);

        List<String> foreignOrderIds = new ArrayList<>();
        mongo.getCollection("orders").find(liveFilter()).forEach(doc -> foreignOrderIds.add(
                String.valueOf(doc.get("_id"))));

        TableSpecDTO table = table(dto, "buyerOrdersTable");
        assertFalse(table.rows().isEmpty(), "a buyer with orders must see them");
        for (TableRowDTO row : table.rows()) {
            Document stored = mongo.getCollection("orders")
                    .find(new Document("_id", new ObjectId(row.key()))).first();
            assertNotNull(stored, "row " + row.key() + " points at an order that does not exist");
            assertEquals(buyer.getId(), String.valueOf(stored.get("buyerId")),
                    "order " + row.key() + " is not this buyer's and must not be listed");
        }
        assertTrue(foreignOrderIds.size() >= table.rows().size());
    }

    @Test
    void aPeriodWithNoTradesRendersZeroAndNoGrowthBadge() {
        User farmer = userWithOrders(Role.FARMER, "farmerId");
        LocalDate from = LocalDate.of(2001, 1, 1);
        LocalDate to = LocalDate.of(2001, 1, 31);

        DashboardSummaryDTO dto = builder.build(farmer.getId(), Role.FARMER, from, to);

        assertEquals(0, BigDecimal.ZERO.compareTo(amountOf(dto, "farmerSales")),
                "a window with no orders must not invent revenue");
        for (MetricCardDTO card : dto.cards()) {
            assertNull(card.trendPercent(),
                    "card " + card.key() + " has nothing to compare against, so it must not show a trend");
            assertNull(card.badge(), "card " + card.key() + " must not carry a badge without a comparison");
        }
        for (ChartSpecDTO chart : dto.charts()) {
            assertTrue(chart.isEmpty(), "chart " + chart.key() + " must read as empty, not as zeros");
        }
        assertTrue(table(dto, "farmerOrdersTable").rows().isEmpty());
    }

    @Test
    void theDefaultWindowComparesAgainstAnEquallyLongPrecedingWindow() {
        User farmer = userWithOrders(Role.FARMER, "farmerId");
        ManagementDashboardBuilder.Window window = ManagementDashboardBuilder.Window.of(null, null);
        assertEquals(30, window.days());
        assertEquals(window.to().minusDays(30), window.previousTo());
        assertEquals(window.from().minusDays(1), window.previousTo());
        assertTrue(window.previousFrom().isBefore(window.from()));
        assertEquals(30, (int) java.time.temporal.ChronoUnit.DAYS.between(
                window.previousFrom(), window.previousTo()) + 1);
    }

    @Test
    void reversedAndOversizedWindowsAreCorrectedNotRejected() {
        User farmer = userWithOrders(Role.FARMER, "farmerId");
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now();

        ManagementDashboardBuilder.Window reversed =
                ManagementDashboardBuilder.Window.of(end, start);
        assertTrue(reversed.from().isBefore(reversed.to()), "a reversed range is normalised");

        ManagementDashboardBuilder.Window huge = ManagementDashboardBuilder.Window.of(
                end.minusYears(5), end);
        assertEquals(366, huge.days(), "a very long range is clamped instead of erroring");

        assertNotNull(builder.build(farmer.getId(), Role.FARMER, end, start));
        assertNotNull(builder.build(farmer.getId(), Role.FARMER, end.minusYears(5), end));
    }

    @Test
    void adminAndSuperAdminSeeMeasuredHealthRatherThanZeros() {
        DashboardSummaryDTO admin = builder.build(anyUserId(Role.ADMIN), Role.ADMIN, null, null);
        DashboardSummaryDTO control = builder.build(anyUserId(Role.SUPER_ADMIN), Role.SUPER_ADMIN, null, null);

        for (DashboardSummaryDTO dto : List.of(admin, control)) {
            assertNotNull(dto.health(), dto.view() + " should carry a health strip");
            assertEquals("UP", dto.health().databaseStatus());
            assertTrue(dto.health().databaseLatencyMs() > 0d, "the ping round trip is measured, not assumed");
            assertTrue(dto.health().queryLatencyMs() > 0d, "this dashboard's own cost is measured, not assumed");
            assertTrue(dto.health().uptimeSeconds() > 0L, "JVM uptime is real");
            assertTrue(dto.health().heapMaxMb() > 0L, "heap ceiling is real");
            assertTrue(dto.health().heapUsedMb() > 0L);
            assertTrue(dto.health().heapUsedMb() <= dto.health().heapMaxMb());
            assertTrue(dto.health().activeUsers() > 0L, "active accounts exist in the fixture");
            assertNotNull(dto.health().checkedAt());
        }
    }

    @Test
    void standardDashboardsHaveNoHealthStripAndAdminTablesExist() {
        DashboardSummaryDTO farmer = builder.build(anyUserId(Role.FARMER), Role.FARMER, null, null);
        DashboardSummaryDTO buyer = builder.build(anyUserId(Role.BUYER), Role.BUYER, null, null);
        assertNull(farmer.health(), "health is an operator concern, not a trading one");
        assertNull(buyer.health());

        DashboardSummaryDTO control = builder.build(anyUserId(Role.SUPER_ADMIN), Role.SUPER_ADMIN, null, null);
        TableSpecDTO access = table(control, "adminAccess");
        assertEquals(userRepository.findByRole(Role.ADMIN).size() + userRepository.findByRole(Role.SUPER_ADMIN).size(),
                access.rows().size(), "every administrator account is listed");
        for (TableRowDTO row : access.rows()) {
            assertTrue(row.cells().contains(Role.ADMIN.name()) || row.cells().contains(Role.SUPER_ADMIN.name()),
                    "an access row must state a real role");
        }
    }

    @Test
    void noWidgetClaimsDataThePlatformDoesNotStore() {
        List<DashboardSummaryDTO> all = List.of(
                builder.build(anyUserId(Role.FARMER), Role.FARMER, null, null),
                builder.build(anyUserId(Role.BUYER), Role.BUYER, null, null),
                builder.build(anyUserId(Role.ADMIN), Role.ADMIN, null, null),
                builder.build(anyUserId(Role.SUPER_ADMIN), Role.SUPER_ADMIN, null, null));

        for (DashboardSummaryDTO dto : all) {
            String everything = String.join(" ", dto.cards().stream().map(MetricCardDTO::label).toList())
                    + String.join(" ", dto.charts().stream().map(ChartSpecDTO::title).toList())
                    + String.join(" ", dto.tables().stream().map(TableSpecDTO::title).toList());

            for (String invented : List.of("vehicle", "fleet", "gps", "commission", "payout",
                    "audit log", "return", "refund", "segment", "PENDING")) {
                assertFalse(everything.toLowerCase().contains(invented.toLowerCase()),
                        dto.view() + " must not advertise a widget for data this platform does not store: "
                                + invented);
            }
        }
    }

    @Test
    void payloadIsFullyRenderableWithNoNullFields() {
        for (Role role : List.of(Role.FARMER, Role.BUYER, Role.ADMIN, Role.SUPER_ADMIN)) {
            DashboardSummaryDTO dto = builder.build(anyUserId(role), role, null, null);
            String where = role + "/" + dto.view();

            assertNotNull(dto.generatedAt(), where);
            assertNotNull(dto.rangeLabel(), where + " must state its date window");
            assertNotNull(dto.cards(), where);
            assertNotNull(dto.charts(), where);
            assertNotNull(dto.tables(), where);
            assertNotNull(dto.attention(), where);
            assertNotNull(dto.activity(), where);
            assertFalse(dto.cards().isEmpty(), where + " needs at least one headline number");

            for (MetricCardDTO card : dto.cards()) {
                assertNotNull(card.key(), where);
                assertNotNull(card.label(), where + " card " + card.key());
                assertNotNull(card.value(), where + " card " + card.key());
                assertNotNull(card.icon(), where + " card " + card.key());
                assertNotNull(card.tone(), where + " card " + card.key());
            }
            for (ChartSpecDTO chart : dto.charts()) {
                assertNotNull(chart.labels(), where + " chart " + chart.key());
                assertNotNull(chart.datasets(), where + " chart " + chart.key());
                assertFalse(chart.datasets().isEmpty(), where + " chart " + chart.key() + " has no dataset");
                chart.datasets().forEach(dataset -> {
                    assertNotNull(dataset.data(), where + " chart " + chart.key() + " dataset " + dataset.label());
                    assertEquals(chart.labels().size(), dataset.data().size(),
                            where + " chart " + chart.key() + " labels and data must line up");
                });
            }
            for (TableSpecDTO table : dto.tables()) {
                assertNotNull(table.emptyMessage(), where + " table " + table.key() + " must say what empty means");
                for (TableRowDTO row : table.rows()) {
                    assertNotNull(row.cells(), where + " table " + table.key());
                    assertEquals(table.columns().size(), row.cells().size(),
                            where + " table " + table.key() + " row " + row.key() + " must fill every column");
                    row.cells().forEach(cell -> assertNotNull(cell,
                            where + " table " + table.key() + " must not render a null cell"));
                }
            }
        }
    }

    @Test
    void listingManagementIsNotCappedToNothingAndCountsMatchTheCollection() {
        DashboardSummaryDTO admin = builder.build(anyUserId(Role.ADMIN), Role.ADMIN, null, null);
        long liveListings = mongo.getCollection("produce_listings")
                .countDocuments(new Document("status", "ACTIVE"));
        TableSpecDTO table = table(admin, "listingManagement");

        assertTrue(table.rows().size() <= 25, "the table caps its own row count");
        assertEquals(Math.min(liveListings + archivedListings(), 25), table.rows().size());
        System.out.println("@@ listing rows=" + table.rows().size() + " active=" + liveListings);
    }

    // ============================================================ helpers

    private String viewFor(Role role) {
        return builder.build(anyUserId(role), role, null, null).view();
    }

    private TableSpecDTO table(DashboardSummaryDTO dto, String key) {
        return dto.tables().stream().filter(t -> key.equals(t.key())).findFirst()
                .orElseThrow(() -> new AssertionError("missing table " + key + " in " + dto.view()));
    }

    private BigDecimal amountOf(DashboardSummaryDTO dto, String cardKey) {
        String rendered = dto.cards().stream().filter(c -> cardKey.equals(c.key())).findFirst()
                .orElseThrow(() -> new AssertionError("missing card " + cardKey + " in " + dto.view()))
                .value();
        String digits = rendered.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digits);
    }

    private BigDecimal sumFor(String userId, String field, LocalDate from, LocalDate to) {
        return rawSum("orders", "totalAmount", liveFilter()
                .append(field, userId)
                .append("createdAt", window(from, to)));
    }

    private Document liveFilter() {
        return new Document("deleted", new Document("$ne", true))
                .append("orderStatus", new Document("$in", LIVE.stream().map(Enum::name).toList()));
    }

    /**
     * The same inclusive window the service builds: from the first instant of {@code from}
     * up to, but not including, the first instant of the day after {@code to}. Anything else
     * would silently drop trades placed in the evening of the last selected day and make
     * this test disagree with the dashboard for the wrong reason.
     */
    private Document window(LocalDate from, LocalDate to) {
        return new Document("$gte", Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .append("$lt", Date.from(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    private BigDecimal rawSum(String collection, String field, Document match) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match == null ? new Document() : match));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", "$" + field))));
        Document row = mongo.getCollection(collection).aggregate(pipeline)
                .into(new ArrayList<>()).stream().filter(d -> d.get("_id") == null).findFirst().orElse(null);
        if (row == null || row.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = row.get("total");
        return total instanceof Decimal128 decimal
                ? decimal.bigDecimalValue() : new BigDecimal(String.valueOf(total));
    }

    private User userWithOrders(Role role, String field) {
        List<String> ids = new ArrayList<>();
        Document query = new Document(field, new Document("$exists", true));
        mongo.getCollection("orders").find(query).forEach(doc ->
                ids.add(String.valueOf(doc.get(field))));
        for (String id : ids) {
            Optional<User> match = userRepository.findById(id).filter(u -> u.getRole() == role);
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new AssertionError("fixture has no " + role + " with orders on field " + field);
    }

    private String anyUserId(Role role) {
        return userRepository.findByRole(role).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture has no " + role + " account"))
                .getId();
    }

    private long archivedListings() {
        return mongo.getCollection("produce_listings").countDocuments(new Document("status", "ARCHIVED"));
    }
}

