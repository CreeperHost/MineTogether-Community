package net.creeperhost.minetogethercommunity.forge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.minecraft.network.Connection;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Created by covers1624 on 26/8/22.
 */
public class MineTogetherPlatformImpl {

    @Nullable
    public static Path getModJar() {
        IModFileInfo fileInfo = ModList.get().getModFileById(MineTogether.MOD_ID);
        if (fileInfo == null) {
            return null;
        }
        return fileInfo.getFile().getFilePath();
    }

    public static String getVersion() {
        IModFileInfo fileInfo = ModList.get().getModFileById(MineTogether.MOD_ID);
        if (fileInfo == null) {
            return "UNKNOWN";
        }

        return fileInfo.versionString();
    }

    public static void prepareClientConnection(Connection connection) {
        NetworkHooks.registerClientLoginChannel(connection);
    }
}
