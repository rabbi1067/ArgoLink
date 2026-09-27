package com.agrolink.app.repository;

import com.agrolink.app.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByConversationId(String conversationId);

    long countByConversationIdAndReadByBuyerIsFalse(String conversationId);

    long countByConversationIdAndReadByFarmerIsFalse(String conversationId);
}