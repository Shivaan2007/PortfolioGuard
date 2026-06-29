package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.dto.AuthResponse;
import com.portfolioguard.portfolioguard.dto.LoginRequest;
import com.portfolioguard.portfolioguard.dto.RegisterRequest;
import com.portfolioguard.portfolioguard.exception.AuthException;
import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.repository.UserRepository;
import com.portfolioguard.portfolioguard.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @InjectMocks AuthService authService;

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "password123", "Alice Smith", "Acme");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = new User();
        saved.setId("u1");
        saved.setUsername("alice");
        saved.setEmail("alice@example.com");
        saved.setRole("USER");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken("u1", "alice", "USER")).thenReturn("token123");

        AuthResponse resp = authService.register(req);

        assertThat(resp.token()).isEqualTo("token123");
        assertThat(resp.userId()).isEqualTo("u1");
        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.role()).isEqualTo("USER");
    }

    @Test
    void register_blankUsername_throwsIllegalArgument() {
        RegisterRequest req = new RegisterRequest("  ", "a@b.com", "password123", null, null);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void register_nullUsername_throwsIllegalArgument() {
        RegisterRequest req = new RegisterRequest(null, "a@b.com", "password123", null, null);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_blankEmail_throwsIllegalArgument() {
        RegisterRequest req = new RegisterRequest("alice", "", "password123", null, null);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is required");
    }

    @Test
    void register_shortPassword_throwsIllegalArgument() {
        RegisterRequest req = new RegisterRequest("alice", "a@b.com", "short", null, null);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void register_duplicateUsername_throwsAuthException() {
        RegisterRequest req = new RegisterRequest("alice", "a@b.com", "password123", null, null);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    void register_duplicateEmail_throwsAuthException() {
        RegisterRequest req = new RegisterRequest("alice", "a@b.com", "password123", null, null);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Email already registered");
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void login_success_returnsAuthResponse() {
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed");
        user.setRole("USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("u1", "alice", "USER")).thenReturn("tok");

        AuthResponse resp = authService.login(new LoginRequest("alice", "secret"));

        assertThat(resp.token()).isEqualTo("tok");
        assertThat(resp.username()).isEqualTo("alice");
    }

    @Test
    void login_unknownUsername_throwsAuthException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pw")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_wrongPassword_throwsAuthException() {
        User user = new User();
        user.setPasswordHash("hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }
}
