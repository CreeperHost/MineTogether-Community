package net.creeperhost.minetogethercommunity.chat;

import com.google.common.hash.Hashing;
import com.mojang.util.UUIDTypeAdapter;
import net.creeperhost.minetogether.lib.chat.ChatAuth;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ChatAuthImpl implements ChatAuth {

    private final UUID uuid;
    private final String uuidHash;

    public ChatAuthImpl(Minecraft mc) {
        Session session = mc.getSession();
        this.uuid = UUIDTypeAdapter.fromString(session.getPlayerID());
        this.uuidHash = Hashing.sha256().hashString(uuid.toString(), StandardCharsets.UTF_8).toString().toUpperCase(Locale.ROOT);
    }

    @Override
    public String getSignature() {
        return MineTogether.FINGERPRINT;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getHash() {
        return uuidHash;
    }

    @Override
    public void resetSessionToken() {
        MineTogetherSession.getDefault().forceResetToken();
    }

    @Override
    public CompletableFuture<JWebToken> getSessionTokenAsync() {
        return MineTogetherSession.getDefault().getTokenAsync();
    }
}
