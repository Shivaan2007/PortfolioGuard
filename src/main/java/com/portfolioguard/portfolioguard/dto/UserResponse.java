package com.portfolioguard.portfolioguard.dto;

import com.portfolioguard.portfolioguard.model.User;
import java.time.LocalDateTime;

public record UserResponse(String id, String username, String email, String fullName,
                            String firm, String role, LocalDateTime createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                u.getFirm(), u.getRole(), u.getCreatedAt());
    }
}
