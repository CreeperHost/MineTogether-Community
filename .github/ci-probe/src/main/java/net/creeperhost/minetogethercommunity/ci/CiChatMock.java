package net.creeperhost.minetogethercommunity.ci;

import com.google.common.hash.Hashing;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Installs a deterministic API response and identity for the local IRC runtime test. */
final class CiChatMock {

    static void install(int port, String role, Path resultDirectory) {
        System.setProperty("minetogether.ci.localChat", "true");
        String username = switch (role) {
            case "chat-sender" -> "CiChatSend";
            case "chat-receiver" -> "CiChatRecv";
            case "ctcp-sender" -> "CiCtcpSend";
            case "ctcp-receiver" -> "CiCtcpRecv";
            default -> throw new IllegalArgumentException("Unknown local chat test role: " + role);
        };
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        String hash = Hashing.sha256()
                .hashString(uuid.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);

        MineTogetherChat.configureLocalChatForTesting(
                port,
                hash,
                uuid,
                resultDirectory.resolve(role + "-muted-users.json")
        );
    }

    private CiChatMock() {
    }
}
