package net.creeperhost.minetogethercommunity.compat.ftbquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FTBQuestsCompat {
    private static final String FALLBACK_ICON = "minecraft:flower_banner_pattern";
    private static final long DUPLICATE_WINDOW_MS = 10_000L;
    private static final Map<String, Long> RECENT_COMPLETIONS = new ConcurrentHashMap<>();
    private static boolean architecturyRegistered;

    /**
     * Registers against FTB Quests' Architectury completion event API without hard-linking MineTogether to FTB classes.
     */
    public static void registerArchitecturyEvents() {
        if (architecturyRegistered) return;
        try {
            Class<?> eventOwner = Class.forName("dev.ftb.mods.ftbquests.events.ObjectCompletedEvent");
            Class<?> actorType = Class.forName("dev.architectury.event.EventActor");
            Object passResult = Class.forName("dev.architectury.event.EventResult")
                    .getMethod("pass")
                    .invoke(null);
            Object listener = Proxy.newProxyInstance(actorType.getClassLoader(), new Class<?>[]{actorType}, (proxy, method, args) -> {
                if ("act".equals(method.getName()) && args != null && args.length == 1) {
                    onQuestEvent(args[0]);
                    return passResult;
                }
                if ("toString".equals(method.getName())) return "MineTogether FTB Quests completion listener";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName()) && args != null && args.length == 1) return proxy == args[0];
                return null;
            });
            registerEvent(eventOwner, "QUEST", listener);
            registerEvent(eventOwner, "TASK", listener);
            architecturyRegistered = true;
        } catch (Throwable t) {
            // Optional integration: FTB Quests API changes should never break MineTogether.
        }
    }

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
            queueQuestObject(invoke(clientQuestFile, "get", new Class<?>[]{long.class}, id));
        } catch (Throwable t) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    public static void onTaskProgress(long id) {
        try {
            Class<?> clientQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            Object exists = invokeStatic(clientQuestFileClass, "exists");
            if (!Boolean.TRUE.equals(exists)) return;

            Object clientQuestFile = clientQuestFile(clientQuestFileClass);
            Object quest = resolveQuest(invoke(clientQuestFile, "get", new Class<?>[]{long.class}, id));
            if (quest == null) {
                return;
            }
            if (!isCompleted(quest, clientQuestFile)) {
                return;
            }

            queueQuestObject(quest);
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    private static void onQuestEvent(Object event) {
        try {
            queueQuestObject(eventQuest(event));
        } catch (Throwable ignored) {
            // Optional integration: FTB Quests internals differ between versions and should never break MineTogether.
        }
    }

    private static void queueQuestObject(Object quest) throws ReflectiveOperationException {
        quest = resolveQuest(quest);
        if (quest == null) return;

        String title = text(invokeNoArg(quest, "getTitle"));
        String description = description(quest);
        String iconItemId = resolveIconItemId(invokeNoArg(quest, "getIcon"));
        String questId = String.valueOf(invokeNoArg(quest, "getCodeString"));
        if (seenRecently(questId)) return;

        ActivityTelemetry.queueQuestAsync(questId, title, description, iconItemId);
    }

    private static Object eventQuest(Object event) throws ReflectiveOperationException {
        try {
            Object quest = invokeNoArg(event, "getQuest");
            if (quest != null) return quest;
        } catch (NoSuchMethodException ignored) {
        }
        return invokeNoArg(event, "getObject");
    }

    private static Object resolveQuest(Object object) throws ReflectiveOperationException {
        if (object == null) return null;

        try {
            invokeNoArg(object, "getTasks");
            return object;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Object quest = invokeNoArg(object, "getQuest");
            if (quest != null) return quest;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Object questFile = invokeNoArg(object, "getQuestFile");
            Object parentId = invokeNoArg(object, "getParentID");
            if (questFile != null && parentId instanceof Long id) {
                return invoke(questFile, "getQuest", new Class<?>[]{long.class}, id);
            }
        } catch (NoSuchMethodException ignored) {
        }

        return null;
    }

    private static boolean isCompleted(Object quest, Object clientQuestFile) throws ReflectiveOperationException {
        Field field = clientQuestFile.getClass().getField("selfTeamData");
        Object teamData = field.get(clientQuestFile);
        return teamData != null && Boolean.TRUE.equals(invoke(quest, "isCompletedRaw", new Class<?>[]{teamData.getClass()}, teamData));
    }

    private static boolean seenRecently(String questId) {
        long now = System.currentTimeMillis();
        Long previous = RECENT_COMPLETIONS.put(questId, now);
        RECENT_COMPLETIONS.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_WINDOW_MS);
        return previous != null && now - previous < DUPLICATE_WINDOW_MS;
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

    private static void registerEvent(Class<?> owner, String fieldName, Object listener) throws ReflectiveOperationException {
        invokeRegister(owner.getField(fieldName).get(null), listener);
    }

    private static void invokeRegister(Object event, Object listener) throws ReflectiveOperationException {
        for (Method method : event.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 1) continue;
            method.setAccessible(true);
            method.invoke(event, listener);
            return;
        }
        throw new NoSuchMethodException("register");
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
