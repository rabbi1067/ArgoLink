package com.agrolink.app.service;

import com.agrolink.app.dto.ProduceListingDTO;
import com.agrolink.app.dto.ProduceListingPage;
import com.agrolink.app.dto.ProduceListingRequest;
import com.agrolink.app.dto.ProduceListingResponse;
import com.agrolink.app.dto.ProduceSearchCriteria;
import com.agrolink.app.model.ListingStatus;

import java.math.BigDecimal;
import java.util.List;

public interface ProduceService {

    ProduceListingResponse create(String farmerId, ProduceListingRequest request);

    ProduceListingResponse update(String id, String actorId, boolean privileged, ProduceListingRequest request);


    ProduceListingResponse getVisibleById(String id, String requesterId, boolean privileged);

    List<ProduceListingResponse> getMyListings(String farmerId);

    ProduceListingResponse changeStatus(String id, String actorId, boolean privileged, ListingStatus status);

    void delete(String id, String actorId, boolean privileged);

    List<ProduceListingDTO> searchActive(String category, String cropName);

    ProduceListingPage search(ProduceSearchCriteria criteria);

    ProduceListingResponse adjustStock(String id, String actorId, boolean privileged, BigDecimal newAvailableQuantity);

    List<ProduceListingResponse> listAllForManagement();
}