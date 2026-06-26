package net.minecraft.util.math;

public final class MathHelper {
    private MathHelper() {
    }

    public static int ceil(float value) {
        return net.minecraft.util.MathHelper.ceiling_float_int(value);
    }

    public static int ceil(double value) {
        return net.minecraft.util.MathHelper.ceiling_double_int(value);
    }

    public static int clamp(int value, int min, int max) {
        return net.minecraft.util.MathHelper.clamp_int(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return net.minecraft.util.MathHelper.clamp_float(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return net.minecraft.util.MathHelper.clamp_double(value, min, max);
    }

    public static float sin(float value) {
        return net.minecraft.util.MathHelper.sin(value);
    }

    public static float cos(float value) {
        return net.minecraft.util.MathHelper.cos(value);
    }
}
