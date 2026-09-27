package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.ProduceListingDTO;
import com.agrolink.app.dto.ProduceListingPage;
import com.agrolink.app.dto.ProduceListingRequest;
import com.agrolink.app.dto.ProduceListingResponse;
import com.agrolink.app.dto.ProduceSearchCriteria;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.Category;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.ProduceService;
import com.agrolink.app.service.PublicCatalogService;
import com.agrolink.app.util.BangladeshLocationRegions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProduceServiceImpl implements ProduceService {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Set<String> ALLOWED_UNITS =
            Set.of("kg", "maund", "quintal", "tonne", "litre", "bag", "piece", "dozen");

    private final ProduceListingRepository listingRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final PublicCatalogService catalogService;

    // ------------------------------------------------------------------ commands

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, allEntries = true)
    public ProduceListingResponse create(String farmerId, ProduceListingRequest request) {
        ProduceListing listing = ProduceListing.builder()
                .farmerId(farmerId)
                .reservedQuantity(BigDecimal.ZERO)
                .status(ListingStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
        apply(listing, request);
        return toResponse(saveSafely(listing));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, allEntries = true)
    public ProduceListingResponse update(String id, String actorId, boolean privileged, ProduceListingRequest request) {
        ProduceListing listing = getManageableListing(id, actorId, privileged, "edited");
        apply(listing, request); // reserved quantity and status are never taken from the client
        return toResponse(saveSafely(listing));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, allEntries = true)
    public ProduceListingResponse changeStatus(String id, String actorId, boolean privileged, ListingStatus status) {
        ProduceListing listing = getManageableListing(id, actorId, privileged, "status -> " + status);
        if (status == ListingStatus.ACTIVE && remaining(listing).signum() <= 0) {
            throw new BusinessRuleException("Add available stock before publishing this listing", 409);
        }
        listing.setStatus(status);
        return toResponse(saveSafely(listing));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, allEntries = true)
    public void delete(String id, String actorId, boolean privileged) {
        ProduceListing listing = getManageableListing(id, actorId, privileged, "deleted");
        if (nz(listing.getReservedQuantity()).signum() > 0) {
            throw new BusinessRuleException(
                    "This listing has reserved stock for open orders. Archive it instead of deleting.", 409);
        }
        listingRepository.delete(listing);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, allEntries = true)
    public ProduceListingResponse adjustStock(String id, String actorId, boolean privileged, BigDecimal newAvailableQuantity) {
        ProduceListing listing = getManageableListing(id, actorId, privileged, "stock adjusted");
        if (newAvailableQuantity == null || newAvailableQuantity.signum() < 0) {
            throw new BusinessRuleException("Available quantity cannot be negative", 400);
        }
        BigDecimal reserved = nz(listing.getReservedQuantity());
        if (newAvailableQuantity.compareTo(reserved) < 0) {
            throw new BusinessRuleException(
                    "Available quantity cannot be less than the reserved quantity (" + reserved + ")", 409);
        }
        listing.setAvailableQuantity(scale2(newAvailableQuantity));
        return toResponse(saveSafely(listing));
    }

    // ------------------------------------------------------------------ queries

    @Override
    public ProduceListingResponse getVisibleById(String id, String requesterId, boolean privileged) {
        ProduceListing listing = getEntity(id);
        boolean owner = requesterId != null && requesterId.equals(listing.getFarmerId());
        if (listing.getStatus() != ListingStatus.ACTIVE && !owner && !privileged) {
            throw new ResourceNotFoundException("ProduceListing", "id", id);
        }
        return toResponse(listing);
    }

    @Override
    public List<ProduceListingResponse> getMyListings(String farmerId) {
        List<ProduceListing> mine = new ArrayList<>(listingRepository.findByFarmerId(farmerId));
        mine.sort(newestFirst());
        return toResponses(mine);
    }

    @Override
    public List<ProduceListingResponse> listAllForManagement() {
        List<ProduceListing> all = new ArrayList<>(mongoTemplate.find(new Query().limit(500), ProduceListing.class));
        all.sort(newestFirst());
        return toResponses(all);
    }

    /** Legacy unpaged search (unchanged behaviour). */
    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS,
            key = "(#category == null ? '' : #category) + '|' + (#cropName == null ? '' : #cropName)")
    public List<ProduceListingDTO> searchActive(String category, String cropName) {
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasCropName = cropName != null && !cropName.isBlank();

        List<ProduceListing> listings;
        if (hasCategory && hasCropName) {
            listings = listingRepository.findByCropNameContainingIgnoreCaseAndStatusAndCategory(
                    cropName, ListingStatus.ACTIVE, category);
        } else if (hasCropName) {
            listings = listingRepository.findByCropNameContainingIgnoreCaseAndStatus(cropName, ListingStatus.ACTIVE);
        } else if (hasCategory) {
            listings = listingRepository.findByCategoryAndStatusOrderByCreatedAtDesc(category, ListingStatus.ACTIVE);
        } else {
            listings = listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.ACTIVE);
        }
        return listings.stream().map(ProduceListingDTO::from).toList();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_PUBLIC_LISTINGS, key = "'search|' + #criteria.cacheKey()")
    public ProduceListingPage search(ProduceSearchCriteria criteria) {
        ProduceSearchCriteria c = criteria.normalized();

        List<ProduceListing> matches = mongoTemplate.find(new Query(buildCriteria(c)), ProduceListing.class)
                .stream()
                .filter(l -> remaining(l).signum() > 0)
                .filter(l -> c.minPrice() == null
                        || (l.getPricePerUnit() != null && l.getPricePerUnit().compareTo(c.minPrice()) >= 0))
                .filter(l -> c.maxPrice() == null
                        || (l.getPricePerUnit() != null && l.getPricePerUnit().compareTo(c.maxPrice()) <= 0))
                .filter(l -> c.minQuantity() == null || remaining(l).compareTo(c.minQuantity()) >= 0)
                .sorted(comparator(c.sort()))
                .toList();

        long total = matches.size();
        int totalPages = (int) Math.ceil(total / (double) c.size());
        int from = (int) Math.min((long) c.page() * c.size(), total);
        int to = (int) Math.min((long) from + c.size(), total);

        List<ProduceListingResponse> items = toResponses(matches.subList(from, to));
        return new ProduceListingPage(items, c.page(), c.size(), total, totalPages, to < total, c.page() > 0);
    }

    // ------------------------------------------------------------------ helpers

    private Criteria buildCriteria(ProduceSearchCriteria c) {
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("status").is(ListingStatus.ACTIVE));

        if (c.category() != null) {
            parts.add(Criteria.where("category").regex(exact(c.category()), "i"));
        }
        if (c.district() != null) {
            String district = BangladeshLocationRegions.normalize(c.district()).orElse(c.district());
            parts.add(Criteria.where("district").regex(exact(district), "i"));
        }
        if (c.division() != null) {
            parts.add(Criteria.where("division").regex(exact(c.division()), "i"));
        }
        if (c.q() != null) {
            String contains = Pattern.quote(c.q());
            parts.add(new Criteria().orOperator(
                    Criteria.where("cropName").regex(contains, "i"),
                    Criteria.where("category").regex(contains, "i"),
                    Criteria.where("location").regex(contains, "i"),
                    Criteria.where("district").regex(contains, "i"),
                    Criteria.where("description").regex(contains, "i")));
        }
        return new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    private static String exact(String value) {
        return "^" + Pattern.quote(value) + "$";
    }

    private Comparator<ProduceListing> newestFirst() {
        return Comparator.comparing((ProduceListing l) -> l.getCreatedAt(),
                Comparator.nullsLast(Comparator.<Instant>reverseOrder()));
    }

    private Comparator<ProduceListing> comparator(String sort) {
        Comparator<ProduceListing> newest = newestFirst();
        return switch (sort) {
            case "price_asc" -> Comparator.comparing((ProduceListing l) -> l.getPricePerUnit(),
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder())).thenComparing(newest);
            case "price_desc" -> Comparator.comparing((ProduceListing l) -> l.getPricePerUnit(),
                    Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())).thenComparing(newest);
            case "quantity_desc" -> Comparator.comparing((ProduceListing l) -> remaining(l),
                    Comparator.<BigDecimal>reverseOrder()).thenComparing(newest);
            default -> newest;
        };
    }

    /** Validates the request and copies it onto the entity. Throws BusinessRuleException on bad input. */
    private void apply(ProduceListing listing, ProduceListingRequest request) {
        String district = BangladeshLocationRegions.normalize(request.district())
                .orElseThrow(() -> new BusinessRuleException("Unknown district '" + request.district() + "'", 400));
        String unit = request.unit().trim().toLowerCase();
        if (!ALLOWED_UNITS.contains(unit)) {
            throw new BusinessRuleException("Unit must be one of: " + String.join(", ", ALLOWED_UNITS.stream().sorted().toList()), 400);
        }
        validateHarvestDate(request.harvestDate());

        BigDecimal available = scale2(request.availableQuantity());
        if (available.compareTo(nz(listing.getReservedQuantity())) < 0) {
            throw new BusinessRuleException(
                    "Available quantity cannot be less than the reserved quantity (" + nz(listing.getReservedQuantity()) + ")", 409);
        }

        listing.setCropName(request.cropName().trim());
        listing.setCategory(resolveCategory(request.category().trim()));
        listing.setDescription(blankToNull(request.description()));
        listing.setAvailableQuantity(available);
        listing.setUnit(unit);
        listing.setPricePerUnit(scale2(request.pricePerUnit()));
        listing.setImageUrl(validateImageUrl(request.imageUrl()));
        listing.setDistrict(district);
        listing.setDivision(BangladeshLocationRegions.divisionOf(district).orElse(null));
        String detail = blankToNull(request.location());
        listing.setLocation(detail == null ? district : detail);
        listing.setHarvestDate(request.harvestDate());
    }

    private String resolveCategory(String category) {
        List<Category> known = catalogService.getCategories();
        if (known == null || known.isEmpty()) {
            return category; // no catalog configured yet: accept as typed
        }
        return known.stream()
                .map(Category::getName)
                .filter(Objects::nonNull)
                .filter(name -> name.equalsIgnoreCase(category))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Unknown category '" + category + "'", 400));
    }

    private void validateHarvestDate(LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate today = LocalDate.now(DHAKA);
        if (date.isBefore(today.minusYears(1)) || date.isAfter(today.plusYears(1))) {
            throw new BusinessRuleException("Harvest date must be within one year of today", 400);
        }
    }

    /** Only images we serve ourselves or https URLs are accepted (blocks javascript:/data: URLs). */
    private String validateImageUrl(String url) {
        String value = blankToNull(url);
        if (value == null) {
            return null;
        }
        if (value.startsWith("/api/v1/files/") || value.startsWith("https://")) {
            return value;
        }
        throw new BusinessRuleException("Image URL must be an uploaded image or an https link", 400);
    }

    private ProduceListing saveSafely(ProduceListing listing) {
        try {
            return listingRepository.save(listing);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessRuleException(
                    "This listing was just changed by another action (for example a new order). Refresh and try again.", 409);
        }
    }

    private ProduceListing getEntity(String id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProduceListing", "id", id));
    }

    /** Owner OR privileged (admin) may manage. Admin actions on someone else's listing are logged. */
    private ProduceListing getManageableListing(String id, String actorId, boolean privileged, String action) {
        ProduceListing listing = getEntity(id);
        boolean owner = listing.getFarmerId() != null && listing.getFarmerId().equals(actorId);
        if (!owner && !privileged) {
            throw new BusinessRuleException("You do not own this listing", 403);
        }
        if (!owner) {
            log.info("ADMIN ACTION: user {} {} listing {} (farmer {})", actorId, action, id, listing.getFarmerId());
        }
        return listing;
    }

    private ProduceListingResponse toResponse(ProduceListing listing) {
        return toResponses(List.of(listing)).get(0);
    }

    /** One user lookup for the whole page instead of one per listing. */
    private List<ProduceListingResponse> toResponses(Collection<ProduceListing> listings) {
        if (listings.isEmpty()) {
            return List.of();
        }
        Set<String> farmerIds = listings.stream()
                .map(ProduceListing::getFarmerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        userRepository.findAllById(farmerIds).forEach(user -> names.put(user.getId(), user.getName()));
        return listings.stream()
                .map(l -> ProduceListingResponse.from(l, names.get(l.getFarmerId())))
                .toList();
    }

    private static BigDecimal remaining(ProduceListing listing) {
        return nz(listing.getAvailableQuantity()).subtract(nz(listing.getReservedQuantity())).max(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}