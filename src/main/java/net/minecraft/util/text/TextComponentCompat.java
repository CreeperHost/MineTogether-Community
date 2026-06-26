package net.minecraft.util.text;

import net.minecraft.util.ChatComponentStyle;
import net.minecraft.util.IChatComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

final class TextComponentCompat {
    private static final String[] SIBLING_METHODS = {"getSiblings", "func_150253_a"};
    private static final String[] SIBLING_FIELDS = {"siblings", "field_150264_a"};
    private static volatile Method siblingMethod;
    private static volatile Field siblingField;

    private TextComponentCompat() {
    }

    @SuppressWarnings("unchecked")
    static List<IChatComponent> siblings(ChatComponentStyle component) {
        if (component == null) {
            return Collections.emptyList();
        }
        Method method = siblingMethod;
        if (method == null) {
            method = findSiblingMethod();
            siblingMethod = method;
        }
        if (method != null) {
            try {
                return (List<IChatComponent>) method.invoke(component);
            } catch (Throwable ignored) {
            }
        }
        Field field = siblingField;
        if (field == null) {
            field = findSiblingField();
            siblingField = field;
        }
        if (field != null) {
            try {
                return (List<IChatComponent>) field.get(component);
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptyList();
    }

    private static Method findSiblingMethod() {
        for (String name : SIBLING_METHODS) {
            try {
                Method method = ChatComponentStyle.class.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field findSiblingField() {
        for (String name : SIBLING_FIELDS) {
            try {
                Field field = ChatComponentStyle.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
