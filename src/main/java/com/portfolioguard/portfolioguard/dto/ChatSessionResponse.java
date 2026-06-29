package com.portfolioguard.portfolioguard.dto;

import com.portfolioguard.portfolioguard.model.ChatSession;
import java.time.LocalDateTime;

public record ChatSessionResponse(String id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ChatSessionResponse from(ChatSession s) {
        return new ChatSessionResponse(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
