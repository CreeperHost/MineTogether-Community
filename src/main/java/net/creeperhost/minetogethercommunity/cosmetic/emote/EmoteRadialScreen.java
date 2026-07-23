package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmoteRadialScreen extends GuiScreen {

    private static final int INNER_RADIUS = 34;
    private static final int OUTER_RADIUS = 112;
    private static final int LABEL_RADIUS = 78;
    private static final double SECTOR_GAP = 0.026D;
    private static final int ARC_STEPS = 18;

    private final List<CosmeticItem> emotes = new ArrayList<CosmeticItem>();

    @Override
    public void initGui() {
        emotes.clear();
        List<CosmeticItem> catalog = CosmeticDownloader.instance().getEmoteCatalog();
        List<String> favorites = EmoteFavorites.ids();
        for (String favorite : favorites) {
            CosmeticItem item = findAvailable(catalog, favorite);
            if (item != null) {
                emotes.add(item);
                CosmeticDownloader.instance().ensureAssetLoaded("emote", item.id());
            }
            if (emotes.size() >= EmoteFavorites.MAX_FAVORITES) break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int centerX = width / 2;
        int centerY = height / 2;
        int selected = selectedIndex(mouseX, mouseY);
        Emote activeEmote = EmotePlayer.localActiveEmote();
        String activeTraversalId = activeEmote != null && activeEmote.requiresMovement() ? activeEmote.id() : "";

        if (emotes.isEmpty()) {
            drawDisc(centerX, centerY, 54, 0xCC11171D, 32);
            drawRing(centerX, centerY, 45, 54, 0x551A9FD7);
            drawCenteredString(fontRenderer, I18n.format("minetogether.gui.emotes.empty"), centerX, centerY - 4, 0xAAAAAA);
            return;
        }

        drawDisc(centerX + 3, centerY + 5, OUTER_RADIUS + 2, 0x55000000, 48);
        drawRing(centerX, centerY, OUTER_RADIUS - 5, OUTER_RADIUS + 2, 0x351DA7DB);

        int count = emotes.size();
        for (int i = 0; i < count; i++) {
            double start = -Math.PI / 2.0D + (Math.PI * 2.0D * i / count);
            double end = -Math.PI / 2.0D + (Math.PI * 2.0D * (i + 1) / count);
            boolean selectedSlice = i == selected;
            boolean activeTraversal = emotes.get(i).id().equals(activeTraversalId);
            int color = activeTraversal ? 0xE06A5417 : selectedSlice ? 0xE02E8FC5 : 0xC5192530;
            int innerColor = activeTraversal ? 0xD98B741F : selectedSlice ? 0xD936B7EF : 0xAF22313C;
            int edge = activeTraversal ? 0xFFFFD34D : selectedSlice ? 0xFF8DEEFF : 0xCC071017;
            drawSector(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, end, color);
            drawSector(centerX, centerY, INNER_RADIUS, OUTER_RADIUS - 18, start, end, innerColor);
            drawSectorEdge(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, edge);
            drawSectorEdge(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, end, edge);
            if (activeTraversal) {
                drawSector(centerX, centerY, OUTER_RADIUS - 13, OUTER_RADIUS - 6, start, end, 0xDDFFD34D);
            }

            double mid = (start + end) / 2.0D;
            int labelX = centerX + (int) Math.round(Math.cos(mid) * LABEL_RADIUS);
            int labelY = centerY + (int) Math.round(Math.sin(mid) * LABEL_RADIUS);
            String label = trimLabel(emotes.get(i).displayName());
            int labelWidth = fontRenderer.getStringWidth(label);
            if (selectedSlice || activeTraversal) {
                int underline = activeTraversal ? 0xDDFFD34D : 0xAA8DEEFF;
                drawRect(labelX - labelWidth / 2 - 5, labelY - 8, labelX + labelWidth / 2 + 5, labelY + 7, 0x66101820);
                drawRect(labelX - labelWidth / 2 - 5, labelY + 6, labelX + labelWidth / 2 + 5, labelY + 7, underline);
            }
            drawCenteredString(fontRenderer, label, labelX, labelY - 4, activeTraversal ? 0xFFFFF0A8 : selectedSlice ? 0xFFFFFFFF : 0xFFD7DDE5);
        }

        drawDisc(centerX, centerY, INNER_RADIUS - 3, 0xF0101419, 32);
        drawRing(centerX, centerY, INNER_RADIUS - 4, INNER_RADIUS + 2, !activeTraversalId.isEmpty() ? 0xDDFFD34D : selected >= 0 ? 0xCC8DEEFF : 0x99505A64);
        drawCenteredString(fontRenderer, I18n.format("minetogether.gui.emotes.title"), centerX, centerY - 4, selected >= 0 ? 0xFFFFFFFF : 0xFFE6E6E6);

        if (selected >= 0) {
            String selectedText = emotes.get(selected).displayName();
            int textWidth = fontRenderer.getStringWidth(selectedText);
            int y = centerY + OUTER_RADIUS + 14;
            drawRect(centerX - textWidth / 2 - 10, y - 5, centerX + textWidth / 2 + 10, y + 12, 0xAA101820);
            drawRect(centerX - textWidth / 2 - 10, y + 11, centerX + textWidth / 2 + 10, y + 12, 0xCC6DE5FF);
            drawCenteredString(fontRenderer, selectedText, centerX, y, 0xFFFFFF);
        } else if (!activeTraversalId.isEmpty()) {
            String activeText = I18n.format("minetogether.gui.emotes.traversal_active", activeEmote.displayName());
            int textWidth = fontRenderer.getStringWidth(activeText);
            int y = centerY + OUTER_RADIUS + 14;
            drawRect(centerX - textWidth / 2 - 10, y - 5, centerX + textWidth / 2 + 10, y + 12, 0xAA181509);
            drawRect(centerX - textWidth / 2 - 10, y + 11, centerX + textWidth / 2 + 10, y + 12, 0xDDFFD34D);
            drawCenteredString(fontRenderer, activeText, centerX, y, 0xFFFFE08A);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int selected = selectedIndex(mouseX, mouseY);
        if (mouseButton == 0 && selected >= 0 && selected < emotes.size()) {
            EmotePlayer.playLocal(emotes.get(selected).id());
            mc.displayGuiScreen(null);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private CosmeticItem findAvailable(List<CosmeticItem> catalog, String id) {
        if (id == null || id.isEmpty()) return null;
        for (CosmeticItem item : catalog) {
            if (id.equals(item.id()) && !item.locked()) return item;
        }
        return null;
    }

    private int selectedIndex(int mouseX, int mouseY) {
        if (emotes.isEmpty()) return -1;
        int centerX = width / 2;
        int centerY = height / 2;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < INNER_RADIUS || distance > OUTER_RADIUS) return -1;

        double angle = Math.atan2(dy, dx) + Math.PI / 2.0D;
        if (angle < 0.0D) angle += Math.PI * 2.0D;
        int index = (int) Math.floor(angle / (Math.PI * 2.0D / emotes.size()));
        return Math.max(0, Math.min(emotes.size() - 1, index));
    }

    private void drawSector(int centerX, int centerY, int innerRadius, int outerRadius, double startAngle, double endAngle, int color) {
        double start = startAngle + SECTOR_GAP;
        double end = endAngle - SECTOR_GAP;
        beginColored();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= ARC_STEPS; i++) {
            double angle = start + (end - start) * i / ARC_STEPS;
            addPolarVertex(buffer, centerX, centerY, outerRadius, angle, color);
            addPolarVertex(buffer, centerX, centerY, innerRadius, angle, color);
        }
        Tessellator.getInstance().draw();
        endColored();
    }

    private void drawSectorEdge(int centerX, int centerY, int innerRadius, int outerRadius, double angle, int color) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int x1 = centerX + (int) Math.round(cos * innerRadius);
        int y1 = centerY + (int) Math.round(sin * innerRadius);
        int x2 = centerX + (int) Math.round(cos * outerRadius);
        int y2 = centerY + (int) Math.round(sin * outerRadius);
        drawLine(x1, y1, x2, y2, color);
    }

    private void drawDisc(int centerX, int centerY, int radius, int color, int steps) {
        beginColored();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        addVertex(buffer, centerX, centerY, color);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            addPolarVertex(buffer, centerX, centerY, radius, angle, color);
        }
        Tessellator.getInstance().draw();
        endColored();
    }

    private void drawRing(int centerX, int centerY, int innerRadius, int outerRadius, int color) {
        beginColored();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            addPolarVertex(buffer, centerX, centerY, outerRadius, angle, color);
            addPolarVertex(buffer, centerX, centerY, innerRadius, angle, color);
        }
        Tessellator.getInstance().draw();
        endColored();
    }

    private void drawLine(int x1, int y1, int x2, int y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0D) return;
        float nx = (float) (-dy / length);
        float ny = (float) (dx / length);
        beginColored();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        addVertex(buffer, x1 + nx, y1 + ny, color);
        addVertex(buffer, x2 + nx, y2 + ny, color);
        addVertex(buffer, x2 - nx, y2 - ny, color);
        addVertex(buffer, x1 - nx, y1 - ny, color);
        Tessellator.getInstance().draw();
        endColored();
    }

    private void addPolarVertex(BufferBuilder buffer, int centerX, int centerY, int radius, double angle, int color) {
        addVertex(buffer, centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius, color);
    }

    private void addVertex(BufferBuilder buffer, float x, float y, int color) {
        int alpha = color >>> 24 & 255;
        int red = color >>> 16 & 255;
        int green = color >>> 8 & 255;
        int blue = color & 255;
        buffer.pos(x, y, 0.0D).color(red, green, blue, alpha).endVertex();
    }

    private void beginColored() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
    }

    private void endColored() {
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private String trimLabel(String label) {
        if (label == null) return "";
        if (label.length() <= 12) return label;
        return label.substring(0, 11) + "...";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
