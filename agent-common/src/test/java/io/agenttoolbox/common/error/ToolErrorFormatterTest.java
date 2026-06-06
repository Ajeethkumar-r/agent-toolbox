package io.agenttoolbox.common.error;

import io.agenttoolbox.common.exception.*;
import io.agenttoolbox.common.exception.BucketNotIndexedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolErrorFormatterTest {

    @Test
    void formatsBucketNotIndexedException() {
        BucketNotIndexedException e = new BucketNotIndexedException("my-bucket");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("my-bucket");
        assertThat(result).contains("ACTION: Call ingestBucket to index the bucket before searching.");
    }

    @Test
    void formatsBucketNotFoundException() {
        BucketNotFoundException e = new BucketNotFoundException("my-bucket");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("my-bucket");
        assertThat(result).contains("ACTION: Call listBuckets to see available bucket names.");
    }

    @Test
    void formatsFileNotFoundException() {
        FileNotFoundException e = new FileNotFoundException("my-bucket", "docs/readme.md");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("docs/readme.md");
        assertThat(result).contains("ACTION: Call listFiles with the bucket name to see available files.");
    }

    @Test
    void formatsPreconditionFailedException() {
        PreconditionFailedException e = new PreconditionFailedException("abc123", "def456");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: Call getFileInfo to get the current ETag, then retry conditionalUpdate with the new ETag.");
    }

    @Test
    void formatsHashMismatchException() {
        HashMismatchException e = new HashMismatchException("expected123", "actual456");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: The file may be corrupted. Try uploading again with uploadWithValidation.");
    }

    @Test
    void formatsGenericStorageException() {
        StorageException e = new StorageException("disk full");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: This is a storage error. Check if the bucket exists and the file path is correct.");
    }

    @Test
    void formatsUnknownException() {
        RuntimeException e = new RuntimeException("something weird");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("something weird");
        assertThat(result).contains("ACTION: An unexpected error occurred. Report this to the user.");
    }
}
