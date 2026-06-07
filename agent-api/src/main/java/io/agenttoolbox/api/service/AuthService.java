package io.agenttoolbox.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.agenttoolbox.api.dto.AuthResponse;
import io.agenttoolbox.api.dto.UserInfo;
import io.agenttoolbox.api.entity.User;
import io.agenttoolbox.api.repository.UserRepository;
import io.agenttoolbox.api.security.GoogleOAuthService;
import io.agenttoolbox.api.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(GoogleOAuthService googleOAuthService,
                       JwtService jwtService,
                       UserRepository userRepository) {
        this.googleOAuthService = googleOAuthService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleOAuthService.verifyIdToken(idToken);

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String displayName = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        User user = userRepository.findByGoogleId(googleId)
                .map(existingUser -> {
                    // Update profile info on each login
                    existingUser.setEmail(email);
                    existingUser.setDisplayName(displayName);
                    existingUser.setAvatarUrl(avatarUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = new User(UUID.randomUUID(), googleId, email, displayName, avatarUrl);
                    log.info("Creating new user: email={}", email);
                    return userRepository.save(newUser);
                });

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshAccessToken(String refreshToken) {
        UUID userId = jwtService.extractUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isSuspended()) {
            throw new IllegalStateException("User account is suspended");
        }

        return buildAuthResponse(user);
    }

    public void logout(UUID userId) {
        // Stateless JWT — no server-side invalidation needed for now.
        // When a refresh_tokens table is added, revoke tokens here.
        log.info("User logged out: userId={}", userId);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
        log.info("User account deleted: userId={}", userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        UserInfo userInfo = new UserInfo(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpirySeconds(),
                userInfo
        );
    }
}
