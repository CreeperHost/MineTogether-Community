package net.creeperhost.minetogethercommunity.compat.legacyquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<String>());

    private static boolean registered;

    private long lastPoll;
    private String lastPlayerKey = "";
    private boolean seeded;

    private BountifulCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        LOGGER.info("Registered optional Bountiful telemetry support");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) return;
        lastPoll = now;
        pollBounties();
    }

    private void pollBounties() {
        try {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null || Minecraft.getMinecraft().world == null) {
                resetPolling();
                return;
            }

            String playerKey = player.getPersistentID().toString();
            if (!playerKey.equals(lastPlayerKey)) {
                lastPlayerKey = playerKey;
                seeded = false;
                LegacyQuestCompat.clear(SEEN);
            }

            scan(player, player.inventory.mainInventory, "main");
            scan(player, player.inventory.offHandInventory, "offhand");
            scan(player, player.inventory.armorInventory, "armor");
            seeded = true;
        } catch (Throwable t) {
            resetPolling();
        }
    }

    private static void scan(EntityPlayer player, NonNullList<ItemStack> stacks, String section) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!isBounty(stack)) continue;
            queueIfComplete(player, stack, section + ":" + slot, INSTANCE.seeded);
        }
    }

    private static boolean isBounty(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) return false;
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        if (id != null && "bountiful:bounty".equals(id.toString())) return true;
        return stack.getItem().getClass().getName().equals("ejektaflex.bountiful.item.ItemBounty");
    }

    private static void queueIfComplete(EntityPlayer player, ItemStack stack, String slotKey, boolean emit) {
        try {
            Object data = bountyData(stack);
            if (data == null || !isComplete(player, data)) return;

            String title = LegacyQuestCompat.strip(stack.getDisplayName());
            if (title.isEmpty()) title = "Bounty";
            String description = description(data);
            String questId = LegacyQuestCompat.hash(PROVIDER + ":" + slotKey + ":" + title + ":" + description);
            String key = questId + "\n" + title + "\n" + description;
            if (!LegacyQuestCompat.remember(SEEN, key)) return;
            if (!emit) return;

            ActivityTelemetry.queueQuestAsync(PROVIDER, questId, title, description, LegacyQuestCompat.iconItemId(stack, FALLBACK_ICON));
        } catch (Throwable ignored) {
        }
    }

    private static Object bountyData(ItemStack stack) throws ClassNotFoundException {
        Class<?> dataClass = Class.forName("ejektaflex.bountiful.data.BountyData");
        Object data = LegacyQuestCompat.invokeAny(dataClass, true, new String[]{"from"}, stack);
        if (data != null) return data;

        Class<?> apiClass = Class.forName("ejektaflex.bountiful.api.BountifulAPI");
        Object api = LegacyQuestCompat.readStatic(apiClass, "INSTANCE", "instance");
        return LegacyQuestCompat.invokeAny(api, false, new String[]{"toBountyData"}, stack);
    }

    private static boolean isComplete(EntityPlayer player, Object data) {
        Object expired = LegacyQuestCompat.invokeAny(data, false, new String[]{"hasExpired"}, player.world);
        if (Boolean.TRUE.equals(expired)) return false;

        Object registry = LegacyQuestCompat.read(data, "getToGet", "toGet");
        Object items = LegacyQuestCompat.read(registry, "getItems", "items");
        boolean sawRequirement = false;
        for (Object entry : LegacyQuestCompat.iterable(items)) {
            sawRequirement = true;
            if (!requirementComplete(player, entry)) return false;
        }
        return sawRequirement;
    }

    private static boolean requirementComplete(EntityPlayer player, Object entry) {
        int amount = intValue(LegacyQuestCompat.read(entry, "getAmount", "amount"));
        int killed = intValue(LegacyQuestCompat.read(entry, "getKilledAmount", "killedAmount"));
        if (entry != null && entry.getClass().getName().contains("PickedEntryEntity")) {
            return amount <= 0 || killed >= amount;
        }

        Object required = LegacyQuestCompat.read(entry, "getItemStack", "itemStack");
        if (required instanceof ItemStack) {
            return countMatching(player, (ItemStack) required) >= Math.max(1, amount);
        }

        return false;
    }

    private static int countMatching(EntityPlayer player, ItemStack required) {
        int count = 0;
        count += countMatching(player.inventory.mainInventory, required);
        count += countMatching(player.inventory.offHandInventory, required);
        count += countMatching(player.inventory.armorInventory, required);
        return count;
    }

    private static int countMatching(NonNullList<ItemStack> inventory, ItemStack required) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.isItemEqualIgnoreDurability(required)) continue;
            if (!ItemStack.areItemStackTagsEqual(stack, required)) continue;
            count += stack.getCount();
        }
        return count;
    }

    private static String description(Object data) {
        String objectives = entries(LegacyQuestCompat.read(LegacyQuestCompat.read(data, "getToGet", "toGet"), "getItems", "items"));
        String rewards = entries(LegacyQuestCompat.read(LegacyQuestCompat.read(data, "getRewards", "rewards"), "getItems", "items"));
        if (!objectives.isEmpty() && !rewards.isEmpty()) return objectives + "\n" + rewards;
        return LegacyQuestCompat.firstNonEmpty(objectives, rewards);
    }

    private static String entries(Object entries) {
        StringBuilder builder = new StringBuilder();
        for (Object entry : LegacyQuestCompat.iterable(entries)) {
            String line = entryLine(entry);
            if (line.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }

    private static String entryLine(Object entry) {
        int amount = intValue(LegacyQuestCompat.read(entry, "getAmount", "amount"));
        Object stack = LegacyQuestCompat.read(entry, "getItemStack", "itemStack");
        if (stack instanceof ItemStack) {
            return Math.max(1, amount) + "x " + LegacyQuestCompat.strip(((ItemStack) stack).getDisplayName());
        }
        Object entity = LegacyQuestCompat.read(entry, "getEntityEntry", "entityEntry", "getEntity", "entity");
        String entityName = LegacyQuestCompat.stringValue(LegacyQuestCompat.read(entity, "getName", "getRegistryName", "name", "registryName"));
        if (!entityName.isEmpty()) {
            return Math.max(1, amount) + "x " + entityName;
        }
        return LegacyQuestCompat.stringValue(LegacyQuestCompat.read(entry, "getPrettyContent", "prettyContent"));
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private void resetPolling() {
        lastPlayerKey = "";
        seeded = false;
        LegacyQuestCompat.clear(SEEN);
    }
}
