package com.agrolink.app.service.impl;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.dto.AdminConversationDTO;
import com.agrolink.app.dto.ConversationDTO;
import com.agrolink.app.dto.MessageDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.Conversation;
import com.agrolink.app.model.Message;
import com.agrolink.app.model.Order;
import com.agrolink.app.model.ProduceListing;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.ConversationRepository;
import com.agrolink.app.repository.MessageRepository;
import com.agrolink.app.repository.OrderRepository;
import com.agrolink.app.repository.ProduceListingRepository;
import com.agrolink.app.repository.UserRepository;
import com.agrolink.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int PREVIEW_LENGTH = 60;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final ProduceListingRepository produceListingRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;

    @Override
    public ConversationDTO openByOrder(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!order.getBuyerId().equals(userId) && !order.getFarmerId().equals(userId)) {
            throw new BusinessRuleException("You are not a party to this order", 403);
        }
        Conversation conversation = getOrCreateConversation(
                order.getBuyerId(), order.getFarmerId(), order.getCropName() + " order",
                order.getId(), null);
        return toDto(conversation, userId);
    }

    @Override
    public ConversationDTO openByListing(String userId, String listingId) {
        ProduceListing listing = produceListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("ProduceListing", "id", listingId));
        if (listing.getFarmerId() == null || listing.getFarmerId().isBlank()) {
            throw new BusinessRuleException("Listing has no farmer to contact", 404);
        }
        if (listing.getFarmerId().equals(userId)) {
            throw new BusinessRuleException("You cannot message yourself about your own listing", 400);
        }
        Conversation conversation = getOrCreateConversation(
                userId, listing.getFarmerId(), listing.getCropName() + " enquiry",
                null, listingId);
        return toDto(conversation, userId);
    }

    @Override
    public void ensureConversationForOffer(String buyerId, String farmerId, String listingId, String cropName) {
        String subject = (cropName == null || cropName.isBlank() ? "Produce" : cropName) + " offer";
        getOrCreateConversation(buyerId, farmerId, subject, null, listingId);
    }


    private Conversation getOrCreateConversation(String buyerId, String farmerId, String subject,
                                                 String orderId, String listingId) {
        Conversation conversation = conversationRepository.findByBuyerIdAndFarmerId(buyerId, farmerId)
                .orElseGet(() -> Conversation.builder()
                        .buyerId(buyerId)
                        .farmerId(farmerId)
                        .subject(subject)
                        .participantIds(new ArrayList<>(List.of(buyerId, farmerId)))
                        .lastMessageAt(null)
                        .build());

        boolean changed = conversation.getId() == null;
        if (orderId != null && !orderId.equals(conversation.getOrderId())) {
            conversation.setOrderId(orderId);
            changed = true;
        }
        if (listingId != null && !listingId.equals(conversation.getListingId())) {
            conversation.setListingId(listingId);
            changed = true;
        }
        if (conversation.getSubject() == null || conversation.getSubject().isBlank()) {
            conversation.setSubject(subject);
            changed = true;
        }

        Conversation saved = changed ? conversationRepository.save(conversation) : conversation;
        evictInbox(buyerId, farmerId);
        return saved;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_CONVERSATIONS, key = "#userId")
    public List<ConversationDTO> listForUser(String userId) {
        return conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(userId)
                .stream()
                .map(conversation -> toDto(conversation, userId))
                .toList();
    }

    @Override
    public PageResponse<AdminConversationDTO> listAllForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Conversation> result = conversationRepository.findAllByOrderByLastMessageAtDesc(pageable);
        List<AdminConversationDTO> content = result.getContent().stream()
                .map(this::toAdminDto)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                result.hasNext(), result.hasPrevious());
    }

    @Override
    public List<MessageDTO> listMessagesForAdmin(String conversationId) {
        findConversation(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        Conversation conversation = findConversation(conversationId);
        messageRepository.deleteAll(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));
        conversationRepository.delete(conversation);
        cacheManager.getCache(CacheConfig.CACHE_CONVERSATIONS).clear();
    }

    private AdminConversationDTO toAdminDto(Conversation conversation) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        String lastMessage = messages.isEmpty() ? null : preview(messages.get(messages.size() - 1).getBody());
        return new AdminConversationDTO(
                conversation.getId(),
                conversation.getOrderId(),
                conversation.getListingId(),
                conversation.getBuyerId(),
                displayName(conversation.getBuyerId()),
                conversation.getFarmerId(),
                displayName(conversation.getFarmerId()),
                conversation.getSubject(),
                lastMessage,
                conversation.getLastMessageAt(),
                messages.size(),
                conversation.isReported(),
                conversation.getReportReason(),
                conversation.isModerated(),
                conversation.getCreatedAt());
    }

    private String displayName(String userId) {
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse("Unknown user");
    }

    @Override
    public List<MessageDTO> listMessages(String userId, String conversationId) {
        Conversation conversation = findConversation(conversationId);
        assertParticipant(conversation, userId);
        markRead(conversation, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Override
    public MessageDTO sendMessage(String userId, String conversationId, String body) {
        Conversation conversation = findConversation(conversationId);
        assertParticipant(conversation, userId);
        assertNotModerated(conversation);
        return saveAndBroadcast(conversation, userId, body.trim());
    }

    @Override
    public MessageDTO sendMessageWs(String senderId, String conversationId, String body) {
        Conversation conversation = findConversation(conversationId);
        assertParticipant(conversation, senderId);
        assertNotModerated(conversation);
        return saveAndBroadcast(conversation, senderId, body.trim());
    }

    private MessageDTO saveAndBroadcast(Conversation conversation, String senderId, String body) {
        String senderRole = conversation.getBuyerId().equals(senderId) ? "BUYER" : "FARMER";
        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .senderRole(senderRole)
                .body(body)
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);
        evictInbox(conversation.getBuyerId(), conversation.getFarmerId());

        MessageDTO dto = toMessageDto(message);
        messagingTemplate.convertAndSend("/topic/conversation." + conversation.getId() + ".messages", dto);
        return dto;
    }


    private void evictInbox(String buyerId, String farmerId) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_CONVERSATIONS);
        if (cache == null) {
            return;
        }
        cache.evict(buyerId);
        cache.evict(farmerId);
    }

    private ConversationDTO toDto(Conversation conversation, String viewerId) {
        boolean viewerIsBuyer = conversation.getBuyerId().equals(viewerId);
        String otherPartyId = viewerIsBuyer ? conversation.getFarmerId() : conversation.getBuyerId();
        String otherPartyRole = viewerIsBuyer ? "FARMER" : "BUYER";
        String otherPartyName = userRepository.findById(otherPartyId)
                .map(User::getName)
                .orElse("Unknown user");

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        long unread = messages.stream()
                .filter(message -> !message.getSenderId().equals(viewerId))
                .filter(message -> viewerIsBuyer ? !message.isReadByBuyer() : !message.isReadByFarmer())
                .count();
        String lastMessage = messages.isEmpty() ? null : preview(messages.get(messages.size() - 1).getBody());

        return new ConversationDTO(
                conversation.getId(),
                conversation.getOrderId(),
                conversation.getListingId(),
                otherPartyId,
                otherPartyName,
                otherPartyRole,
                conversation.getSubject(),
                lastMessage,
                conversation.getLastMessageAt(),
                unread,
                conversation.isModerated()
        );
    }

    private MessageDTO toMessageDto(Message message) {
        return new MessageDTO(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getSenderRole(),
                message.getBody(),
                message.isReadByBuyer(),
                message.isReadByFarmer(),
                message.getCreatedAt()
        );
    }

    private void markRead(Conversation conversation, String viewerId) {
        boolean viewerIsBuyer = conversation.getBuyerId().equals(viewerId);
        List<Message> unread = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .filter(message -> !message.getSenderId().equals(viewerId))
                .filter(message -> viewerIsBuyer ? !message.isReadByBuyer() : !message.isReadByFarmer())
                .toList();
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(message -> {
            if (viewerIsBuyer) {
                message.setReadByBuyer(true);
            } else {
                message.setReadByFarmer(true);
            }
        });
        messageRepository.saveAll(unread);
        evictInbox(conversation.getBuyerId(), conversation.getFarmerId());
    }

    private Conversation findConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
    }

    private void assertParticipant(Conversation conversation, String userId) {
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new BusinessRuleException("You are not a participant of this conversation", 403);
        }
    }

    private void assertNotModerated(Conversation conversation) {
        if (conversation.isModerated()) {
            throw new BusinessRuleException("This thread is under moderation", 403);
        }
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= PREVIEW_LENGTH ? trimmed : trimmed.substring(0, PREVIEW_LENGTH) + "…";
    }
}