package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.FileNotFoundException;
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

class DeltaSyncServiceTest {

    @TempDir
    Path tempDir;

    private StorageAdapter storageAdapter;
    private DeltaSyncService service;

    @BeforeEach
    void setUp() {
        storageAdapter = Mockito.mock(StorageAdapter.class);
        service = new DeltaSyncService(storageAdapter);
    }

    @Test
    void syncsNewFileThatDoesNotExistInBucket() throws IOException {
        Path file = tempDir.resolve("newfile.txt");
        Files.writeString(file, "new content");

        String md5 = Md5Hasher.hashBytes("new content".getBytes(StandardCharsets.UTF_8));

        when(storageAdapter.exists(eq("my-bucket"), eq("newfile.txt"))).thenReturn(false);
        when(storageAdapter.write(eq("my-bucket"), eq("newfile.txt"), any(byte[].class), eq(md5)))
                .thenReturn(new FileMetadata("newfile.txt", "my-bucket", md5, md5, 11, Instant.now(), "application/octet-stream"));

        String result = service.sync(tempDir.toString(), "my-bucket");

        verify(storageAdapter).write(eq("my-bucket"), eq("newfile.txt"), any(byte[].class), eq(md5));
        assertThat(result).contains("Synced 1");
    }

    @Test
    void skipsFileWithMatchingHash() throws IOException {
        Path file = tempDir.resolve("unchanged.txt");
        Files.writeString(file, "same content");

        String md5 = Md5Hasher.hashBytes("same content".getBytes(StandardCharsets.UTF_8));

        when(storageAdapter.exists(eq("my-bucket"), eq("unchanged.txt"))).thenReturn(true);
        when(storageAdapter.getMetadata(eq("my-bucket"), eq("unchanged.txt")))
                .thenReturn(new FileMetadata("unchanged.txt", "my-bucket", md5, md5, 12, Instant.now(), "application/octet-stream"));

        String result = service.sync(tempDir.toString(), "my-bucket");

        verify(storageAdapter, never()).write(eq("my-bucket"), eq("unchanged.txt"), any(byte[].class), anyString());
        assertThat(result).contains("skipped");
    }

    @Test
    void uploadsFileWithDifferentHash() throws IOException {
        Path file = tempDir.resolve("changed.txt");
        Files.writeString(file, "new version");

        String newMd5 = Md5Hasher.hashBytes("new version".getBytes(StandardCharsets.UTF_8));
        String oldMd5 = "oldmd5hash00000000000000000000000";

        when(storageAdapter.exists(eq("my-bucket"), eq("changed.txt"))).thenReturn(true);
        when(storageAdapter.getMetadata(eq("my-bucket"), eq("changed.txt")))
                .thenReturn(new FileMetadata("changed.txt", "my-bucket", oldMd5, oldMd5, 10, Instant.now(), "application/octet-stream"));
        when(storageAdapter.write(eq("my-bucket"), eq("changed.txt"), any(byte[].class), eq(newMd5)))
                .thenReturn(new FileMetadata("changed.txt", "my-bucket", newMd5, newMd5, 11, Instant.now(), "application/octet-stream"));

        String result = service.sync(tempDir.toString(), "my-bucket");

        verify(storageAdapter).write(eq("my-bucket"), eq("changed.txt"), any(byte[].class), eq(newMd5));
        assertThat(result).contains("Synced 1");
    }
}
