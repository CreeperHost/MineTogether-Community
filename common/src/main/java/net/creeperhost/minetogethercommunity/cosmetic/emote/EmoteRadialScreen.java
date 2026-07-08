package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    private static final double FULL_CIRCLE = Math.PI * 2.0D;

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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2;
        int selected = selectedIndex(mouseX, mouseY);
        Emote activeEmote = EmotePlayer.localActiveEmote();
        String activeTraversalId = activeEmote != null && activeEmote.requiresMovement() ? activeEmote.id() : "";

        if (emotes.isEmpty()) {
            drawDisc(graphics, centerX, centerY, 54, 0xCC11171D);
            drawRing(graphics, centerX, centerY, 45, 54, 0x551A9FD7);
            graphics.centeredText(font, Component.translatable("minetogether:gui.emotes.empty"), centerX, centerY - 4, 0xAAAAAA);
            return;
        }

        drawDisc(graphics, centerX + 3, centerY + 5, OUTER_RADIUS + 2, 0x55000000);
        drawRing(graphics, centerX, centerY, OUTER_RADIUS - 5, OUTER_RADIUS + 2, 0x351DA7DB);

        int count = emotes.size();
        for (int i = 0; i < count; i++) {
            double start = -Math.PI / 2.0 + (Math.PI * 2.0 * i / count);
            double end = -Math.PI / 2.0 + (Math.PI * 2.0 * (i + 1) / count);
            boolean selectedSlice = i == selected;
            boolean activeTraversal = emotes.get(i).id().equals(activeTraversalId);
            int color = activeTraversal ? 0xE06A5417 : selectedSlice ? 0xE02E8FC5 : 0xC5192530;
            int innerColor = activeTraversal ? 0xD98B741F : selectedSlice ? 0xD936B7EF : 0xAF22313C;
            int edge = activeTraversal ? 0xFFFFD34D : selectedSlice ? 0xFF8DEEFF : 0xCC071017;
            drawSector(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, end, color);
            drawSector(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS - 18, start, end, innerColor);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, edge);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, end, edge);
            if (activeTraversal) {
                drawSector(graphics, centerX, centerY, OUTER_RADIUS - 13, OUTER_RADIUS - 6, start, end, 0xDDFFD34D);
            }

            double mid = (start + end) / 2.0;
            int labelX = centerX + (int) Math.round(Math.cos(mid) * LABEL_RADIUS);
            int labelY = centerY + (int) Math.round(Math.sin(mid) * LABEL_RADIUS);
            String label = trimLabel(emotes.get(i).displayName());
            int labelWidth = font.width(label);
            if (selectedSlice || activeTraversal) {
                int underline = activeTraversal ? 0xDDFFD34D : 0xAA8DEEFF;
                graphics.fill(labelX - labelWidth / 2 - 5, labelY - 8, labelX + labelWidth / 2 + 5, labelY + 7, 0x66101820);
                graphics.fill(labelX - labelWidth / 2 - 5, labelY + 6, labelX + labelWidth / 2 + 5, labelY + 7, underline);
            }
            graphics.centeredText(font, label, labelX, labelY - 4, activeTraversal ? 0xFFFFF0A8 : selectedSlice ? 0xFFFFFFFF : 0xFFD7DDE5);
        }

        drawDisc(graphics, centerX, centerY, INNER_RADIUS - 3, 0xF0101419);
        drawRing(graphics, centerX, centerY, INNER_RADIUS - 4, INNER_RADIUS + 2, !activeTraversalId.isEmpty() ? 0xDDFFD34D : selected >= 0 ? 0xCC8DEEFF : 0x99505A64);
        graphics.centeredText(font, title, centerX, centerY - 4, selected >= 0 ? 0xFFFFFFFF : 0xFFE6E6E6);

        if (selected >= 0) {
            Component selectedText = Component.literal(emotes.get(selected).displayName());
            int textWidth = font.width(selectedText);
            int y = centerY + OUTER_RADIUS + 14;
            graphics.fill(centerX - textWidth / 2 - 10, y - 5, centerX + textWidth / 2 + 10, y + 12, 0xAA101820);
            graphics.fill(centerX - textWidth / 2 - 10, y + 11, centerX + textWidth / 2 + 10, y + 12, 0xCC6DE5FF);
            graphics.centeredText(font, selectedText, centerX, y, 0xFFFFFF);
        } else if (!activeTraversalId.isEmpty()) {
            Component activeText = Component.translatable("minetogether:gui.emotes.traversal_active", activeEmote.displayName());
            int textWidth = font.width(activeText);
            int y = centerY + OUTER_RADIUS + 14;
            graphics.fill(centerX - textWidth / 2 - 10, y - 5, centerX + textWidth / 2 + 10, y + 12, 0xAA181509);
            graphics.fill(centerX - textWidth / 2 - 10, y + 11, centerX + textWidth / 2 + 10, y + 12, 0xDDFFD34D);
            graphics.centeredText(font, activeText, centerX, y, 0xFFFFE08A);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int selected = selectedIndex((int) event.x(), (int) event.y());
        if (event.button() == 0 && selected >= 0 && selected < emotes.size()) {
            EmotePlayer.playLocal(emotes.get(selected).id());
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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

    private void drawSector(GuiGraphicsExtractor graphics, int centerX, int centerY, int innerRadius, int outerRadius,
                            double startAngle, double endAngle, int color) {
        double start = normalizeAngle(startAngle + SECTOR_GAP);
        double end = normalizeAngle(endAngle - SECTOR_GAP);
        int innerSq = innerRadius * innerRadius;
        int outerSq = outerRadius * outerRadius;
        for (int y = -outerRadius; y <= outerRadius; y++) {
            int runStart = Integer.MIN_VALUE;
            for (int x = -outerRadius; x <= outerRadius; x++) {
                boolean inside = inSector(x, y, innerSq, outerSq, start, end);
                if (inside && runStart == Integer.MIN_VALUE) {
                    runStart = x;
                } else if (!inside && runStart != Integer.MIN_VALUE) {
                    graphics.fill(centerX + runStart, centerY + y, centerX + x, centerY + y + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                graphics.fill(centerX + runStart, centerY + y, centerX + outerRadius + 1, centerY + y + 1, color);
            }
        }
    }

    private void drawSectorEdge(GuiGraphicsExtractor graphics, int centerX, int centerY, int innerRadius, int outerRadius,
                                double angle, int color) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int x1 = centerX + (int) Math.round(cos * innerRadius);
        int y1 = centerY + (int) Math.round(sin * innerRadius);
        int x2 = centerX + (int) Math.round(cos * outerRadius);
        int y2 = centerY + (int) Math.round(sin * outerRadius);
        drawLine(graphics, x1, y1, x2, y2, color);
    }

    private void drawDisc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        int radiusSq = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radiusSq - y * y));
            graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private void drawRing(GuiGraphicsExtractor graphics, int centerX, int centerY, int innerRadius, int outerRadius, int color) {
        int innerSq = innerRadius * innerRadius;
        int outerSq = outerRadius * outerRadius;
        for (int y = -outerRadius; y <= outerRadius; y++) {
            int outerHalf = (int) Math.floor(Math.sqrt(outerSq - y * y));
            if (y * y >= innerSq) {
                graphics.fill(centerX - outerHalf, centerY + y, centerX + outerHalf + 1, centerY + y + 1, color);
                continue;
            }
            int innerHalf = (int) Math.floor(Math.sqrt(innerSq - y * y));
            graphics.fill(centerX - outerHalf, centerY + y, centerX - innerHalf, centerY + y + 1, color);
            graphics.fill(centerX + innerHalf + 1, centerY + y, centerX + outerHalf + 1, centerY + y + 1, color);
        }
    }

    private void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(x1 + dx * i / steps);
            int y = (int) Math.round(y1 + dy * i / steps);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private boolean inSector(int x, int y, int innerSq, int outerSq, double start, double end) {
        int distanceSq = x * x + y * y;
        if (distanceSq < innerSq || distanceSq > outerSq) return false;
        double angle = normalizeAngle(Math.atan2(y, x));
        if (start <= end) {
            return angle >= start && angle <= end;
        }
        return angle >= start || angle <= end;
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % FULL_CIRCLE;
        if (normalized < 0.0D) normalized += FULL_CIRCLE;
        return normalized;
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
