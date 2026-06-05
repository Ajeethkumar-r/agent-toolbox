package io.agenttoolbox.common.storage;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionalReadResultTest {
    @Test void notModified_hasCorrectState() {
        ConditionalReadResult r = ConditionalReadResult.notModified("abc123");
        assertThat(r.modified()).isFalse();
        assertThat(r.content()).isNull();
        assertThat(r.etag()).isEqualTo("abc123");
        assertThat(r.contentLength()).isZero();
    }
    @Test void modified_hasCorrectState() {
        byte[] content = "hello".getBytes();
        ConditionalReadResult r = ConditionalReadResult.modified(content, "def456");
        assertThat(r.modified()).isTrue();
        assertThat(r.content()).isEqualTo(content);
        assertThat(r.etag()).isEqualTo("def456");
        assertThat(r.contentLength()).isEqualTo(5);
    }
}
