package io.agenttoolbox.tool.drive.service;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Business logic for Google Drive operations.
 * Each method works with a pre-authorized Drive client.
 */
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);
    private static final int MAX_CONTENT_CHARS = 10_000;
    private static final String FILE_FIELDS = "files(id, name, mimeType, modifiedTime, size, webViewLink)";

    /**
     * Google Docs export MIME types — when downloading these, we export instead.
     */
    private static final Set<String> GOOGLE_DOC_TYPES = Set.of(
            "application/vnd.google-apps.document",
            "application/vnd.google-apps.spreadsheet",
            "application/vnd.google-apps.presentation",
            "application/vnd.google-apps.drawing"
    );

    /**
     * Searches for files matching the given query string.
     * The query is wrapped in fullText contains '...' syntax.
     *
     * @param drive authorized Drive client
     * @param query user's search query (natural language)
     * @return formatted search results
     */
    public String searchFiles(Drive drive, String query) throws IOException {
        // Transform natural language query into Drive query syntax
        String driveQuery = "fullText contains '" + escapeQuery(query) + "' and trashed = false";

        FileList result = drive.files().list()
                .setQ(driveQuery)
                .setPageSize(10)
                .setFields(FILE_FIELDS)
                .setOrderBy("modifiedTime desc")
                .execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return "No files found matching: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(files.size()).append(" file(s) matching '").append(query).append("':\n\n");
        for (File file : files) {
            appendFileInfo(sb, file);
        }
        return sb.toString();
    }

    /**
     * Gets the content of a file by ID.
     * For Google Docs/Sheets/Slides, exports as plain text.
     * For regular files, downloads the content directly.
     * Content is capped at 10,000 characters.
     */
    public String getFileContent(Drive drive, String fileId) throws IOException {
        File fileMeta = drive.files().get(fileId)
                .setFields("id, name, mimeType, size")
                .execute();

        String mimeType = fileMeta.getMimeType();
        String fileName = fileMeta.getName();
        byte[] content;

        if (GOOGLE_DOC_TYPES.contains(mimeType)) {
            // Export Google Workspace files as plain text
            String exportMimeType = getExportMimeType(mimeType);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().export(fileId, exportMimeType)
                    .executeMediaAndDownloadTo(outputStream);
            content = outputStream.toByteArray();
        } else {
            // Download regular file content
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);
            content = outputStream.toByteArray();
        }

        String text = new String(content);
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(fileName).append("\n");
        sb.append("Type: ").append(mimeType).append("\n");
        sb.append("---\n");

        if (text.length() > MAX_CONTENT_CHARS) {
            sb.append(text, 0, MAX_CONTENT_CHARS);
            sb.append("\n\n[Content truncated at ").append(MAX_CONTENT_CHARS)
                    .append(" characters. Total length: ").append(text.length()).append(" characters]");
        } else {
            sb.append(text);
        }

        return sb.toString();
    }

    /**
     * Uploads a new file to the user's Drive root.
     *
     * @param drive   authorized Drive client
     * @param name    file name
     * @param content file content as string
     * @param mimeType MIME type of the content (e.g., text/plain)
     * @return confirmation with file ID and name
     */
    public String uploadFile(Drive drive, String name, String content, String mimeType) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(name);

        ByteArrayContent mediaContent = new ByteArrayContent(mimeType, content.getBytes());

        File uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute();

        return "File uploaded successfully.\n"
                + "Name: " + uploadedFile.getName() + "\n"
                + "ID: " + uploadedFile.getId() + "\n"
                + "Link: " + (uploadedFile.getWebViewLink() != null ? uploadedFile.getWebViewLink() : "N/A");
    }

    /**
     * Lists files in a folder (or root if folderId is null or "root").
     *
     * @param drive      authorized Drive client
     * @param folderId   folder ID, or null/"root" for root folder
     * @param maxResults maximum number of results (default 20)
     * @return formatted file listing
     */
    public String listFiles(Drive drive, String folderId, int maxResults) throws IOException {
        if (maxResults <= 0) {
            maxResults = 20;
        }

        String query;
        String label;

        if ("shared".equalsIgnoreCase(folderId)) {
            query = "sharedWithMe = true and trashed = false";
            label = "Shared with me";
        } else if ("starred".equalsIgnoreCase(folderId)) {
            query = "starred = true and trashed = false";
            label = "Starred";
        } else if ("recent".equalsIgnoreCase(folderId)) {
            query = "trashed = false";
            label = "Recent files";
        } else {
            String parentId = (folderId == null || folderId.isBlank() || "root".equalsIgnoreCase(folderId))
                    ? "root" : folderId;
            query = "'" + parentId + "' in parents and trashed = false";
            label = "root".equals(parentId) ? "root folder" : "folder " + parentId;
        }

        Drive.Files.List request = drive.files().list()
                .setQ(query)
                .setPageSize(maxResults)
                .setFields(FILE_FIELDS);

        if ("recent".equalsIgnoreCase(folderId)) {
            request.setOrderBy("viewedByMeTime desc");
        } else {
            request.setOrderBy("folder,name");
        }

        FileList result = request.execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return "No files found in " + label + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Files in ").append(label)
                .append(" (").append(files.size()).append(" items):\n\n");
        for (File file : files) {
            appendFileInfo(sb, file);
        }
        return sb.toString();
    }

    /**
     * Gets metadata about a specific file.
     */
    public String getFileMetadata(Drive drive, String fileId) throws IOException {
        File file = drive.files().get(fileId)
                .setFields("id, name, mimeType, size, modifiedTime, createdTime, webViewLink, owners, shared")
                .execute();

        StringBuilder sb = new StringBuilder();
        sb.append("File Metadata\n");
        sb.append("Name: ").append(file.getName()).append("\n");
        sb.append("ID: ").append(file.getId()).append("\n");
        sb.append("Type: ").append(file.getMimeType()).append("\n");
        sb.append("Size: ").append(formatSize(file.getSize())).append("\n");
        sb.append("Modified: ").append(file.getModifiedTime() != null ? file.getModifiedTime().toString() : "N/A").append("\n");
        sb.append("Created: ").append(file.getCreatedTime() != null ? file.getCreatedTime().toString() : "N/A").append("\n");
        sb.append("Link: ").append(file.getWebViewLink() != null ? file.getWebViewLink() : "N/A").append("\n");
        sb.append("Shared: ").append(file.getShared() != null ? file.getShared() : "N/A");
        return sb.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void appendFileInfo(StringBuilder sb, File file) {
        sb.append("- ").append(file.getName()).append("\n");
        sb.append("  ID: ").append(file.getId()).append("\n");
        sb.append("  Type: ").append(file.getMimeType()).append("\n");
        if (file.getModifiedTime() != null) {
            sb.append("  Modified: ").append(file.getModifiedTime().toString()).append("\n");
        }
        if (file.getSize() != null) {
            sb.append("  Size: ").append(formatSize(file.getSize())).append("\n");
        }
        sb.append("\n");
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String getExportMimeType(String googleMimeType) {
        return switch (googleMimeType) {
            case "application/vnd.google-apps.spreadsheet" -> "text/csv";
            case "application/vnd.google-apps.presentation" -> "text/plain";
            case "application/vnd.google-apps.drawing" -> "image/svg+xml";
            default -> "text/plain"; // Google Docs and others
        };
    }

    private String escapeQuery(String query) {
        // Escape single quotes for Drive API query syntax
        return query.replace("'", "\\'");
    }
}
