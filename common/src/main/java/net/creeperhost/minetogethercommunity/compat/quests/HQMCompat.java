package net.creeperhost.minetogethercommunity.compat.quests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class HQMCompat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether HQM");
    private static final HQMCompat INSTANCE = new HQMCompat();
    private static final String PROVIDER = "hqm";
    private static final String FALLBACK_ICON = "hardcorequesting:quest_book";
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<>());

    private static boolean registered;

    private long lastPoll;
    private String lastPlayerKey = "";
    private boolean seeded;

    private HQMCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PolyClientTickEvents.CLIENT_TICK_END.register(INSTANCE::tick);
        LOGGER.info("Registered optional HQM telemetry support");
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

            UUID playerId = mc.player.getUUID();
            String playerKey = playerId.toString();
            if (!playerKey.equals(lastPlayerKey)) {
                lastPlayerKey = playerKey;
                seeded = false;
                QuestCompat.clear(SEEN);
            }

            Class<?> managerClass = QuestCompat.classForName(
                    "hardcorequesting.common.quests.QuestingDataManager",
                    "hardcorequesting.quests.QuestingData");
            Object manager = QuestCompat.invokeAny(managerClass, true, "getInstance");
            Object active = QuestCompat.invokeAny(manager == null ? managerClass : manager, manager == null, "isQuestActive");
            if (Boolean.FALSE.equals(active)) {
                reset();
                return;
            }

            Class<?> questClass = QuestCompat.classForName(
                    "hardcorequesting.common.quests.Quest",
                    "hardcorequesting.quests.Quest");
            Object quests = QuestCompat.invokeAny(questClass, true, "getQuests");
            boolean found = false;
            for (Object quest : QuestCompat.iterable(quests)) {
                found = true;
                Object completed = QuestCompat.invokeAny(quest, false, new String[]{"isCompleted"}, playerId);
                if (Boolean.TRUE.equals(completed)) {
                    queueQuest(quest, seeded);
                }
            }
            if (found) seeded = true;
        } catch (Throwable t) {
            reset();
        }
    }

    private static void queueQuest(Object quest, boolean emit) {
        String questId = QuestCompat.stringValue(QuestCompat.read(quest, "getQuestId", "getId", "id"));
        if (questId.isEmpty()) return;

        String title = QuestCompat.stringValue(QuestCompat.read(quest, "getName", "name"));
        String description = QuestCompat.stringValue(QuestCompat.read(QuestCompat.read(quest, "getDescription", "description"), "getText", "text"));
        String icon = QuestCompat.itemStackId(QuestCompat.read(quest, "getIconStack", "iconStack", "getIcon", "icon"), FALLBACK_ICON);

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
