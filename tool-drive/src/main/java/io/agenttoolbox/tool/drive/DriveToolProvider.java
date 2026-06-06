package io.agenttoolbox.tool.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.google.auth.GoogleAuthService;
import io.agenttoolbox.tool.drive.service.DriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * SPI ToolProvider for Google Drive integration.
 * Discovered via ServiceLoader from META-INF/services.
 *
 * <p>Uses the shared {@link GoogleAuthService} for OAuth2 authorization,
 * so credentials are managed centrally across all Google tool modules.</p>
 */
public class DriveToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DriveToolProvider.class);
    private static final String SERVICE_ID = "drive";
    private static final List<String> SCOPES = Arrays.asList(
            DriveScopes.DRIVE_READONLY,
            DriveScopes.DRIVE_FILE
    );

    private Drive drive;
    private boolean configured = false;
    private String configError;

    @Override
    public String name() {
        return "drive";
    }

    @Override
    public String description() {
        return "Google Drive tools for searching, reading, uploading, and managing files";
    }

    @Override
    public void configure(AgentConfig config) {
        try {
            GoogleAuthService authService = GoogleAuthService.fromEnvironment();
            Credential credential = authService.authorize(SERVICE_ID, SCOPES);
            this.drive = new Drive.Builder(
                    authService.getHttpTransport(),
                    authService.getJsonFactory(),
                    credential)
                    .setApplicationName("Agent Toolbox")
                    .build();
            this.configured = true;
            log.info("Google Drive tool configured successfully");
        } catch (IllegalStateException e) {
            this.configError = e.getMessage();
            log.warn("Google Drive tool not configured: {}", configError);
        } catch (Exception e) {
            this.configError = "Failed to initialize Google Drive: " + e.getMessage();
            log.warn(configError, e);
        }
    }

    @Override
    public Object toolInstance() {
        if (!configured || drive == null) {
            return new DriveToolsStub(configError != null ? configError
                    : "Google Drive is not configured. "
                    + "Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables.");
        }
        return new DriveTools(drive, new DriveService());
    }

    /**
     * Stub returned when Drive credentials are not configured.
     * Every tool method returns a helpful message explaining how to set up credentials.
     */
    static class DriveToolsStub {

        private final String message;

        DriveToolsStub(String message) {
            this.message = message;
        }

        @dev.langchain4j.agent.tool.Tool("Search for files in the user's Google Drive by name or content query")
        public String driveSearchFiles(@dev.langchain4j.agent.tool.P("search query") String query) {
            return message;
        }

        @dev.langchain4j.agent.tool.Tool("Read the contents of a file from Google Drive")
        public String driveGetFileContent(@dev.langchain4j.agent.tool.P("the Google Drive file ID") String fileId) {
            return message;
        }

        @dev.langchain4j.agent.tool.Tool("Upload a new file to Google Drive")
        public String driveUploadFile(
                @dev.langchain4j.agent.tool.P("file name") String name,
                @dev.langchain4j.agent.tool.P("file content") String content,
                @dev.langchain4j.agent.tool.P("MIME type e.g. text/plain") String mimeType) {
            return message;
        }

        @dev.langchain4j.agent.tool.Tool("List files in a Google Drive folder. Use 'root' for root folder, 'shared' for shared with me, 'starred' for starred files, 'recent' for recently viewed files, or a folder ID.")
        public String driveListFiles(
                @dev.langchain4j.agent.tool.P("folder ID, or 'root', 'shared', 'starred', 'recent'") String folderId,
                @dev.langchain4j.agent.tool.P("maximum number of results") int maxResults) {
            return message;
        }

        @dev.langchain4j.agent.tool.Tool("Get metadata about a file on Google Drive")
        public String driveGetFileMetadata(@dev.langchain4j.agent.tool.P("the Google Drive file ID") String fileId) {
            return message;
        }
    }
}
