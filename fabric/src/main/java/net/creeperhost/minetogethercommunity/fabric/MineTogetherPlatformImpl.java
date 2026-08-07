package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.platform.MineTogetherPlatformService;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Created by covers1624 on 26/8/22.
 */
public class MineTogetherPlatformImpl implements MineTogetherPlatformService {

    @Nullable
    @Override
    public Path getModJar() {
        Optional<ModContainer> container = FabricLoader.getInstance()
                .getModContainer(MineTogether.MOD_ID);
        if (container.isEmpty()) return null;

        ModOrigin origin = container.get().getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return null;
        }
        List<Path> paths = origin.getPaths();
        return !paths.isEmpty() ? paths.get(0) : null;
    }

    @Override
    public String getVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance()
                .getModContainer(MineTogether.MOD_ID);
        if (container.isEmpty()) return "UNKNOWN";

        return container.get().getMetadata().getVersion().getFriendlyString();
    }

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path getGameFolder() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void sendEmoteStartToClient(ServerPlayer player, EmoteNetworking.StartEmoteS2C packet) {
        FabricEmoteNetworking.sendToPlayer(player, packet);
    }

    @Override
    public void sendEmoteStopToClient(ServerPlayer player, EmoteNetworking.StopEmoteS2C packet) {
        FabricEmoteNetworking.sendToPlayer(player, packet);
    }
}
