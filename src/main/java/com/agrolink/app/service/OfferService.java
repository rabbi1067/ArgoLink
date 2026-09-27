package com.agrolink.app.service;

import com.agrolink.app.dto.CounterOfferRecord;
import com.agrolink.app.dto.CreateOfferRecord;
import com.agrolink.app.dto.OfferDTO;
import com.agrolink.app.dto.PageResponse;

import java.util.List;

public interface OfferService {

    OfferDTO submit(String buyerId, CreateOfferRecord request);

    OfferDTO counter(String buyerId, String offerId, CounterOfferRecord request);

    OfferDTO withdraw(String buyerId, String offerId);

    OfferDTO rejectByFarmer(String farmerId, String offerId);

    List<OfferDTO> getOffersForListing(String listingId);

    List<OfferDTO> getOffersByBuyer(String buyerId);

    List<OfferDTO> getOffersByFarmer(String farmerId);

    PageResponse<OfferDTO> getBuyerOffersPage(String buyerId, int page, int size);

    PageResponse<OfferDTO> getFarmerOffersPage(String farmerId, int page, int size);
}