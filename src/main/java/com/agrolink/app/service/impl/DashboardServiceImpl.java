package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.ActivityItemDTO;
import com.agrolink.app.dto.AttentionItemDTO;
import com.agrolink.app.dto.ChartDatasetDTO;
import com.agrolink.app.dto.ChartSpecDTO;
import com.agrolink.app.dto.DashboardSummaryDTO;
import com.agrolink.app.dto.MetricCardDTO;
import com.agrolink.app.model.EscrowStatus;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.OfferStatus;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.PaymentMethod;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.OfferRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.DashboardService;
import com.agrolink.app.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String ORDERS = "orders";
    private static final String LISTINGS = "produce_listings";
    private static final String OFFERS = "offers";
    private static final String USERS = "users";
    private static final String CONVERSATIONS = "conversations";

    /** Order value is "real" for these states; cancelled and disputed money never counts as trade. */
    private static final Set<OrderStatus> LIVE_ORDER_STATES = Set.of(
            OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID_CONFIRMED,
            OrderStatus.ESCROW_HELD, OrderStatus.PROCESSING, OrderStatus.CONFIRMED,
            OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    /** Everything physically moving right now, which is what a buyer asks "where is it?". */
    private static final Set<OrderStatus> IN_TRANSIT_STATES = Set.of(
            OrderStatus.PAID_CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT);

    private static final Set<EscrowStatus> PAID_ESCROW = Set.of(EscrowStatus.ESCROW_HELD, EscrowStatus.RELEASED);

    private static final int ACTIVITY_LIMIT = 8;

    private final AnalyticsAggregations agg;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final ManagementDashboardBuilder managementBuilder;

    // ================================================================== entry points

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'summary-' + #role + '-' + #userId")
    public DashboardSummaryDTO summary(String userId, Role role) {
        return switch (role == null ? Role.BUYER : role) {
            case FARMER -> farmerSummary(userId);
            case ADMIN, SUPER_ADMIN -> platformSummary(6);
            default -> buyerSummary(userId);
        };
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'analytics-' + #months")
    public DashboardSummaryDTO analytics(int months) {
        return platformSummary(clampMonths(months));
    }

    @Override
    public DashboardSummaryDTO dashboard(String userId, Role role, java.time.LocalDate from, java.time.LocalDate to) {
        return managementBuilder.build(userId, role, from, to);
    }

    // ================================================================== buyer

    private DashboardSummaryDTO buyerSummary(String buyerId) {
        Criteria mine = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("buyerId").is(buyerId));
        Criteria live = and(mine, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));

        Map<String, Long> byStatus = statusCounts(mine);
        BigDecimal committed = sumField(live, "totalAmount");

        long awaitingPayment = orderCount(and(mine, Criteria.where("orderStatus")
                .in(OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING)));
        long inEscrow = byStatus.getOrDefault(OrderStatus.ESCROW_HELD.name(), 0L);
        long inTransit = byStatus.entrySet().stream()
                .filter(e -> IN_TRANSIT_STATES.contains(orderStatusOf(e.getKey())))
                .mapToLong(Map.Entry::getValue).sum();
        long delivered = byStatus.getOrDefault(OrderStatus.DELIVERED.name(), 0L);
        long disputed = byStatus.getOrDefault(OrderStatus.DISPUTED.name(), 0L);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long openOffers = offerRepository.countByBuyerIdAndStatus(buyerId, OfferStatus.OFFERED);
        long availableCrops = listingCount(ListingStatus.ACTIVE);

        List<MetricCardDTO> cards = List.of(
                MetricCardDTO.of("buyerOrders", "My orders", DisplayFormat.count(total), "orders").withTone("info"),
                card("buyerAwaiting", "Awaiting payment", DisplayFormat.count(awaitingPayment), "wallet", "info",
                        "address and pay to move this forward", awaitingPayment > 0 ? "action needed" : null),
                card("buyerEscrow", "In escrow", DisplayFormat.count(inEscrow), "shield", "success",
                        "money held safely until delivery", inEscrow > 0 ? "protected" : null),
                card("buyerTransit", "On the road", DisplayFormat.count(inTransit), "truck", "info",
                        "paid, not delivered yet", null),
                card("buyerDelivered", "Delivered", DisplayFormat.count(delivered), "check", "success",
                        "escrow released, order settled", null),
                MetricCardDTO.of("buyerCommitted", "Committed value", DisplayFormat.moneyShort(committed), "wallet")
                        .withHint("total value of your live orders")
                        .withTone(committed.signum() > 0 ? "success" : "neutral"),
                card("buyerOffers", "Offers waiting", DisplayFormat.count(openOffers), "chat", "info",
                        "farmers have replied to your listing interest", openOffers > 0 ? "open" : null),
                MetricCardDTO.of("buyerMarket", "Crops on market", DisplayFormat.count(availableCrops), "seedling")
                        .withHint("active listings you can buy from"));

        var spend = agg.monthly(ORDERS, live, 6, "totalAmount", "createdAt");
        var cropMix = agg.groupSum(ORDERS, live, "cropName", "totalAmount");

        List<ChartSpecDTO> charts = List.of(
                ChartSpecDTO.of("buyerSpend", "My spending", "Value of your orders placed each month",
                                "area", spend.labels(), List.of(
                                        ChartDatasetDTO.of("Committed", spend.sumsAsNumbers()).withFill(true)))
                        .withHeight(260).withCurrency(true),
                doughnut("buyerStatusMix", "Where my orders stand", "Live order count by stage",
                        orderedStatusLabels(byStatus), statusValues(byStatus)),
                hbar("buyerCrops", "What I buy", "Your order value by crop", topN(cropMix, 8)));

        List<AttentionItemDTO> attention = new ArrayList<>();
        if (awaitingPayment > 0) {
            attention.add(AttentionItemDTO.of("buyerPay", "orders are waiting for payment", awaitingPayment,
                    "warning", "Pay now", "orders"));
        }
        if (disputed > 0) {
            attention.add(AttentionItemDTO.of("buyerDispute", "disputes under review", disputed,
                    "danger", "View disputes", "orders"));
        }
        if (inTransit > 0) {
            attention.add(AttentionItemDTO.of("buyerTransit", "deliveries on the way", inTransit,
                    "info", "Track orders", "orders"));
        }

        return DashboardSummaryDTO.of(Role.BUYER.name(), "buyer", cards, charts, attention,
                activity(mine, buyerId));
    }

    // ================================================================== farmer

    private DashboardSummaryDTO farmerSummary(String farmerId) {
        Criteria mine = and(AnalyticsAggregations.NOT_DELETED, Criteria.where("farmerId").is(farmerId));
        Criteria live = and(mine, Criteria.where("orderStatus").in(LIVE_ORDER_STATES));
        Criteria paid = and(mine, Criteria.where("escrowStatus").in(PAID_ESCROW));

        Map<String, Long> byStatus = statusCounts(mine);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        // Offers, not orders: an order only exists once the farmer has already accepted.
        long offersToAccept = offerRepository.countByFarmerIdAndStatus(farmerId, OfferStatus.OFFERED);
        long offersAccepted = offerRepository.countByFarmerIdAndStatus(farmerId, OfferStatus.ACCEPTED);
        long awaitingDispatch = statusCount(mine, OrderStatus.PAID_CONFIRMED, OrderStatus.PROCESSING);
        long inTransit = statusCount(mine, OrderStatus.IN_TRANSIT);
        long delivered = statusCount(mine, OrderStatus.DELIVERED);
        long disputed = statusCount(mine, OrderStatus.DISPUTED);
        long awaitingPayment = statusCount(mine, OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING);
        long escrowOrders = escrowCount(mine, EscrowStatus.ESCROW_HELD);

        BigDecimal escrowValue = sumEscrow(mine, EscrowStatus.ESCROW_HELD, "totalAmount");
        BigDecimal earned = sumEscrow(mine, EscrowStatus.RELEASED, "totalAmount");

        Criteria myListings = Criteria.where("farmerId").is(farmerId);
        long activeListings = listingCount(and(myListings, Criteria.where("status").is(ListingStatus.ACTIVE)));
        long draftListings = listingCount(and(myListings, Criteria.where("status").is(ListingStatus.DRAFT)));
        long archivedListings = listingCount(and(myListings, Criteria.where("status").is(ListingStatus.ARCHIVED)));

        List<MetricCardDTO> cards = List.of(
                card("farmerToAccept", "Offers to accept", DisplayFormat.count(offersToAccept), "inbox", "warning",
                        "buyers waiting for your decision", offersToAccept > 0 ? "action needed" : null),
                card("farmerDispatch", "Ready to dispatch", DisplayFormat.count(awaitingDispatch), "box", "info",
                        "paid orders waiting for you to start delivery", awaitingDispatch > 0 ? "ship it" : null),
                MetricCardDTO.of("farmerEscrow", "In escrow", DisplayFormat.moneyShort(escrowValue), "shield")
                        .withHint(escrowOrders + " paid order(s) awaiting delivery")
                        .withTone(escrowValue.signum() > 0 ? "info" : "neutral"),
                MetricCardDTO.of("farmerEarned", "Earned", DisplayFormat.moneyShort(earned), "money")
                        .withHint("released after delivery")
                        .withTone(earned.signum() > 0 ? "success" : "neutral"),
                card("farmerTransit", "On the road", DisplayFormat.count(inTransit), "truck", "info",
                        "deliveries in progress", null),
                MetricCardDTO.of("farmerDelivered", "Delivered", DisplayFormat.count(delivered), "check")
                        .withHint("settled orders"),
                MetricCardDTO.of("farmerListings", "Active listings", DisplayFormat.count(activeListings), "seedling")
                        .withHint(draftListings + " draft, " + archivedListings + " archived")
                        .withTone(activeListings > 0 ? "success" : "warning"),
                card("farmerOffers", "Offers won", DisplayFormat.count(offersAccepted), "chat", "info",
                        "offers you have accepted", offersAccepted > 0 ? "accepted" : null));

        // Earnings follow paidAt (when the money actually arrived), not createdAt.
        var earnings = agg.monthly(ORDERS,
                and(mine, Criteria.where("escrowStatus").in(PAID_ESCROW)), 6, "totalAmount", "paidAt");
        var orderValue = agg.monthly(ORDERS, live, 6, "totalAmount", "createdAt");
        var cropValue = agg.groupSum(ORDERS, live, "cropName", "totalAmount");
        var inventory = agg.groupSum(LISTINGS, and(myListings, Criteria.where("status").is(ListingStatus.ACTIVE)),
                "cropName", "availableQuantity");
        var reserved = agg.groupSum(LISTINGS, and(myListings, Criteria.where("status").is(ListingStatus.ACTIVE)),
                "cropName", "reservedQuantity");

        List<String> stockCrops = new ArrayList<>(inventory.keySet());
        List<Number> availableSeries = new ArrayList<>();
        List<Number> reservedSeries = new ArrayList<>();
        for (String crop : stockCrops) {
            availableSeries.add(inventory.getOrDefault(crop, BigDecimal.ZERO));
            reservedSeries.add(reserved.getOrDefault(crop, BigDecimal.ZERO));
        }

        List<ChartSpecDTO> charts = List.of(
                ChartSpecDTO.of("farmerEarnings", "My earnings", "Escrow-backed value by payment month",
                                "area", earnings.labels(), List.of(
                                        ChartDatasetDTO.of("Earned", earnings.sumsAsNumbers()).withFill(true)))
                        .withHeight(260).withCurrency(true),
                ChartSpecDTO.of("farmerOrderTrend", "Order flow", "How many orders arrive each month",
                                "bar", orderValue.labels(), List.of(
                                        ChartDatasetDTO.of("Orders", orderValue.countsAsNumbers())))
                        .withHeight(220),
                doughnut("farmerStatusMix", "Where my orders stand", "Live order count by stage",
                        orderedStatusLabels(byStatus), statusValues(byStatus)),
                ChartSpecDTO.of("farmerStock", "Stock on hand", "Available versus reserved, by crop",
                                "bar", stockCrops, List.of(
                                        ChartDatasetDTO.of("Available", availableSeries),
                                        ChartDatasetDTO.of("Reserved", reservedSeries).withColor("#f59e0b")))
                        .withHeight(240).withUnit("units"),
                hbar("farmerCrops", "What sells", "Your order value by crop", topN(cropValue, 8)));

        List<AttentionItemDTO> attention = new ArrayList<>();
        if (offersToAccept > 0) {
            attention.add(AttentionItemDTO.of("farmerAccept", "offers are waiting for you to accept", offersToAccept,
                    "warning", "Review offers", "orders"));
        }
        if (awaitingDispatch > 0) {
            attention.add(AttentionItemDTO.of("farmerDispatch", "paid orders need dispatch", awaitingDispatch,
                    "info", "Start fulfilment", "orders"));
        }
        if (awaitingPayment > 0) {
            attention.add(AttentionItemDTO.of("farmerOwed", "accepted orders still awaiting buyer payment",
                    awaitingPayment, "info", "View orders", "orders"));
        }
        if (disputed > 0) {
            attention.add(AttentionItemDTO.of("farmerDispute", "disputes raised on your orders", disputed,
                    "danger", "View disputes", "orders"));
        }
        if (activeListings == 0) {
            attention.add(AttentionItemDTO.of("farmerNoListing",
                    "you have no active listings, so buyers cannot find you", 0,
                    "info", "Add supply", "produce"));
        }

        return DashboardSummaryDTO.of(Role.FARMER.name(), "farmer", cards, charts, attention,
                activity(mine, farmerId));
    }

    // ================================================================== platform (admin / super admin)

    private DashboardSummaryDTO platformSummary(int months) {
        Criteria live = and(AnalyticsAggregations.NOT_DELETED,
                Criteria.where("orderStatus").in(LIVE_ORDER_STATES));
        Criteria paid = and(live, Criteria.where("escrowStatus").in(PAID_ESCROW));

        BigDecimal grossValue = sumField(live, "totalAmount");
        BigDecimal escrowHeld = sumEscrow(live, EscrowStatus.ESCROW_HELD, "totalAmount");
        BigDecimal released = sumEscrow(live, EscrowStatus.RELEASED, "totalAmount");
        BigDecimal awaitingPayment = sumEscrow(live, EscrowStatus.NONE, "totalAmount");

        Map<String, Long> orderStatus = statusCounts(AnalyticsAggregations.NOT_DELETED);
        Map<String, Long> escrowStatus = agg.groupCount(ORDERS, AnalyticsAggregations.NOT_DELETED, "escrowStatus");
        Map<String, Long> paymentMethods = agg.groupCount(ORDERS, paid, "paymentMethod");
        Map<String, Long> offerStatus = agg.groupCount(OFFERS, null, "status");

        long totalOrders = agg.count(ORDERS, AnalyticsAggregations.NOT_DELETED);
        long disputes = orderStatus.getOrDefault(OrderStatus.DISPUTED.name(), 0L);
        long cancelled = orderStatus.getOrDefault(OrderStatus.CANCELLED.name(), 0L);
        long farmers = agg.count(USERS, Criteria.where("role").is(Role.FARMER));
        long buyers = agg.count(USERS, Criteria.where("role").is(Role.BUYER));
        long admins = agg.count(USERS, Criteria.where("role").in(Role.ADMIN, Role.SUPER_ADMIN));
        long activeListings = listingCount(ListingStatus.ACTIVE);
        long draftListings = listingCount(ListingStatus.DRAFT);
        long reportedConversations = agg.count(CONVERSATIONS, Criteria.where("reported").is(true));

        List<MetricCardDTO> cards = List.of(
                MetricCardDTO.of("gmv", "Gross trade value", DisplayFormat.moneyShort(grossValue), "money")
                        .withHint("value of all live orders").withTone("info"),
                card("escrowHeld", "Held in escrow", DisplayFormat.moneyShort(escrowHeld), "shield", "info",
                        "paid, not yet delivered", escrowHeld.signum() > 0 ? "protected" : null),
                card("released", "Escrow released", DisplayFormat.moneyShort(released), "check", "success",
                        "delivered and settled", released.signum() > 0 ? "settled" : null),
                card("awaitingPay", "Awaiting payment", DisplayFormat.moneyShort(awaitingPayment), "wallet", "warning",
                        "committed but not paid", awaitingPayment.signum() > 0 ? "chase" : null),
                card("orders", "Orders", DisplayFormat.count(totalOrders), "box", "neutral",
                        cancelled + " cancelled, " + disputes + " disputed", null),
                card("disputes", "Open disputes", DisplayFormat.count(disputes), "flag", "danger",
                        "need an admin decision", disputes > 0 ? "review" : null),
                MetricCardDTO.of("listings", "Active listings", DisplayFormat.count(activeListings), "seedling")
                        .withHint(draftListings + " still in draft").withTone("success"),
                card("people", "Farmers and buyers", DisplayFormat.count(farmers + buyers), "users", "info",
                        DisplayFormat.count(admins) + " admin staff", null),
                card("buyers", "Buyer accounts", DisplayFormat.count(buyers), "cart", "info",
                        "registered demand side", null));

        var trade = agg.monthly(ORDERS, live, months, "totalAmount", "createdAt");
        var userGrowth = agg.monthly(USERS, null, months, null, "createdAt");
        var listingGrowth = agg.monthly(LISTINGS, null, months, null, "createdAt");
        var supply = agg.groupSum(LISTINGS, Criteria.where("status").is(ListingStatus.ACTIVE),
                "cropName", "availableQuantity");
        var demand = agg.groupSum(ORDERS, live, "cropName", "agreedQuantity");
        var districtValue = agg.orderValueByListingField(live, "district");
        var categoryValue = agg.orderValueByListingField(live, "category");
        var cropValue = agg.groupSum(ORDERS, live, "cropName", "totalAmount");

        List<ChartSpecDTO> charts = List.of(
                ChartSpecDTO.of("tradeTrend", "Trade value and order count",
                                "Monthly order value against how many orders were placed",
                                "bar", trade.labels(), List.of(
                                        ChartDatasetDTO.of("Order value", trade.sumsAsNumbers()),
                                        ChartDatasetDTO.of("Orders", trade.countsAsNumbers())
                                                .withAxis("y1").withColor("#f59e0b")))
                        .withHeight(300).withCurrency(true),
                ChartSpecDTO.of("growth", "Platform growth", "New accounts, listings and orders each month",
                                "line", userGrowth.labels(), List.of(
                                        ChartDatasetDTO.of("New users", userGrowth.countsAsNumbers()).withFill(true),
                                        ChartDatasetDTO.of("New listings", listingGrowth.countsAsNumbers()).withColor("#3b82f6"),
                                        ChartDatasetDTO.of("New orders", trade.countsAsNumbers()).withColor("#a855f7")))
                        .withHeight(280),
                doughnut("orderStatusMix", "Order status mix", "Every order and where it sits",
                        orderedStatusLabels(orderStatus), statusValues(orderStatus)),
                paymentDoughnut("paymentMix", "Payment methods", "How paid orders were paid", paymentMethods),
                doughnut("escrowMix", "Escrow state", "Money currently held, released or refunded",
                        escrowLabels(escrowStatus), escrowValues(escrowStatus)),
                offerFunnel("offerFunnel", "Offer funnel", "How offers end up, from raised to accepted", offerStatus),
                groupedSupplyDemand("supplyDemand", "Supply versus demand",
                        "Active stock against agreed order quantity, by crop", supply, demand),
                hbar("districtReach", "Where trade happens", "Order value by seller district",
                        topN(districtValue, 8)).withCurrency(true),
                hbar("categoryMix", "Category mix", "Order value by produce category",
                        topN(categoryValue, 8)).withCurrency(true),
                hbar("topCrops", "Top crops by value", "Order value by crop", topN(cropValue, 8)).withCurrency(true));

        List<AttentionItemDTO> attention = new ArrayList<>();
        if (disputes > 0) {
            attention.add(AttentionItemDTO.of("platDispute", "disputes need an admin decision", disputes,
                    "danger", "Resolve disputes", "orders"));
        }
        long stalePending = stalePendingCount();
        if (stalePending > 0) {
            attention.add(AttentionItemDTO.of("platStale", "orders sitting unpaid for more than 3 days",
                    stalePending, "warning", "Chase payment", "orders"));
        }
        if (escrowHeld.signum() > 0) {
            attention.add(AttentionItemDTO.of("platEscrow",
                    "money is in escrow waiting for delivery", 1, "info", "Track deliveries", "orders"));
        }
        if (reportedConversations > 0) {
            attention.add(AttentionItemDTO.of("platReported", "conversations were reported by users",
                    reportedConversations, "danger", "Moderate", "messages"));
        }
        if (draftListings > 0) {
            attention.add(AttentionItemDTO.of("platDraft", "listings are stuck in draft", draftListings,
                    "info", "Review listings", "produce"));
        }

        return DashboardSummaryDTO.of("PLATFORM", "platform", cards, charts, attention,
                activity(AnalyticsAggregations.NOT_DELETED, null));
    }

    // ================================================================== shared builders

    private long stalePendingCount() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(3));
        return agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED,
                Criteria.where("orderStatus").in(OrderStatus.OFFER_ACCEPTED, OrderStatus.PAYMENT_PENDING),
                Criteria.where("createdAt").lt(cutoff)));
    }

    private List<ActivityItemDTO> activity(Criteria scope, String viewerId) {
        List<Order> orders = mongoTemplate.find(
                new Query(scope)
                        .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .limit(ACTIVITY_LIMIT),
                Order.class);

        Set<String> userIds = new java.util.HashSet<>();
        for (Order order : orders) {
            userIds.add(order.getBuyerId());
            userIds.add(order.getFarmerId());
        }
        Map<String, String> names = namesOf(userIds);

        List<ActivityItemDTO> items = new ArrayList<>();
        for (Order order : orders) {
            // Buyers and farmers see the other side; platform staff see the supplier behind the
            // order, because that is who the gross trade value is attributed to.
            boolean iAmBuyer = viewerId != null && viewerId.equals(order.getBuyerId());
            String otherPartyId = viewerId == null
                    ? order.getFarmerId()
                    : (iAmBuyer ? order.getFarmerId() : order.getBuyerId());
            String counterparty = names.getOrDefault(otherPartyId, "Unknown");
            EscrowStatus escrow = order.getEscrowStatus() == null ? EscrowStatus.NONE : order.getEscrowStatus();
            String subtitle = switch (escrow) {
                case NONE -> order.getOrderStatus() == OrderStatus.CANCELLED
                        ? "Cancelled before payment"
                        : "Awaiting payment";
                case ESCROW_HELD -> "Paid, held in escrow";
                case RELEASED -> "Delivered, escrow released";
                case REFUNDED -> "Refunded";
                case FAILED -> "Payment failed";
            };
            items.add(new ActivityItemDTO(
                    order.getId(),
                    "order",
                    order.getCropName() + " Â· " + DisplayFormat.quantity(order.getAgreedQuantity()),
                    subtitle,
                    order.getTotalAmount() == null ? null : DisplayFormat.moneyShort(order.getTotalAmount()),
                    order.getOrderStatus() == null ? null : order.getOrderStatus().name(),
                    counterparty,
                    order.getCreatedAt(),
                    "orders"));
        }
        return items;
    }

    /** Resolves display names in a single query; the dashboard renders up to ACTIVITY_LIMIT orders. */
    private Map<String, String> namesOf(Set<String> ids) {
        Set<String> wanted = ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(wanted).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }

    private Map<String, Long> statusCounts(Criteria scope) {
        return agg.groupCount(ORDERS, scope, "orderStatus");
    }

    private List<String> orderedStatusLabels(Map<String, Long> byStatus) {
        List<String> labels = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (count > 0) {
                labels.add(humanize(status.name()));
            }
        }
        return labels;
    }

    private List<Number> statusValues(Map<String, Long> byStatus) {
        List<Number> values = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (count > 0) {
                values.add(count);
            }
        }
        return values;
    }

    private List<String> escrowLabels(Map<String, Long> byStatus) {
        List<String> labels = new ArrayList<>();
        for (EscrowStatus status : EscrowStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (count > 0) {
                labels.add(status.displayName());
            }
        }
        return labels;
    }

    private List<Number> escrowValues(Map<String, Long> byStatus) {
        List<Number> values = new ArrayList<>();
        for (EscrowStatus status : EscrowStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (count > 0) {
                values.add(count);
            }
        }
        return values;
    }

    private ChartSpecDTO paymentDoughnut(String key, String title, String subtitle, Map<String, Long> byMethod) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            long count = byMethod.getOrDefault(method.name(), 0L);
            if (count > 0) {
                labels.add(method.displayName());
                values.add(count);
            }
        }
        return doughnut(key, title, subtitle, labels, values);
    }

    private ChartSpecDTO offerFunnel(String key, String title, String subtitle, Map<String, Long> byStatus) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        for (OfferStatus status : OfferStatus.values()) {
            labels.add(humanize(status.name()));
            values.add(byStatus.getOrDefault(status.name(), 0L));
        }
        long accepted = byStatus.getOrDefault(OfferStatus.ACCEPTED.name(), 0L);
        long raised = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return ChartSpecDTO.of(key, title,
                subtitle + (raised > 0 ? " - " + DisplayFormat.percent((double) accepted / raised) + " accepted" : ""),
                "bar", labels, List.of(ChartDatasetDTO.of("Offers", values)))
                .withHeight(240);
    }

    private ChartSpecDTO groupedSupplyDemand(String key, String title, String subtitle,
                                             Map<String, BigDecimal> supply, Map<String, BigDecimal> demand) {
        List<String> crops = new ArrayList<>(supply.keySet());
        crops.addAll(demand.keySet());
        crops = crops.stream().distinct().limit(10).toList();

        List<Number> supplySeries = new ArrayList<>();
        List<Number> demandSeries = new ArrayList<>();
        for (String crop : crops) {
            supplySeries.add(supply.getOrDefault(crop, BigDecimal.ZERO));
            demandSeries.add(demand.getOrDefault(crop, BigDecimal.ZERO));
        }
        return ChartSpecDTO.of(key, title, subtitle, "bar", crops, List.of(
                        ChartDatasetDTO.of("Available stock", supplySeries),
                        ChartDatasetDTO.of("Ordered quantity", demandSeries).withColor("#f59e0b")))
                .withHeight(280);
    }

    private ChartSpecDTO doughnut(String key, String title, String subtitle,
                                  List<String> labels, List<Number> values) {
        return ChartSpecDTO.of(key, title, subtitle, "doughnut", labels,
                List.of(ChartDatasetDTO.of(title, values))).withHeight(300);
    }

    private ChartSpecDTO hbar(String key, String title, String subtitle, List<Map.Entry<String, BigDecimal>> rows) {
        return ChartSpecDTO.of(key, title, subtitle, "hbar",
                rows.stream().map(Map.Entry::getKey).toList(),
                List.of(ChartDatasetDTO.of(title, rows.stream()
                        .map(e -> (Number) e.getValue()).toList())))
                .withHeight(Math.max(220, 40 + rows.size() * 34));
    }

    private List<Map.Entry<String, BigDecimal>> topN(Map<String, BigDecimal> source, int limit) {
        return source.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private MetricCardDTO card(String key, String label, String value, String icon,
                               String tone, String hint, String badge) {
        return MetricCardDTO.of(key, label, value, icon).withHint(hint).withTone(tone).withBadge(badge);
    }

    // ================================================================== small queries

    private long orderCount(Criteria match) {
        return agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED, match));
    }

    private long statusCount(Criteria scope, OrderStatus... statuses) {
        return agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED, scope,
                Criteria.where("orderStatus").in(statuses)));
    }

    private long escrowCount(Criteria scope, EscrowStatus... states) {
        return agg.count(ORDERS, and(AnalyticsAggregations.NOT_DELETED, scope,
                Criteria.where("escrowStatus").in(states)));
    }

    private long listingCount(ListingStatus status) {
        return agg.count(LISTINGS, Criteria.where("status").is(status));
    }

    private long listingCount(Criteria extra) {
        return agg.count(LISTINGS, extra);
    }

    /** Sum of {@code field} over every order still inside {@link #LIVE_ORDER_STATES}. */
    private BigDecimal sumField(Criteria scope, String field) {
        Map<String, BigDecimal> byStatus = agg.groupSum(ORDERS, scope, "orderStatus", field);
        return byStatus.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of {@code field} for orders whose escrow currently sits in {@code state}. */
    private BigDecimal sumEscrow(Criteria scope, EscrowStatus state, String field) {
        Map<String, BigDecimal> byEscrow = agg.groupSum(ORDERS,
                and(scope, Criteria.where("escrowStatus").is(state)), "escrowStatus", field);
        return byEscrow.getOrDefault(state.name(), BigDecimal.ZERO);
    }

    // ================================================================== utils

    private static Criteria and(Criteria first, Criteria second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : new Criteria().andOperator(first, second);
    }

    private static Criteria and(Criteria first, Criteria second, Criteria third) {
        return and(and(first, second), third);
    }

    private static org.bson.Document criteriaObject(Criteria criteria) {
        return criteria == null ? new org.bson.Document() : new org.bson.Document(criteria.getCriteriaObject());
    }

    private static OrderStatus orderStatusOf(String name) {
        try {
            return OrderStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int clampMonths(int months) {
        return Math.min(Math.max(months, 3), 24);
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
}
