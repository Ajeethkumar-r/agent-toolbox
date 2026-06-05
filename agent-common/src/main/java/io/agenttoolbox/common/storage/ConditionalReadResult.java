package io.agenttoolbox.common.storage;

public record ConditionalReadResult(boolean modified, byte[] content, String etag, long contentLength) {
    public static ConditionalReadResult notModified(String etag) { return new ConditionalReadResult(false, null, etag, 0); }
    public static ConditionalReadResult modified(byte[] content, String etag) { return new ConditionalReadResult(true, content, etag, content.length); }
}
