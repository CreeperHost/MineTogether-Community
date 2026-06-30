package net.creeperhost.minetogethercommunity.compat.legacyquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BetterQuestingCompat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether BetterQuesting");
    private static final BetterQuestingCompat INSTANCE = new BetterQuestingCompat();
    private static final String PROVIDER = "betterquesting";
    private static final String FALLBACK_ICON = "minecraft:book";
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<String>());

    private static boolean registered;

    private long lastPoll;
    private String lastUserKey = "";
    private boolean seeded;

    private BetterQuestingCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        LOGGER.info("Registered optional BetterQuesting telemetry support");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) return;
        lastPoll = now;
        pollQuestDatabase();
    }

    @SubscribeEvent
    public void onQuestEvent(Event event) {
        if (!event.getClass().getName().toLowerCase(Locale.ROOT).contains("betterquesting")) return;
        if (!event.getClass().getName().toLowerCase(Locale.ROOT).contains("quest")) return;

        String type = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(event, "getType", "type"));
        if (!type.isEmpty() && !type.toUpperCase(Locale.ROOT).contains("COMPLETE")) return;

        UUID localId = questingUuid(Minecraft.getMinecraft().player);
        Object eventId = LegacyQuestCompat.read(event, "getPlayerID", "getPlayerId", "playerID", "playerId");
        if (localId != null && eventId instanceof UUID && !localId.equals(eventId)) return;

        Object ids = LegacyQuestCompat.read(event, "getQuestIDs", "getQuestIds", "questIDs", "questIds");
        boolean queued = false;
        for (Object id : LegacyQuestCompat.iterable(ids)) {
            queued = true;
            queueQuestById(id, true);
        }
        if (!queued) {
            queueQuestById(LegacyQuestCompat.read(event, "getQuestID", "getQuestId", "questID", "questId", "id"), true);
        }
    }

    private void pollQuestDatabase() {
        try {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null || Minecraft.getMinecraft().world == null) {
                resetPolling();
                return;
            }

            UUID user = questingUuid(player);
            if (user == null) {
                resetPolling();
                return;
            }

            Class<?> databaseClass = Class.forName("betterquesting.questing.QuestDatabase");
            Object database = LegacyQuestCompat.readStatic(databaseClass, "INSTANCE", "instance");
            Object entries = LegacyQuestCompat.read(database, "getEntries", "entries");
            if (!(entries instanceof Iterable)) {
                resetPolling();
                return;
            }

            String userKey = user.toString();
            if (!userKey.equals(lastUserKey)) {
                lastUserKey = userKey;
                seeded = false;
                LegacyQuestCompat.clear(SEEN);
            }

            boolean hasEntries = false;
            for (Object entry : (Iterable<?>) entries) {
                hasEntries = true;
                Object quest = LegacyQuestCompat.read(entry, "getValue", "value");
                Object complete = LegacyQuestCompat.invokeAny(quest, false, new String[]{"isComplete"}, user);
                if (Boolean.TRUE.equals(complete)) {
                    queueQuest(entryId(entry), quest, seeded);
                }
            }
            if (hasEntries) seeded = true;
        } catch (Throwable t) {
            resetPolling();
        }
    }

    private static UUID questingUuid(EntityPlayer player) {
        if (player == null) return null;
        try {
            Class<?> api = Class.forName("betterquesting.api.api.QuestingAPI");
            Object value = LegacyQuestCompat.invokeAny(api, true, new String[]{"getQuestingUUID"}, player);
            if (value instanceof UUID) return (UUID) value;
        } catch (Throwable ignored) {
        }
        return player.getGameProfile() == null ? null : player.getGameProfile().getId();
    }

    private static void queueQuestById(Object id, boolean emit) {
        Object quest = null;
        try {
            Class<?> databaseClass = Class.forName("betterquesting.questing.QuestDatabase");
            Object database = LegacyQuestCompat.readStatic(databaseClass, "INSTANCE", "instance");
            quest = LegacyQuestCompat.invokeAny(database, false, new String[]{"getValue", "get"}, id);
        } catch (Throwable ignored) {
        }
        queueQuest(LegacyQuestCompat.stringValue(id), quest, emit);
    }

    private static void queueQuest(String questId, Object quest, boolean emit) {
        if (quest == null || questId == null || questId.isEmpty()) return;

        String title = translatedProperty(quest, "NAME");
        title = LegacyQuestCompat.firstNonEmpty(title, LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getName", "name")));
        String description = translatedProperty(quest, "DESC");
        description = LegacyQuestCompat.firstNonEmpty(description, LegacyQuestCompat.stringValue(LegacyQuestCompat.read(quest, "getDescription", "description")));
        String icon = LegacyQuestCompat.iconItemId(property(quest, "ICON"), FALLBACK_ICON);

        String key = questId + "\n" + title + "\n" + description;
        if (!LegacyQuestCompat.remember(SEEN, key)) return;
        if (!emit) return;

        ActivityTelemetry.queueQuestAsync(PROVIDER, questId, title, description, icon);
    }

    private static String entryId(Object entry) {
        return LegacyQuestCompat.stringValue(LegacyQuestCompat.read(entry, "getID", "getId", "id"));
    }

    private static Object property(Object quest, String name) {
        try {
            Class<?> nativeProps = Class.forName("betterquesting.api.properties.NativeProps");
            Object property = LegacyQuestCompat.readStatic(nativeProps, name);
            return property == null ? null : LegacyQuestCompat.invokeAny(quest, false, new String[]{"getProperty"}, property);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String translatedProperty(Object quest, String name) {
        String value = LegacyQuestCompat.stringValue(property(quest, name));
        if (value.isEmpty()) return "";
        try {
            String translated = I18n.format(value);
            if (translated != null && !translated.startsWith("Format error: ")) {
                return LegacyQuestCompat.strip(translated);
            }
        } catch (Throwable ignored) {
        }
        return value;
    }

    private void resetPolling() {
        lastUserKey = "";
        seeded = false;
        LegacyQuestCompat.clear(SEEN);
    }
}
