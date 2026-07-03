package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EmoteRadialScreen extends Screen {

    private static final int INNER_RADIUS = 34;
    private static final int OUTER_RADIUS = 112;
    private static final int LABEL_RADIUS = 78;
    private static final double SECTOR_GAP = 0.026D;
    private static final int ARC_STEPS = 18;

    private final List<CosmeticItem> emotes = new ArrayList<>();

    public EmoteRadialScreen() {
        super(Component.translatable("minetogether:gui.emotes.title"));
    }

    @Override
    protected void init() {
        emotes.clear();
        Map<String, CosmeticItem> available = CosmeticDownloader.instance().getEmoteCatalog().stream()
                .filter(item -> !item.locked())
                .collect(Collectors.toMap(CosmeticItem::id, Function.identity(), (first, second) -> first));
        EmoteFavorites.ids().stream()
                .map(available::get)
                .filter(item -> item != null)
                .limit(EmoteFavorites.MAX_FAVORITES)
                .forEach(emotes::add);
        emotes.forEach(item -> CosmeticDownloader.instance().ensureAssetLoaded("emote", item.id()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int centerX = width / 2;
        int centerY = height / 2;
        int selected = selectedIndex(mouseX, mouseY);

        if (emotes.isEmpty()) {
            drawDisc(graphics, centerX, centerY, 54, 0xCC11171D, 32);
            drawRing(graphics, centerX, centerY, 45, 54, 0x551A9FD7);
            graphics.drawCenteredString(font, Component.translatable("minetogether:gui.emotes.empty"), centerX, centerY - 4, 0xAAAAAA);
            return;
        }

        drawDisc(graphics, centerX + 3, centerY + 5, OUTER_RADIUS + 2, 0x55000000, 48);
        drawRing(graphics, centerX, centerY, OUTER_RADIUS - 5, OUTER_RADIUS + 2, 0x351DA7DB);

        int count = emotes.size();
        for (int i = 0; i < count; i++) {
            double start = -Math.PI / 2.0 + (Math.PI * 2.0 * i / count);
            double end = -Math.PI / 2.0 + (Math.PI * 2.0 * (i + 1) / count);
            boolean selectedSlice = i == selected;
            int color = selectedSlice ? 0xE02E8FC5 : 0xC5192530;
            int innerColor = selectedSlice ? 0xD936B7EF : 0xAF22313C;
            int edge = selectedSlice ? 0xFF8DEEFF : 0xCC071017;
            drawSector(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, end, color);
            drawSector(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS - 18, start, end, innerColor);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, edge);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, end, edge);

            double mid = (start + end) / 2.0;
            int labelX = centerX + (int) Math.round(Math.cos(mid) * LABEL_RADIUS);
            int labelY = centerY + (int) Math.round(Math.sin(mid) * LABEL_RADIUS);
            String label = trimLabel(emotes.get(i).displayName());
            int labelWidth = font.width(label);
            if (selectedSlice) {
                graphics.fill(labelX - labelWidth / 2 - 5, labelY - 8, labelX + labelWidth / 2 + 5, labelY + 7, 0x66101820);
                graphics.fill(labelX - labelWidth / 2 - 5, labelY + 6, labelX + labelWidth / 2 + 5, labelY + 7, 0xAA8DEEFF);
            }
            graphics.drawCenteredString(font, label, labelX, labelY - 4, selectedSlice ? 0xFFFFFFFF : 0xFFD7DDE5);
        }

        drawDisc(graphics, centerX, centerY, INNER_RADIUS - 3, 0xF0101419, 32);
        drawRing(graphics, centerX, centerY, INNER_RADIUS - 4, INNER_RADIUS + 2, selected >= 0 ? 0xCC8DEEFF : 0x99505A64);
        graphics.drawCenteredString(font, title, centerX, centerY - 4, selected >= 0 ? 0xFFFFFFFF : 0xFFE6E6E6);

        if (selected >= 0) {
            Component selectedText = Component.literal(emotes.get(selected).displayName());
            int textWidth = font.width(selectedText);
            int y = centerY + OUTER_RADIUS + 14;
            graphics.fill(centerX - textWidth / 2 - 10, y - 5, centerX + textWidth / 2 + 10, y + 12, 0xAA101820);
            graphics.fill(centerX - textWidth / 2 - 10, y + 11, centerX + textWidth / 2 + 10, y + 12, 0xCC6DE5FF);
            graphics.drawCenteredString(font, selectedText, centerX, y, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int selected = selectedIndex((int) mouseX, (int) mouseY);
        if (button == 0 && selected >= 0 && selected < emotes.size()) {
            EmotePlayer.playLocal(emotes.get(selected).id());
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int selectedIndex(int mouseX, int mouseY) {
        if (emotes.isEmpty()) return -1;
        int centerX = width / 2;
        int centerY = height / 2;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < INNER_RADIUS || distance > OUTER_RADIUS) return -1;

        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (angle < 0.0) angle += Math.PI * 2.0;
        int index = (int) Math.floor(angle / (Math.PI * 2.0 / emotes.size()));
        return Math.max(0, Math.min(emotes.size() - 1, index));
    }

    private void drawSector(GuiGraphics graphics, int centerX, int centerY, int innerRadius, int outerRadius,
                            double startAngle, double endAngle, int color) {
        double start = startAngle + SECTOR_GAP;
        double end = endAngle - SECTOR_GAP;
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= ARC_STEPS; i++) {
            double angle = start + (end - start) * i / ARC_STEPS;
            addPolarVertex(buffer, matrix, centerX, centerY, outerRadius, angle, color);
            addPolarVertex(buffer, matrix, centerX, centerY, innerRadius, angle, color);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawSectorEdge(GuiGraphics graphics, int centerX, int centerY, int innerRadius, int outerRadius,
                                double angle, int color) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int x1 = centerX + (int) Math.round(cos * innerRadius);
        int y1 = centerY + (int) Math.round(sin * innerRadius);
        int x2 = centerX + (int) Math.round(cos * outerRadius);
        int y2 = centerY + (int) Math.round(sin * outerRadius);
        drawLine(graphics, x1, y1, x2, y2, color);
    }

    private void drawDisc(GuiGraphics graphics, int centerX, int centerY, int radius, int color, int steps) {
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        addVertex(buffer, matrix, centerX, centerY, color);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            addPolarVertex(buffer, matrix, centerX, centerY, radius, angle, color);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawRing(GuiGraphics graphics, int centerX, int centerY, int innerRadius, int outerRadius, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            addPolarVertex(buffer, matrix, centerX, centerY, outerRadius, angle, color);
            addPolarVertex(buffer, matrix, centerX, centerY, innerRadius, angle, color);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0D) return;
        float nx = (float) (-dy / length);
        float ny = (float) (dx / length);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addVertex(buffer, matrix, x1 + nx, y1 + ny, color);
        addVertex(buffer, matrix, x2 + nx, y2 + ny, color);
        addVertex(buffer, matrix, x2 - nx, y2 - ny, color);
        addVertex(buffer, matrix, x1 - nx, y1 - ny, color);
        BufferUploader.drawWithShader(buffer.end());
    }

    private void addPolarVertex(BufferBuilder buffer, Matrix4f matrix, int centerX, int centerY, int radius, double angle, int color) {
        addVertex(
                buffer,
                matrix,
                centerX + (float) Math.cos(angle) * radius,
                centerY + (float) Math.sin(angle) * radius,
                color
        );
    }

    private void addVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
        int alpha = color >>> 24 & 255;
        int red = color >>> 16 & 255;
        int green = color >>> 8 & 255;
        int blue = color & 255;
        buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    private String trimLabel(String label) {
        if (label.length() <= 12) return label;
        return label.substring(0, 11) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
