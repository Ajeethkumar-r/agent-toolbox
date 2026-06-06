package io.agenttoolbox.common.error;

import io.agenttoolbox.common.exception.*;

/**
 * Formats tool exceptions into structured ERROR + ACTION strings
 * that guide the LLM toward recovery actions.
 */
public final class ToolErrorFormatter {

    private ToolErrorFormatter() {}

    public static String format(Exception e) {
        if (e instanceof BucketNotFoundException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call listBuckets to see available bucket names.";
        }
        if (e instanceof FileNotFoundException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call listFiles with the bucket name to see available files.";
        }
        if (e instanceof PreconditionFailedException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call getFileInfo to get the current ETag, then retry conditionalUpdate with the new ETag.";
        }
        if (e instanceof HashMismatchException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: The file may be corrupted. Try uploading again with uploadWithValidation.";
        }
        if (e instanceof StorageException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: This is a storage error. Check if the bucket exists and the file path is correct.";
        }
        return "ERROR: " + e.getMessage()
                + "\nACTION: An unexpected error occurred. Report this to the user.";
    }
}
