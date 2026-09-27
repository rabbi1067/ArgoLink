package com.agrolink.app.service.impl;

import com.agrolink.app.dto.CounterOfferRecord;
import com.agrolink.app.dto.CreateOfferRecord;
import com.agrolink.app.dto.OfferDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.Offer;
import com.agrolink.app.model.OfferStatus;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.repository.OfferRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.service.MessageService;
import com.agrolink.app.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OfferServiceImpl implements OfferService {

    private static final long OFFER_EXPIRY_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 50;

    private final OfferRepository offerRepository;
    private final ProduceListingRepository produceListingRepository;
    private final MessageService messageService;

    @Override
    public OfferDTO submit(String buyerId, CreateOfferRecord request) {
        ProduceListing listing = produceListingRepository.findById(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("ProduceListing", "id", request.listingId()));

        if (listing.getFarmerId().equals(buyerId)) {
            throw new BusinessRuleException("You cannot place an offer on your own listing", 403);
        }
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("Listing is not available for offers", 409);
        }
        if (request.offeredQuantity().compareTo(listing.getAvailableQuantity()) > 0) {
            throw new BusinessRuleException(
                    "Offered quantity exceeds available stock (" + listing.getAvailableQuantity() + ")", 422);
        }

        Offer offer = Offer.builder()
                .listingId(listing.getId())
                .requestId(request.requestId())
                .farmerId(listing.getFarmerId())
                .buyerId(buyerId)
                .offeredQuantity(request.offeredQuantity())
                .offeredPrice(request.offeredPrice())
                .status(OfferStatus.OFFERED)
                .build();

        Offer saved = offerRepository.save(offer);
        expireStaleOffers(listing.getId());
        // Buyer just made an offer -> open (or reuse) the WhatsApp-style thread with
        // this farmer immediately, exactly like the requirement asks for.
        messageService.ensureConversationForOffer(buyerId, listing.getFarmerId(), listing.getId(), listing.getCropName());
        return OfferDTO.from(saved);
    }

    @Override
    public OfferDTO counter(String buyerId, String offerId, CounterOfferRecord request) {
        Offer offer = getOwnedOffer(buyerId, offerId);
        requireOffered(offer);
        offer.setOfferedQuantity(request.offeredQuantity());
        offer.setOfferedPrice(request.offeredPrice());
        return OfferDTO.from(offerRepository.save(offer));
    }

    @Override
    public OfferDTO withdraw(String buyerId, String offerId) {
        Offer offer = getOwnedOffer(buyerId, offerId);
        requireOffered(offer);
        offer.setStatus(OfferStatus.REJECTED);
        return OfferDTO.from(offerRepository.save(offer));
    }

    @Override
    public OfferDTO rejectByFarmer(String farmerId, String offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));
        if (!offer.getFarmerId().equals(farmerId)) {
            throw new BusinessRuleException("You do not own this offer", 403);
        }
        requireOffered(offer);
        offer.setStatus(OfferStatus.REJECTED);
        return OfferDTO.from(offerRepository.save(offer));
    }

    @Override
    public List<OfferDTO> getOffersForListing(String listingId) {
        return offerRepository.findByListingId(listingId).stream().map(OfferDTO::from).toList();
    }

    @Override
    public List<OfferDTO> getOffersByBuyer(String buyerId) {
        return offerRepository.findByBuyerId(buyerId).stream().map(OfferDTO::from).toList();
    }

    @Override
    public List<OfferDTO> getOffersByFarmer(String farmerId) {
        return offerRepository.findByFarmerId(farmerId).stream().map(OfferDTO::from).toList();
    }

    @Override
    public PageResponse<OfferDTO> getBuyerOffersPage(String buyerId, int page, int size) {
        Page<OfferDTO> result = offerRepository
                .findByBuyerIdOrderByCreatedAtDesc(buyerId, pageRequest(page, size))
                .map(OfferDTO::from);
        return PageResponse.from(result);
    }

    @Override
    public PageResponse<OfferDTO> getFarmerOffersPage(String farmerId, int page, int size) {
        Page<OfferDTO> result = offerRepository
                .findByFarmerIdOrderByCreatedAtDesc(farmerId, pageRequest(page, size))
                .map(OfferDTO::from);
        return PageResponse.from(result);
    }

    private Pageable pageRequest(int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize);
    }

    private Offer getOwnedOffer(String buyerId, String offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));
        if (!offer.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleException("You do not own this offer", 403);
        }
        return offer;
    }

    private void requireOffered(Offer offer) {
        if (offer.getStatus() != OfferStatus.OFFERED) {
            throw new BusinessRuleException("Offer is no longer negotiable (status: " + offer.getStatus() + ")", 409);
        }
    }

    private void expireStaleOffers(String listingId) {
        Instant cutoff = Instant.now().minusSeconds(OFFER_EXPIRY_SECONDS);
        offerRepository.findByListingIdAndStatus(listingId, OfferStatus.OFFERED)
                .stream()
                .filter(offer -> offer.getCreatedAt() != null && offer.getCreatedAt().isBefore(cutoff))
                .forEach(offer -> {
                    offer.setStatus(OfferStatus.EXPIRED);
                    offerRepository.save(offer);
                });
    }
}