package com.agrolink.app.repository;

import com.agrolink.app.model.PurchaseRequest;
import com.agrolink.app.model.PurchaseRequestStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseRequestRepository extends MongoRepository<PurchaseRequest, String> {

    List<PurchaseRequest> findByBuyerId(String buyerId);

    List<PurchaseRequest> findByStatus(PurchaseRequestStatus status);

    List<PurchaseRequest> findByBuyerIdAndStatus(String buyerId, PurchaseRequestStatus status);

    List<PurchaseRequest> findByStatusAndTargetDateBefore(PurchaseRequestStatus status, LocalDate date);

    List<PurchaseRequest> findByCropNameContainingIgnoreCaseAndStatus(String cropName, PurchaseRequestStatus status);

    long countByStatus(PurchaseRequestStatus status);
}