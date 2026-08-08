package net.creeperhost.minetogethercommunity.ci;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.config.LocalConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;

/** Keeps profile and incidental chat API access local while Connect UI tests use offline accounts. */
final class CiConnectMock {

    static void install(int chatPort, String role, UUID uuid, Path resultDirectory) {
        System.setProperty("minetogether.ci.localChat", "true");
        // Set this before constructing the local ChatState so its IRC client is
        // never started for Connect-only tests.
        LocalConfig.instance().chatEnabled = false;
        MineTogetherChat.configureLocalChatForTesting(
                chatPort,
                hash(uuid),
                uuid,
                resultDirectory.resolve(role + "-muted-users.json")
        );
    }

    private static String hash(UUID uuid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) builder.append(String.format("%02X", b & 0xFF));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash Connect CI identity", exception);
        }
    }

    private CiConnectMock() {
    }
}
