package io.agenttoolbox.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.agenttoolbox.api.entity.UserToken;
import io.agenttoolbox.api.repository.UserTokenRepository;
import io.agenttoolbox.api.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles the web-based Google OAuth consent flow for Google Drive access.
 * Exchanges authorization codes for tokens, encrypts them, and stores in DB.
 */
@Service
public class GoogleDriveOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveOAuthService.class);

    private static final String PROVIDER = "google";
    private static final List<String> DRIVE_SCOPES = List.of(
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/drive.file"
    );

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final TokenService tokenService;
    private final UserTokenRepository userTokenRepository;

    public GoogleDriveOAuthService(
            @Value("${google.client-id:}") String clientId,
            @Value("${google.client-secret:}") String clientSecret,
            @Value("${google.redirect-uri:http://localhost:8085/api/v1/auth/google/callback}") String redirectUri,
            TokenService tokenService,
            UserTokenRepository userTokenRepository) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenService = tokenService;
        this.userTokenRepository = userTokenRepository;
    }

    /**
     * Builds the Google OAuth consent URL that the frontend should redirect the user to.
     *
     * @param userId the authenticated user's ID, encoded in the state parameter
     * @return the full Google consent URL
     */
    public String buildConsentUrl(UUID userId) {
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                clientId,
                clientSecret,
                DRIVE_SCOPES)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(userId.toString())
                .build();
    }

    /**
     * Exchanges an authorization code for access/refresh tokens,
     * encrypts them, and stores in the user_tokens table.
     *
     * @param code   the authorization code from Google callback
     * @param userId the user who granted consent
     */
    @Transactional
    public void exchangeAndStoreTokens(String code, UUID userId) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    code,
                    redirectUri)
                    .execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

            // Encrypt tokens
            byte[] accessTokenEnc = tokenService.encrypt(accessToken);
            byte[] refreshTokenEnc = refreshToken != null
                    ? tokenService.encrypt(refreshToken)
                    : null;

            // Upsert: update existing or create new
            UserToken userToken = userTokenRepository
                    .findByUserIdAndProviderAndDeletedAtIsNull(userId, PROVIDER)
                    .orElseGet(() -> new UserToken(userId, PROVIDER));

            userToken.setAccessTokenEnc(accessTokenEnc);
            if (refreshTokenEnc != null) {
                userToken.setRefreshTokenEnc(refreshTokenEnc);
            }
            userToken.setScopes(DRIVE_SCOPES.toArray(new String[0]));
            if (expiresInSeconds != null) {
                userToken.setExpiresAt(Instant.now().plusSeconds(expiresInSeconds));
            }

            userTokenRepository.save(userToken);
            log.info("Google Drive tokens stored for userId={}", userId);

        } catch (IOException e) {
            log.error("Failed to exchange Google auth code for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code", e);
        }
    }

    /**
     * Retrieves and decrypts the Google access token for a user.
     *
     * @param userId the user whose token to retrieve
     * @return the decrypted access token, or null if not found
     */
    @Transactional(readOnly = true)
    public String getAccessToken(UUID userId) {
        return userTokenRepository
                .findByUserIdAndProviderAndDeletedAtIsNull(userId, PROVIDER)
                .map(token -> tokenService.decrypt(token.getAccessTokenEnc()))
                .orElse(null);
    }

    /**
     * Checks whether a user has connected their Google Drive.
     *
     * @param userId the user to check
     * @return true if tokens exist for this user
     */
    @Transactional(readOnly = true)
    public boolean hasConnectedDrive(UUID userId) {
        return userTokenRepository
                .findByUserIdAndProviderAndDeletedAtIsNull(userId, PROVIDER)
                .isPresent();
    }

    /**
     * Revokes Google Drive access by soft-deleting the stored tokens.
     *
     * @param userId the user whose tokens to revoke
     */
    @Transactional
    public void revokeAccess(UUID userId) {
        userTokenRepository.findByUserIdAndProviderAndDeletedAtIsNull(userId, PROVIDER)
                .ifPresent(token -> {
                    token.setDeletedAt(Instant.now());
                    userTokenRepository.save(token);
                    log.info("Google Drive access revoked for userId={}", userId);
                });
    }
}
