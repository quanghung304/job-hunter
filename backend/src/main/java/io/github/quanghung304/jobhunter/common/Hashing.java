package io.github.quanghung304.jobhunter.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content hashes stored in the database (fingerprints, payload and description hashes). */
public final class Hashing {

    private Hashing() {
    }

    /** Lower-case hex SHA-256 of the UTF-8 bytes of {@code text}. */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Lower-case hex SHA-256 of {@code bytes}. */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
        }
    }
}
