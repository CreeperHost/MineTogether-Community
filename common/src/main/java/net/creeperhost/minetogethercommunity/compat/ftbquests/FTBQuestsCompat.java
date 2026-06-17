package net.creeperhost.minetogethercommunity.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

public class FTBQuestsCompat {
    private static final String FALLBACK_ICON = "minecraft:flower_banner_pattern";

    /**
     * Called from the mixin when any quest-object completion message is received on the client.
     * Filters to actual {@link Quest} completions (not tasks or chapters) and forwards to telemetry.
     */
    public static void onObjectCompleted(long id) {
        if (!ClientQuestFile.exists()) return;
        Quest quest = ClientQuestFile.INSTANCE.getQuest(id);
        if (quest == null) return;
        String title = strip(quest.getTitle());
        String description = quest.getDescription().stream()
                .map(FTBQuestsCompat::strip)
                .collect(java.util.stream.Collectors.joining("\n"));
        String iconItemId = resolveIconItemId(quest.getIcon());
        ActivityTelemetry.queueQuest(quest.getCodeString(), title, description, iconItemId);
    }

    private static String resolveIconItemId(Icon icon) {
        if (icon instanceof ItemIcon itemIcon) {
            ItemStack stack = itemIcon.getStack();
            if (!stack.isEmpty()) {
                return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
        }
        return FALLBACK_ICON;
    }

    private static String strip(Component s) {
        if (s == null) return "";
        return ChatFormatting.stripFormatting(s.getString());
    }
}
