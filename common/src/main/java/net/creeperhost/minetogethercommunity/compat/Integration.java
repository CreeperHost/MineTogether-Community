package net.creeperhost.minetogethercommunity.compat;

import net.creeperhost.minetogethercommunity.MineTogetherPlatform;

import java.util.function.Supplier;

/**
 * Created by brandon3055 on 14/07/2024
 */
public class Integration {

    public static void runOptional(String modid, Supplier<Runnable> runnable) {
        if (MineTogetherPlatform.isModLoaded(modid)) {
            runnable.get().run();
        }
    }

}
