package com.agrolink.app.config;

import com.agrolink.app.model.Invoice;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.model.PurchaseRequest;
import com.agrolink.app.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class Decimal128Migration {

    public static final String BACKUP_COLLECTION = "_migration_decimal128_backup";

    private static final String BACKUP_MARKER = "_migratedBy";
    private static final String BACKUP_MIGRATION = "decimal128-" + MongoDecimalConfig.class.getSimpleName();
    private static final String UNPARSEABLE = "__UNPARSEABLE__";

    private final MongoTemplate mongo;

    private static final List<Class<?>> ENTITIES = List.of(
            Order.class, ProduceListing.class, Offer.class, Invoice.class,
            PurchaseRequest.class, User.class);


    public record FieldPlan(String collection, String field, long stringValued, long numeric, long nullish, long other) {
        long convertible() {
            return stringValued;
        }
    }

    public record PlanReport(List<FieldPlan> fields, long documentsScanned, List<String> unparseableSamples) {

        public long totalStringValued() {
            return fields.stream().mapToLong(FieldPlan::stringValued).sum();
        }

        public boolean hasUnparseable() {
            return !unparseableSamples.isEmpty();
        }
    }

    public PlanReport plan() {
        List<FieldPlan> plans = new ArrayList<>();
        Set<String> unparseable = new LinkedHashSet<>();
        long documents = 0;

        for (Class<?> entity : ENTITIES) {
            String collection = collectionOf(entity);
            List<String> fields = decimalFields(entity);
            if (fields.isEmpty()) {
                continue;
            }
            documents += mongo.getCollection(collection).countDocuments();

            for (String field : fields) {
                long stringValued = 0;
                long numeric = 0;
                long nullish = 0;
                long other = 0;
                List<Document> typed = mongo.getCollection(collection).aggregate(List.of(
                        new Document("$project", new Document("_id", 1).append(field, 1)))).into(new ArrayList<>());
                for (Document doc : typed) {
                    Object value = doc.get(field);
                    if (value == null) {
                        nullish++;
                    } else if (value instanceof String text) {
                        stringValued++;
                        if (isNumeric(text)) {
                            continue;
                        }
                        unparseable.add(collection + "." + field + " = '" + text + "' (id " + doc.get("_id") + ")");
                    } else if (value instanceof Number || value instanceof Decimal128) {
                        numeric++;
                    } else {
                        other++;
                        unparseable.add(collection + "." + field + " has unexpected type "
                                + value.getClass().getName() + " (id " + doc.get("_id") + ")");
                    }
                }
                plans.add(new FieldPlan(collection, field, stringValued, numeric, nullish, other));
            }
        }
        return new PlanReport(plans, documents, new ArrayList<>(unparseable));
    }


    public record ApplyReport(int documentsUpdated, int valuesConverted, int skippedAlreadyNumeric,
                              List<String> unparseable, int backups) {
    }

    public ApplyReport apply() {
        PlanReport plan = plan();
        int documents = 0;
        int values = 0;
        int alreadyNumeric = 0;
        int backups = 0;

        for (FieldPlan fieldPlan : plan.fields()) {
            if (fieldPlan.stringValued() == 0) {
                continue;
            }
            String collection = fieldPlan.collection();
            String field = fieldPlan.field();

            List<Document> stringDocs = mongo.getCollection(collection)
                    .find(new Document(field, new Document("$type", "string")))
                    .into(new ArrayList<>());
            if (stringDocs.isEmpty()) {
                continue;
            }

            alreadyNumeric += fieldPlan.numeric();
            backups += backup(collection, stringDocs);
            documents += stringDocs.size();

            List<WriteModel<Document>> setOps = new ArrayList<>();
            for (Document doc : stringDocs) {
                Object raw = doc.get(field);
                if (!(raw instanceof String text) || !isNumeric(text)) {
                    continue;
                }
                values++;
                setOps.add(new UpdateOneModel<>(
                        new Document("_id", doc.get("_id")),
                        new Document("$set", new Document(field, parseDecimal(text))),
                        new UpdateOptions()));
            }
            if (!setOps.isEmpty()) {
                mongo.getCollection(collection).bulkWrite(setOps);
            }
        }
        return new ApplyReport(documents, values, alreadyNumeric, plan.unparseableSamples(), backups);
    }

    private int backup(String collection, List<Document> documents) {
        List<Document> copies = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            Document copy = new Document(doc);
            copy.append(BACKUP_MARKER, BACKUP_MIGRATION);
            copy.append("_sourceCollection", collection);
            copies.add(copy);
        }
        List<WriteModel<Document>> upserts = new ArrayList<>(copies.size());
        for (Document copy : copies) {
            upserts.add(new ReplaceOneModel<>(
                    new Document("_id", copy.get("_id")), copy,
                    new ReplaceOptions().upsert(true)));
        }
        mongo.getCollection(BACKUP_COLLECTION).bulkWrite(upserts);
        return copies.size();
    }

    public int rollback() {
        List<Document> stored = mongo.getCollection(BACKUP_COLLECTION)
                .find(new Document(BACKUP_MARKER, BACKUP_MIGRATION))
                .into(new ArrayList<>());

        Map<String, List<Document>> byCollection = new LinkedHashMap<>();
        for (Document backupDoc : stored) {
            String from = String.valueOf(backupDoc.get("_sourceCollection"));
            Document restored = new Document(backupDoc);
            // Keep the original _id so the restore lands on the same document, not a copy.
            Object id = restored.get("_id");
            restored.remove(BACKUP_MARKER);
            restored.remove("_sourceCollection");
            restored.put("_id", id);
            byCollection.computeIfAbsent(from, key -> new ArrayList<>()).add(restored);
        }

        int restoredCount = 0;
        for (Map.Entry<String, List<Document>> entry : byCollection.entrySet()) {
            List<WriteModel<Document>> writes = new ArrayList<>(entry.getValue().size());
            for (Document doc : entry.getValue()) {
                writes.add(new ReplaceOneModel<>(
                        new Document("_id", doc.get("_id")), doc,
                        new ReplaceOptions().upsert(true)));
            }
            mongo.getCollection(entry.getKey()).bulkWrite(writes);
            restoredCount += entry.getValue().size();
        }
        mongo.getCollection(BACKUP_COLLECTION).deleteMany(new Document(BACKUP_MARKER, BACKUP_MIGRATION));
        return restoredCount;
    }


    public record VerifyReport(boolean clean, List<String> problems) {
    }

    /** Re-checks storage types after a run: no {@code BigDecimal} field may still be a string. */
    public VerifyReport verify() {
        List<String> problems = new ArrayList<>();
        for (FieldPlan fieldPlan : plan().fields()) {
            if (fieldPlan.stringValued() > 0) {
                problems.add(fieldPlan.collection() + "." + fieldPlan.field() + " still has "
                        + fieldPlan.stringValued() + " string value(s)");
            }
        }
        return new VerifyReport(problems.isEmpty(), problems);
    }


    private static String collectionOf(Class<?> entity) {
        var annotation = entity.getAnnotation(
                org.springframework.data.mongodb.core.mapping.Document.class);
        if (annotation == null || annotation.collection().isBlank()) {
            throw new IllegalStateException(entity.getSimpleName() + " is missing @Document");
        }
        return annotation.collection();
    }

    private static List<String> decimalFields(Class<?> entity) {
        Set<String> names = new TreeSet<>();
        for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == BigDecimal.class) {
                    names.add(field.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean isNumeric(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Decimal128 parseDecimal(String text) {
        return new Decimal128(new BigDecimal(text.trim()));
    }

    static String unparseableMarker() {
        return UNPARSEABLE;
    }
}
