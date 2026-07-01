package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EmoteRadialScreen extends Screen {

    private static final int INNER_RADIUS = 30;
    private static final int OUTER_RADIUS = 108;
    private static final int LABEL_RADIUS = 74;

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
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int selected = selectedIndex(mouseX, mouseY);

        if (emotes.isEmpty()) {
            drawDisc(graphics, centerX, centerY, 50, 0xAA16191D);
            graphics.drawCenteredString(font, Component.translatable("minetogether:gui.emotes.empty"), centerX, centerY - 4, 0xAAAAAA);
            return;
        }

        int count = emotes.size();
        for (int i = 0; i < count; i++) {
            double start = -Math.PI / 2.0 + (Math.PI * 2.0 * i / count);
            double end = -Math.PI / 2.0 + (Math.PI * 2.0 * (i + 1) / count);
            int color = i == selected ? 0xD02A9BDB : 0xAA202832;
            int edge = i == selected ? 0xFF6DE5FF : 0xAA0A1118;
            drawSector(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, end, color);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, start, edge);
            drawSectorEdge(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, end, edge);

            double mid = (start + end) / 2.0;
            int labelX = centerX + (int) Math.round(Math.cos(mid) * LABEL_RADIUS);
            int labelY = centerY + (int) Math.round(Math.sin(mid) * LABEL_RADIUS);
            String label = trimLabel(emotes.get(i).displayName());
            graphics.drawCenteredString(font, label, labelX, labelY - 4, i == selected ? 0xFFFFFF : 0xD7DDE5);
        }

        drawDisc(graphics, centerX, centerY, INNER_RADIUS - 6, 0xEE101419);
        graphics.drawCenteredString(font, title, centerX, centerY - 4, 0xFFFFFF);

        if (selected >= 0) {
            Component selectedText = Component.literal(emotes.get(selected).displayName());
            graphics.drawCenteredString(font, selectedText, centerX, centerY + OUTER_RADIUS + 18, 0xFFFFFF);
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
        for (double angle = startAngle + 0.02; angle < endAngle - 0.02; angle += 0.025) {
            for (int radius = innerRadius; radius <= outerRadius; radius += 4) {
                int x = centerX + (int) Math.round(Math.cos(angle) * radius);
                int y = centerY + (int) Math.round(Math.sin(angle) * radius);
                graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
            }
        }
    }

    private void drawSectorEdge(GuiGraphics graphics, int centerX, int centerY, int innerRadius, int outerRadius,
                                double angle, int color) {
        for (int radius = innerRadius; radius <= outerRadius; radius += 3) {
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void drawDisc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y += 3) {
            int width = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - width, centerY + y, centerX + width + 1, centerY + y + 3, color);
        }
    }

    private String trimLabel(String label) {
        if (label.length() <= 11) return label;
        return label.substring(0, 10) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
