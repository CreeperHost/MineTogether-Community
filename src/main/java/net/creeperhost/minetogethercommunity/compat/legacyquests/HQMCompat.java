package net.creeperhost.minetogethercommunity.compat.legacyquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<String>());

    private static boolean registered;

    private long lastPoll;
    private String lastTeamKey = "";
    private boolean seeded;

    private HQMCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        LOGGER.info("Registered optional HQM telemetry support");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) return;
        lastPoll = now;
        pollQuestData();
    }

    private void pollQuestData() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.player;
            if (player == null || mc.world == null) {
                resetPolling();
                return;
            }

            Class<?> questingDataClass = LegacyQuestCompat.classForName(
                    "hardcorequesting.quests.QuestingData",
                    "hardcorequesting.QuestingData");
            Object active = LegacyQuestCompat.invokeAny(questingDataClass, true, "isQuestActive");
            if (Boolean.FALSE.equals(active)) {
                resetPolling();
                return;
            }

            UUID playerId = player.getPersistentID();
            Object questingData = LegacyQuestCompat.invokeAny(questingDataClass, true, new String[]{"getQuestingData"}, player);
            Object team = LegacyQuestCompat.read(questingData, "getTeam", "team");
            String teamKey = playerId + ":" + LegacyQuestCompat.stringValue(LegacyQuestCompat.read(team, "getId", "id"));
            if (!teamKey.equals(lastTeamKey)) {
                lastTeamKey = teamKey;
                seeded = false;
                LegacyQuestCompat.clear(SEEN);
            }

            Class<?> questClass = Class.forName("hardcorequesting.quests.Quest");
            Object quests = LegacyQuestCompat.invokeAny(questClass, true, "getQuests");
            boolean hasQuests = false;
            for (Object quest : LegacyQuestCompat.iterable(quests)) {
                hasQuests = true;
                Object completed = LegacyQuestCompat.invokeAny(quest, false, new String[]{"isCompleted"}, playerId);
                if (Boolean.TRUE.equals(completed)) {
                    queueQuest(quest, seeded);
                }
            }
            if (hasQuests) seeded = true;
        } catch (Throwable t) {
            resetPolling();
        }
    }

    private static void queueQuest(Object quest, boolean emit) {
        String questId = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getQuestId", "getId", "id"));
        if (questId.isEmpty()) return;

        String title = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getName", "name"));
        String description = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getDescription", "description"));
        String icon = LegacyQuestCompat.iconItemId(LegacyQuestCompat.read(quest, "getIcon", "getIconStack", "icon"), FALLBACK_ICON);

        String key = questId + "\n" + title + "\n" + description;
        if (!LegacyQuestCompat.remember(SEEN, key)) return;
        if (!emit) return;

        ActivityTelemetry.queueQuestAsync(PROVIDER, questId, title, description, icon);
    }

    private void resetPolling() {
        lastTeamKey = "";
        seeded = false;
        LegacyQuestCompat.clear(SEEN);
    }
}
