package net.creeperhost.minetogethercommunity.platform;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface MineTogetherPlatformService {

    @Nullable
    Path getModJar();

    String getVersion();

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    boolean isClient();

    Path getGameFolder();

    Path getConfigFolder();

    void sendEmoteStartToClient(ServerPlayer player, EmoteNetworking.StartEmoteS2C packet);

    void sendEmoteStopToClient(ServerPlayer player, EmoteNetworking.StopEmoteS2C packet);
}
