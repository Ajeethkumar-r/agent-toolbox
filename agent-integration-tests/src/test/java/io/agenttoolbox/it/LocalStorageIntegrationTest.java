package io.agenttoolbox.it;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.tool.etag.EtagTools;
import io.agenttoolbox.tool.etag.service.*;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageIntegrationTest {

    @TempDir Path bucketRoot;
    @TempDir Path localDir;
    EtagTools tools;

    @BeforeEach
    void setUp() {
        LocalStorageAdapter storage = new LocalStorageAdapter(bucketRoot.toString());
        tools = new EtagTools(new DeltaSyncService(storage), new UploadValidationService(storage),
                new ConcurrencyControlService(storage), new CacheValidationService(storage));
    }

    @Test
    void deltaSyncUploadsNewFiles() throws IOException {
        Files.writeString(localDir.resolve("file1.txt"), "content1");
        Files.writeString(localDir.resolve("file2.txt"), "content2");
        String result = tools.deltaSync(localDir.toString(), "my-bucket");
        assertThat(result).contains("2");
    }

    @Test
    void deltaSyncSkipsUnchangedFiles() throws IOException {
        Files.writeString(localDir.resolve("same.txt"), "unchanged");
        tools.deltaSync(localDir.toString(), "my-bucket");
        String result = tools.deltaSync(localDir.toString(), "my-bucket");
        assertThat(result).containsIgnoringCase("skipped");
    }

    @Test
    void uploadWithValidationSucceeds() throws IOException {
        Path file = localDir.resolve("upload.dat");
        Files.writeString(file, "upload this data");
        String result = tools.uploadWithValidation(file.toString(), "uploads", "upload.dat");
        assertThat(result).containsIgnoringCase("uploaded");
    }

    @Test
    void conditionalUpdateDetectsConflict() throws IOException {
        Path v1 = localDir.resolve("v1.txt");
        Files.writeString(v1, "version 1");
        tools.uploadWithValidation(v1.toString(), "shared", "config.txt");
        Path v2 = localDir.resolve("v2.txt");
        Files.writeString(v2, "version 2 by other");
        tools.uploadWithValidation(v2.toString(), "shared", "config.txt");
        Path myEdits = localDir.resolve("my-edits.txt");
        Files.writeString(myEdits, "my version");
        String v1Etag = Md5Hasher.hashBytes("version 1".getBytes());
        String result = tools.conditionalUpdate("shared", "config.txt", myEdits.toString(), v1Etag);
        assertThat(result).containsIgnoringCase("conflict");
    }

    @Test
    void cacheValidationReturnsNotModified() throws IOException {
        Path file = localDir.resolve("catalog.json");
        Files.writeString(file, "{\"products\": []}");
        tools.uploadWithValidation(file.toString(), "cache", "catalog.json");
        String etag = Md5Hasher.hashBytes("{\"products\": []}".getBytes());
        String result = tools.cacheValidation("cache", "catalog.json", etag);
        assertThat(result).containsIgnoringCase("not modified");
    }

    @Test
    void cacheValidationDetectsChange() throws IOException {
        Path file = localDir.resolve("catalog.json");
        Files.writeString(file, "old catalog");
        tools.uploadWithValidation(file.toString(), "cache", "catalog.json");
        Path updated = localDir.resolve("new-catalog.json");
        Files.writeString(updated, "new catalog");
        tools.uploadWithValidation(updated.toString(), "cache", "catalog.json");
        String oldEtag = Md5Hasher.hashBytes("old catalog".getBytes());
        String result = tools.cacheValidation("cache", "catalog.json", oldEtag);
        assertThat(result).containsIgnoringCase("modified").doesNotContainIgnoringCase("not modified");
    }
}
