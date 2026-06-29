package com.portfolioguard.portfolioguard.controller;

import com.portfolioguard.portfolioguard.dto.AuthResponse;
import com.portfolioguard.portfolioguard.dto.LoginRequest;
import com.portfolioguard.portfolioguard.dto.RegisterRequest;
import com.portfolioguard.portfolioguard.dto.UserResponse;
import com.portfolioguard.portfolioguard.security.UserPrincipal;
import com.portfolioguard.portfolioguard.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(UserResponse.from(principal.getUser()));
    }
}
