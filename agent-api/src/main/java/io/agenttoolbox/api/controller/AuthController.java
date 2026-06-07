package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.dto.AuthResponse;
import io.agenttoolbox.api.dto.GoogleTokenRequest;
import io.agenttoolbox.api.dto.RefreshTokenRequest;
import io.agenttoolbox.api.security.JwtService;
import io.agenttoolbox.api.service.AuthService;
import org.springframework.http.ResponseEntity;
import io.agenttoolbox.api.dto.UserInfo;
import io.agenttoolbox.api.entity.User;
import io.agenttoolbox.api.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, JwtService jwtService, UserRepository userRepository) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/auth/google")
    public ResponseEntity<?> authenticateWithGoogle(@RequestBody GoogleTokenRequest request) {
        try {
            AuthResponse response = authService.authenticateWithGoogle(request.idToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(new UserInfo(
                        user.getId(), user.getEmail(),
                        user.getDisplayName(), user.getAvatarUrl())))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> deleteAccount(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        authService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractUserId(token);
    }
}
