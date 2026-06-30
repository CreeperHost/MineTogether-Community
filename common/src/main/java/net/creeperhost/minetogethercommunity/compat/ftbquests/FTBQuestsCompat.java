package net.creeperhost.minetogethercommunity.compat.ftbquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

public class FTBQuestsCompat {
    private static final String FALLBACK_ICON = "minecraft:flower_banner_pattern";
    private static boolean fabricRegistered;
    private static boolean neoForgeRegistered;

    /**
     * Registers against FTB Quests' Fabric progress event API without hard-linking MineTogether to FTB classes.
     */
    public static void registerFabricEvents() {
        if (fabricRegistered) return;
        try {
            Class<?> listenerType = Class.forName("dev.ftb.mods.ftbquests.api.event.progress.QuestProgressEvent");
            Object event = Class.forName("dev.ftb.mods.ftbquests.api.fabric.FTBQuestsEvents")
                    .getField("QUEST_PROGRESS")
                    .get(null);
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, (proxy, method, args) -> {
                if ("accept".equals(method.getName()) && args != null && args.length == 1) {
                    onQuestProgress(args[0]);
                    return null;
                }
                if ("toString".equals(method.getName())) return "MineTogether FTB Quests progress listener";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName()) && args != null && args.length == 1) return proxy == args[0];
                return null;
            });
            invokeRegister(event, listener);
            fabricRegistered = true;
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests API changes should never break MineTogether.
        }
    }

    /**
     * Registers against FTB Quests' NeoForge progress event API without hard-linking MineTogether to FTB classes.
     */
    public static void registerNeoForgeEvents() {
        if (neoForgeRegistered) return;
        try {
            Class<?> eventClass = Class.forName("dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent$QuestProgress");
            Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge")
                    .getField("EVENT_BUS")
                    .get(null);
            Consumer<Object> listener = event -> {
                try {
                    onQuestProgress(invokeNoArg(event, "getEventData"));
                } catch (Throwable ignored) {
                }
            };
            invokeAddListener(bus, eventClass, listener);
            neoForgeRegistered = true;
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests API changes should never break MineTogether.
        }
    }

    private static void onQuestProgress(Object eventData) {
        try {
            Object type = invokeNoArg(eventData, "type");
            if (!"COMPLETED".equals(String.valueOf(type))) return;

            Object progressData = invokeNoArg(eventData, "progressData");
            if (isServerProgressData(progressData)) return;

            Object quest = invokeNoArg(progressData, "object");
            String title = text(invokeNoArg(quest, "getTitle"));
            String description = description(quest);
            String iconItemId = resolveIconItemId(invokeNoArg(quest, "getIcon"));
            ActivityTelemetry.queueQuestAsync(String.valueOf(invokeNoArg(quest, "getCodeString")), title, description, iconItemId);
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    private static boolean isServerProgressData(Object progressData) {
        try {
            Object teamData = invokeNoArg(progressData, "teamData");
            if (teamData != null) {
                var field = teamData.getClass().getDeclaredField("serverSide");
                field.setAccessible(true);
                if (Boolean.TRUE.equals(field.get(teamData))) return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object onlineMembers = invokeNoArg(progressData, "onlineMembers");
            if (onlineMembers instanceof Iterable<?> members && members.iterator().hasNext()) return true;
        } catch (Throwable ignored) {
        }

        return false;
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

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static void invokeRegister(Object event, Object listener) throws ReflectiveOperationException {
        for (Method method : event.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 1) continue;
            method.invoke(event, listener);
            return;
        }
        throw new NoSuchMethodException("register");
    }

    private static void invokeAddListener(Object bus, Class<?> eventClass, Consumer<Object> listener) throws ReflectiveOperationException {
        for (Method method : bus.getClass().getMethods()) {
            if (!"addListener".equals(method.getName()) || method.getParameterCount() != 2) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0] != Class.class || !parameterTypes[1].isAssignableFrom(Consumer.class)) continue;
            method.invoke(bus, eventClass, listener);
            return;
        }
        throw new NoSuchMethodException("addListener");
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
