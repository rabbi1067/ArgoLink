package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.AnalyticsDTO;
import com.agrolink.app.dto.CropDistributionDTO;
import com.agrolink.app.dto.MonthlyVolumeDTO;
import com.agrolink.app.dto.RevenueDTO;
import com.agrolink.app.dto.SupplyDemandDTO;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.projection.PriceProjection;
import com.agrolink.app.repository.CategoryRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ProduceListingRepository produceListingRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AnalyticsAggregations aggregations;

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'overview'")
    public AnalyticsDTO overview() {
        long activeListings = produceListingRepository.countByStatus(ListingStatus.ACTIVE);
        long totalListings = countNotDeleted(ProduceListing.class);
        long totalUsers = userRepository.count();
        long totalCategories = categoryRepository.count();
        long totalOrders = countNotDeleted(Order.class);

        return AnalyticsDTO.of(
                totalListings,
                activeListings,
                totalUsers,
                totalCategories,
                totalOrders,
                averageActivePricePerUnit()
        );
    }

    private <T> long countNotDeleted(Class<T> type) {
        return mongoTemplate.count(
                Query.query(new Criteria().andOperator(AnalyticsAggregations.NOT_DELETED)),
                type);
    }


    private static Criteria orderMatch() {
        return new Criteria().andOperator(
                AnalyticsAggregations.NOT_DELETED,
                AnalyticsAggregations.NOT_CANCELLED);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'monthly-volume-' + #months")
    public MonthlyVolumeDTO monthlyOrderVolume(int months) {
        AnalyticsAggregations.MonthlySeries series =
                aggregations.monthly("orders", orderMatch(), months, null, null);
        return new MonthlyVolumeDTO(series.labels(), series.counts());
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'crop-distribution'")
    public List<CropDistributionDTO> cropDistribution() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(orderMatch()),
                Aggregation.group("cropName").count().as("count"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count")),
                Aggregation.limit(12)
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "orders", Map.class);
        return results.getMappedResults().stream()
                .map(row -> CropDistributionDTO.of(String.valueOf(row.get("_id")), toLong(row.get("count"))))
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'revenue-' + #months")
    public RevenueDTO monthlyRevenue(int months) {
        AnalyticsAggregations.MonthlySeries series =
                aggregations.monthly("orders", orderMatch(), months, "totalAmount", "createdAt");
        return new RevenueDTO(series.labels(), series.sums());
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DASHBOARD_STATS, key = "'supply-demand'")
    public SupplyDemandDTO supplyDemand() {
        Map<String, BigDecimal> supply = groupedSum(
                "produce_listings",
                Aggregation.match(new Criteria().andOperator(AnalyticsAggregations.NOT_DELETED)
                        .and("status").is(ListingStatus.ACTIVE)),
                "cropName",
                "availableQuantity");
        Map<String, BigDecimal> demand = groupedSum("orders",
                Aggregation.match(orderMatch()),
                "cropName", "agreedQuantity");

        Set<String> keys = new TreeSet<>(supply.keySet());
        keys.addAll(demand.keySet());

        List<String> labels = new ArrayList<>(keys);
        List<BigDecimal> supplySeries = new ArrayList<>();
        List<BigDecimal> demandSeries = new ArrayList<>();
        for (String key : keys) {
            supplySeries.add(supply.getOrDefault(key, BigDecimal.ZERO));
            demandSeries.add(demand.getOrDefault(key, BigDecimal.ZERO));
        }

        return SupplyDemandDTO.of(labels, supplySeries, demandSeries);
    }

    private Map<String, BigDecimal> groupedSum(String collection, AggregationOperation match, String groupField, String sumField) {
        List<AggregationOperation> operations = new ArrayList<>();
        if (match != null) {
            operations.add(match);
        }
        operations.add(Aggregation.group(groupField).sum(sumField).as("value"));

        AggregationResults<Map> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(operations), collection, Map.class);

        Map<String, BigDecimal> values = new HashMap<>();
        for (Map row : results.getMappedResults()) {
            Object id = row.get("_id");
            if (id != null) {
                values.put(String.valueOf(id), toDecimal(row.get("value")));
            }
        }
        return values;
    }

    private BigDecimal averageActivePricePerUnit() {
        List<PriceProjection> prices = produceListingRepository.findAveragePricePerUnitByStatus(ListingStatus.ACTIVE);
        if (prices.isEmpty() || prices.get(0).pricePerUnit() == null) {
            return BigDecimal.ZERO;
        }
        return prices.get(0).pricePerUnit();
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }
        return value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }
}