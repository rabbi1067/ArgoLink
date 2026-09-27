package com.agrolink.app.repository;

import com.agrolink.app.model.Offer;
import com.agrolink.app.model.OfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends MongoRepository<Offer, String> {

    List<Offer> findByRequestId(String requestId);

    List<Offer> findByListingId(String listingId);

    List<Offer> findByFarmerId(String farmerId);

    List<Offer> findByBuyerId(String buyerId);

    List<Offer> findByListingIdAndStatus(String listingId, OfferStatus status);

    List<Offer> findByRequestIdAndStatus(String requestId, OfferStatus status);

    List<Offer> findByFarmerIdAndStatus(String farmerId, OfferStatus status);

    List<Offer> findByBuyerIdAndStatus(String buyerId, OfferStatus status);

    Optional<Offer> findFirstByListingIdAndStatusOrderByOfferedPriceDesc(String listingId, OfferStatus status);

    Optional<Offer> findFirstByRequestIdAndStatus(String requestId, OfferStatus status);

    long countByStatus(OfferStatus status);


    long countByBuyerIdAndStatus(String buyerId, OfferStatus status);

    long countByFarmerIdAndStatus(String farmerId, OfferStatus status);


    Page<Offer> findByBuyerIdOrderByCreatedAtDesc(String buyerId, Pageable pageable);

    Page<Offer> findByFarmerIdOrderByCreatedAtDesc(String farmerId, Pageable pageable);
}