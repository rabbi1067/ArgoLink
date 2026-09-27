package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ApiResponse;
import com.agrolink.app.dto.ChatMessageWsPayload;
import com.agrolink.app.dto.MessageDTO;
import com.agrolink.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final MessageService messageService;

    @MessageMapping("/chat.send")
    public void send(ChatMessageWsPayload payload) {
        if (payload == null || payload.conversationId() == null || payload.body() == null || payload.body().isBlank()) {
            return;
        }
        MessageDTO dto = messageService.sendMessageWs(
                payload.senderId(), payload.conversationId(), payload.body());
    }
}