package com.portfolioguard.portfolioguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioguard.portfolioguard.dto.AuthResponse;
import com.portfolioguard.portfolioguard.dto.LoginRequest;
import com.portfolioguard.portfolioguard.dto.RegisterRequest;
import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.security.UserPrincipal;
import com.portfolioguard.portfolioguard.service.AuthService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @InjectMocks AuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        setPrincipal("u1", "alice");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_success_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest("alice", "alice@test.com", "password123", "Alice Smith", "ACME");
        AuthResponse resp = new AuthResponse("tok", "u1", "alice", "alice@test.com", "USER");
        when(authService.register(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void login_success_returns200() throws Exception {
        LoginRequest req = new LoginRequest("alice", "password123");
        AuthResponse resp = new AuthResponse("tok", "u1", "alice", "alice@test.com", "USER");
        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"));
    }

    @Test
    void me_returns200WithCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setPrincipal(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setRole("USER");
        UserPrincipal principal = new UserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
