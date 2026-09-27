package com.agrolink.app.service.impl;

import com.agrolink.app.dto.SuggestionDTO;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.repository.OfferRepository;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Four fixed slots, each answered from the database or skipped:
 * Best Selling (top platform demand), Best Value (cheapest in the buyer's
 * favourite category), Most Repurchased (the buyer's most-ordered crop) and
 * Lowest Price (cheapest buyable listing). A listing is never picked twice.
 */
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final OrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final ProduceListingRepository listingRepository;

    @Override
    public List<SuggestionDTO> suggestForBuyer(String buyerId) {
        List<Order> mine = orderRepository.findByBuyerId(buyerId).stream()
                .filter(order -> !order.isDeleted())
                .toList();
        List<Offer> offers = offerRepository.findByBuyerId(buyerId);
        if (mine.isEmpty() && offers.isEmpty()) {
            return List.of();
        }

        List<Order> live = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            if (!status.isTerminal()) {
                live.addAll(orderRepository.findByDeletedFalseAndOrderStatus(status));
            }
        }

        Map<String, BigDecimal> qtyByCrop = new HashMap<>();
        Map<String, Set<String>> buyersByCrop = new HashMap<>();
        Map<String, Map<String, Integer>> ordersByCropBuyer = new HashMap<>();
        for (Order order : live) {
            if (order.getCropName() == null || order.getAgreedQuantity() == null) {
                continue;
            }
            qtyByCrop.merge(order.getCropName(), order.getAgreedQuantity(), BigDecimal::add);
            buyersByCrop.computeIfAbsent(order.getCropName(), k -> new HashSet<>()).add(order.getBuyerId());
            ordersByCropBuyer.computeIfAbsent(order.getCropName(), k -> new HashMap<>())
                    .merge(order.getBuyerId(), 1, Integer::sum);
        }

        List<ProduceListing> active = listingRepository.findByStatus(ListingStatus.ACTIVE);
        if (active.isEmpty()) {
            return List.of();
        }

        Map<String, Long> myCountByCrop = new HashMap<>();
        for (Order order : mine) {
            if (order.getCropName() != null) {
                myCountByCrop.merge(order.getCropName(), 1L, Long::sum);
            }
        }
        String myTopCrop = myCountByCrop.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> offerCrop(offers));
        String platformTopCrop = qtyByCrop.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(myTopCrop);
        String platformFrequentCrop = ordersByCropBuyer.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(myTopCrop);

        Set<String> used = new HashSet<>();
        List<SuggestionDTO> picks = new ArrayList<>();
        pickCheapestOfCrop(active, platformTopCrop, used, "Best Selling", qtyByCrop, ordersByCropBuyer, buyersByCrop)
                .ifPresent(picks::add);
        String favouriteCategory = categoryOf(active, myTopCrop);
        if (favouriteCategory != null) {
            cheapestIn(active, favouriteCategory, used)
                    .flatMap(listing -> toSuggestion(listing, "Best Value",
                            qtyByCrop, ordersByCropBuyer, buyersByCrop))
                    .ifPresent(suggestion -> {
                        used.add(suggestion.listingId());
                        picks.add(suggestion);
                    });
        }
        String repurchasedCrop = myTopCrop != null ? myTopCrop : platformFrequentCrop;
        pickCheapestOfCrop(active, repurchasedCrop, used, "Most Repurchased",
                qtyByCrop, ordersByCropBuyer, buyersByCrop).ifPresent(picks::add);
        active.stream()
                .filter(listing -> !used.contains(listing.getId()))
                .filter(listing -> listing.getPricePerUnit() != null)
                .min(Comparator.comparing(ProduceListing::getPricePerUnit))
                .flatMap(listing -> toSuggestion(listing, "Lowest Price",
                        qtyByCrop, ordersByCropBuyer, buyersByCrop))
                .ifPresent(suggestion -> {
                    used.add(suggestion.listingId());
                    picks.add(suggestion);
                });
        return picks;
    }

    /** Best-effort crop from the buyer's offers (offers carry no crop name). */
    private String offerCrop(List<Offer> offers) {
        for (Offer offer : offers) {
            if (offer.getListingId() == null) {
                continue;
            }
            String crop = listingRepository.findById(offer.getListingId())
                    .map(ProduceListing::getCropName)
                    .orElse(null);
            if (crop != null) {
                return crop;
            }
        }
        return null;
    }

    private java.util.Optional<SuggestionDTO> pickCheapestOfCrop(
            List<ProduceListing> active, String crop, Set<String> used, String badge,
            Map<String, BigDecimal> qtyByCrop,
            Map<String, Map<String, Integer>> ordersByCropBuyer,
            Map<String, Set<String>> buyersByCrop) {
        if (crop == null) {
            return java.util.Optional.empty();
        }
        java.util.Optional<ProduceListing> found = active.stream()
                .filter(listing -> crop.equals(listing.getCropName()))
                .filter(listing -> !used.contains(listing.getId()))
                .filter(listing -> listing.getPricePerUnit() != null)
                .min(Comparator.comparing(ProduceListing::getPricePerUnit));
        found.ifPresent(listing -> used.add(listing.getId()));
        return found.flatMap(listing -> toSuggestion(listing, badge, qtyByCrop, ordersByCropBuyer, buyersByCrop));
    }

    private java.util.Optional<ProduceListing> cheapestIn(
            List<ProduceListing> active, String category, Set<String> used) {
        return active.stream()
                .filter(listing -> category.equals(listing.getCategory()))
                .filter(listing -> !used.contains(listing.getId()))
                .filter(listing -> listing.getPricePerUnit() != null)
                .min(Comparator.comparing(ProduceListing::getPricePerUnit));
    }

    private String categoryOf(List<ProduceListing> active, String crop) {
        if (crop == null) {
            return null;
        }
        return active.stream()
                .filter(listing -> crop.equals(listing.getCropName()))
                .map(ProduceListing::getCategory)
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);
    }

    private java.util.Optional<SuggestionDTO> toSuggestion(
            ProduceListing listing, String badge,
            Map<String, BigDecimal> qtyByCrop,
            Map<String, Map<String, Integer>> ordersByCropBuyer,
            Map<String, Set<String>> buyersByCrop) {
        java.util.Optional<SuggestionDTO> result = java.util.Optional.of(new SuggestionDTO(
                badge,
                listing.getId(),
                listing.getCropName(),
                listing.getCategory(),
                listing.getImageUrl(),
                listing.getPricePerUnit(),
                listing.getUnit(),
                listing.getLocation(),
                qtyByCrop.getOrDefault(listing.getCropName(), BigDecimal.ZERO),
                repurchaseRate(listing.getCropName(), ordersByCropBuyer, buyersByCrop)));
        return result;
    }

    /** Share of buyers who ordered the crop more than once, platform-wide. */
    private int repurchaseRate(String crop,
                               Map<String, Map<String, Integer>> ordersByCropBuyer,
                               Map<String, Set<String>> buyersByCrop) {
        if (crop == null) {
            return 0;
        }
        Set<String> buyers = buyersByCrop.getOrDefault(crop, Set.of());
        if (buyers.isEmpty()) {
            return 0;
        }
        Map<String, Integer> perBuyer = ordersByCropBuyer.getOrDefault(crop, Map.of());
        long repeat = buyers.stream().filter(b -> perBuyer.getOrDefault(b, 0) > 1).count();
        return (int) Math.round(repeat * 100.0 / buyers.size());
    }
}
