package io.agenttoolbox.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Encrypts and decrypts OAuth tokens (access_token, refresh_token) using AES-256-GCM
 * before storing them in the database.
 *
 * Each encryption uses a random 12-byte IV that is prepended to the ciphertext.
 * The stored byte array format is: [IV (12 bytes)][ciphertext + GCM auth tag].
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(@Value("${token.encryption-key:default-dev-key-change-in-prod-32chars!}") String encryptionKey) {
        this.secretKey = deriveKey(encryptionKey);
    }

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plainText the string to encrypt
     * @return encrypted bytes with the IV prepended
     */
    public byte[] encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] result = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, result, GCM_IV_LENGTH, cipherText.length);

            return result;
        } catch (Exception e) {
            logger.error("Token encryption failed", e);
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted byte array back to a plaintext string.
     *
     * @param cipherText the encrypted bytes (IV prepended)
     * @return the original plaintext string
     */
    public String decrypt(byte[] cipherText) {
        try {
            byte[] iv = Arrays.copyOfRange(cipherText, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(cipherText, GCM_IV_LENGTH, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Token decryption failed", e);
            throw new RuntimeException("Failed to decrypt token", e);
        }
    }

    /**
     * Derives a 32-byte AES-256 key from the provided string.
     * If the key is already 32 bytes, it is used directly.
     * Otherwise, SHA-256 is used to produce a 32-byte key.
     */
    private static SecretKeySpec deriveKey(String key) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length == 32) {
                return new SecretKeySpec(keyBytes, ALGORITHM);
            }
            // Hash to exactly 32 bytes
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(keyBytes);
            return new SecretKeySpec(hash, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }
}
