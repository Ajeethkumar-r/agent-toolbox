package io.agenttoolbox.tool.etag.storage;

import io.agenttoolbox.common.exception.FileNotFoundException;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.ConditionalReadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LocalStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalStorageAdapter(tempDir.toString());
    }

    @Test
    void write_createsFileAndReturnsMetadata() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);

        FileMetadata metadata = adapter.write("test-bucket", "file.txt", content, md5);

        assertThat(metadata.key()).isEqualTo("file.txt");
        assertThat(metadata.bucket()).isEqualTo("test-bucket");
        assertThat(metadata.md5Hash()).isEqualTo(md5);
        assertThat(metadata.etag()).isEqualTo(md5);
        assertThat(metadata.size()).isEqualTo(content.length);
        assertThat(metadata.lastModified()).isNotNull();
    }

    @Test
    void write_rejectsMismatchedMd5() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String wrongMd5 = "0000000000000000000000000000dead";

        assertThatThrownBy(() -> adapter.write("test-bucket", "file.txt", content, wrongMd5))
                .isInstanceOf(HashMismatchException.class);
    }

    @Test
    void read_returnsWrittenContent() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "file.txt", content, md5);

        byte[] result = adapter.read("test-bucket", "file.txt");

        assertThat(result).isEqualTo(content);
    }

    @Test
    void read_throwsForMissingFile() {
        assertThatThrownBy(() -> adapter.read("test-bucket", "missing.txt"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getMetadata_returnsCorrectMetadata() {
        byte[] content = "metadata test".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "meta.txt", content, md5);

        FileMetadata metadata = adapter.getMetadata("test-bucket", "meta.txt");

        assertThat(metadata.key()).isEqualTo("meta.txt");
        assertThat(metadata.bucket()).isEqualTo("test-bucket");
        assertThat(metadata.md5Hash()).isEqualTo(md5);
        assertThat(metadata.size()).isEqualTo(content.length);
    }

    @Test
    void exists_returnsTrueForExistingFile() {
        byte[] content = "exists".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "exists.txt", content, md5);

        assertThat(adapter.exists("test-bucket", "exists.txt")).isTrue();
    }

    @Test
    void exists_returnsFalseForMissingFile() {
        assertThat(adapter.exists("test-bucket", "nope.txt")).isFalse();
    }

    @Test
    void list_returnsAllFilesWithPrefix() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "docs/a.txt", content, md5);
        adapter.write("test-bucket", "docs/b.txt", content, md5);
        adapter.write("test-bucket", "images/c.png", content, md5);

        List<FileMetadata> result = adapter.list("test-bucket", "docs/");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FileMetadata::key)
                .containsExactlyInAnyOrder("docs/a.txt", "docs/b.txt");
    }

    @Test
    void conditionalWrite_succeedsWithMatchingEtag() {
        byte[] content1 = "version1".getBytes(StandardCharsets.UTF_8);
        String md5v1 = Md5Hasher.hashBytes(content1);
        FileMetadata written = adapter.write("test-bucket", "file.txt", content1, md5v1);

        byte[] content2 = "version2".getBytes(StandardCharsets.UTF_8);
        FileMetadata updated = adapter.conditionalWrite("test-bucket", "file.txt", content2, written.etag());

        assertThat(updated.md5Hash()).isEqualTo(Md5Hasher.hashBytes(content2));
        assertThat(adapter.read("test-bucket", "file.txt")).isEqualTo(content2);
    }

    @Test
    void conditionalWrite_failsWithStaleEtag() {
        byte[] content = "version1".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "file.txt", content, md5);

        byte[] content2 = "version2".getBytes(StandardCharsets.UTF_8);
        String staleEtag = "stale-etag-value";

        assertThatThrownBy(() -> adapter.conditionalWrite("test-bucket", "file.txt", content2, staleEtag))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void conditionalRead_returnsNotModifiedWhenEtagMatches() {
        byte[] content = "cached".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        FileMetadata written = adapter.write("test-bucket", "file.txt", content, md5);

        ConditionalReadResult result = adapter.conditionalRead("test-bucket", "file.txt", written.etag());

        assertThat(result.modified()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.etag()).isEqualTo(written.etag());
    }

    @Test
    void conditionalRead_returnsContentWhenEtagDiffers() {
        byte[] content = "fresh".getBytes(StandardCharsets.UTF_8);
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("test-bucket", "file.txt", content, md5);

        ConditionalReadResult result = adapter.conditionalRead("test-bucket", "file.txt", "old-etag");

        assertThat(result.modified()).isTrue();
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.etag()).isEqualTo(md5);
    }
}
