package net.creeperhost.minetogethercommunity.compat.ftbquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class FTBQuestsCompat {
    private static final String FALLBACK_ICON = "minecraft:flower_banner_pattern";

    /**
     * Called from the optional mixin when any quest-object completion message is received on the client.
     * Reflection keeps the base 1.20.x build independent from FTB Quests compile-time artifacts.
     */
    public static void onObjectCompleted(long id) {
        try {
            Class<?> clientQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            Object exists = invokeStatic(clientQuestFileClass, "exists");
            if (!Boolean.TRUE.equals(exists)) return;

            Field instanceField = clientQuestFileClass.getField("INSTANCE");
            Object clientQuestFile = instanceField.get(null);
            Object quest = invoke(clientQuestFile, "getQuest", new Class<?>[]{long.class}, id);
            if (quest == null) return;

            String title = strip((Component) invokeNoArg(quest, "getTitle"));
            String description = description(quest);
            String iconItemId = resolveIconItemId(invokeNoArg(quest, "getIcon"));
            ActivityTelemetry.queueQuest(String.valueOf(invokeNoArg(quest, "getCodeString")), title, description, iconItemId);
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    private static String description(Object quest) throws ReflectiveOperationException {
        Object value = invokeNoArg(quest, "getDescription");
        if (!(value instanceof List<?> list)) return "";
        StringBuilder builder = new StringBuilder();
        for (Object entry : list) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(strip(entry instanceof Component ? (Component) entry : null));
        }
        return builder.toString();
    }

    private static String resolveIconItemId(Object icon) {
        if (icon == null) return FALLBACK_ICON;
        try {
            Object stack = invokeNoArg(icon, "getStack");
            if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
            }
        } catch (Throwable ignored) {
        }
        return FALLBACK_ICON;
    }

    private static Object invokeStatic(Class<?> owner, String name) throws ReflectiveOperationException {
        Method method = owner.getMethod(name);
        return method.invoke(null);
    }

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameterTypes);
        return method.invoke(target, args);
    }

    private static String strip(Component s) {
        if (s == null) return "";
        return ChatFormatting.stripFormatting(s.getString());
    }
}