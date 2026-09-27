package com.agrolink.app.service.impl;

import com.agrolink.app.dto.ActivityItemDTO;
import com.agrolink.app.dto.AttentionItemDTO;
import com.agrolink.app.dto.ChartDatasetDTO;
import com.agrolink.app.dto.ChartSpecDTO;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.dto.MetricCardDTO;
import com.agrolink.app.dto.SystemHealthDTO;
import com.agrolink.app.dto.TableColumnDTO;
import com.agrolink.app.dto.TableRowDTO;
import com.agrolink.app.dto.TableSpecDTO;
import com.agrolink.app.model.EscrowStatus;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.OfferStatus;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.OfferRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ManagementDashboardBuilder {

    private static final String ORDERS = "orders";
    private static final String LISTINGS = "produce_listings";
    private static final String OFFERS = "offers";
    private static final String USERS = "users";

    private static final int MAX_WINDOW_DAYS = 366;
    private static final int TABLE_ROW_LIMIT = 25;

    /** Dashboards show the 5 newest rows with a "view more" link to the full page. */
    private static final int DASHBOARD_TABLE_LIMIT = 5;

    private static final DateTimeFormatter RANGE_DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TABLE_DAY = DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    /** Order value counts as real trade for these states only. */
    private static final Set<OrderStatus> LIVE_ORDER_STATES = Set.of(
            OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID_CONFIRMED,
            OrderStatus.ESCROW_HELD, OrderStatus.PROCESSING, OrderStatus.CONFIRMED,
            OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    private final AnalyticsAggregations agg;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;

    // ================================================================== entry point

    public DashboardSummaryDTO build(String userId, Role role, LocalDate from, LocalDate to) {
        Role effective = role == null ? Role.BUYER : role;
        Window window = Window.of(from, to);
        long started = System.nanoTime();
        return (switch (effective) {
            case FARMER -> standardFarmer(userId, window);
            case ADMIN -> operations(window);
            case SUPER_ADMIN -> control(window);
            default -> standardBuyer(userId, window);
        }).withQueryLatencyMs((System.nanoTime() - started) / 1_000_000d);
    }

    // ================================================================== standard: farmer

    private DashboardSummaryDTO standardFarmer(String farmerId, Window w) {
        Criteria mine = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("farmerId").is(farmerId));
        Criteria live = and(mine, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));
        Criteria mineListings = Criteria.where("farmerId").is(farmerId);

        BigDecimal sales = agg.sumBetween(ORDERS, live, w.from(), w.to(), "totalAmount");
        BigDecimal salesPrev = agg.sumBetween(ORDERS, live, w.previousFrom(), w.previousTo(), "totalAmount");
        BigDecimal escrow = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.from(), w.to(), "totalAmount");
        BigDecimal escrowPrev = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.previousFrom(), w.previousTo(), "totalAmount");
        long completed = agg.countBetween(ORDERS, and(mine, statusIs(OrderStatus.DELIVERED)), w.from(), w.to(), "createdAt");
        long completedPrev = agg.countBetween(ORDERS, and(mine, statusIs(OrderStatus.DELIVERED)),
                w.previousFrom(), w.previousTo(), "createdAt");
        long activeListings = agg.count(LISTINGS, and(mineListings, statusIs(ListingStatus.ACTIVE)));
        long archivedListings = agg.count(LISTINGS, and(mineListings, statusIs(ListingStatus.ARCHIVED)));

        List<MetricCardDTO> cards = List.of(
                money("farmerSales", "Total sales", sales, w, salesPrev, "money", "success",
                        "value of your live orders in this period"),
                counted("farmerCompleted", "Orders completed", completed, w, completedPrev, "check", "success",
                        "delivered and settled"),
                MetricCardDTO.of("farmerListings", "Active listings", DisplayFormat.count(activeListings), "seedling")
                        .withHint(archivedListings + " archived, " + DisplayFormat.count(activeListings)
                                + " live on the market")
                        .withTone(activeListings > 0 ? "success" : "warning"),
                money("farmerEscrow", "Held in escrow", escrow, w, escrowPrev, "shield", "info",
                        "paid, released on delivery"));

        AnalyticsAggregations.MonthlySeries revenue = agg.monthlyBetween(ORDERS, live, w.from(), w.to(),
                "totalAmount", "createdAt");
        Map<String, Long> statusMix = agg.groupCountBetween(ORDERS, mine, "orderStatus",
                w.from(), w.to(), "createdAt");
        Map<String, BigDecimal> cropValue = agg.groupSumBetween(ORDERS, live, "cropName", "totalAmount",
                w.from(), w.to(), "createdAt");

        List<ChartSpecDTO> charts = List.of(
                peakBar("farmerRevenue", "Revenue analytics", "Monthly sales value in this period",
                        revenue, w),
                statusDoughnut("farmerStatusMix", statusMix, "farmer"),
                ranked("farmerCrops", "What sells", "Your order value by crop", cropValue, true));

        List<TableSpecDTO> tables = List.of(
                listingTable("farmerListingsTable", "My listings", mineListings, "You have no listings yet."),
                orderTable("farmerOrdersTable", "Recent orders", mine, Role.FARMER, w, "orders", "No orders in this period."));

        List<AttentionItemDTO> attention = farmerAttention(sales, escrow, activeListings);
        return DashboardSummaryDTO.of(Role.FARMER.name(), "STANDARD", "farmer",
                "Sales & inventory overview",
                "How your produce is selling and what is still in escrow.",
                cards, charts, tables, attention,
                recentActivity(mine, farmerId), null, w.label());
    }

    // ================================================================== standard: buyer

    private DashboardSummaryDTO standardBuyer(String buyerId, Window w) {
        Criteria mine = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("buyerId").is(buyerId));
        Criteria live = and(mine, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));

        BigDecimal spend = agg.sumBetween(ORDERS, live, w.from(), w.to(), "totalAmount");
        BigDecimal spendPrev = agg.sumBetween(ORDERS, live, w.previousFrom(), w.previousTo(), "totalAmount");
        BigDecimal escrow = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.from(), w.to(), "totalAmount");
        BigDecimal escrowPrev = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.previousFrom(), w.previousTo(), "totalAmount");
        long placed = agg.countBetween(ORDERS, mine, w.from(), w.to(), "createdAt");
        long placedPrev = agg.countBetween(ORDERS, mine, w.previousFrom(), w.previousTo(), "createdAt");
        long openOffers = offerRepository.countByBuyerIdAndStatus(buyerId, OfferStatus.OFFERED);
        long accepted = offerRepository.countByBuyerIdAndStatus(buyerId, OfferStatus.ACCEPTED);

        List<MetricCardDTO> cards = List.of(
                money("buyerSpend", "Total spend", spend, w, spendPrev, "wallet", "success",
                        "value of your live orders in this period"),
                counted("buyerOrders", "Orders placed", placed, w, placedPrev, "cart", "info",
                        "all orders, whatever their state"),
                MetricCardDTO.of("buyerOffers", "Offers open", DisplayFormat.count(openOffers), "chat")
                        .withHint(accepted + " already accepted by farmers")
                        .withTone(openOffers > 0 ? "warning" : "neutral"),
                money("buyerEscrow", "Held in escrow", escrow, w, escrowPrev, "shield", "info",
                        "released to the farmer on delivery"));

        AnalyticsAggregations.MonthlySeries spendSeries = agg.monthlyBetween(ORDERS, live, w.from(), w.to(),
                "totalAmount", "createdAt");
        Map<String, Long> statusMix = agg.groupCountBetween(ORDERS, mine, "orderStatus",
                w.from(), w.to(), "createdAt");
        Map<String, BigDecimal> cropValue = agg.groupSumBetween(ORDERS, live, "cropName", "totalAmount",
                w.from(), w.to(), "createdAt");

        List<ChartSpecDTO> charts = List.of(
                peakBar("buyerRevenue", "Spend analytics", "What you spent each month in this period",
                        spendSeries, w),
                statusDoughnut("buyerStatusMix", statusMix, "buyer"),
                ranked("buyerCrops", "What I buy", "Your order value by crop", cropValue, true));

        List<TableSpecDTO> tables = List.of(
                orderTable("buyerOrdersTable", "My orders", mine, Role.BUYER, w, "orders", "No orders in this period."),
                offerTable("buyerOffersTable", "Open offers", buyerId));

        List<AttentionItemDTO> attention = buyerAttention(escrow);
        return DashboardSummaryDTO.of(Role.BUYER.name(), "STANDARD", "buyer",
                "Purchasing overview",
                "What you are buying, and where your money is right now.",
                cards, charts, tables, attention,
                recentActivity(mine, buyerId), null, w.label());
    }

    // ================================================================== operations: admin

    private DashboardSummaryDTO operations(Window w) {
        Criteria live = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));

        long activeFarmers = activeUsers(Role.FARMER);
        long activeBuyers = activeUsers(Role.BUYER);
        long transactions = agg.countBetween(ORDERS, AnalyticsAggregations.NOT_DELETED, w.from(), w.to(), "createdAt");
        long transactionsPrev = agg.countBetween(ORDERS, AnalyticsAggregations.NOT_DELETED,
                w.previousFrom(), w.previousTo(), "createdAt");
        BigDecimal gmv = agg.sumBetween(ORDERS, live, w.from(), w.to(), "totalAmount");
        BigDecimal gmvPrev = agg.sumBetween(ORDERS, live, w.previousFrom(), w.previousTo(), "totalAmount");
        BigDecimal escrowHeld = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.from(), w.to(), "totalAmount");
        BigDecimal escrowHeldPrev = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.previousFrom(), w.previousTo(), "totalAmount");
        long disputes = agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED, statusIs(OrderStatus.DISPUTED)));
        long activeListings = agg.count(LISTINGS, statusIs(ListingStatus.ACTIVE));

        SystemHealthDTO health = probeHealth();

        List<MetricCardDTO> cards = List.of(
                MetricCardDTO.of("activeFarmers", "Active farmers", DisplayFormat.count(activeFarmers), "seedling")
                        .withHint("registered sellers with a live account")
                        .withTone(activeFarmers > 0 ? "success" : "warning"),
                MetricCardDTO.of("activeBuyers", "Active buyers", DisplayFormat.count(activeBuyers), "cart")
                        .withHint("registered demand with a live account")
                        .withTone(activeBuyers > 0 ? "success" : "warning"),
                counted("transactions", "Transactions", transactions, w, transactionsPrev, "box", "info",
                        "orders created in this period"),
                money("gmv", "Marketplace GMV", gmv, w, gmvPrev, "money", "success",
                        "value of every live order in this period"),
                money("escrowHeld", "Held in escrow", escrowHeld, w, escrowHeldPrev, "shield", "info",
                        "buyer money waiting for delivery"),
                MetricCardDTO.of("disputes", "Open disputes", DisplayFormat.count(disputes), "flag")
                        .withHint(disputes > 0 ? "need an admin decision" : "nothing escalated")
                        .withTone(disputes > 0 ? "danger" : "success"));

        AnalyticsAggregations.MonthlySeries gmvSeries = agg.monthlyBetween(ORDERS, live, w.from(), w.to(),
                "totalAmount", "createdAt");
        AnalyticsAggregations.MonthlySeries heldSeries = agg.monthlyBetween(ORDERS,
                and(live, escrowIs(EscrowStatus.ESCROW_HELD)), w.from(), w.to(), "totalAmount", "createdAt");
        AnalyticsAggregations.MonthlySeries releasedSeries = agg.monthlyBetween(ORDERS,
                and(live, escrowIs(EscrowStatus.RELEASED)), w.from(), w.to(), "totalAmount", "createdAt");

        List<ChartSpecDTO> charts = List.of(
                escrowFlowChart("gmvVersusEscrow", gmvSeries, heldSeries, releasedSeries),
                statusDoughnut("orderStatusMix", agg.groupCountBetween(ORDERS,
                        AnalyticsAggregations.NOT_DELETED, "orderStatus", w.from(), w.to(), "createdAt"),
                        "platform"),
                ranked("categoryMix", "Category mix", "Order value by produce category",
                        agg.orderValueByListingField(live, "category"), true));

        List<TableSpecDTO> tables = List.of(
                listingTable("listingManagement", "Listing management", null, "No listings exist yet."),
                disputeTable("disputeQueue", "Disputed orders"));

        List<AttentionItemDTO> attention = new ArrayList<>();
        if (disputes > 0) {
            attention.add(AttentionItemDTO.of("adminDisputes", "disputes need an admin decision", disputes,
                    "danger", "Resolve disputes", "orders"));
        }
        if (escrowHeld.signum() > 0) {
            attention.add(AttentionItemDTO.of("adminEscrow", "buyer money is waiting for delivery", 1,
                    "info", "Track deliveries", "orders"));
        }
        if (activeListings == 0) {
            attention.add(AttentionItemDTO.of("adminNoListings",
                    "no active listings, so the marketplace has nothing to sell", 0,
                    "warning", "Review listings", "produce"));
        }

        return DashboardSummaryDTO.of("PLATFORM", "OPERATIONS", "platform",
                "Marketplace administration",
                "Supply, demand and money movement across the whole platform.",
                cards, charts, tables, attention,
                recentActivity(AnalyticsAggregations.NOT_DELETED, null), health, w.label());
    }

    // ================================================================== control: super admin

    private DashboardSummaryDTO control(Window w) {
        Criteria live = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));

        BigDecimal gmv = agg.sumBetween(ORDERS, live, w.from(), w.to(), "totalAmount");
        BigDecimal gmvPrev = agg.sumBetween(ORDERS, live, w.previousFrom(), w.previousTo(), "totalAmount");
        BigDecimal escrowHeld = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.from(), w.to(), "totalAmount");
        BigDecimal escrowHeldPrev = agg.sumBetween(ORDERS, and(live, escrowIs(EscrowStatus.ESCROW_HELD)),
                w.previousFrom(), w.previousTo(), "totalAmount");
        long newUsers = agg.countBetween(USERS, null, w.from(), w.to(), "createdAt");
        long newUsersPrev = agg.countBetween(USERS, null, w.previousFrom(), w.previousTo(), "createdAt");
        long totalUsers = agg.count(USERS, null);
        long escalations = agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED, statusIs(OrderStatus.DISPUTED)));
        long reportedChats = agg.count("conversations", Criteria.where("reported").is(true));

        SystemHealthDTO health = probeHealth();

        List<MetricCardDTO> cards = new ArrayList<>();
        cards.add(money("platformGmv", "Platform revenue", gmv, w, gmvPrev, "money", "success",
                "gross trade value in this period"));
        cards.add(money("escrowHeld", "Escrow balance", escrowHeld, w, escrowHeldPrev, "shield", "info",
                "buyer funds not yet released"));
        cards.add(counted("newUsers", "New accounts", newUsers, w, newUsersPrev, "users", "info",
                DisplayFormat.count(totalUsers) + " registered in total"));
        cards.add(MetricCardDTO.of("escalations", "Escalations", DisplayFormat.count(escalations), "flag")
                .withHint(escalations > 0 ? "disputed orders awaiting a decision" : "no open escalations")
                .withTone(escalations > 0 ? "danger" : "success"));
        if (health != null && health.healthy()) {
            cards.add(MetricCardDTO.of("uptime", "Uptime", formatUptime(health.uptimeSeconds()), "clock")
                    .withHint("database answered in " + Math.round(health.databaseLatencyMs()) + " ms")
                    .withTone("success"));
        }

        AnalyticsAggregations.MonthlySeries gmvSeries = agg.monthlyBetween(ORDERS, live, w.from(), w.to(),
                "totalAmount", "createdAt");
        AnalyticsAggregations.MonthlySeries userSeries = agg.monthlyBetween(USERS, null, w.from(), w.to(),
                null, "createdAt");
        Map<String, BigDecimal> regionValue = agg.orderValueByListingField(live, "location");
        Map<String, Long> escrowMix = agg.groupCountBetween(ORDERS, AnalyticsAggregations.NOT_DELETED,
                "escrowStatus", w.from(), w.to(), "createdAt");

        List<ChartSpecDTO> charts = List.of(
                peakBar("platformRevenue", "Revenue by month", "Gross trade value in this period", gmvSeries, w),
                escrowDoughnut("escrowState", escrowMix),
                ChartSpecDTO.of("userGrowth", "Account growth", "New accounts registered each month",
                                "line", userSeries.labels(), List.of(
                                        ChartDatasetDTO.of("New accounts", userSeries.countsAsNumbers()).withFill(true)))
                        .withHeight(240),
                ranked("regionTrade", "Trade by region", "Order value by seller location", regionValue, true));

        List<TableSpecDTO> tables = List.of(
                adminAccessTable("adminAccess"),
                financialAuditTable("financialAudit"));

        List<AttentionItemDTO> attention = new ArrayList<>();
        if (escalations > 0) {
            attention.add(AttentionItemDTO.of("controlEscalations", "disputed trades awaiting a decision",
                    escalations, "danger", "Review escalations", "orders"));
        }
        if (reportedChats > 0) {
            attention.add(AttentionItemDTO.of("controlChats", "conversations were reported by users",
                    reportedChats, "danger", "Moderate chats", "messages"));
        }
        if (health != null && !health.healthy()) {
            attention.add(AttentionItemDTO.of("controlHealth", "the database did not answer the health probe",
                    1, "danger", "Check connectivity", "settings"));
        }

        return DashboardSummaryDTO.of("PLATFORM", "CONTROL", "platform",
                "Platform control center",
                "Governance, access and money movement across every region.",
                cards, charts, tables, attention,
                recentActivity(AnalyticsAggregations.NOT_DELETED, null), health, w.label());
    }

    // ================================================================== health

    /**
     * Measures the live system rather than reading it from a stored snapshot.
     * A failing probe is reported as DOWN with the reason, never as zero-valued UP.
     */
    private SystemHealthDTO probeHealth() {
        long start = System.nanoTime();
        try {
            mongoTemplate.executeCommand(new org.bson.Document("ping", 1));
            double latencyMs = (System.nanoTime() - start) / 1_000_000d;
            Runtime runtime = Runtime.getRuntime();
            return new SystemHealthDTO("UP", latencyMs, 0d,
                    ManagementFactory.getRuntimeMXBean().getUptime() / 1000L,
                    (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                    runtime.maxMemory() / (1024 * 1024),
                    agg.count(USERS, Criteria.where("isActive").is(true)),
                    null, Instant.now());
        } catch (RuntimeException error) {
            return SystemHealthDTO.down(error.getMessage());
        }
    }

    private String formatUptime(long seconds) {
        Duration d = Duration.ofSeconds(Math.max(seconds, 0));
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    // ================================================================== cards

    private MetricCardDTO money(String key, String label, BigDecimal value, Window w,
                                BigDecimal previous, String icon, String tone, String hint) {
        return MetricCardDTO.of(key, label, DisplayFormat.money(value), icon)
                .withHint(hint)
                .withTone(value.signum() > 0 ? tone : "neutral")
                .withTrend(MetricCardDTO.BigDecimalPair.of(value),
                        MetricCardDTO.BigDecimalPair.of(previous), w.trendCaption());
    }

    private MetricCardDTO counted(String key, String label, long value, Window w,
                                  long previous, String icon, String tone, String hint) {
        return MetricCardDTO.of(key, label, DisplayFormat.count(value), icon)
                .withHint(hint)
                .withTone(value > 0 ? tone : "neutral")
                .withTrend(MetricCardDTO.BigDecimalPair.of(BigDecimal.valueOf(value)),
                        MetricCardDTO.BigDecimalPair.of(BigDecimal.valueOf(previous)), w.trendCaption());
    }

    private long activeUsers(Role role) {
        return agg.count(USERS, Criteria.where("role").is(role).and("isActive").is(true));
    }

    // ================================================================== charts

    /** Solid bar for the best month, striped bars for the rest, so the peak reads instantly. */
    private ChartSpecDTO peakBar(String key, String title, String subtitle,
                                 AnalyticsAggregations.MonthlySeries series, Window w) {
        List<Number> values = series.sumsAsNumbers();
        int peak = -1;
        BigDecimal best = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal candidate = BigDecimal.valueOf(values.get(i).doubleValue());
            if (candidate.compareTo(best) > 0) {
                best = candidate;
                peak = i;
            }
        }
        StringBuilder note = new StringBuilder(subtitle);
        if (peak >= 0) {
            note.append(" - best month ").append(series.labels().get(peak))
                    .append(" at ").append(DisplayFormat.money(best));
        }
        return ChartSpecDTO.of(key, title, note.toString(), "bar", series.labels(),
                        List.of(ChartDatasetDTO.of("Value", values)))
                .withHeight(300)
                .withCurrency(true)
                .highlight(peak);
    }

    private ChartSpecDTO escrowFlowChart(String key,
                                         AnalyticsAggregations.MonthlySeries gmv,
                                         AnalyticsAggregations.MonthlySeries held,
                                         AnalyticsAggregations.MonthlySeries released) {
        return ChartSpecDTO.of(key, "Revenue against escrow movement",
                        "Gross trade value next to what is still held and what has been released",
                        "bar", gmv.labels(), List.of(
                        ChartDatasetDTO.of("Gross trade value", gmv.sumsAsNumbers()),
                        ChartDatasetDTO.of("Held in escrow", held.sumsAsNumbers()).withColor("#0ea5e9"),
                        ChartDatasetDTO.of("Released", released.sumsAsNumbers()).withColor("#94a3b8")))
                .withHeight(300)
                .withCurrency(true);
    }

    private ChartSpecDTO statusDoughnut(String key, Map<String, Long> byStatus, String scope) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (count > 0) {
                labels.add(humanize(status.name()));
                values.add(count);
            }
        }
        return ChartSpecDTO.of(key, "Order status mix", "Where " + scope + " orders stand right now",
                        "doughnut", labels, List.of(ChartDatasetDTO.of("Orders", values)))
                .withHeight(280);
    }

    private ChartSpecDTO escrowDoughnut(String key, Map<String, Long> byEscrow) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        for (EscrowStatus status : EscrowStatus.values()) {
            long count = byEscrow.getOrDefault(status.name(), 0L);
            if (count > 0) {
                labels.add(status.displayName());
                values.add(count);
            }
        }
        return ChartSpecDTO.of(key, "Escrow state", "Buyer money by escrow state", "doughnut", labels,
                        List.of(ChartDatasetDTO.of("Orders", values)))
                .withHeight(280);
    }

    private ChartSpecDTO ranked(String key, String title, String subtitle,
                                Map<String, BigDecimal> source, boolean currency) {
        List<Map.Entry<String, BigDecimal>> rows = source.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .toList();
        return ChartSpecDTO.of(key, title, subtitle, "hbar",
                        rows.stream().map(Map.Entry::getKey).toList(),
                        List.of(ChartDatasetDTO.of(title, rows.stream()
                                .map(e -> (Number) e.getValue()).toList())))
                .withHeight(Math.max(220, 40 + rows.size() * 34))
                .withCurrency(currency);
    }

    // ================================================================== tables

    private TableSpecDTO listingTable(String key, String title, Criteria farmerScope, String emptyMessage) {
        Criteria scope = farmerScope == null ? null : new Criteria().andOperator(farmerScope);
        Query query = scope == null ? new Query() : new Query(scope);
        List<ProduceListing> listings = mongoTemplate.find(
                query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(DASHBOARD_TABLE_LIMIT + 1),
                ProduceListing.class);
        boolean truncated = listings.size() > DASHBOARD_TABLE_LIMIT;
        List<ProduceListing> visible = truncated ? listings.subList(0, DASHBOARD_TABLE_LIMIT) : listings;
        Map<String, String> sellers = namesOf(visible.stream()
                .map(ProduceListing::getFarmerId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("product", "Product"),
                TableColumnDTO.of("seller", "Seller"),
                TableColumnDTO.of("category", "Category"),
                TableColumnDTO.right("price", "Price / unit"),
                TableColumnDTO.right("available", "Available"),
                TableColumnDTO.right("reserved", "Reserved"),
                TableColumnDTO.of("status", "Status"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (ProduceListing listing : visible) {
            rows.add(new TableRowDTO(listing.getId(), List.of(
                    nz(listing.getCropName()),
                    sellers.getOrDefault(listing.getFarmerId(), "Unknown"),
                    nz(listing.getCategory()),
                    DisplayFormat.money(listing.getPricePerUnit()) + (nz(listing.getUnit()).isEmpty()
                            ? "" : " /" + listing.getUnit()),
                    DisplayFormat.quantity(listing.getAvailableQuantity()),
                    DisplayFormat.quantity(listing.getReservedQuantity()),
                    listing.getStatus() == null ? "UNKNOWN" : listing.getStatus().name()), "produce"));
        }

        return TableSpecDTO.of(key, title,
                        farmerScope == null ? "Every listing on the marketplace, newest first"
                                : "Your listings, newest first",
                        columns, rows, emptyMessage)
                .viewMore("View all listings", "produce");
    }


    private TableSpecDTO orderTable(String key, String title, Criteria scope, Role viewerRole,
                                   Window w, String route, String emptyMessage) {
        Criteria windowed = and(scope, new Criteria().andOperator(
                Criteria.where("createdAt").gte(w.from().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Criteria.where("createdAt").lt(w.to().plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant())));
        List<Order> orders = mongoTemplate.find(
                new Query(windowed).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(DASHBOARD_TABLE_LIMIT + 1),
                Order.class);
        boolean truncated = orders.size() > DASHBOARD_TABLE_LIMIT;
        List<Order> visible = truncated ? orders.subList(0, DASHBOARD_TABLE_LIMIT) : orders;
        Map<String, String> parties = namesOf(visible.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getFarmerId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        String counterpartyLabel = viewerRole == Role.BUYER ? "Seller" : "Buyer";

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("order", "Order"),
                TableColumnDTO.of("crop", "Crop"),
                TableColumnDTO.of("counterparty", counterpartyLabel),
                TableColumnDTO.right("quantity", "Quantity"),
                TableColumnDTO.right("value", "Value"),
                TableColumnDTO.of("escrow", "Escrow"),
                TableColumnDTO.of("status", "Status"),
                TableColumnDTO.of("placed", "Placed"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (Order order : visible) {
            String counterpartyId = viewerRole == Role.BUYER ? order.getFarmerId() : order.getBuyerId();
            rows.add(new TableRowDTO(order.getId(), List.of(
                    shortId(order.getId()),
                    nz(order.getCropName()),
                    parties.getOrDefault(counterpartyId, "Unknown"),
                    DisplayFormat.quantity(order.getAgreedQuantity()),
                    DisplayFormat.money(order.getTotalAmount()),
                    order.getEscrowStatus() == null ? EscrowStatus.NONE.name() : order.getEscrowStatus().name(),
                    order.getOrderStatus() == null ? "UNKNOWN" : order.getOrderStatus().name(),
                    order.getCreatedAt() == null ? "-" : TABLE_DAY.format(order.getCreatedAt()
                            .atZone(ZoneId.systemDefault()))), route));
        }

        return TableSpecDTO.of(key, title, "Newest 5 in " + w.label(), columns, rows, emptyMessage)
                .viewMore("View all orders", route);
    }

    private TableSpecDTO offerTable(String key, String title, String buyerId) {
        List<Offer> offers = offerRepository.findByBuyerId(buyerId).stream()
                .filter(offer -> offer.getStatus() == OfferStatus.OFFERED
                        || offer.getStatus() == OfferStatus.ACCEPTED)
                .sorted(Comparator.comparing(Offer::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Offer> visible = offers.size() > DASHBOARD_TABLE_LIMIT
                ? offers.subList(0, DASHBOARD_TABLE_LIMIT) : offers;
        Map<String, ProduceListing> listings = listingsById(visible.stream()
                .map(Offer::getListingId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("offer", "Offer"),
                TableColumnDTO.of("crop", "Product"),
                TableColumnDTO.of("farmer", "Farmer"),
                TableColumnDTO.right("quantity", "Quantity"),
                TableColumnDTO.right("offered", "Offered / unit"),
                TableColumnDTO.of("status", "Status"),
                TableColumnDTO.of("raised", "Raised"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (Offer offer : visible) {
            ProduceListing listing = listings.get(offer.getListingId());
            rows.add(new TableRowDTO(offer.getId(), List.of(
                    shortId(offer.getId()),
                    listing == null ? "Listing removed" : nz(listing.getCropName()),
                    listing == null ? "Unknown" : nz(listing.getFarmerId()).isEmpty() ? "Unknown" : farmerName(offer.getFarmerId()),
                    DisplayFormat.quantity(offer.getOfferedQuantity()),
                    DisplayFormat.money(offer.getOfferedPrice()),
                    offer.getStatus() == null ? "UNKNOWN" : offer.getStatus().name(),
                    offer.getCreatedAt() == null ? "-" : TABLE_DAY.format(offer.getCreatedAt()
                            .atZone(ZoneId.systemDefault()))), "orders"));
        }

        return TableSpecDTO.of(key, title, "Your newest open offers", columns, rows,
                        "You have not raised any offers yet.")
                .viewMore("View all offers", "orders");
    }

    private TableSpecDTO disputeTable(String key, String title) {
        List<Order> disputes = mongoTemplate.find(
                new Query(new Criteria().andOperator(AnalyticsAggregations.NOT_DELETED,
                                Criteria.where("orderStatus").is(OrderStatus.DISPUTED)))
                        .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .limit(DASHBOARD_TABLE_LIMIT),
                Order.class);
        Map<String, String> parties = namesOf(disputes.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getFarmerId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("order", "Order"),
                TableColumnDTO.of("crop", "Crop"),
                TableColumnDTO.of("buyer", "Buyer"),
                TableColumnDTO.of("farmer", "Farmer"),
                TableColumnDTO.right("value", "Value"),
                TableColumnDTO.of("escrow", "Escrow"),
                TableColumnDTO.of("raised", "Raised"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (Order order : disputes) {
            rows.add(new TableRowDTO(order.getId(), List.of(
                    shortId(order.getId()),
                    nz(order.getCropName()),
                    parties.getOrDefault(order.getBuyerId(), "Unknown"),
                    parties.getOrDefault(order.getFarmerId(), "Unknown"),
                    DisplayFormat.money(order.getTotalAmount()),
                    order.getEscrowStatus() == null ? EscrowStatus.NONE.name() : order.getEscrowStatus().name(),
                    order.getCreatedAt() == null ? "-" : TABLE_DAY.format(order.getCreatedAt()
                            .atZone(ZoneId.systemDefault()))), "orders"));
        }

        return TableSpecDTO.of(key, title, "Disputed trades that still need a decision", columns, rows,
                "No disputes are open. Disputed orders appear here until an admin decides.")
                .viewMore("View disputed orders", "orders");
    }

    private TableSpecDTO adminAccessTable(String key) {
        List<User> staff = new ArrayList<>();
        staff.addAll(userRepository.findByRole(Role.ADMIN));
        staff.addAll(userRepository.findByRole(Role.SUPER_ADMIN));
        staff.sort(Comparator.comparing(User::getName, Comparator.nullsLast(String::compareTo)));
        Map<String, Instant> lastSeen = lastActivityByUser(staff.stream().map(User::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("name", "Administrator"),
                TableColumnDTO.of("email", "Email"),
                TableColumnDTO.of("role", "Role"),
                TableColumnDTO.of("status", "Status"),
                TableColumnDTO.of("lastActivity", "Last recorded activity"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (User user : staff) {
            Instant seen = lastSeen.get(user.getId());
            rows.add(new TableRowDTO(user.getId(), List.of(
                    nz(user.getName()),
                    nz(user.getEmail()),
                    user.getRole() == null ? "-" : user.getRole().name(),
                    user.isActive() ? "ACTIVE" : "SUSPENDED",
                    seen == null ? "No recorded activity" : TABLE_DAY.format(seen.atZone(ZoneId.systemDefault())))));
        }

        return TableSpecDTO.of(key, "Administrative access", "Who can change platform configuration",
                        columns, rows, "No administrator accounts exist.")
                .viewMore("Manage users", "users");
    }

    private TableSpecDTO financialAuditTable(String key) {
        Criteria flagged = new Criteria().andOperator(AnalyticsAggregations.NOT_DELETED,
                new Criteria().orOperator(
                        Criteria.where("orderStatus").is(OrderStatus.DISPUTED),
                        Criteria.where("escrowStatus").is(EscrowStatus.ESCROW_HELD)));
        List<Order> orders = mongoTemplate.find(
                new Query(flagged).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(DASHBOARD_TABLE_LIMIT),
                Order.class);
        Map<String, String> parties = namesOf(orders.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getFarmerId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<TableColumnDTO> columns = List.of(
                TableColumnDTO.of("order", "Order"),
                TableColumnDTO.of("crop", "Crop"),
                TableColumnDTO.of("buyer", "Buyer"),
                TableColumnDTO.of("farmer", "Farmer"),
                TableColumnDTO.right("value", "Value"),
                TableColumnDTO.of("flag", "Flag"),
                TableColumnDTO.of("escrow", "Escrow"));

        List<TableRowDTO> rows = new ArrayList<>();
        for (Order order : orders) {
            boolean disputed = order.getOrderStatus() == OrderStatus.DISPUTED;
            rows.add(new TableRowDTO(order.getId(), List.of(
                    shortId(order.getId()),
                    nz(order.getCropName()),
                    parties.getOrDefault(order.getBuyerId(), "Unknown"),
                    parties.getOrDefault(order.getFarmerId(), "Unknown"),
                    DisplayFormat.money(order.getTotalAmount()),
                    disputed ? "DISPUTED" : "ESCROW HELD",
                    order.getEscrowStatus() == null ? EscrowStatus.NONE.name() : order.getEscrowStatus().name()), "orders"));
        }

        return TableSpecDTO.of(key, "Financial audit", "Disputed trades and money still held in escrow",
                        columns, rows, "Nothing is disputed and no money is held in escrow.")
                .viewMore("View orders", "orders");
    }

    // ================================================================== activity & attention

    private List<ActivityItemDTO> recentActivity(Criteria scope, String viewerId) {
        List<Order> orders = mongoTemplate.find(
                new Query(scope).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(8), Order.class);
        Map<String, String> names = namesOf(orders.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getFarmerId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<ActivityItemDTO> items = new ArrayList<>();
        for (Order order : orders) {
            boolean iAmBuyer = viewerId != null && viewerId.equals(order.getBuyerId());
            String other = viewerId == null ? order.getFarmerId()
                    : (iAmBuyer ? order.getFarmerId() : order.getBuyerId());
            EscrowStatus escrow = order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus();
            String subtitle = switch (escrow) {
                case NONE -> "Awaiting payment";
                case ESCROW_HELD -> "Paid, held in escrow";
                case RELEASED -> "Delivered, escrow released";
                case REFUNDED -> "Refunded";
                case FAILED -> "Payment failed";
            };
            items.add(new ActivityItemDTO(order.getId(), "order",
                    nz(order.getCropName()) + " · " + DisplayFormat.quantity(order.getAgreedQuantity()),
                    subtitle,
                    order.getTotalAmount() == null ? null : DisplayFormat.moneyShort(order.getTotalAmount()),
                    order.getOrderStatus() == null ? null : order.getOrderStatus().name(),
                    names.getOrDefault(other, "Unknown"),
                    order.getCreatedAt(), "orders"));
        }
        return items;
    }

    private List<AttentionItemDTO> farmerAttention(BigDecimal sales, BigDecimal escrow, long activeListings) {
        List<AttentionItemDTO> items = new ArrayList<>();
        if (activeListings == 0) {
            items.add(AttentionItemDTO.of("farmerNoListings",
                    "no active listings, so buyers cannot find your produce", 0,
                    "warning", "Add supply", "produce"));
        }
        if (escrow.signum() > 0) {
            items.add(AttentionItemDTO.of("farmerEscrow", "money is held until you deliver", 1,
                    "info", "Track deliveries", "orders"));
        }
        if (sales.signum() == 0) {
            items.add(AttentionItemDTO.of("farmerNoSales", "no sales in this period", 0,
                    "info", "Review pricing", "produce"));
        }
        return items;
    }


    private List<AttentionItemDTO> buyerAttention(BigDecimal escrow) {
        List<AttentionItemDTO> items = new ArrayList<>();
        if (escrow.signum() > 0) {
            items.add(AttentionItemDTO.of("buyerEscrow", "money is protected in escrow", 1,
                    "info", "Track orders", "orders"));
        }
        return items;
    }

    // ================================================================== lookups

    private Map<String, String> namesOf(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> nz(u.getName()), (a, b) -> a));
    }

    private Map<String, String> farmerNames(Set<String> ids) {
        Map<String, String> names = namesOf(ids);
        return names;
    }

    private String farmerName(String farmerId) {
        return farmerNames(Set.of(farmerId)).getOrDefault(farmerId, "Unknown");
    }

    private Map<String, ProduceListing> listingsById(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, ProduceListing> out = new LinkedHashMap<>();
        mongoTemplate.find(new Query(new Criteria().where("id").in(ids)), ProduceListing.class)
                .forEach(listing -> out.put(listing.getId(), listing));
        return out;
    }


    private Map<String, Instant> lastActivityByUser(Set<String> userIds) {
        Map<String, Instant> latest = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return latest;
        }
        Criteria involved = new Criteria().orOperator(
                Criteria.where("buyerId").in(userIds),
                Criteria.where("farmerId").in(userIds));
        for (Order order : mongoTemplate.find(
                new Query(involved).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(200), Order.class)) {
            Instant placedAt = order.getCreatedAt();
            if (placedAt == null) {
                continue;
            }
            for (String participant : List.of(order.getBuyerId(), order.getFarmerId())) {
                if (userIds.contains(participant)) {
                    latest.merge(participant, placedAt, (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        }
        return latest;
    }

    // ================================================================== criteria helpers

    private static Criteria and(Criteria first, Criteria second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : new Criteria().andOperator(first, second);
    }

    private static Criteria statusIs(OrderStatus status) {
        return Criteria.where("orderStatus").is(status);
    }

    private static Criteria statusIs(ListingStatus status) {
        return Criteria.where("status").is(status);
    }

    private static Criteria escrowIs(EscrowStatus status) {
        return Criteria.where("escrowStatus").is(status);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "-";
        }
        return id.length() <= 8 ? id : id.substring(id.length() - 8);
    }

    private static String humanize(String token) {
        String[] words = token.toLowerCase().split("_");
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return out.toString();
    }

    // ================================================================== window


    public record Window(LocalDate from, LocalDate to,
                         LocalDate previousFrom, LocalDate previousTo,
                         int days, String label) {

        public static Window of(LocalDate from, LocalDate to) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(29) : from;
            if (start.isAfter(end)) {
                LocalDate swap = start;
                start = end;
                end = swap;
            }
            long span = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (span > MAX_WINDOW_DAYS) {
                start = end.minusDays(MAX_WINDOW_DAYS - 1L);
                span = MAX_WINDOW_DAYS;
            }
            int days = (int) span;
            LocalDate previousTo = start.minusDays(1);
            LocalDate previousFrom = previousTo.minusDays(days - 1L);
            return new Window(start, end, previousFrom, previousTo, days,
                    RANGE_DAY.format(start) + " - " + RANGE_DAY.format(end));
        }

        public String trendCaption() {
            return days == 1 ? "vs the previous day" : "vs the previous " + days + " days";
        }
    }
}
