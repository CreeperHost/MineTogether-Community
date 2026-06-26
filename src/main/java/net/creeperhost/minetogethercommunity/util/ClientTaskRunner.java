package net.creeperhost.minetogethercommunity.util;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

public final class ClientTaskRunner {
    private static final String[] SCHEDULE_METHODS = {"addScheduledTask", "func_152344_a"};
    private static volatile Method scheduleMethod;

    private ClientTaskRunner() {
    }

    public static void run(Runnable task) {
        if (task == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        Method method = scheduleMethod;
        if (method == null) {
            method = findScheduleMethod();
            scheduleMethod = method;
        }
        if (method != null) {
            try {
                method.invoke(minecraft, task);
                return;
            } catch (Throwable ignored) {
            }
        }
        task.run();
    }

    private static Method findScheduleMethod() {
        for (String name : SCHEDULE_METHODS) {
            try {
                Method method = Minecraft.class.getMethod(name, Runnable.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
