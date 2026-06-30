package net.creeperhost.minetogethercommunity.compat.quests;

import dev.architectury.event.events.client.ClientTickEvent;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HeraclesCompat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Heracles");
    private static final HeraclesCompat INSTANCE = new HeraclesCompat();
    private static final String PROVIDER = "heracles";
    private static final String FALLBACK_ICON = "heracles:quest_book";
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<>());

    private static boolean registered;

    private long lastPoll;
    private String lastPlayerKey = "";
    private boolean seeded;

    private HeraclesCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientTickEvent.CLIENT_POST.register(INSTANCE::tick);
        LOGGER.info("Registered optional Heracles telemetry support");
    }

    private void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) return;
        lastPoll = now;
        poll(mc);
    }

    private void poll(Minecraft mc) {
        try {
            if (mc.player == null || mc.level == null) {
                reset();
                return;
            }

            String playerKey = mc.player.getUUID().toString();
            if (!playerKey.equals(lastPlayerKey)) {
                lastPlayerKey = playerKey;
                seeded = false;
                QuestCompat.clear(SEEN);
            }

            Class<?> clientQuests = QuestCompat.classForName("earth.terrarium.heracles.client.handlers.ClientQuests");
            Object entries = QuestCompat.invokeAny(clientQuests, true, "entries");
            boolean found = false;
            for (Object entry : QuestCompat.iterable(entries)) {
                found = true;
                String questId = QuestCompat.stringValue(QuestCompat.read(entry, "key"));
                if (questId.isEmpty()) continue;
                Object progress = QuestCompat.invokeAny(clientQuests, true, new String[]{"getProgress"}, questId);
                if (!Boolean.TRUE.equals(QuestCompat.invokeAny(progress, false, "isComplete"))) continue;
                queueQuest(questId, QuestCompat.read(entry, "value"), seeded);
            }
            if (found) seeded = true;
        } catch (Throwable t) {
            reset();
        }
    }

    private static void queueQuest(String questId, Object quest, boolean emit) {
        if (quest == null || questId.isEmpty()) return;

        Object display = QuestCompat.read(quest, "display");
        String title = QuestCompat.stringValue(QuestCompat.read(display, "title"));
        String description = QuestCompat.stringValue(QuestCompat.read(display, "description"));
        String icon = QuestCompat.itemStackId(QuestCompat.read(display, "icon"), FALLBACK_ICON);

        String key = questId + "\n" + title + "\n" + description;
        if (!QuestCompat.remember(SEEN, key)) return;
        if (!emit) return;

        ActivityTelemetry.queueQuestAsync(PROVIDER, questId, title, description, icon);
    }

    private void reset() {
        lastPlayerKey = "";
        seeded = false;
        QuestCompat.clear(SEEN);
    }
}
