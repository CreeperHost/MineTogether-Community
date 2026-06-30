package net.creeperhost.minetogethercommunity.compat.legacyquests;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

final class LegacyQuestCompat {

    static final int MAX_SEEN = 512;

    private LegacyQuestCompat() {
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

    static Object invokeAny(Object target, boolean allowStaticTarget, String[] names, Object... args) {
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

    static Object invokeAny(Object target, boolean allowStaticTarget, String name, Object arg) {
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
            } else if ((type == short.class || type == Short.class) && arg instanceof Number) {
                converted[i] = ((Number) arg).shortValue();
            } else if (type == String.class) {
                converted[i] = String.valueOf(arg);
            } else {
                return null;
            }
        }
        return converted;
    }

    static String stringValue(Object value) {
        if (value == null) return "";
        if (value instanceof IChatComponent) {
            return strip(((IChatComponent) value).getUnformattedText());
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
        Object text = invokeAny(value, false, new String[]{"getString", "getUnformattedText", "getFormattedText", "getText"});
        if (text != null && text != value) return stringValue(text);
        return strip(String.valueOf(value));
    }

    static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second == null ? "" : second : first;
    }

    static String iconItemId(Object value, String fallback) {
        Object icon = value;
        if (!(icon instanceof ItemStack)) {
            icon = read(icon, "getBaseStack", "getStack", "getItemStack", "baseStack", "stack", "icon", "getIcon");
        }
        if (icon instanceof ItemStack) {
            ItemStack stack = (ItemStack) icon;
            if (stack.getItem() != null) {
                String name = Item.itemRegistry.getNameForObject(stack.getItem());
                return name == null ? stack.getUnlocalizedName() : name;
            }
        }
        return fallback;
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

    static String strip(String value) {
        if (value == null) return "";
        return value.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "").trim();
    }
}
