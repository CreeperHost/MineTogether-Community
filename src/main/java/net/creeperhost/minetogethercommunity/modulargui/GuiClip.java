package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

public final class GuiClip {

    private static final Deque<int[]> STACK = new ArrayDeque<int[]>();
    private static final Deque<int[]> OFFSETS = new ArrayDeque<int[]>();
    private static int offsetX;
    private static int offsetY;

    private GuiClip() {
    }

    public static void push(int x, int y, int width, int height) {
        int[] rect = new int[] {x + offsetX, y + offsetY, Math.max(0, width), Math.max(0, height)};
        if (!STACK.isEmpty()) {
            int[] parent = STACK.peek();
            int left = Math.max(rect[0], parent[0]);
            int top = Math.max(rect[1], parent[1]);
            int right = Math.min(rect[0] + rect[2], parent[0] + parent[2]);
            int bottom = Math.min(rect[1] + rect[3], parent[1] + parent[3]);
            rect[0] = left;
            rect[1] = top;
            rect[2] = Math.max(0, right - left);
            rect[3] = Math.max(0, bottom - top);
        }
        STACK.push(rect);
        apply(rect);
    }

    public static void pushOffset(int x, int y) {
        offsetX += x;
        offsetY += y;
        OFFSETS.push(new int[] {x, y});
    }

    public static void popOffset() {
        if (OFFSETS.isEmpty()) return;
        int[] offset = OFFSETS.pop();
        offsetX -= offset[0];
        offsetY -= offset[1];
    }

    public static void pop() {
        if (!STACK.isEmpty()) {
            STACK.pop();
        }
        if (STACK.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(STACK.peek());
        }
    }

    public static void clear() {
        STACK.clear();
        OFFSETS.clear();
        offsetX = 0;
        offsetY = 0;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void apply(int[] rect) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int factor = scaledResolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rect[0] * factor,
                (scaledResolution.getScaledHeight() - rect[1] - rect[3]) * factor,
                rect[2] * factor,
                rect[3] * factor);
    }
}
