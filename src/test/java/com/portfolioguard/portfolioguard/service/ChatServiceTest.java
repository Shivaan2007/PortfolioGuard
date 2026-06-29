package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.exception.ForbiddenException;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import com.portfolioguard.portfolioguard.model.ChatMessage;
import com.portfolioguard.portfolioguard.model.ChatSession;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.repository.ChatMessageRepository;
import com.portfolioguard.portfolioguard.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock PortfolioService portfolioService;
    @Mock RiskMetricsService riskMetricsService;
    @Mock StockSearchService stockSearchService;
    @Mock SentimentService sentimentService;

    @InjectMocks ChatService chatService;

    // ------------------------------------------------------------------
    // createSession
    // ------------------------------------------------------------------

    @Test
    void createSession_withTitle_savesSessionWithTitle() {
        ChatSession saved = session("s1", "u1", "My Session");
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(saved);

        ChatSession result = chatService.createSession("u1", "My Session");
        assertThat(result.getTitle()).isEqualTo("My Session");
    }

    @Test
    void createSession_nullTitle_savesWithDefaultTitle() {
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatSession result = chatService.createSession("u1", null);
        assertThat(result.getTitle()).isEqualTo("New conversation");
    }

    @Test
    void createSession_blankTitle_savesWithDefaultTitle() {
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatSession result = chatService.createSession("u1", "   ");
        assertThat(result.getTitle()).isEqualTo("New conversation");
    }

    // ------------------------------------------------------------------
    // getSessionForUser
    // ------------------------------------------------------------------

    @Test
    void getSessionForUser_ownerAccess_returnsSession() {
        ChatSession s = session("s1", "u1", "title");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(s));
        assertThat(chatService.getSessionForUser("s1", "u1")).isSameAs(s);
    }

    @Test
    void getSessionForUser_notFound_throwsResourceNotFound() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.getSessionForUser("missing", "u1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSessionForUser_differentUser_throwsForbidden() {
        ChatSession s = session("s1", "u1", "title");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> chatService.getSessionForUser("s1", "attacker"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------
    // deleteSession
    // ------------------------------------------------------------------

    @Test
    void deleteSession_ownerDeletes_callsRepository() {
        ChatSession s = session("s1", "u1", "title");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(s));
        chatService.deleteSession("s1", "u1");
        verify(sessionRepository).deleteById("s1");
    }

    // ------------------------------------------------------------------
    // getMessages
    // ------------------------------------------------------------------

    @Test
    void getMessages_delegatesToRepository() {
        List<ChatMessage> msgs = List.of(new ChatMessage());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s1")).thenReturn(msgs);
        assertThat(chatService.getMessages("s1")).isSameAs(msgs);
    }

    // ------------------------------------------------------------------
    // sendMessage
    // ------------------------------------------------------------------

    @Test
    void sendMessage_savesUserAndAssistantMessages() {
        ChatSession session = session("s1", "u1", "New conversation");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatMessage result = chatService.sendMessage("s1", "u1", "explain VaR", null);

        assertThat(result.getRole()).isEqualTo("assistant");
        assertThat(result.getContent()).containsIgnoringCase("var");
        verify(messageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_setsSessionOnBothMessages() {
        ChatSession session = session("s1", "u1", "New conversation");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(messageRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenReturn(session);

        chatService.sendMessage("s1", "u1", "hello", null);

        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSession()).isSameAs(session);
        assertThat(saved.get(1).getSession()).isSameAs(session);
    }

    @Test
    void sendMessage_autoTitlesSessionFromFirstMessage() {
        ChatSession session = session("s1", "u1", "New conversation");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        when(sessionRepository.save(sessionCaptor.capture())).thenReturn(session);

        chatService.sendMessage("s1", "u1", "What is VaR?", null);

        assertThat(sessionCaptor.getValue().getTitle()).isEqualTo("What is VaR?");
    }

    @Test
    void sendMessage_titleTruncatedAt50Chars() {
        ChatSession session = session("s1", "u1", "New conversation");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        when(sessionRepository.save(sessionCaptor.capture())).thenReturn(session);

        String longMessage = "A".repeat(60);
        chatService.sendMessage("s1", "u1", longMessage, null);

        String title = sessionCaptor.getValue().getTitle();
        assertThat(title).endsWith("…");
        assertThat(title.length()).isLessThanOrEqualTo(51); // 50 chars + ellipsis
    }

    @Test
    void sendMessage_existingTitle_notOverwritten() {
        ChatSession session = session("s1", "u1", "Custom Title");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        when(sessionRepository.save(captor.capture())).thenReturn(session);

        chatService.sendMessage("s1", "u1", "What is beta?", null);

        assertThat(captor.getValue().getTitle()).isEqualTo("Custom Title");
    }

    // ------------------------------------------------------------------
    // generateReply — tested through sendMessage
    // ------------------------------------------------------------------

    @Test
    void generateReply_varKeyword_returnsVarExplanation() {
        setupSessionAndMessageMocks("s1", "u1");
        ChatMessage reply = chatService.sendMessage("s1", "u1", "explain my VaR", null);
        assertThat(reply.getContent()).containsIgnoringCase("value at risk");
    }

    @Test
    void generateReply_betaKeyword_nullPortfolio_returnsBaseExplanation() {
        setupSessionAndMessageMocks("s1", "u1");
        ChatMessage reply = chatService.sendMessage("s1", "u1", "what is beta?", null);
        assertThat(reply.getContent()).containsIgnoringCase("beta");
    }

    @Test
    void generateReply_betaKeyword_emptyPortfolio_doesNotCrash() {
        Portfolio p = new Portfolio();
        p.setId("p1");
        p.setUserId("u1");
        p.setStocks(new ArrayList<>());
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        setupSessionAndMessageMocks("s1", "u1");

        // Should not crash on empty portfolio — returns base explanation
        ChatMessage reply = chatService.sendMessage("s1", "u1", "explain beta", "p1");
        assertThat(reply.getContent()).containsIgnoringCase("beta");
    }

    @Test
    void generateReply_sharpeKeyword_returnsSharpeExplanation() {
        setupSessionAndMessageMocks("s1", "u1");
        ChatMessage reply = chatService.sendMessage("s1", "u1", "what is sharpe ratio?", null);
        assertThat(reply.getContent()).containsIgnoringCase("sharpe");
    }

    @Test
    void generateReply_unknownQuery_returnsHelpMessage() {
        setupSessionAndMessageMocks("s1", "u1");
        ChatMessage reply = chatService.sendMessage("s1", "u1", "hello world how are you doing today", null);
        assertThat(reply.getContent()).isNotBlank();
    }

    @Test
    void generateReply_exceptionInSubCall_returnsSanitizedMessage() {
        // Simulate a sub-call failure — portfolio access throws
        when(portfolioService.getPortfolioForUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));
        setupSessionAndMessageMocks("s1", "u1");

        ChatMessage reply = chatService.sendMessage("s1", "u1", "explain my portfolio risk", "p1");

        // Error message must NOT expose the raw exception details
        assertThat(reply.getContent()).doesNotContain("DB down");
        assertThat(reply.getContent()).containsIgnoringCase("portfolio");
    }

    @Test
    void generateReply_tickerSymbolDetected_callsGetQuote() {
        var quote = new com.portfolioguard.portfolioguard.dto.StockQuote(
                "AAPL", 150.0, 1.5, 1.0, 149.0, 152.0, 148.0, 148.5, 50000000L, "2024-01-01");
        when(stockSearchService.getQuote("AAPL")).thenReturn(quote);
        when(sentimentService.getSentiment("AAPL")).thenThrow(new RuntimeException("unavailable"));
        setupSessionAndMessageMocks("s1", "u1");

        ChatMessage reply = chatService.sendMessage("s1", "u1", "Tell me about AAPL", null);

        assertThat(reply.getContent()).contains("AAPL");
        verify(stockSearchService).getQuote("AAPL");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setupSessionAndMessageMocks(String sessionId, String userId) {
        ChatSession session = session(sessionId, userId, "New conversation");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            if (msg.getContent() == null) msg.setContent("mock reply");
            return msg;
        });
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(session);
    }

    private ChatSession session(String id, String userId, String title) {
        ChatSession s = new ChatSession();
        s.setId(id);
        s.setUserId(userId);
        s.setTitle(title);
        s.setMessages(new ArrayList<>());
        return s;
    }
}
