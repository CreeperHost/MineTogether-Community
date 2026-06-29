package net.creeperhost.minetogethercommunity.util;

import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import net.creeperhost.minetogether.lib.MineTogetherLib;
import net.creeperhost.minetogether.session.MojangUtils;
import net.creeperhost.minetogether.session.SessionProvider;
import net.creeperhost.minetogether.session.data.mc.ProfileKeyPairResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by covers1624 on 24/8/23.
 */
public class MTSessionProvider implements SessionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MTSessionProvider.class);
    private static final String UA =
            "MineTogether-lib/" + MineTogetherLib.VERSION +
            " MineTogether-Community-mod/" + MineTogetherPlatform.getVersion() +
            " Minecraft/" + MineTogetherPlatform.getMinecraftVersion() +
            " Modloader/" + MineTogetherPlatform.getPlatformName();

    private static @Nullable User getUser() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getUser();
    }

    // @formatter:off
    @Override public @Nullable UUID getUUID() {
        User user = getUser();
        return user == null ? null : user.getProfileId();
    }
    @Override public String getUsername() {
        User user = getUser();
        return user == null ? "" : user.getName();
    }
    @Override public @Nullable String beginAuth() throws IOException {
        User user = getUser();
        return user == null ? null : MojangUtils.joinServer(user.getProfileId(), user.getAccessToken());
    }
    @Override public @Nullable ProfileKeyPairResponse getProfileKeyPair() throws IOException {
        User user = getUser();
        return user == null ? null : MojangUtils.getProfileKeypair(user.getAccessToken());
    }
    @Override public void infoLog(String msg, Object... args) { LOGGER.info(msg, args); }
    @Override public void warnLog(String msg, Object... args) { LOGGER.warn(msg, args); }
    @Override public void errorLog(String msg, Object... args) { LOGGER.error(msg, args); }
    @Override public String describe() { return UA; }
    // @formatter:on
}
