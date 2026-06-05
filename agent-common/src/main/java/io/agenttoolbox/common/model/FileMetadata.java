package io.agenttoolbox.common.model;

import java.time.Instant;

public record FileMetadata(String key, String bucket, String md5Hash, String etag, long size, Instant lastModified, String contentType) {}
