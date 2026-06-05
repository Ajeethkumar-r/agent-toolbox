package io.agenttoolbox.common.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Md5Hasher {
    private Md5Hasher() {}

    public static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException("MD5 not available", e); }
    }

    public static String hashFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException("MD5 not available", e); }
    }
}
