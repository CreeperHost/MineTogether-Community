package net.creeperhost.minetogethercommunity.util;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public final class ServerTaskRunner {
    private static final String[] SCHEDULE_METHODS = {"addScheduledTask", "func_152344_a"};
    private static volatile Method scheduleMethod;

    private ServerTaskRunner() {
    }

    public static void run(MinecraftServer server, Runnable task) {
        if (server == null || task == null) {
            return;
        }
        Method method = scheduleMethod;
        if (method == null) {
            method = findScheduleMethod();
            scheduleMethod = method;
        }
        if (method != null) {
            try {
                method.invoke(server, task);
                return;
            } catch (Throwable ignored) {
            }
        }
        task.run();
    }

    private static Method findScheduleMethod() {
        for (String name : SCHEDULE_METHODS) {
            try {
                Method method = MinecraftServer.class.getMethod(name, Runnable.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
