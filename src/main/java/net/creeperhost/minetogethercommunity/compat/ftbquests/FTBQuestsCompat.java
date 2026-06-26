package net.creeperhost.minetogethercommunity.compat.ftbquests;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class FTBQuestsCompat {

    private static final String FALLBACK_ICON = "minecraft:book";
    private static final int MAX_SEEN = 512;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<String>());

    private long lastPoll;
    private String lastTeamKey = "";
    private boolean seeded;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL_MS) return;
        lastPoll = now;
        pollClientQuestFile();
    }

    @SubscribeEvent
    public void onEvent(Event event) {
        String eventName = event.getClass().getName().toLowerCase(Locale.ROOT);
        if (!eventName.contains("ftb") || !eventName.contains("quest") || !eventName.contains("complete")) {
            return;
        }

        Object quest = findQuest(event);
        if (quest == null) return;

        queueQuest(quest, true);
    }

    private void pollClientQuestFile() {
        try {
            Class<?> fileClass = Class.forName("com.feed_the_beast.ftbquests.client.ClientQuestFile");
            Object exists = invokeAny(fileClass, true, new String[]{"exists"});
            if (!(exists instanceof Boolean) || !((Boolean) exists)) {
                resetPolling();
                return;
            }

            Object file = readStatic(fileClass, "INSTANCE");
            Object data = read(file, "self");
            if (file == null || data == null) {
                resetPolling();
                return;
            }

            String teamKey = stringValue(read(data, "getTeamID", "teamID"));
            if (!teamKey.equals(lastTeamKey)) {
                lastTeamKey = teamKey;
                seeded = false;
                synchronized (SEEN) {
                    SEEN.clear();
                }
            }

            for (Object quest : quests(file)) {
                Object complete = invokeAny(quest, false, new String[]{"isComplete"}, data);
                if (Boolean.TRUE.equals(complete)) {
                    queueQuest(quest, !seeded);
                }
            }
            seeded = true;
        } catch (Throwable ignored) {
            resetPolling();
        }
    }

    private void resetPolling() {
        lastTeamKey = "";
        seeded = false;
        synchronized (SEEN) {
            SEEN.clear();
        }
    }

    private static Collection<?> quests(Object file) {
        Object direct = read(file, "quests");
        if (direct instanceof Collection) return (Collection<?>) direct;

        Set<Object> result = new LinkedHashSet<>();
        Object chapters = read(file, "chapters", "getChapters");
        if (chapters instanceof Iterable) {
            for (Object chapter : (Iterable<?>) chapters) {
                Object quests = read(chapter, "quests", "getQuests");
                if (quests instanceof Iterable) {
                    for (Object quest : (Iterable<?>) quests) {
                        result.add(quest);
                    }
                }
            }
        }
        return result;
    }

    private static void queueQuest(Object quest, boolean emit) {
        String questId = firstNonEmpty(stringValue(read(quest, "getCodeString", "getCode", "getId", "getID", "id")),
                stringValue(read(quest, "id")));
        if (questId.isEmpty()) return;

        String title = stringValue(read(quest, "getTitle", "title"));
        String description = firstNonEmpty(stringValue(read(quest, "getDescription", "description")),
                stringValue(read(quest, "getSubtitle", "subtitle")));
        String icon = iconItemId(quest);
        String key = questId + "\n" + title + "\n" + description;
        if (!remember(key)) return;
        if (!emit) return;

        ActivityTelemetry.queueQuest(questId, title, description, icon);
    }

    private static Object findQuest(Object event) {
        Object quest = read(event, "getQuest", "quest");
        if (isQuest(quest)) return quest;

        Object object = read(event, "getObject", "getQuestObject", "getQuestObjectBase", "object", "questObject", "questObjectBase");
        if (isQuest(object)) return object;

        quest = read(object, "getQuest", "quest");
        if (isQuest(quest)) return quest;

        Object id = read(event, "getObjectId", "getId", "getID", "id");
        return findQuestById(id);
    }

    private static Object findQuestById(Object id) {
        if (id == null) return null;
        String[] classNames = {
                "dev.ftb.mods.ftbquests.client.ClientQuestFile",
                "com.feed_the_beast.ftbquests.client.ClientQuestFile"
        };
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                Object instance = readStatic(type, "INSTANCE", "instance");
                Object quest = invokeAny(instance == null ? type : instance, true,
                        new String[]{"getQuest", "getQuestById", "get", "getBase", "getObject", "getObjectById", "getQuestObject", "getQuestObjectBase"}, id);
                if (isQuest(quest)) return quest;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isQuest(Object value) {
        if (value == null) return false;
        Class<?> type = value.getClass();
        return "Quest".equals(type.getSimpleName()) && type.getName().toLowerCase(Locale.ROOT).contains("ftb");
    }

    private static String iconItemId(Object quest) {
        Object icon = read(quest, "icon", "getIcon");
        Object stack = icon instanceof ItemStack ? icon : read(icon, "getStack", "getItemStack", "stack");
        if (stack instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) stack;
            if (!itemStack.isEmpty()) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(itemStack.getItem());
                if (id != null) return id.toString();
            }
        }
        return FALLBACK_ICON;
    }

    private static Object read(Object target, String... names) {
        if (target == null) return null;
        Object value = invokeAny(target, false, names);
        if (value != null) return value;
        for (String name : names) {
            try {
                Field field = target.getClass().getField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object readStatic(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
            }
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, boolean allowStaticTarget, String[] names, Object... args) {
        if (target == null) return null;
        Class<?> type = target instanceof Class && allowStaticTarget ? (Class<?>) target : target.getClass();
        Object receiver = target instanceof Class && allowStaticTarget ? null : target;
        for (String name : names) {
            Object value = invoke(type.getMethods(), receiver, name, args);
            if (value != null) return value;
            value = invoke(type.getDeclaredMethods(), receiver, name, args);
            if (value != null) return value;
        }
        return null;
    }

    private static Object invokeAny(Object target, boolean allowStaticTarget, String name, Object arg) {
        return invokeAny(target, allowStaticTarget, new String[]{name}, arg);
    }

    private static Object invoke(Method[] methods, Object receiver, String name, Object... args) {
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
            try {
                method.setAccessible(true);
                Object[] converted = convertArgs(method.getParameterTypes(), args);
                if (converted == null) continue;
                return method.invoke(receiver, converted);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object[] convertArgs(Class<?>[] types, Object[] args) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class<?> type = types[i];
            if (arg == null || type.isInstance(arg)) {
                converted[i] = arg;
            } else if ((type == long.class || type == Long.class) && arg instanceof Number) {
                converted[i] = ((Number) arg).longValue();
            } else if ((type == int.class || type == Integer.class) && arg instanceof Number) {
                converted[i] = ((Number) arg).intValue();
            } else if (type == String.class) {
                converted[i] = String.valueOf(arg);
            } else {
                return null;
            }
        }
        return converted;
    }

    private static String stringValue(Object value) {
        if (value == null) return "";
        if (value instanceof ITextComponent) {
            return strip(((ITextComponent) value).getUnformattedText());
        }
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object entry : (Iterable<?>) value) {
                String text = stringValue(entry);
                if (text.isEmpty()) continue;
                if (builder.length() > 0) builder.append('\n');
                builder.append(text);
            }
            return builder.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String text = stringValue(Array.get(value, i));
                if (text.isEmpty()) continue;
                if (builder.length() > 0) builder.append('\n');
                builder.append(text);
            }
            return builder.toString();
        }
        Object text = invokeAny(value, false, new String[]{"getString", "getUnformattedText"});
        if (text != null && text != value) return stringValue(text);
        return strip(String.valueOf(value));
    }

    private static String strip(String value) {
        if (value == null) return "";
        String stripped = TextFormatting.getTextWithoutFormattingCodes(value);
        return stripped == null ? "" : stripped.trim();
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second == null ? "" : second : first;
    }

    private static boolean remember(String key) {
        synchronized (SEEN) {
            if (!SEEN.add(key)) return false;
            while (SEEN.size() > MAX_SEEN) {
                Iterator<String> iterator = SEEN.iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }
}
