package net.creeperhost.minetogethercommunity.chat;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogether.lib.chat.request.StatisticsRequest;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ChatStatistics {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Statistics");
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(1,
            new ThreadFactoryBuilder()
                    .setNameFormat("MineTogether Stats %d")
                    .setDaemon(true)
                    .build());

    public static String userCount = "over 2 million";
    public static String onlineCount = "thousands of";

    private static CompletableFuture<Void> future;
    private static long lastUpdate;

    private ChatStatistics() {
    }

    public static void pollStats() {
        if (MineTogetherChat.CHAT_STATE == null) return;
        if (future != null && !future.isDone()) return;
        if (lastUpdate + TimeUnit.MINUTES.toMillis(30) > System.currentTimeMillis()) return;

        future = CompletableFuture.runAsync(() -> {
            try {
                ApiClientResponse<StatisticsRequest.Response> response = MineTogetherChat.CHAT_STATE.api.execute(new StatisticsRequest());
                StatisticsRequest.Response stats = response.apiResponse();
                if (stats != null) {
                    userCount = stats.users;
                    onlineCount = stats.online;
                    lastUpdate = System.currentTimeMillis();
                }
            } catch (Throwable ex) {
                LOGGER.warn("Error polling MineTogether statistics.", ex);
            }
        }, EXECUTOR);
    }
}
