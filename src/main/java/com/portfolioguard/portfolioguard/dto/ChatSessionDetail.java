package com.portfolioguard.portfolioguard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionDetail(String id, String title, LocalDateTime createdAt,
                                  LocalDateTime updatedAt, List<ChatMessageResponse> messages) {}
