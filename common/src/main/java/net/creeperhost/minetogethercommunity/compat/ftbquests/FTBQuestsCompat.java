package net.creeperhost.minetogethercommunity.compat.ftbquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FTBQuestsCompat {
    private static final String FALLBACK_ICON = "minecraft:flower_banner_pattern";

    /**
     * Called from the optional mixin when any quest-object completion message is received on the client.
     * Reflection keeps the base build independent from FTB Quests compile-time artifacts.
     */
    public static void onObjectCompleted(long id) {
        try {
            Class<?> clientQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            Object exists = invokeStatic(clientQuestFileClass, "exists");
            if (!Boolean.TRUE.equals(exists)) return;

            Object clientQuestFile = clientQuestFile(clientQuestFileClass);
            Object quest = invoke(clientQuestFile, "getQuest", new Class<?>[]{long.class}, id);
            if (quest == null) return;

            String title = text(invokeNoArg(quest, "getTitle"));
            String description = description(quest);
            String iconItemId = resolveIconItemId(invokeNoArg(quest, "getIcon"));
            ActivityTelemetry.queueQuest(String.valueOf(invokeNoArg(quest, "getCodeString")), title, description, iconItemId);
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    private static Object clientQuestFile(Class<?> clientQuestFileClass) throws ReflectiveOperationException {
        try {
            Object instance = invokeStatic(clientQuestFileClass, "getInstance");
            if (instance != null) return instance;
        } catch (NoSuchMethodException ignored) {
        }

        Field instanceField;
        try {
            instanceField = clientQuestFileClass.getField("INSTANCE");
        } catch (NoSuchFieldException ignored) {
            instanceField = clientQuestFileClass.getDeclaredField("INSTANCE");
        }
        instanceField.setAccessible(true);
        return instanceField.get(null);
    }

    private static String description(Object quest) throws ReflectiveOperationException {
        Object value = invokeNoArg(quest, "getDescription");
        if (!(value instanceof Iterable<?> entries)) return text(value);
        StringBuilder builder = new StringBuilder();
        for (Object entry : entries) {
            String text = text(entry);
            if (text.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(text);
        }
        return builder.toString();
    }

    private static String resolveIconItemId(Object icon) {
        if (icon == null) return FALLBACK_ICON;
        try {
            Object stack = invokeNoArg(icon, "getStack");
            String itemId = itemStackId(stack);
            if (!itemId.isEmpty()) return itemId;

            Object createdStack = invokeNoArg(stack, "create");
            itemId = itemStackId(createdStack);
            if (!itemId.isEmpty()) return itemId;
        } catch (Throwable ignored) {
        }
        return FALLBACK_ICON;
    }

    private static String itemStackId(Object stack) {
        if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        }
        return "";
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

    private static String text(Object value) {
        if (value instanceof Component component) {
            return strip(component.getString());
        }
        return value == null ? "" : strip(String.valueOf(value));
    }

    private static String strip(String value) {
        if (value == null) return "";
        String stripped = ChatFormatting.stripFormatting(value);
        return stripped == null ? "" : stripped.trim();
    }
}
