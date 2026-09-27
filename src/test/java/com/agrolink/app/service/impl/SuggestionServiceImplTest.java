package com.agrolink.app.service.impl;

import com.agrolink.app.dto.SuggestionDTO;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.ProduceListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionServiceImplTest {

    private final List<Order> orders = new ArrayList<>();
    private final List<Offer> offers = new ArrayList<>();
    private final List<ProduceListing> listings = new ArrayList<>();

    private SuggestionServiceImpl service;

    @SuppressWarnings("unchecked")
    private static <T> T repo(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                SuggestionServiceImplTest.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        orders.clear();
        offers.clear();
        listings.clear();

        com.agrolink.app.repository.OrderRepository orderRepo =
                repo(com.agrolink.app.repository.OrderRepository.class, (proxy, method, args) -> {
                    if (method.getName().equals("findByBuyerId")) {
                        return orders.stream().filter(o -> args[0].equals(o.getBuyerId())).toList();
                    }
                    if (method.getName().equals("findByDeletedFalseAndOrderStatus")) {
                        return orders.stream()
                                .filter(o -> !o.isDeleted() && args[0].equals(o.getOrderStatus()))
                                .toList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        com.agrolink.app.repository.OfferRepository offerRepo =
                repo(com.agrolink.app.repository.OfferRepository.class, (proxy, method, args) -> {
                    if (method.getName().equals("findByBuyerId")) {
                        return offers.stream().filter(o -> args[0].equals(o.getBuyerId())).toList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        com.agrolink.app.repository.ProduceListingRepository listingRepo =
                repo(com.agrolink.app.repository.ProduceListingRepository.class, (proxy, method, args) -> {
                    if (method.getName().equals("findByStatus")) {
                        return listings.stream().filter(l -> args[0].equals(l.getStatus())).toList();
                    }
                    if (method.getName().equals("findById")) {
                        return listings.stream().filter(l -> args[0].equals(l.getId())).findFirst();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new SuggestionServiceImpl(orderRepo, offerRepo, listingRepo);
    }

    private static Order order(String buyer, String crop, String qty) {
        return Order.builder().buyerId(buyer).farmerId("f1").cropName(crop)
                .agreedQuantity(new BigDecimal(qty)).orderStatus(OrderStatus.DELIVERED)
                .deleted(false).build();
    }

    private static ProduceListing listing(String id, String crop, String category, String price) {
        return ProduceListing.builder().id(id).cropName(crop).category(category)
                .pricePerUnit(new BigDecimal(price)).unit("kg").location("Naogaon")
                .status(ListingStatus.ACTIVE).availableQuantity(new BigDecimal("100")).build();
    }

    @Test
    void noHistory_returnsEmpty() {
        listings.add(listing("l1", "Rice", "Grain", "30"));
        assertTrue(service.suggestForBuyer("ghost").isEmpty());
    }

    @Test
    void history_returnsFourDedupedPicks() {
        listings.add(listing("l1", "Rice", "Grain", "30"));
        listings.add(listing("l2", "Rice", "Grain", "25"));
        listings.add(listing("l3", "Wheat", "Grain", "10"));
        listings.add(listing("l4", "Potato", "Vegetable", "5"));

        orders.add(order("b1", "Rice", "100"));
        orders.add(order("b1", "Rice", "50"));
        orders.add(order("b1", "Wheat", "20"));
        orders.add(order("b2", "Rice", "1000"));

        List<SuggestionDTO> picks = service.suggestForBuyer("b1");

        assertEquals(4, picks.size());
        assertEquals("Best Selling", picks.get(0).badge());
        assertEquals("l2", picks.get(0).listingId());
        assertEquals("Best Value", picks.get(1).badge());
        assertEquals("Most Repurchased", picks.get(2).badge());
        assertEquals("l1", picks.get(2).listingId());
        assertEquals("Lowest Price", picks.get(3).badge());
        assertEquals("l4", picks.get(3).listingId());

        // Rice: platform 1150 kg sold; b1 (2 orders) + b2 (1 order) -> 50% repurchase.
        assertEquals(0, new BigDecimal("1150").compareTo(picks.get(0).soldQuantity()));
        assertEquals(50, picks.get(0).repurchaseRatePct());

        long distinct = picks.stream().map(SuggestionDTO::listingId).distinct().count();
        assertEquals(4, distinct);
    }
}
