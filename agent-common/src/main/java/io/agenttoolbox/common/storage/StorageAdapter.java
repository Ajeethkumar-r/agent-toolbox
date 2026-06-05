package io.agenttoolbox.common.storage;

import io.agenttoolbox.common.model.FileMetadata;
import java.util.List;

public interface StorageAdapter {
    FileMetadata getMetadata(String bucket, String key);
    byte[] read(String bucket, String key);
    FileMetadata write(String bucket, String key, byte[] content, String expectedMd5);
    FileMetadata conditionalWrite(String bucket, String key, byte[] content, String ifMatchEtag);
    ConditionalReadResult conditionalRead(String bucket, String key, String ifNoneMatchEtag);
    List<FileMetadata> list(String bucket, String prefix);
    boolean exists(String bucket, String key);
    List<String> listBuckets();
    void delete(String bucket, String key);
}
