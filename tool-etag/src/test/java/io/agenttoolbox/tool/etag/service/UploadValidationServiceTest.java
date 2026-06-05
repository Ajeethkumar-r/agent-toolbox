package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadValidationServiceTest {

    @TempDir
    Path tempDir;

    private StorageAdapter storageAdapter;
    private UploadValidationService service;

    @BeforeEach
    void setUp() {
        storageAdapter = Mockito.mock(StorageAdapter.class);
        service = new UploadValidationService(storageAdapter);
    }

    @Test
    void uploadsFileWithValidMd5() throws IOException {
        Path file = tempDir.resolve("upload.txt");
        Files.writeString(file, "upload content");

        byte[] content = "upload content".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);

        when(storageAdapter.write(eq("my-bucket"), eq("dest/upload.txt"), any(byte[].class), eq(md5)))
                .thenReturn(new FileMetadata("dest/upload.txt", "my-bucket", md5, md5, content.length, Instant.now(), "application/octet-stream"));

        String result = service.upload(file.toString(), "my-bucket", "dest/upload.txt");

        verify(storageAdapter).write(eq("my-bucket"), eq("dest/upload.txt"), any(byte[].class), eq(md5));
        assertThat(result).contains("Uploaded");
        assertThat(result).contains("my-bucket/dest/upload.txt");
        assertThat(result).contains(String.valueOf(content.length));
        assertThat(result).contains("MD5 verified");
        assertThat(result).contains(md5);
    }

    @Test
    void reportsCorruptionOnMd5Mismatch() throws IOException {
        Path file = tempDir.resolve("corrupt.txt");
        Files.writeString(file, "corrupt content");

        byte[] content = "corrupt content".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);

        when(storageAdapter.write(eq("my-bucket"), eq("dest/corrupt.txt"), any(byte[].class), eq(md5)))
                .thenThrow(new HashMismatchException(md5, "different-hash"));

        String result = service.upload(file.toString(), "my-bucket", "dest/corrupt.txt");

        assertThat(result).contains("REJECTED");
        assertThat(result).contains("mismatch");
    }
}
