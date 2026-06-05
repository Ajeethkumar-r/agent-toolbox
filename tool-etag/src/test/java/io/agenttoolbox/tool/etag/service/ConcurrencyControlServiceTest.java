package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.PreconditionFailedException;
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

class ConcurrencyControlServiceTest {

    @TempDir
    Path tempDir;

    private StorageAdapter storageAdapter;
    private ConcurrencyControlService service;

    @BeforeEach
    void setUp() {
        storageAdapter = Mockito.mock(StorageAdapter.class);
        service = new ConcurrencyControlService(storageAdapter);
    }

    @Test
    void updatesFileWhenEtagMatches() throws IOException {
        Path file = tempDir.resolve("update.txt");
        Files.writeString(file, "updated content");

        String newMd5 = Md5Hasher.hashBytes("updated content".getBytes(StandardCharsets.UTF_8));
        String knownEtag = "current-etag-value";

        when(storageAdapter.conditionalWrite(eq("my-bucket"), eq("doc.txt"), any(byte[].class), eq(knownEtag)))
                .thenReturn(new FileMetadata("doc.txt", "my-bucket", newMd5, newMd5, 15, Instant.now(), "application/octet-stream"));

        String result = service.update("my-bucket", "doc.txt", file.toString(), knownEtag);

        verify(storageAdapter).conditionalWrite(eq("my-bucket"), eq("doc.txt"), any(byte[].class), eq(knownEtag));
        assertThat(result).contains("Updated");
        assertThat(result).contains("my-bucket/doc.txt");
        assertThat(result).contains("New ETag:");
        assertThat(result).contains(newMd5);
    }

    @Test
    void reportsConflictWhenEtagStale() throws IOException {
        Path file = tempDir.resolve("conflict.txt");
        Files.writeString(file, "conflict content");

        String staleEtag = "stale-etag-value";

        when(storageAdapter.conditionalWrite(eq("my-bucket"), eq("doc.txt"), any(byte[].class), eq(staleEtag)))
                .thenThrow(new PreconditionFailedException(staleEtag, "newer-etag-value"));

        String result = service.update("my-bucket", "doc.txt", file.toString(), staleEtag);

        assertThat(result).contains("CONFLICT");
        assertThat(result).contains("my-bucket/doc.txt");
    }
}
