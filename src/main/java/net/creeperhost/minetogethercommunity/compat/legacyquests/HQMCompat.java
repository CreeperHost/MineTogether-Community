package net.creeperhost.minetogethercommunity.compat.legacyquests;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HQMCompat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether HQM");
    private static final HQMCompat INSTANCE = new HQMCompat();
    private static final String PROVIDER = "hqm";
    private static final String FALLBACK_ICON = "HardcoreQuesting:quest_book";
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
            EntityPlayer player = mc.thePlayer;
            if (player == null || mc.theWorld == null) {
                resetPolling();
                return;
            }

            Class<?> questingDataClass = Class.forName("hardcorequesting.QuestingData");
            Object active = LegacyQuestCompat.invokeAny(questingDataClass, true, new String[]{"isQuestActive"});
            if (Boolean.FALSE.equals(active)) {
                resetPolling();
                return;
            }

            String playerName = playerName(questingDataClass, player);
            Object questingData = LegacyQuestCompat.invokeAny(questingDataClass, true, new String[]{"getQuestingData"}, player);
            if (questingData == null) {
                resetPolling();
                return;
            }
            Object team = LegacyQuestCompat.read(questingData, "getTeam", "team");
            if (team == null) {
                resetPolling();
                return;
            }
            String teamKey = playerName + ":" + LegacyQuestCompat.stringValue(LegacyQuestCompat.read(team, "getId", "id"));
            if (!teamKey.equals(lastTeamKey)) {
                lastTeamKey = teamKey;
                seeded = false;
                LegacyQuestCompat.clear(SEEN);
            }

            Class<?> questClass = Class.forName("hardcorequesting.quests.Quest");
            Object quests = LegacyQuestCompat.invokeAny(questClass, true, new String[]{"getQuests"});
            if (!(quests instanceof Iterable)) return;

            boolean hasQuests = false;
            for (Object quest : (Iterable<?>) quests) {
                hasQuests = true;
                Object completed = LegacyQuestCompat.invokeAny(quest, false, new String[]{"isCompleted"}, playerName);
                if (Boolean.TRUE.equals(completed)) {
                    queueQuest(quest, seeded);
                }
            }
            if (hasQuests) seeded = true;
        } catch (Throwable t) {
            resetPolling();
        }
    }

    private static String playerName(Class<?> questingDataClass, EntityPlayer player) {
        Object value = LegacyQuestCompat.invokeAny(questingDataClass, true, new String[]{"getUserName"}, player);
        String name = LegacyQuestCompat.stringValue(value);
        if (!name.isEmpty()) return name;
        return player.getGameProfile() == null ? player.getCommandSenderName() : player.getGameProfile().getName();
    }

    private static void queueQuest(Object quest, boolean emit) {
        String questId = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getId", "id"));
        if (questId.isEmpty()) return;

        String title = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getName", "name"));
        String description = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getDescription", "description"));
        String icon = LegacyQuestCompat.iconItemId(LegacyQuestCompat.read(quest, "getIcon", "icon"), FALLBACK_ICON);

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
