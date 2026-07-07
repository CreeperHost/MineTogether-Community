package net.creeperhost.minetogethercommunity.util;

import com.mojang.util.UUIDTypeAdapter;
import net.creeperhost.minetogether.lib.MineTogetherLib;
import net.creeperhost.minetogether.session.MojangUtils;
import net.creeperhost.minetogether.session.SessionProvider;
import net.creeperhost.minetogether.session.data.mc.ProfileKeyPairResponse;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.UUID;

public class MTSessionProvider implements SessionProvider {

    private static final Logger LOGGER = LogManager.getLogger(MTSessionProvider.class);
    private static final String UA =
            "MineTogether-lib/" + MineTogetherLib.VERSION +
            " MineTogether-Community-mod/" + MineTogether.VERSION +
            " Minecraft/1.7.10 Modloader/forge";

    private final Session session = Minecraft.getMinecraft().getSession();
    private final UUID uuid = safeParseUUID(session.getPlayerID());

    private static UUID safeParseUUID(String raw) {
        try {
            return UUIDTypeAdapter.fromString(raw);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Could not parse player UUID '" + raw + "', using offline UUID");
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + raw).getBytes());
        }
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getUsername() {
        return session.getUsername();
    }

    @Override
    public String beginAuth() throws IOException {
        return MojangUtils.joinServer(uuid, session.getToken());
    }

    @Override
    public ProfileKeyPairResponse getProfileKeyPair() throws IOException {
        String token = session.getToken();
        if (token == null || token.isEmpty()) {
            return null;
        }
        return MojangUtils.getProfileKeypair(token);
    }

    @Override
    public void infoLog(String msg, Object... args) {
        LOGGER.info(msg, args);
    }

    @Override
    public void warnLog(String msg, Object... args) {
        LOGGER.warn(msg, args);
    }

    @Override
    public void errorLog(String msg, Object... args) {
        LOGGER.error(msg, args);
    }

    @Override
    public String describe() {
        return UA;
    }
}
