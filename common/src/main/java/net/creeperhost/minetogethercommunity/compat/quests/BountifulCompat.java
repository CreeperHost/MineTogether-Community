package net.creeperhost.minetogethercommunity.compat.quests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BountifulCompat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Bountiful");
    private static final BountifulCompat INSTANCE = new BountifulCompat();
    private static final String PROVIDER = "bountiful";
    private static final String FALLBACK_ICON = "bountiful:bounty";
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<>());

    private static boolean registered;

    private long lastPoll;
    private String lastPlayerKey = "";
    private boolean seeded;

    private BountifulCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PolyClientTickEvents.CLIENT_TICK_END.register(INSTANCE::tick);
        LOGGER.info("Registered optional Bountiful telemetry support");
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

            scanInventory(mc.player);
            seeded = true;
        } catch (Throwable t) {
            reset();
        }
    }

    private static void scanInventory(Player player) {
        Object inventory = player.getInventory();
        Object sizeValue = QuestCompat.invokeAny(inventory, false, "getContainerSize", "size");
        int size = sizeValue instanceof Number number ? number.intValue() : 0;
        for (int slot = 0; slot < size; slot++) {
            Object value = QuestCompat.invokeAny(inventory, false, new String[]{"getItem"}, slot);
            if (!(value instanceof ItemStack stack)) continue;
            if (!isBounty(stack)) continue;
            queueIfComplete(player, stack, "inventory:" + slot, INSTANCE.seeded);
        }
    }

    private static boolean isBounty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("bountiful:bounty") || id.startsWith("bountiful:bounty-");
    }

    private static void queueIfComplete(Player player, ItemStack stack, String slotKey, boolean emit) {
        try {
            Object data = bountyData(stack);
            if (data == null) return;

            Object complete = QuestCompat.invokeAny(data, false, new String[]{"hasFinishedAllObjectives"}, player);
            if (!Boolean.TRUE.equals(complete)) return;

            String title = QuestCompat.stringValue(stack.getHoverName());
            if (title.isEmpty()) title = "Bounty";
            String description = description(data, player);
            String questId = QuestCompat.hash(PROVIDER + ":" + slotKey + ":" + title + ":" + description);
            String key = questId + "\n" + title + "\n" + description;
            if (!QuestCompat.remember(SEEN, key)) return;
            if (!emit) return;

            ActivityTelemetry.queueQuestAsync(PROVIDER, questId, title, description, QuestCompat.itemStackId(stack, FALLBACK_ICON));
        } catch (Throwable ignored) {
        }
    }

    private static Object bountyData(ItemStack stack) throws ClassNotFoundException {
        Class<?> dataClass = QuestCompat.classForName("io.ejekta.bountiful.bounty.BountyData");
        Object companion = QuestCompat.readStatic(dataClass, "Companion");
        return QuestCompat.invokeAny(companion, false, new String[]{"get", "of"}, stack);
    }

    private static String description(Object data, Player player) {
        String objectives = entries(data, player, true, "getObjectives", "objectives");
        String rewards = entries(data, player, false, "getRewards", "rewards");
        if (!objectives.isEmpty() && !rewards.isEmpty()) return objectives + "\n" + rewards;
        return QuestCompat.firstNonEmpty(objectives, rewards);
    }

    private static String entries(Object data, Player player, boolean objective, String... names) {
        StringBuilder builder = new StringBuilder();
        for (Object entry : QuestCompat.iterable(QuestCompat.read(data, names))) {
            Object text = QuestCompat.invokeAny(entry, false, new String[]{"textSummary"}, player, objective);
            String line = QuestCompat.stringValue(text);
            if (line.isEmpty()) line = QuestCompat.stringValue(QuestCompat.read(entry, "getName", "name", "getContent", "content"));
            if (line.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }

    private void reset() {
        lastPlayerKey = "";
        seeded = false;
        QuestCompat.clear(SEEN);
    }
}
