package com.agrolink.app.repository;

import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.projection.PriceProjection;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProduceListingRepository extends MongoRepository<ProduceListing, String> {

    @Aggregation({ "{ '$match': { 'status': ?0 } }", "{ '$group': { '_id': null, 'pricePerUnit': { '$avg': '$pricePerUnit' } } }" })
    List<PriceProjection> findAveragePricePerUnitByStatus(ListingStatus status);

    Optional<ProduceListing> findByIdAndFarmerId(String id, String farmerId);

    List<ProduceListing> findByFarmerId(String farmerId);

    List<ProduceListing> findByStatus(ListingStatus status);

    List<ProduceListing> findByStatusOrderByCreatedAtDesc(ListingStatus status);

    List<ProduceListing> findByCategoryAndStatus(String category, ListingStatus status);

    List<ProduceListing> findByCategoryAndStatusOrderByCreatedAtDesc(String category, ListingStatus status);

    List<ProduceListing> findByCropNameContainingIgnoreCase(String cropName);

    List<ProduceListing> findByCropNameContainingIgnoreCaseAndStatus(String cropName, ListingStatus status);

    List<ProduceListing> findByCropNameContainingIgnoreCaseAndStatusAndCategory(String cropName, ListingStatus status, String category);

    List<ProduceListing> findTop10ByStatusOrderByCreatedAtDesc(ListingStatus status);

    List<ProduceListing> findByAvailableQuantityGreaterThanEqual(BigDecimal minimumQuantity);

    long countByStatus(ListingStatus status);
}