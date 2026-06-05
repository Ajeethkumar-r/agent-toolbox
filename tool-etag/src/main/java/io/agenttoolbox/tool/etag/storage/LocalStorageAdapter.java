package io.agenttoolbox.tool.etag.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.FileNotFoundException;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.exception.StorageException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LocalStorageAdapter implements StorageAdapter {

    private final Path bucketRoot;
    private final ObjectMapper objectMapper;

    public LocalStorageAdapter(String bucketRoot) {
        this.bucketRoot = Path.of(bucketRoot);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public FileMetadata write(String bucket, String key, byte[] content, String expectedMd5) {
        String actualMd5 = Md5Hasher.hashBytes(content);
        if (!actualMd5.equals(expectedMd5)) {
            throw new HashMismatchException(expectedMd5, actualMd5);
        }

        Path filePath = resolveFilePath(bucket, key);
        Path metadataPath = resolveMetadataPath(bucket, key);

        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);

            FileMetadata metadata = new FileMetadata(
                    key, bucket, actualMd5, actualMd5, content.length, Instant.now(), "application/octet-stream"
            );

            objectMapper.writeValue(metadataPath.toFile(), metadata);
            return metadata;
        } catch (IOException e) {
            throw new StorageException("Failed to write file: " + bucket + "/" + key, e);
        }
    }

    @Override
    public FileMetadata conditionalWrite(String bucket, String key, byte[] content, String ifMatchEtag) {
        FileMetadata currentMetadata = getMetadata(bucket, key);
        if (!currentMetadata.etag().equals(ifMatchEtag)) {
            throw new PreconditionFailedException(ifMatchEtag, currentMetadata.etag());
        }
        String md5 = Md5Hasher.hashBytes(content);
        return write(bucket, key, content, md5);
    }

    @Override
    public byte[] read(String bucket, String key) {
        Path filePath = resolveFilePath(bucket, key);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException(bucket, key);
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + bucket + "/" + key, e);
        }
    }

    @Override
    public ConditionalReadResult conditionalRead(String bucket, String key, String ifNoneMatchEtag) {
        FileMetadata metadata = getMetadata(bucket, key);
        if (metadata.etag().equals(ifNoneMatchEtag)) {
            return ConditionalReadResult.notModified(metadata.etag());
        }
        byte[] content = read(bucket, key);
        return ConditionalReadResult.modified(content, metadata.etag());
    }

    @Override
    public FileMetadata getMetadata(String bucket, String key) {
        Path metadataPath = resolveMetadataPath(bucket, key);
        if (!Files.exists(metadataPath)) {
            throw new FileNotFoundException(bucket, key);
        }
        try {
            return objectMapper.readValue(metadataPath.toFile(), FileMetadata.class);
        } catch (IOException e) {
            throw new StorageException("Failed to read metadata: " + bucket + "/" + key, e);
        }
    }

    @Override
    public List<FileMetadata> list(String bucket, String prefix) {
        Path bucketPath = bucketRoot.resolve(bucket);
        if (!Files.exists(bucketPath)) {
            return List.of();
        }

        List<FileMetadata> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(bucketPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".metadata.json"))
                    .filter(p -> {
                        String relativePath = bucketPath.relativize(p).toString().replace('\\', '/');
                        return relativePath.startsWith(prefix);
                    })
                    .forEach(p -> {
                        String relativePath = bucketPath.relativize(p).toString().replace('\\', '/');
                        result.add(getMetadata(bucket, relativePath));
                    });
        } catch (IOException e) {
            throw new StorageException("Failed to list files in bucket: " + bucket, e);
        }
        return result;
    }

    @Override
    public boolean exists(String bucket, String key) {
        return Files.exists(resolveFilePath(bucket, key));
    }

    private Path resolveFilePath(String bucket, String key) {
        return bucketRoot.resolve(bucket).resolve(key);
    }

    private Path resolveMetadataPath(String bucket, String key) {
        Path filePath = resolveFilePath(bucket, key);
        return filePath.getParent().resolve(filePath.getFileName().toString() + ".metadata.json");
    }
}
