package io.agenttoolbox.google.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Shared Google OAuth2 service for all Google API integrations.
 * Handles authorization flow, token storage, and token refresh.
 *
 * <p>Each Google service (Drive, Gmail, Sheets, etc.) calls
 * {@link #authorize(String, List)} with its service ID and required scopes.
 * Tokens are stored per service ID so each integration maintains
 * its own credentials.</p>
 *
 * <p>For CLI mode, uses {@link LocalServerReceiver} to open a browser
 * for user consent. Tokens are persisted locally so re-auth is not
 * needed on subsequent runs.</p>
 */
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final Path CREDENTIALS_ROOT = Path.of(
            System.getProperty("user.home"), ".agent-toolbox", "google-credentials"
    );

    private final String clientId;
    private final String clientSecret;
    private NetHttpTransport httpTransport;

    /**
     * Creates a GoogleAuthService using the given OAuth client credentials.
     */
    public GoogleAuthService(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "Google OAuth credentials not configured. "
                            + "Set the GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables. "
                            + "Create OAuth credentials at https://console.cloud.google.com/apis/credentials"
            );
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Creates a GoogleAuthService reading credentials from environment variables.
     *
     * @throws IllegalStateException if GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET is missing
     */
    public static GoogleAuthService fromEnvironment() {
        return new GoogleAuthService(
                System.getenv("GOOGLE_CLIENT_ID"),
                System.getenv("GOOGLE_CLIENT_SECRET")
        );
    }

    /**
     * Returns the shared HTTP transport, creating it lazily.
     */
    public NetHttpTransport getHttpTransport() throws GeneralSecurityException, IOException {
        if (httpTransport == null) {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        }
        return httpTransport;
    }

    /**
     * Returns the JSON factory used for serialization.
     */
    public JsonFactory getJsonFactory() {
        return JSON_FACTORY;
    }

    /**
     * Authorizes access to a Google service, reusing stored tokens if available.
     * On first run for a given serviceId, opens a browser for user consent.
     *
     * @param serviceId unique identifier for the service (e.g., "drive", "gmail")
     * @param scopes    required OAuth scopes for the service
     * @return authorized credential
     */
    public Credential authorize(String serviceId, List<String> scopes)
            throws IOException, GeneralSecurityException {

        NetHttpTransport transport = getHttpTransport();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(clientId)
                .setClientSecret(clientSecret);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets()
                .setInstalled(details);

        Path tokensDir = CREDENTIALS_ROOT.resolve(serviceId);
        Files.createDirectories(tokensDir);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, JSON_FACTORY, clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(tokensDir.toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        log.info("Authorizing Google {} access...", serviceId);
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
}
