package com.agrolink.app.service;

import com.agrolink.app.dto.AssistantChatRequest;
import com.agrolink.app.dto.AssistantChatResponse;
import com.agrolink.app.model.User;

public interface AssistantService {
    AssistantChatResponse chat(User user, AssistantChatRequest request);
}