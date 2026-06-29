package com.portfolioguard.portfolioguard.repository;

import com.portfolioguard.portfolioguard.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);
}
