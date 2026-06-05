package io.agenttoolbox.common.exception;

public class PreconditionFailedException extends StorageException {
    private final String expectedEtag;
    private final String currentEtag;

    public PreconditionFailedException(String expectedEtag, String currentEtag) {
        super("Precondition failed: expected ETag=" + expectedEtag + ", current ETag=" + currentEtag);
        this.expectedEtag = expectedEtag;
        this.currentEtag = currentEtag;
    }

    public String getExpectedEtag() { return expectedEtag; }
    public String getCurrentEtag() { return currentEtag; }
}
