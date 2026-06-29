package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.dto.AuthResponse;
import com.portfolioguard.portfolioguard.dto.LoginRequest;
import com.portfolioguard.portfolioguard.dto.RegisterRequest;
import com.portfolioguard.portfolioguard.exception.AuthException;
import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.repository.UserRepository;
import com.portfolioguard.portfolioguard.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (req.email() == null || req.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (req.password() == null || req.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new AuthException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new AuthException("Email already registered");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setFirm(req.firm());
        user.setRole("USER");

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getId(), saved.getUsername(), saved.getRole());

        return new AuthResponse(token, saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new AuthException("Invalid username or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
