package com.portfolioguard.portfolioguard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-must-be-32-chars!!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 86400000L);
        ReflectionTestUtils.invokeMethod(jwtUtil, "initKey");
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateToken("user-1", "alice", "USER");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void parseClaims_extractsSubjectAndCustomClaims() {
        String token = jwtUtil.generateToken("user-1", "alice", "ADMIN");
        Claims claims = jwtUtil.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void isValid_returnsTrueForFreshToken() {
        String token = jwtUtil.generateToken("user-1", "alice", "USER");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForGarbageToken() {
        assertThat(jwtUtil.isValid("not.a.valid.jwt")).isFalse();
    }

    @Test
    void isValid_returnsFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L);
        String token = jwtUtil.generateToken("user-1", "alice", "USER");
        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void isValid_returnsFalseForEmptyString() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    void getUserId_returnsSubjectFromToken() {
        String token = jwtUtil.generateToken("user-42", "bob", "USER");
        assertThat(jwtUtil.getUserId(token)).isEqualTo("user-42");
    }

    @Test
    void getRole_returnsRoleClaimFromToken() {
        String token = jwtUtil.generateToken("user-1", "alice", "ADMIN");
        assertThat(jwtUtil.getRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void initKey_shortSecretPaddedToMinimumLength() {
        JwtUtil shortSecret = new JwtUtil();
        ReflectionTestUtils.setField(shortSecret, "secret", "short");
        ReflectionTestUtils.setField(shortSecret, "expirationMs", 86400000L);
        // Should not throw — padding brings it to 32 bytes
        ReflectionTestUtils.invokeMethod(shortSecret, "initKey");
        String token = shortSecret.generateToken("u1", "alice", "USER");
        assertThat(shortSecret.isValid(token)).isTrue();
    }

    @Test
    void cachedKey_sameKeyUsedAcrossMultipleCalls() {
        // Generate two tokens and verify both can be parsed with the same key
        String t1 = jwtUtil.generateToken("u1", "alice", "USER");
        String t2 = jwtUtil.generateToken("u2", "bob", "USER");
        assertThat(jwtUtil.getUserId(t1)).isEqualTo("u1");
        assertThat(jwtUtil.getUserId(t2)).isEqualTo("u2");
    }
}
