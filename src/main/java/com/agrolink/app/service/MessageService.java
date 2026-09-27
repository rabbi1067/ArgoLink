package com.agrolink.app.service;

import com.agrolink.app.dto.AdminConversationDTO;
import com.agrolink.app.dto.ConversationDTO;
import com.agrolink.app.dto.MessageDTO;
import com.agrolink.app.dto.PageResponse;

import java.util.List;

public interface MessageService {

    ConversationDTO openByOrder(String userId, String orderId);

    ConversationDTO openByListing(String userId, String listingId);


    void ensureConversationForOffer(String buyerId, String farmerId, String listingId, String cropName);

    List<ConversationDTO> listForUser(String userId);

    PageResponse<AdminConversationDTO> listAllForAdmin(int page, int size);

    List<MessageDTO> listMessagesForAdmin(String conversationId);

    void deleteConversation(String conversationId);

    List<MessageDTO> listMessages(String userId, String conversationId);

    MessageDTO sendMessage(String userId, String conversationId, String body);

    MessageDTO sendMessageWs(String senderId, String conversationId, String body);
}