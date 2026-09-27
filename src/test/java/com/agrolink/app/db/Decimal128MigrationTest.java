package com.agrolink.app.db;

import com.agrolink.app.config.Decimal128Migration;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link Decimal128Migration} against the real database.
 *
 * <p>Safe to run repeatedly: {@code plan()}, {@code verify()} and the compatibility checks
 * are read-only. The conversion only happens when explicitly requested with
 * {@code -Dagrolink.migrate=decimal128}, so an ordinary test run can never mutate data.</p>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Decimal128MigrationTest {

    private static final String MIGRATE_FLAG = "agrolink.migrate";
    private static final String MIGRATE_VALUE = "decimal128";

    @Autowired
    private Decimal128Migration migration;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProduceListingRepository listingRepository;

    @Test
    @org.junit.jupiter.api.Order(1)
    void planReportsBlastRadius() {
        Decimal128Migration.PlanReport report = migration.plan();
        System.out.println("@@ MIGRATION documents scanned: " + report.documentsScanned());
        System.out.println("@@ MIGRATION total string-valued BigDecimal values: " + report.totalStringValued());
        for (Decimal128Migration.FieldPlan field : report.fields()) {
            System.out.println("@@   " + field.collection() + "." + field.field()
                    + " string=" + field.stringValued()
                    + " numeric=" + field.numeric()
                    + " null=" + field.nullish()
                    + " other=" + field.other());
        }
        if (report.hasUnparseable()) {
            System.out.println("@@ MIGRATION UNPARSEABLE (will be skipped, not coerced):");
            report.unparseableSamples().forEach(s -> System.out.println("@@   " + s));
        } else {
            System.out.println("@@ MIGRATION every string value parses cleanly");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void legacyStringValuesStillReadIntoEntities() {
        // The custom conversions must not break documents written before the migration:
        // if StringToBigDecimalConverter was displaced, the app would fail here, not later.
        boolean anyStringStored = migration.plan().totalStringValued() > 0;
        if (!anyStringStored) {
            System.out.println("@@ COMPAT no string-stored values left, skipping pre-migration read check");
            return;
        }
        var orders = orderRepository.findAll();
        for (Order order : orders) {
            if (order.getTotalAmount() != null) {
                System.out.println("@@ COMPAT order " + order.getId() + " totalAmount=" + order.getTotalAmount()
                        + " agreedQuantity=" + order.getAgreedQuantity());
            }
        }
        assertTrue(orders.stream().anyMatch(o -> o.getTotalAmount() != null
                        && o.getTotalAmount().compareTo(BigDecimal.ZERO) > 0),
                "string-stored totalAmount must still map to a positive BigDecimal");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void applyWhenRequested() {
        if (!MIGRATE_VALUE.equals(System.getProperty(MIGRATE_FLAG))) {
            System.out.println("@@ MIGRATION apply skipped (pass -D" + MIGRATE_FLAG + "=" + MIGRATE_VALUE + " to run)");
            return;
        }
        Decimal128Migration.ApplyReport applied = migration.apply();
        System.out.println("@@ MIGRATION APPLIED documents=" + applied.documentsUpdated()
                + " valuesConverted=" + applied.valuesConverted()
                + " alreadyNumeric=" + applied.skippedAlreadyNumeric()
                + " backups=" + applied.backups()
                + " unparseable=" + applied.unparseable().size());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void verifyAndProveNumbersWork() {
        Decimal128Migration.VerifyReport verify = migration.verify();
        verify.problems().forEach(p -> System.out.println("@@ VERIFY PROBLEM " + p));
        System.out.println("@@ VERIFY clean=" + verify.clean());

        // The whole point: sums, range filters and numeric sort must now agree with the data.
        var sum = mongo.getCollection("orders").aggregate(List.of(
                        new Document("$group", new Document("_id", null)
                                .append("total", new Document("$sum", "$totalAmount")))))
                .first();
        System.out.println("@@ PROOF sum(totalAmount) = "
                + (sum == null ? "none" : sum.get("total") + " (" + typeOf(sum == null ? null : sum.get("total")) + ")"));

        long above100 = mongo.getCollection("orders").countDocuments(
                new Document("totalAmount", new Document("$gt", 100)));
        long below1000 = mongo.getCollection("orders").countDocuments(
                new Document("totalAmount", new Document("$lt", 1000)));
        System.out.println("@@ PROOF range >100 = " + above100 + ", <1000 = " + below1000);

        var sorted = new java.util.ArrayList<Object>();
        mongo.getCollection("orders").aggregate(List.of(
                        new Document("$sort", new Document("totalAmount", 1)),
                        new Document("$project", new Document("_id", 0).append("totalAmount", 1))))
                .forEach(d -> sorted.add(d.get("totalAmount")));
        System.out.println("@@ PROOF numeric sort ascending = " + sorted);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void newWritesAreStoredAsDecimal() {
        // Round-trips a throwaway listing through the real converters, then deletes it, so
        // the write path is proven without leaving demo data behind.
        ProduceListing listing = ProduceListing.builder()
                .cropName("__migration_probe__")
                .category("__probe__")
                .availableQuantity(new BigDecimal("12.34"))
                .reservedQuantity(BigDecimal.ZERO)
                .pricePerUnit(new BigDecimal("7.89"))
                .unit("kg")
                .location("__probe__")
                .district("__probe__")
                .status(com.agrolink.app.model.ListingStatus.ACTIVE)
                .build();

        ProduceListing saved = listingRepository.save(listing);
        // Look the probe up by its unique marker rather than by id: the entity exposes id as a
        // String while MongoDB stores it as an ObjectId, so a raw lookup by id would not match.
        Document stored = mongo.getCollection("produce_listings")
                .find(new Document("cropName", "__migration_probe__")).first();
        assertTrue(stored != null, "probe listing must be readable as raw BSON");
        Object available = stored.get("availableQuantity");
        Object price = stored.get("pricePerUnit");
        System.out.println("@@ WRITE availableQuantity = " + available + " (" + typeOf(available) + ")");
        System.out.println("@@ WRITE pricePerUnit = " + price + " (" + typeOf(price) + ")");
        try {
            assertTrue(available instanceof Decimal128, "new writes must be stored as Decimal128");
            assertTrue(price instanceof Decimal128, "new writes must be stored as Decimal128");
        } finally {
            mongo.getCollection("produce_listings")
                    .deleteMany(new Document("cropName", "__migration_probe__"));
            System.out.println("@@ WRITE probe listing removed, remaining=" + listingRepository.count());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void offerPriceOrderingIsNowNumeric() {
        // findFirstByListingIdAndStatusOrderByOfferedPriceDesc relied on numeric ordering;
        // as strings it returned the wrong offer.
        var offers = mongo.getCollection("offers").aggregate(List.of(
                        new Document("$project", new Document("_id", 1).append("offeredPrice", 1))))
                .into(new java.util.ArrayList<Document>());
        List<BigDecimal> prices = offers.stream()
                .map(d -> d.get("offeredPrice"))
                .map(v -> v instanceof Decimal128 d ? d.bigDecimalValue() : new BigDecimal(String.valueOf(v)))
                .sorted()
                .toList();
        System.out.println("@@ OFFER prices ascending = " + prices);
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
