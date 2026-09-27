package com.agrolink.app.controller.api;

import com.agrolink.app.dto.AdminConversationDTO;
import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.ConversationDTO;
import com.agrolink.app.dto.CreateConversationRecord;
import com.agrolink.app.dto.MessageDTO;
import com.agrolink.app.dto.PageResponse;
import com.agrolink.app.dto.SendMessageRecord;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.model.User;
import com.agrolink.app.service.MessageService;
import com.agrolink.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final UserService userService;

    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ConversationDTO>> openConversation(
            @Valid @RequestBody CreateConversationRecord request,
            Authentication authentication) {
        if (!request.hasTarget()) {
            throw new BusinessRuleException("Provide an orderId or listingId to start a conversation", 400);
        }
        String userId = currentUserId(authentication);
        ConversationDTO conversation = request.orderId() != null && !request.orderId().isBlank()
                ? messageService.openByOrder(userId, request.orderId())
                : messageService.openByListing(userId, request.listingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Conversation ready", conversation));
    }

    @GetMapping("/admin/conversations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminConversationDTO>>> allConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Conversations loaded",
                messageService.listAllForAdmin(page, size)));
    }

    @GetMapping("/admin/conversations/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<MessageDTO>>> allMessages(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                messageService.listMessagesForAdmin(conversationId)));
    }

    @DeleteMapping("/admin/conversations/{conversationId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable String conversationId) {
        messageService.deleteConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.ok("Conversation deleted", null));
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ConversationDTO>>> conversations(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.listForUser(currentUserId(authentication))));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MessageDTO>>> messages(@PathVariable String conversationId,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                messageService.listMessages(currentUserId(authentication), conversationId)));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MessageDTO>> send(@PathVariable String conversationId,
                                                        @Valid @RequestBody SendMessageRecord request,
                                                        Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Message sent",
                messageService.sendMessage(currentUserId(authentication), conversationId, request.content())));
    }

    private String currentUserId(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return user.getId();
    }
}