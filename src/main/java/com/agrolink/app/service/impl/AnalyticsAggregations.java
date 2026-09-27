package com.agrolink.app.service.impl;

import com.agrolink.app.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyticsAggregations {


    public static final Criteria NOT_DELETED = Criteria.where("deleted").ne(true);

    public static final Criteria NOT_CANCELLED = Criteria.where("orderStatus")
            .nin(OrderStatus.CANCELLED, OrderStatus.DISPUTED);

    private static final String SUM_SOURCE = "__sumSource";

    private static final String OFFER_KEY = "__offerKey";
    private static final String LISTING_KEY = "__listingKey";

    private final MongoTemplate mongo;

    public long count(String collection, Criteria match) {
        if (match == null) {
            return mongo.getCollection(collection).countDocuments();
        }
        return mongo.getCollection(collection)
                .countDocuments(new BasicQuery(match.getCriteriaObject()).getQueryObject());
    }

    public Map<String, Long> groupCount(String collection, Criteria match, String field) {
        List<AggregationOperation> pipeline = new ArrayList<>();
        if (match != null) {
            pipeline.add(Aggregation.match(match));
        }
        pipeline.add(Aggregation.group(field).count().as("value"));
        return read(mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class));
    }

    public Map<String, Long> groupCountBetween(String collection, Criteria match, String field,
                                               LocalDate from, LocalDate to, String dateField) {
        return read(groupWindow(collection, match, field, dateField, from, to, null));
    }

    public Map<String, BigDecimal> groupSumBetween(String collection, Criteria match, String field,
                                                  String sumField, LocalDate from, LocalDate to,
                                                  String dateField) {
        return readDecimal(groupWindow(collection, match, field, dateField, from, to, sumField));
    }

    private AggregationResults<Map> groupWindow(String collection, Criteria match, String field,
                                                String dateField, LocalDate from, LocalDate to,
                                                String sumField) {
        String bucket = dateField == null ? "createdAt" : dateField;
        Criteria windowed = criteriaAnd(match, new Criteria().andOperator(
                Criteria.where(bucket).gte(from.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Criteria.where(bucket).lt(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(windowed));
        if (sumField == null) {
            pipeline.add(Aggregation.group(field).count().as("value"));
        } else {
            pipeline.add(Aggregation.group(field).sum(sumField).as("value"));
        }
        return mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class);
    }

    public Map<String, BigDecimal> groupSum(String collection, Criteria match, String field, String sumField) {
        List<AggregationOperation> pipeline = new ArrayList<>();
        if (match != null) {
            pipeline.add(Aggregation.match(match));
        }
        pipeline.add(Aggregation.group(field).sum(sumField).as("value"));
        return readDecimal(mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class));
    }

    public MonthlySeries monthly(String collection, Criteria match, int months, String sumField, String dateField) {
        int window = Math.max(1, months);
        String bucket = dateField == null ? "createdAt" : dateField;
        YearMonth start = YearMonth.now().minusMonths(window - 1L);
        Instant cutoff = start.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(criteriaAnd(match, Criteria.where(bucket).gte(cutoff))));

        org.springframework.data.mongodb.core.aggregation.ProjectionOperation project =
                Aggregation.project()
                        .andExpression("year(" + bucket + ")").as("year")
                        .andExpression("month(" + bucket + ")").as("month");
        String carriedField = sumField == null ? null : sumField;
        if (carriedField != null) {
            project = project.and(carriedField).as(SUM_SOURCE);
        }
        pipeline.add(project);

        org.springframework.data.mongodb.core.aggregation.GroupOperation group = Aggregation.group("year", "month")
                .count().as("count");
        pipeline.add(carriedField == null
                ? group.sum(bucket).as("sum")
                : group.sum(SUM_SOURCE).as("sum"));
        pipeline.add(Aggregation.project()
                .and("_id.year").as("year")
                .and("_id.month").as("month")
                .and("count").as("count")
                .and("sum").as("sum"));

        Map<String, Map> rows = new LinkedHashMap<>();
        for (Map row : mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class).getMappedResults()) {
            rows.put(monthKey(row), row);
        }

        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<BigDecimal> sums = new ArrayList<>();
        for (int i = 0; i < window; i++) {
            YearMonth month = start.plusMonths(i);
            String key = month.toString();
            Map row = rows.get(key);
            labels.add(key);
            counts.add(row == null ? 0L : toLong(row.get("count")));
            sums.add(row == null ? BigDecimal.ZERO : toDecimal(row.get("sum")));
        }
        return new MonthlySeries(labels, counts, sums);
    }

    public MonthlySeries monthlyBetween(String collection, Criteria match, LocalDate from, LocalDate to,
                                        String sumField, String dateField) {
        String bucket = dateField == null ? "createdAt" : dateField;
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);

        Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        Criteria windowed = criteriaAnd(match, new Criteria().andOperator(
                Criteria.where(bucket).gte(fromInstant),
                Criteria.where(bucket).lt(toInstant)));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(windowed));

        org.springframework.data.mongodb.core.aggregation.ProjectionOperation project =
                Aggregation.project()
                        .andExpression("year(" + bucket + ")").as("year")
                        .andExpression("month(" + bucket + ")").as("month");
        String carried = sumField;
        if (carried != null) {
            project = project.and(carried).as(SUM_SOURCE);
        }
        pipeline.add(project);

        org.springframework.data.mongodb.core.aggregation.GroupOperation group = Aggregation.group("year", "month")
                .count().as("count");
        pipeline.add(carried == null ? group.sum(bucket).as("sum") : group.sum(SUM_SOURCE).as("sum"));
        pipeline.add(Aggregation.project()
                .and("_id.year").as("year")
                .and("_id.month").as("month")
                .and("count").as("count")
                .and("sum").as("sum"));

        Map<String, Map> rows = new LinkedHashMap<>();
        for (Map row : mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class)
                .getMappedResults()) {
            rows.put(monthKey(row), row);
        }

        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<BigDecimal> sums = new ArrayList<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            String key = month.toString();
            Map row = rows.get(key);
            labels.add(key);
            counts.add(row == null ? 0L : toLong(row.get("count")));
            sums.add(row == null ? BigDecimal.ZERO : toDecimal(row.get("sum")));
        }
        return new MonthlySeries(labels, counts, sums);
    }

    public BigDecimal sumBetween(String collection, Criteria match, LocalDate from, LocalDate to,
                                 String field) {
        Criteria windowed = criteriaAnd(match, new Criteria().andOperator(
                Criteria.where("createdAt").gte(from.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Criteria.where("createdAt").lt(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())));
        List<AggregationOperation> pipeline = new ArrayList<>();
        if (windowed != null) {
            pipeline.add(Aggregation.match(windowed));
        }
        pipeline.add(Aggregation.group()
                .sum(field).as("value"));
        return mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class)
                .getMappedResults().stream()
                .map(row -> row.get("value"))
                .filter(java.util.Objects::nonNull)
                .map(AnalyticsAggregations::toDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long countBetween(String collection, Criteria match, LocalDate from, LocalDate to, String dateField) {
        String bucket = dateField == null ? "createdAt" : dateField;
        Criteria windowed = criteriaAnd(match, new Criteria().andOperator(
                Criteria.where(bucket).gte(from.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Criteria.where(bucket).lt(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())));
        return count(collection, windowed);
    }

    public Map<String, BigDecimal> orderValueByListingField(Criteria orderMatch, String listingField) {
        var pipeline = List.of(
                new Document("$match", new Document(criteriaObject(orderMatch))),
                objectIdRef("offerId", OFFER_KEY),
                new Document("$lookup", new Document("from", "offers")
                        .append("localField", OFFER_KEY)
                        .append("foreignField", "_id")
                        .append("as", "offer")),
                new Document("$unwind", "$offer"),
                objectIdRef("offer.listingId", LISTING_KEY),
                new Document("$lookup", new Document("from", "produce_listings")
                        .append("localField", LISTING_KEY)
                        .append("foreignField", "_id")
                        .append("as", "listing")),
                new Document("$unwind", "$listing"),
                new Document("$group", new Document("_id", "$listing." + listingField)
                        .append("value", new Document("$sum", "$totalAmount"))),
                new Document("$match", new Document("value",
                        new Document("$gt", 0))));
        return collectDecimal(pipeline);
    }

    public Map<String, Long> orderCountByListingField(Criteria orderMatch, String listingField) {
        var pipeline = List.of(
                new Document("$match", new Document(criteriaObject(orderMatch))),
                objectIdRef("offerId", OFFER_KEY),
                new Document("$lookup", new Document("from", "offers")
                        .append("localField", OFFER_KEY)
                        .append("foreignField", "_id")
                        .append("as", "offer")),
                new Document("$unwind", "$offer"),
                objectIdRef("offer.listingId", LISTING_KEY),
                new Document("$lookup", new Document("from", "produce_listings")
                        .append("localField", LISTING_KEY)
                        .append("foreignField", "_id")
                        .append("as", "listing")),
                new Document("$unwind", "$listing"),
                new Document("$group", new Document("_id", "$listing." + listingField)
                        .append("value", new Document("$sum", 1))));
        Map<String, Long> values = new LinkedHashMap<>();
        mongo.getCollection("orders").aggregate(pipeline).forEach(row -> {
            Object id = row.get("_id");
            if (id != null) {
                values.put(String.valueOf(id), toLong(row.get("value")));
            }
        });
        return values;
    }


    private static Document objectIdRef(String sourceField, String alias) {
        return new Document("$addFields", new Document(alias,
                new Document("$convert", new Document("input", "$" + sourceField)
                        .append("to", "objectId")
                        .append("onError", null)
                        .append("onNull", null))));
    }


    public List<Map.Entry<String, BigDecimal>> topSums(String collection, Criteria match,
                                                      String groupField, String sumField, int limit) {
        List<AggregationOperation> pipeline = new ArrayList<>();
        if (match != null) {
            pipeline.add(Aggregation.match(match));
        }
        pipeline.add(Aggregation.group(groupField).sum(sumField).as("value"));
        pipeline.add(Aggregation.match(Criteria.where("value").gt(0)));
        pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "value")));
        pipeline.add(Aggregation.limit(limit));

        List<Map.Entry<String, BigDecimal>> out = new ArrayList<>();
        for (Map row : mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class).getMappedResults()) {
            Object id = row.get("_id");
            if (id != null) {
                out.add(Map.entry(String.valueOf(id), toDecimal(row.get("value"))));
            }
        }
        return out;
    }

    public List<Map.Entry<String, Long>> topCounts(String collection, Criteria match, String field, int limit) {
        List<AggregationOperation> pipeline = new ArrayList<>();
        if (match != null) {
            pipeline.add(Aggregation.match(match));
        }
        pipeline.add(Aggregation.group(field).count().as("value"));
        pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "value")));
        pipeline.add(Aggregation.limit(limit));

        List<Map.Entry<String, Long>> out = new ArrayList<>();
        for (Map row : mongo.aggregate(Aggregation.newAggregation(pipeline), collection, Map.class).getMappedResults()) {
            Object id = row.get("_id");
            if (id != null) {
                out.add(Map.entry(String.valueOf(id), toLong(row.get("value"))));
            }
        }
        return out;
    }


    private Map<String, Long> read(AggregationResults<Map> results) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Map row : results.getMappedResults()) {
            Object id = row.get("_id");
            if (id != null) {
                values.put(String.valueOf(id), toLong(row.get("value")));
            }
        }
        return values;
    }

    private Map<String, BigDecimal> readDecimal(AggregationResults<Map> results) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map row : results.getMappedResults()) {
            Object id = row.get("_id");
            if (id != null) {
                values.put(String.valueOf(id), toDecimal(row.get("value")));
            }
        }
        return values;
    }

    private Map<String, BigDecimal> collectDecimal(List<org.bson.Document> pipeline) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        mongo.getCollection("orders").aggregate(pipeline).forEach(row -> {
            Object id = row.get("_id");
            if (id != null) {
                values.put(String.valueOf(id), toDecimal(row.get("value")));
            }
        });
        return values;
    }

    private static Criteria criteriaAnd(Criteria first, Criteria second) {
        return first == null ? second : new Criteria().andOperator(first, second);
    }

    private static String monthKey(Map row) {
        Object year = row.get("year");
        Object month = row.get("month");
        if (!(year instanceof Number y) || !(month instanceof Number m)) {
            return "";
        }
        return String.format("%04d-%02d", y.intValue(), m.intValue());
    }

    private static long toLong(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().longValue();
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        return value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private static org.bson.Document criteriaObject(Criteria criteria) {
        return criteria == null ? new org.bson.Document() : new org.bson.Document(criteria.getCriteriaObject());
    }


    public record MonthlySeries(List<String> labels, List<Long> counts, List<BigDecimal> sums) {

        public List<Number> countsAsNumbers() {
            List<Number> out = new ArrayList<>(counts.size());
            counts.forEach(out::add);
            return out;
        }

        public List<Number> sumsAsNumbers() {
            List<Number> out = new ArrayList<>(sums.size());
            sums.forEach(out::add);
            return out;
        }
    }
}
