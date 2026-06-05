package io.agenttoolbox.common.exception;

public class HashMismatchException extends StorageException {
    private final String expectedMd5;
    private final String actualMd5;

    public HashMismatchException(String expectedMd5, String actualMd5) {
        super("MD5 mismatch: expected=" + expectedMd5 + ", actual=" + actualMd5);
        this.expectedMd5 = expectedMd5;
        this.actualMd5 = actualMd5;
    }

    public String getExpectedMd5() { return expectedMd5; }
    public String getActualMd5() { return actualMd5; }
}
