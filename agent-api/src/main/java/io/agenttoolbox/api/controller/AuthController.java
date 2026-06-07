package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.dto.AuthResponse;
import io.agenttoolbox.api.dto.GoogleTokenRequest;
import io.agenttoolbox.api.dto.RefreshTokenRequest;
import io.agenttoolbox.api.dto.UserInfo;
import io.agenttoolbox.api.entity.User;
import io.agenttoolbox.api.repository.UserRepository;
import io.agenttoolbox.api.security.JwtService;
import io.agenttoolbox.api.service.AuthService;
import io.agenttoolbox.api.service.GoogleDriveOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final GoogleDriveOAuthService googleDriveOAuthService;
    private final String googleClientId;

    public AuthController(AuthService authService, JwtService jwtService,
                          UserRepository userRepository,
                          GoogleDriveOAuthService googleDriveOAuthService,
                          @org.springframework.beans.factory.annotation.Value("${google.client-id:}") String googleClientId) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.googleDriveOAuthService = googleDriveOAuthService;
        this.googleClientId = googleClientId;
    }

    /**
     * Returns the Google OAuth client ID for frontend Sign-In integration.
     */
    @GetMapping("/auth/google/client-id")
    public ResponseEntity<?> getGoogleClientId() {
        return ResponseEntity.ok(Map.of("clientId", googleClientId != null ? googleClientId : ""));
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

    /**
     * Initiates Google Drive OAuth consent. Returns the consent URL for the frontend to redirect to.
     */
    @GetMapping("/auth/google/consent")
    public ResponseEntity<?> googleDriveConsent(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        String consentUrl = googleDriveOAuthService.buildConsentUrl(userId);
        return ResponseEntity.ok(Map.of("consentUrl", consentUrl));
    }

    /**
     * Google OAuth callback — exchanges the authorization code for tokens,
     * encrypts and stores them. This endpoint is public (called by Google redirect).
     */
    @GetMapping("/auth/google/callback")
    public ResponseEntity<?> googleDriveCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "error", required = false) String error) {

        if (error != null) {
            log.warn("Google OAuth consent denied: error={}", error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Google consent denied: " + error));
        }

        try {
            UUID userId = UUID.fromString(state);
            googleDriveOAuthService.exchangeAndStoreTokens(code, userId);
            // Redirect to frontend success page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("http://localhost:3000/settings?drive=connected"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid state parameter in Google callback: {}", state);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid callback state"));
        } catch (Exception e) {
            log.error("Google Drive token exchange failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to connect Google Drive"));
        }
    }

    /**
     * Checks if the user has connected their Google Drive.
     */
    @GetMapping("/auth/google/drive/status")
    public ResponseEntity<?> driveStatus(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        boolean connected = googleDriveOAuthService.hasConnectedDrive(userId);
        return ResponseEntity.ok(Map.of("connected", connected));
    }

    /**
     * Revokes Google Drive access by soft-deleting stored tokens.
     */
    @PostMapping("/auth/google/drive/revoke")
    public ResponseEntity<Void> revokeDriveAccess(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        googleDriveOAuthService.revokeAccess(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractUserId(token);
    }
}
