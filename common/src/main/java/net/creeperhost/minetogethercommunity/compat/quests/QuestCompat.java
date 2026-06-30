package net.creeperhost.minetogethercommunity.compat.quests;

import com.google.common.hash.Hashing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class QuestCompat {

    static final int MAX_SEEN = 512;

    private QuestCompat() {
    }

    static Class<?> classForName(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        throw last == null ? new ClassNotFoundException() : last;
    }

    static Object read(Object target, String... names) {
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

    static Object readStatic(Class<?> type, String... names) {
        if (type == null) return null;
        Object value = invokeAny(type, true, names);
        if (value != null) return value;
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

    static Object invokeAny(Object target, boolean allowStaticTarget, String... names) {
        return invokeAny(target, allowStaticTarget, names, new Object[0]);
    }

    static Object invokeAny(Object target, boolean allowStaticTarget, String[] names, Object... args) {
        if (target == null) return null;
        Class<?> type = target instanceof Class<?> && allowStaticTarget ? (Class<?>) target : target.getClass();
        Object receiver = target instanceof Class<?> && allowStaticTarget ? null : target;
        for (String name : names) {
            Object value = invoke(type.getMethods(), receiver, name, args);
            if (value != null) return value;
            value = invoke(type.getDeclaredMethods(), receiver, name, args);
            if (value != null) return value;
        }
        return null;
    }

    private static Object invoke(Method[] methods, Object receiver, String name, Object... args) {
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
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
            } else if ((type == long.class || type == Long.class) && arg instanceof Number number) {
                converted[i] = number.longValue();
            } else if ((type == int.class || type == Integer.class) && arg instanceof Number number) {
                converted[i] = number.intValue();
            } else if ((type == boolean.class || type == Boolean.class) && arg instanceof Boolean bool) {
                converted[i] = bool;
            } else if (type == String.class) {
                converted[i] = String.valueOf(arg);
            } else {
                return null;
            }
        }
        return converted;
    }

    static String stringValue(Object value) {
        value = unwrapOptional(value);
        if (value == null) return "";
        if (value instanceof Component component) return strip(component.getString());
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object entry : iterable) {
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
        Object text = invokeAny(value, false, new String[]{"getString", "getUnformattedText", "getFormattedText", "getText"});
        if (text != null && text != value) return stringValue(text);
        return strip(String.valueOf(value));
    }

    static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second == null ? "" : second : first;
    }

    static String itemStackId(Object value, String fallback) {
        Object candidate = unwrapItemLike(value);
        if (candidate instanceof ItemStack stack && !stack.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return fallback;
    }

    static Object unwrapItemLike(Object value) {
        value = unwrapOptional(value);
        if (value instanceof ItemStack) return value;

        Object left = unwrapOptional(invokeAny(value, false, "left"));
        if (left instanceof ItemStack) return left;

        Object itemValue = read(value, "item", "getItem");
        Object stack = invokeAny(itemValue, false, "getDefaultInstance");
        if (stack instanceof ItemStack) return stack;

        stack = invokeAny(value, false, "getDefaultInstance");
        if (stack instanceof ItemStack) return stack;

        stack = read(value, "getBaseStack", "getStack", "getItemStack", "baseStack", "stack", "icon", "getIcon");
        if (stack != null && stack != value) return unwrapItemLike(stack);
        return null;
    }

    static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    static Iterable<?> iterable(Object value) {
        value = unwrapOptional(value);
        if (value instanceof Iterable<?> iterable) return iterable;
        if (value instanceof Map<?, ?> map) return map.values();
        return java.util.List.of();
    }

    static boolean remember(Set<String> seen, String key) {
        synchronized (seen) {
            if (!seen.add(key)) return false;
            while (seen.size() > MAX_SEEN) {
                Iterator<String> iterator = seen.iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    static void clear(Set<String> seen) {
        synchronized (seen) {
            seen.clear();
        }
    }

    static String hash(String value) {
        return Hashing.sha256().hashString(value == null ? "" : value, StandardCharsets.UTF_8).toString();
    }

    static String strip(String value) {
        if (value == null) return "";
        String stripped = ChatFormatting.stripFormatting(value);
        return stripped == null ? "" : stripped.trim();
    }
}
