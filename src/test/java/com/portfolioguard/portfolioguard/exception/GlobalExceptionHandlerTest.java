package com.portfolioguard.portfolioguard.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void authException_returns401() throws Exception {
        mockMvc.perform(get("/throw").param("type", "auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("bad credentials"));
    }

    @Test
    void forbiddenException_returns403() throws Exception {
        mockMvc.perform(get("/throw").param("type", "forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void resourceNotFoundException_returns404() throws Exception {
        mockMvc.perform(get("/throw").param("type", "notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("not here"));
    }

    @Test
    void illegalArgumentException_returns400() throws Exception {
        mockMvc.perform(get("/throw").param("type", "illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad input"));
    }

    @Test
    void alphaVantageRateLimitException_returns503() throws Exception {
        mockMvc.perform(get("/throw").param("type", "ratelimit"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void genericException_returns500WithSanitizedMessage() throws Exception {
        mockMvc.perform(get("/throw").param("type", "generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                // Raw exception message must NOT be returned to the client
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again."));
    }

    @Test
    void errorBody_containsTimestampAndError() throws Exception {
        mockMvc.perform(get("/throw").param("type", "notfound"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ------------------------------------------------------------------
    // Inner test controller that throws specific exceptions on request
    // ------------------------------------------------------------------

    @RestController
    static class ThrowingController {

        @GetMapping("/throw")
        void throwException(@RequestParam String type) {
            switch (type) {
                case "auth"      -> throw new AuthException("bad credentials");
                case "forbidden" -> throw new ForbiddenException("access denied");
                case "notfound"  -> throw new ResourceNotFoundException("not here");
                case "illegal"   -> throw new IllegalArgumentException("bad input");
                case "ratelimit" -> throw new AlphaVantageRateLimitException();
                default          -> throw new RuntimeException("internal DB error with secrets");
            }
        }
    }
}
