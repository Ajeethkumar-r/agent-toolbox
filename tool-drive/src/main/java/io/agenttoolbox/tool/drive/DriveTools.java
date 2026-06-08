package io.agenttoolbox.tool.drive;

import com.google.api.services.drive.Drive;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.agenttoolbox.tool.drive.service.DriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j tool class providing Google Drive operations.
 * Each method is annotated with @Tool for automatic discovery by the agent.
 * Method names are prefixed with "drive" to avoid collisions with local storage tools.
 */
public class DriveTools {

    private static final Logger log = LoggerFactory.getLogger(DriveTools.class);

    private final Drive drive;
    private final DriveService driveService;

    public DriveTools(Drive drive, DriveService driveService) {
        this.drive = drive;
        this.driveService = driveService;
    }

    @Tool("Search for files in the user's Google Drive by name or content query. ALWAYS call this first to get the file ID before reading a file. Returns file IDs that can be used with driveGetFileContent.")
    public String driveSearchFiles(@P("search query — a file name or keyword to search for") String query) {
        try {
            progress("Searching Drive for '%s'...", query);
            return driveService.searchFiles(drive, query);
        } catch (Exception e) {
            log.error("Failed to search Drive files", e);
            return formatError("search files", e);
        }
    }

    @Tool("Read the contents of a file from Google Drive. The fileId must be the actual Google Drive file ID (like '1aBcDeFgHiJkL'), NOT the file name. Use driveSearchFiles first to get the file ID.")
    public String driveGetFileContent(@P("the Google Drive file ID (e.g. '1aBcDeFgHiJkL') — NOT the file name. Get this from driveSearchFiles or driveListFiles first.") String fileId) {
        try {
            progress("Reading file %s from Drive...", fileId);
            return driveService.getFileContent(drive, fileId);
        } catch (Exception e) {
            log.error("Failed to read Drive file content", e);
            return formatError("read file content", e);
        }
    }

    @Tool("Upload a new file to Google Drive")
    public String driveUploadFile(
            @P("file name") String name,
            @P("file content") String content,
            @P("MIME type e.g. text/plain") String mimeType) {
        try {
            progress("Uploading '%s' to Drive...", name);
            return driveService.uploadFile(drive, name, content, mimeType);
        } catch (Exception e) {
            log.error("Failed to upload file to Drive", e);
            return formatError("upload file", e);
        }
    }

    @Tool("List files in a Google Drive folder. Use 'root' for root folder, 'shared' for shared with me, 'starred' for starred files, 'recent' for recently viewed files, or a folder ID.")
    public String driveListFiles(
            @P("folder ID, or 'root', 'shared', 'starred', 'recent'") String folderId,
            @P("maximum number of results") int maxResults) {
        try {
            progress("Listing files in folder %s...", folderId);
            return driveService.listFiles(drive, folderId, maxResults);
        } catch (Exception e) {
            log.error("Failed to list Drive files", e);
            return formatError("list files", e);
        }
    }

    @Tool("Get metadata about a file on Google Drive. The fileId must be the actual Drive file ID, not the file name.")
    public String driveGetFileMetadata(@P("the Google Drive file ID (e.g. '1aBcDeFgHiJkL') — NOT the file name") String fileId) {
        try {
            progress("Getting metadata for file %s...", fileId);
            return driveService.getFileMetadata(drive, fileId);
        } catch (Exception e) {
            log.error("Failed to get Drive file metadata", e);
            return formatError("get file metadata", e);
        }
    }

    private static String formatError(String operation, Exception e) {
        return "ERROR: Failed to " + operation + " — " + e.getMessage()
                + "\nACTION: Check that the file ID is correct and you have access. "
                + "If this is a permissions issue, re-authorize with Google Drive.";
    }

    private static void progress(String format, Object... args) {
        System.out.printf("  >> " + format + "%n", args);
        System.out.flush();
    }
}
