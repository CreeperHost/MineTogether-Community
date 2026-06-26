package net.creeperhost.minetogethercommunity.compat;

import net.minecraftforge.fml.common.Loader;

import java.util.function.Supplier;

public final class Integration {

    private Integration() {
    }

    public static boolean isLoaded(String modid) {
        return Loader.isModLoaded(modid);
    }

    public static void runOptional(String modid, Supplier<Runnable> runnable) {
        if (isLoaded(modid)) {
            runnable.get().run();
        }
    }
}
