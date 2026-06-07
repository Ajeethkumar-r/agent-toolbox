package io.agenttoolbox.api.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.agenttoolbox.tool.drive.DriveTools;
import io.agenttoolbox.tool.drive.service.DriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Builds per-user DriveTools instances from stored OAuth tokens.
 * Used by ChatService to give the LLM access to the user's Google Drive.
 */
@Service
public class UserDriveClientFactory {

    private static final Logger log = LoggerFactory.getLogger(UserDriveClientFactory.class);

    private final GoogleDriveOAuthService driveOAuthService;

    public UserDriveClientFactory(GoogleDriveOAuthService driveOAuthService) {
        this.driveOAuthService = driveOAuthService;
    }

    /**
     * Builds a DriveTools instance for the given user using their stored OAuth token.
     *
     * @param userId the authenticated user
     * @return DriveTools wired with the user's Drive client, or null if Drive not connected
     */
    public DriveTools buildDriveTools(UUID userId) {
        if (!driveOAuthService.hasConnectedDrive(userId)) {
            return null;
        }

        String accessToken = driveOAuthService.getAccessToken(userId);
        if (accessToken == null) {
            log.warn("Drive connected but access token is null for userId={}", userId);
            return null;
        }

        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

        Drive drive = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Agent Toolbox")
                .build();

        log.debug("Built DriveTools for userId={}", userId);
        return new DriveTools(drive, new DriveService());
    }
}
