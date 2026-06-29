package com.portfolioguard.portfolioguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioguard.portfolioguard.model.ChatMessage;
import com.portfolioguard.portfolioguard.model.ChatSession;
import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.security.UserPrincipal;
import com.portfolioguard.portfolioguard.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock ChatService chatService;
    @InjectMocks ChatController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        setPrincipal("u1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // createSession
    // ------------------------------------------------------------------

    @Test
    void createSession_returns201() throws Exception {
        ChatSession session = session("s1", "u1", "My Session");
        when(chatService.createSession(eq("u1"), anyString())).thenReturn(session);

        String body = "{\"title\":\"My Session\",\"portfolioId\":null}";

        mockMvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s1"))
                .andExpect(jsonPath("$.title").value("My Session"));
    }

    // ------------------------------------------------------------------
    // getSessions
    // ------------------------------------------------------------------

    @Test
    void getSessions_returns200WithList() throws Exception {
        ChatSession s1 = session("s1", "u1", "Portfolio Q&A");
        ChatSession s2 = session("s2", "u1", "Risk Questions");
        when(chatService.getUserSessions("u1")).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("s1"))
                .andExpect(jsonPath("$[1].id").value("s2"));
    }

    @Test
    void getSessions_empty_returns200WithEmptyArray() throws Exception {
        when(chatService.getUserSessions("u1")).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // getSession
    // ------------------------------------------------------------------

    @Test
    void getSession_returns200WithMessages() throws Exception {
        ChatSession s = session("s1", "u1", "My Session");
        ChatMessage msg = message("m1", "user", "What is VaR?");
        when(chatService.getSessionForUser("s1", "u1")).thenReturn(s);
        when(chatService.getMessages("s1")).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/chat/sessions/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"))
                .andExpect(jsonPath("$.title").value("My Session"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"));
    }

    // ------------------------------------------------------------------
    // deleteSession
    // ------------------------------------------------------------------

    @Test
    void deleteSession_returns204() throws Exception {
        doNothing().when(chatService).deleteSession("s1", "u1");

        mockMvc.perform(delete("/api/chat/sessions/s1"))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // sendMessage
    // ------------------------------------------------------------------

    @Test
    void sendMessage_returns200WithReply() throws Exception {
        ChatMessage reply = message("m2", "assistant", "VaR estimates your worst-day loss.");
        when(chatService.sendMessage(eq("s1"), eq("u1"), eq("What is VaR?"), isNull()))
                .thenReturn(reply);

        String body = "{\"content\":\"What is VaR?\",\"portfolioId\":null}";

        mockMvc.perform(post("/api/chat/sessions/s1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m2"))
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").value("VaR estimates your worst-day loss."));
    }

    @Test
    void sendMessage_withPortfolioId_passesPortfolioId() throws Exception {
        ChatMessage reply = message("m3", "assistant", "Your VaR is -3%.");
        when(chatService.sendMessage("s1", "u1", "What is my VaR?", "p1")).thenReturn(reply);

        String body = "{\"content\":\"What is my VaR?\",\"portfolioId\":\"p1\"}";

        mockMvc.perform(post("/api/chat/sessions/s1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Your VaR is -3%."));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setPrincipal(String userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setRole("USER");
        UserPrincipal principal = new UserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private ChatSession session(String id, String userId, String title) {
        ChatSession s = new ChatSession();
        s.setId(id);
        s.setUserId(userId);
        s.setTitle(title);
        s.setMessages(new ArrayList<>());
        return s;
    }

    private ChatMessage message(String id, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
