package net.creeperhost.minetogethercommunity.ci.connectservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Identity(UUID uuid, String username, String uuidHash) {

    private static final Pattern SUBJECT = stringField("sub");
    private static final Pattern USERNAME = stringField("usn");
    private static final Pattern HASH = stringField("sha");

    static Identity parse(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Session token is not a JWT");

        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Session token payload is not base64url", exception);
        }

        UUID uuid = UUID.fromString(field(SUBJECT, payload, "sub"));
        String username = unescape(field(USERNAME, payload, "usn"));
        String claimedHash = field(HASH, payload, "sha").toUpperCase(Locale.ROOT);
        String expectedHash = hash(uuid);
        if (!expectedHash.equals(claimedHash)) {
            throw new IllegalArgumentException("Session token UUID hash does not match its subject");
        }
        return new Identity(uuid, username, claimedHash);
    }

    static String hash(UUID uuid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) builder.append(String.format("%02X", b & 0xFF));
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Pattern stringField(String name) {
        return Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    }

    private static String field(Pattern pattern, String payload, String name) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) throw new IllegalArgumentException("Session token is missing " + name);
        return matcher.group(1);
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || ++i >= value.length()) {
                builder.append(c);
                continue;
            }
            char escaped = value.charAt(i);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw new IllegalArgumentException("Invalid JSON escape in username");
                    builder.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape in username");
            }
        }
        return builder.toString();
    }
}
