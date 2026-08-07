package net.creeperhost.minetogethercommunity.ci;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;

import java.nio.file.Path;
import java.util.UUID;

final class CiChatMock {
    static void install(int port, String role, Path resultDirectory) {
        System.setProperty("minetogether.ci.localChat", "true");
        String hash = "chat-sender".equals(role)
                ? "1111111111111111111111111111111111111111111111111111111111111111"
                : "2222222222222222222222222222222222222222222222222222222222222222";
        UUID uuid = "chat-sender".equals(role)
                ? UUID.fromString("00000000-0000-0000-0000-000000000060")
                : UUID.fromString("00000000-0000-0000-0000-000000000070");
        MineTogetherChat.configureLocalChatForTesting(port, hash, uuid, resultDirectory.resolve(role + "-muted-users.json"));
    }
    private CiChatMock() { }
}
