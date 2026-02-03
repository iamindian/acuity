package com.acuity.common;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric encryption utility using AES
 */
public class SymmetricEncryption {
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static SecretKey secretKey;

    /**
     * Initialize or get the shared secret key
     */
    public static synchronized SecretKey getOrGenerateKey() throws Exception {
        if (secretKey == null) {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            secretKey = keyGen.generateKey();
        }
        return secretKey;
    }

    /**
     * Set a predefined secret key (useful for client-server coordination)
     */
    public static synchronized void setSecretKey(byte[] encodedKey) {
        secretKey = new SecretKeySpec(encodedKey, 0, encodedKey.length, ALGORITHM);
    }

    /**
     * Set secret key from Base64-encoded string (for command-line convenience)
     */
    public static synchronized void setSecretKeyFromBase64(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        setSecretKey(decodedKey);
    }

    /**
     * Get the secret key as base64 encoded string for sharing
     */
    public static String getKeyAsString() throws Exception {
        return Base64.getEncoder().encodeToString(getOrGenerateKey().getEncoded());
    }

    /**
     * Encrypt data using the shared secret key
     */
    public static byte[] encrypt(byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrGenerateKey());
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt data using the shared secret key
     */
    public static byte[] decrypt(byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrGenerateKey());
        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypt data and return as base64 string
     */
    public static String encryptToString(byte[] plaintext) throws Exception {
        return Base64.getEncoder().encodeToString(encrypt(plaintext));
    }

    /**
     * Decrypt base64 encoded string
     */
    public static byte[] decryptFromString(String encryptedData) throws Exception {
        return decrypt(Base64.getDecoder().decode(encryptedData));
    }
}
