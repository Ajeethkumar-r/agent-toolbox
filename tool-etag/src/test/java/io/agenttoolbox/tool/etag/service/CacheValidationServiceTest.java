package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CacheValidationServiceTest {

    private StorageAdapter storageAdapter;
    private CacheValidationService service;

    @BeforeEach
    void setUp() {
        storageAdapter = Mockito.mock(StorageAdapter.class);
        service = new CacheValidationService(storageAdapter);
    }

    @Test
    void returnsNotModifiedWhenEtagMatches() {
        String etag = "matching-etag";

        when(storageAdapter.conditionalRead("my-bucket", "doc.txt", etag))
                .thenReturn(ConditionalReadResult.notModified(etag));

        String result = service.validate("my-bucket", "doc.txt", etag);

        assertThat(result).contains("NOT MODIFIED");
        assertThat(result).contains("my-bucket/doc.txt");
        assertThat(result).contains("0 bytes transferred");
    }

    @Test
    void returnsNewContentWhenEtagDiffers() {
        String oldEtag = "old-etag";
        byte[] newContent = "new content here".getBytes(StandardCharsets.UTF_8);
        String newEtag = "new-etag";

        when(storageAdapter.conditionalRead("my-bucket", "doc.txt", oldEtag))
                .thenReturn(ConditionalReadResult.modified(newContent, newEtag));

        String result = service.validate("my-bucket", "doc.txt", oldEtag);

        assertThat(result).contains("MODIFIED");
        assertThat(result).contains("my-bucket/doc.txt");
        assertThat(result).containsIgnoringCase("transferred");
    }
}
