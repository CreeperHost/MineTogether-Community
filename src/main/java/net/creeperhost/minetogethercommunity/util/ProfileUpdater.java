package net.creeperhost.minetogethercommunity.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ProfileUpdater {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Profile Updater");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int index;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MT Profile Updater " + index++);
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final LoadingCache<UUID, CompletableFuture<GameProfile>> PROFILE_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(256)
            .build(new CacheLoader<UUID, CompletableFuture<GameProfile>>() {
                @Override
                public CompletableFuture<GameProfile> load(UUID key) {
                    return loadProfile(key);
                }
            });

    private ProfileUpdater() {
    }

    public static void updateProfile(UUID uuid, Consumer<GameProfile> callback) {
        if (uuid == null || callback == null) return;
        PROFILE_CACHE.getUnchecked(uuid).whenComplete((profile, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("Failed to refresh Minecraft profile {}", uuid, throwable);
            }
            ClientTaskRunner.run(new Runnable() {
                @Override
                public void run() {
                    callback.accept(profile);
                }
            });
        });
    }

    private static CompletableFuture<GameProfile> loadProfile(final UUID uuid) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<GameProfile>() {
            @Override
            public GameProfile get() {
                return Minecraft.getMinecraft().getSessionService().fillProfileProperties(new GameProfile(uuid, null), true);
            }
        }, EXECUTOR);
    }
}
