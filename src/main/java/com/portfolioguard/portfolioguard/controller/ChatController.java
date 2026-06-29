package com.portfolioguard.portfolioguard.controller;

import com.portfolioguard.portfolioguard.dto.*;
import com.portfolioguard.portfolioguard.model.ChatMessage;
import com.portfolioguard.portfolioguard.model.ChatSession;
import com.portfolioguard.portfolioguard.security.UserPrincipal;
import com.portfolioguard.portfolioguard.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(@RequestBody CreateChatSessionRequest request,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        ChatSession session = chatService.createSession(principal.getId(), request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatSessionResponse.from(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getSessions(@AuthenticationPrincipal UserPrincipal principal) {
        List<ChatSessionResponse> sessions = chatService.getUserSessions(principal.getId())
                .stream().map(ChatSessionResponse::from).toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ChatSessionDetail> getSession(@PathVariable String id,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        ChatSession session = chatService.getSessionForUser(id, principal.getId());
        List<ChatMessageResponse> messages = chatService.getMessages(id)
                .stream().map(ChatMessageResponse::from).toList();
        return ResponseEntity.ok(new ChatSessionDetail(session.getId(), session.getTitle(),
                session.getCreatedAt(), session.getUpdatedAt(), messages));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        chatService.deleteSession(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(@PathVariable String id,
                                                             @RequestBody SendMessageRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        ChatMessage reply = chatService.sendMessage(id, principal.getId(), request.content(), request.portfolioId());
        return ResponseEntity.ok(ChatMessageResponse.from(reply));
    }
}
