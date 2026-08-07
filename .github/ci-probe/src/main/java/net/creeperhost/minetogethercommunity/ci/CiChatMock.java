package net.creeperhost.minetogethercommunity.ci;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;

import java.nio.file.Path;
import java.util.UUID;

/** Installs a deterministic API response and identity for the local IRC runtime test. */
final class CiChatMock {

    static void install(int port, String role, Path resultDirectory) {
        System.setProperty("minetogether.ci.localChat", "true");
        String hash = role.equals("chat-sender")
                ? "1111111111111111111111111111111111111111111111111111111111111111"
                : "2222222222222222222222222222222222222222222222222222222222222222";
        UUID uuid = role.equals("chat-sender")
                ? UUID.fromString("00000000-0000-0000-0000-000000000060")
                : UUID.fromString("00000000-0000-0000-0000-000000000070");

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
