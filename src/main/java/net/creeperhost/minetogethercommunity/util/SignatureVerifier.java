package net.creeperhost.minetogethercommunity.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SignatureVerifier {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Signature");

    private SignatureVerifier() {
    }

    public static String generateSignature(File modSource, boolean deobfuscated) {
        String override = System.getProperty("mt.develop.signature");
        if (override != null && !override.trim().isEmpty()) {
            LOGGER.info("Using MineTogether development signature override.");
            return override.trim();
        }
        if (deobfuscated) return "Development";

        if (modSource != null && modSource.isFile() && modSource.getName().endsWith(".jar")) {
            try {
                return sha256(modSource);
            } catch (IOException | NoSuchAlgorithmException ex) {
                LOGGER.error("Failed to hash mod jar {}.", modSource, ex);
            }
        }
        return "Unknown";
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = hex[value >>> 4];
            chars[i * 2 + 1] = hex[value & 0x0F];
        }
        return new String(chars);
    }
}
