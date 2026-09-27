package com.agrolink.app.repository;

import com.agrolink.app.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(String userId);

    Page<Conversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    Optional<Conversation> findByOrderId(String orderId);

    Optional<Conversation> findByListingId(String listingId);
    Optional<Conversation> findByBuyerIdAndFarmerId(String buyerId, String farmerId);
}