package com.portfolioguard.portfolioguard.dto;

import com.portfolioguard.portfolioguard.model.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageResponse(String id, String role, String content, LocalDateTime createdAt) {
    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
