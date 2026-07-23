package net.creeperhost.minetogethercommunity.cosmetic;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteFavorites;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteRegistry;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiClip;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextField;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CosmeticsGui implements GuiProvider {

    private static final int CATEGORY_WIDTH = 62;
    private static final int PREVIEW_WIDTH = 150;
    private static final int GAP = 4;
    private static final int HEADER_HEIGHT = 38;
    private static final int TAB_HEIGHT = 18;
    private static final int TILE_GAP = 6;
    private static final int GRID_COLUMNS = 3;
    private static final int PREVIEW_MAX_SCALE = 58;
    private static final float PLAYER_PREVIEW_BOTTOM_RATIO = 0.98F;
    private static final char[] SPINNER = new char[] {'|', '/', '-', '\\'};

    private CosmeticTypes activeTab = CosmeticTypes.HAT;
    private int catalogScroll;
    private String searchQuery = "";
    private boolean focusSearch;
    private boolean profileFetchRequested;
    private boolean pendingInitialized;
    private boolean userTouchedSelections;
    private String savedHatId = "";
    private String savedCapeId = "";
    private String savedTailId = "";
    private String savedWingId = "";
    private String pendingHatId = "";
    private String pendingCapeId = "";
    private String pendingTailId = "";
    private String pendingWingId = "";
    private String previewEmoteId = "";
    private float previewYaw = -20.0F;
    private boolean previewTracking = true;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        CosmeticDownloader.instance().startCatalogFetch();
        initializePendingSelections();
        syncExternalSelectionsIfClean();
        ensurePendingSelectionAssets();
        if (!profileFetchRequested) {
            profileFetchRequested = true;
            CosmeticApiClient.fetchProfileAsync();
        }
        gui.onTick(new Runnable() {
            @Override
            public void run() {
                syncExternalSelectionsIfClean();
            }
        });

        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int categoryLeft = 0;
        int listLeft = CATEGORY_WIDTH + GAP;
        int previewLeft = Math.max(listLeft + 120, screenWidth - PREVIEW_WIDTH);
        int listWidth = Math.max(120, previewLeft - listLeft - GAP);
        int bodyTop = HEADER_HEIGHT;
        int bodyHeight = screenHeight - HEADER_HEIGHT;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiRectangle(root, 0xF0202020).setBounds(0, 0, screenWidth, HEADER_HEIGHT);
        new GuiText(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return I18n.format("minetogether.gui.cosmetics.title");
            }
        }).setBounds(8, 14, 120, 8);

        new GuiButton(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return "+";
            }
        }).primary().setBounds(screenWidth - 26, 8, 18, 18).onPress(new Runnable() {
            @Override
            public void run() {
                applyPendingSelections();
            }
        });
        new GuiButton(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return "<";
            }
        }).setBounds(screenWidth - 50, 8, 18, 18).onPress(new Runnable() {
            @Override
            public void run() {
                gui.mc().displayGuiScreen(gui.getParentScreen());
            }
        });

        new GuiRectangle(root, 0xD9141414).setBounds(categoryLeft, bodyTop, CATEGORY_WIDTH, bodyHeight);
        int tabIndex = 0;
        for (final CosmeticTypes type : CosmeticTypes.values()) {
            new CosmeticTab(root, type).setBounds(4, bodyTop + 8 + tabIndex * (TAB_HEIGHT + 3), CATEGORY_WIDTH - 8, TAB_HEIGHT);
            tabIndex++;
        }

        new GuiRectangle(root, 0xD91A1A1A).setBounds(listLeft, bodyTop, listWidth, bodyHeight);
        new GuiRectangle(root, 0xA0202020).setBounds(listLeft + 8, bodyTop + 8, listWidth - 16, 18);
        new GuiTextField(root)
                .setText(searchQuery)
                .setMaxLength(64)
                .setFocused(focusSearch)
                .onChanged(new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String value) {
                        searchQuery = value == null ? "" : value;
                        catalogScroll = 0;
                        focusSearch = true;
                        gui.getScreen().initGui();
                    }
                })
                .setBounds(listLeft + 9, bodyTop + 13, listWidth - 18, 8);
        focusSearch = false;

        final List<CosmeticItem> catalog = filteredCatalogFor(activeTab);
        int gridLeft = listLeft + 8;
        int gridTop = bodyTop + 36;
        int gridWidth = listWidth - 22;
        new CatalogGrid(root).setBounds(gridLeft, gridTop, gridWidth, screenHeight - gridTop - 14);
        if (CosmeticDownloader.instance().isCatalogLoading() && catalog.isEmpty()) {
            new GuiText(root, new java.util.function.Supplier<String>() {
                @Override
                public String get() {
                    return I18n.format("minetogether.gui.cosmetics.loading");
                }
            }).setColor(0xAAAAAA).setBounds(gridLeft, gridTop, gridWidth, 12);
        }

        new GuiRectangle(root, 0xE0101010).setBounds(previewLeft, bodyTop, PREVIEW_WIDTH, bodyHeight);
        new PreviewElement(root).setBounds(previewLeft + 8, bodyTop + 50, PREVIEW_WIDTH - 16, bodyHeight - 98);
        new GuiText(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return previewStatus();
            }
        }).setColor(0xAAAAAA).setBounds(previewLeft + 12, screenHeight - 44, PREVIEW_WIDTH - 24, 8);
        new GuiButton(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return "T";
            }
        }).setToggleMode(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return previewTracking;
            }
        }).setBounds(previewLeft + PREVIEW_WIDTH - 26, bodyTop + 7, 18, 18).onPress(new Runnable() {
            @Override
            public void run() {
                previewTracking = !previewTracking;
            }
        });
        new GuiText(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return currentPendingName();
            }
        }).setBounds(previewLeft + 8, bodyTop + 12, PREVIEW_WIDTH - 42, 8);
        new GuiText(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return currentAuthorLine();
            }
        }).setColor(0xAAAAAA).setBounds(previewLeft + 8, bodyTop + 24, PREVIEW_WIDTH - 42, 8);
        new GuiButton(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return I18n.format("minetogether.gui.cosmetics.preview.front");
            }
        }).setBounds(previewLeft + PREVIEW_WIDTH / 2 - 43, screenHeight - 26, 40, 18).onPress(new Runnable() {
            @Override
            public void run() {
                previewYaw = -20.0F;
            }
        });
        new GuiButton(root, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return I18n.format("minetogether.gui.cosmetics.preview.back");
            }
        }).setBounds(previewLeft + PREVIEW_WIDTH / 2 + 3, screenHeight - 26, 40, 18).onPress(new Runnable() {
            @Override
            public void run() {
                previewYaw = 160.0F;
            }
        });

        return root;
    }

    private class CosmeticTab extends GuiElement<CosmeticTab> {
        private final CosmeticTypes type;

        private CosmeticTab(GuiElement<?> parent, CosmeticTypes type) {
            super(parent);
            this.type = type;
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            boolean selected = activeTab == type;
            boolean hover = isMouseOver(mouseX, mouseY);
            int color = selected ? (hover ? 0xFF2B5F94 : 0xFF17456F) : (hover ? 0xFF242424 : 0x00151515);
            drawRect(x, y, x + width, y + height, color);
            drawCenteredString(font(), I18n.format(type.translationKey()), x + width / 2, y + (height - 8) / 2, selected ? 0xFFFFFF : 0xDDDDDD);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
            activeTab = type;
            catalogScroll = 0;
            gui.getScreen().initGui();
            return true;
        }
    }

    private class CatalogGrid extends GuiElement<CatalogGrid> {
        private static final int TILE_HEIGHT = 76;
        private static final int SCROLLBAR_WIDTH = 4;
        private static final int SCROLLBAR_RIGHT_PADDING = 6;

        private boolean draggingScrollBar;
        private int scrollDragOffset;

        private CatalogGrid(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            List<CosmeticItem> catalog = filteredCatalogFor(activeTab);
            int totalEntries = catalog.size() + 1;
            int maxScroll = maxScroll(totalEntries);
            if (catalogScroll > maxScroll) catalogScroll = maxScroll;
            if (catalogScroll < 0) catalogScroll = 0;
            updateScrollDrag(mouseY, totalEntries);

            int tileWidth = tileWidth();
            int pitchY = TILE_HEIGHT + TILE_GAP;
            int firstRow = Math.max(0, catalogScroll / pitchY);
            int lastRow = Math.min(rowCount(totalEntries) - 1, (catalogScroll + height) / pitchY + 1);
            pushScissor(x, y, width, height);
            try {
                for (int row = firstRow; row <= lastRow; row++) {
                    for (int column = 0; column < GRID_COLUMNS; column++) {
                        int index = row * GRID_COLUMNS + column;
                        if (index >= totalEntries) break;
                        int tileX = x + column * (tileWidth + TILE_GAP);
                        int tileY = y + row * pitchY - catalogScroll;
                        CosmeticItem item = index == 0 ? null : catalog.get(index - 1);
                        renderTile(tileX, tileY, tileWidth, TILE_HEIGHT, item, mouseX, mouseY);
                    }
                }
                resetGlColor();
                boolean hover = mouseX >= x + width - 6 && mouseX < x + width - 2 && mouseY >= y && mouseY < y + height;
                MTStyle.Flat.drawVerticalScrollBar(x + width - 6, y, 4, height, contentHeight(totalEntries), height, catalogScroll, hover);
            } finally {
                popScissor();
                resetGuiGlState();
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (activeTab == CosmeticTypes.EMOTES) {
                if (mouseButton != 0 && mouseButton != 1) return false;
            } else if (mouseButton != 0) {
                return false;
            }
            List<CosmeticItem> catalog = filteredCatalogFor(activeTab);
            int totalEntries = catalog.size() + 1;
            if (maxScroll(totalEntries) > 0 && isOverScrollBar(mouseX, mouseY)) {
                startScrollDrag(mouseY, totalEntries);
                return true;
            }
            int tileWidth = tileWidth();
            int localX = mouseX - x;
            int localY = mouseY - y + catalogScroll;
            int pitchX = tileWidth + TILE_GAP;
            int pitchY = TILE_HEIGHT + TILE_GAP;
            int column = localX / pitchX;
            int row = localY / pitchY;
            if (column < 0 || column >= GRID_COLUMNS) return true;
            if (localX % pitchX >= tileWidth || localY % pitchY >= TILE_HEIGHT) return true;
            int index = row * GRID_COLUMNS + column;
            if (index < 0 || index >= totalEntries) return true;
            if (index == 0) {
                if (activeTab != CosmeticTypes.EMOTES) {
                    setPending(activeTab, "");
                }
            } else {
                CosmeticItem item = catalog.get(index - 1);
                if (item.locked()) return true;
                if (activeTab == CosmeticTypes.EMOTES) {
                    if (mouseButton == 1 || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        EmoteFavorites.toggle(item.id());
                    } else {
                        setPending(activeTab, item.id());
                    }
                } else {
                    setPending(activeTab, item.id());
                }
            }
            return true;
        }

        @Override
        public boolean mouseReleased(int mouseX, int mouseY, int state) {
            if (state == 0 && draggingScrollBar) {
                draggingScrollBar = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseInput(int mouseX, int mouseY, int dWheel) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            int maxScroll = maxScroll(filteredCatalogFor(activeTab).size() + 1);
            if (maxScroll <= 0) return false;
            catalogScroll += dWheel < 0 ? 24 : -24;
            if (catalogScroll < 0) catalogScroll = 0;
            if (catalogScroll > maxScroll) catalogScroll = maxScroll;
            return true;
        }

        private void startScrollDrag(int mouseY, int totalEntries) {
            ScrollBarMetrics metrics = scrollBarMetrics(totalEntries);
            if (metrics == null) return;
            draggingScrollBar = true;
            if (mouseY >= metrics.handleTop && mouseY <= metrics.handleTop + metrics.handleHeight) {
                scrollDragOffset = mouseY - metrics.handleTop;
            } else {
                scrollDragOffset = metrics.handleHeight / 2;
                setScrollFromMouse(mouseY, metrics);
            }
        }

        private void updateScrollDrag(int mouseY, int totalEntries) {
            if (!draggingScrollBar) return;
            if (!Mouse.isButtonDown(0)) {
                draggingScrollBar = false;
                return;
            }
            ScrollBarMetrics metrics = scrollBarMetrics(totalEntries);
            if (metrics != null) {
                setScrollFromMouse(mouseY, metrics);
            }
        }

        private void setScrollFromMouse(int mouseY, ScrollBarMetrics metrics) {
            if (metrics.travel <= 0) {
                catalogScroll = 0;
                return;
            }
            int handleTop = mouseY - scrollDragOffset;
            int localTop = Math.max(0, Math.min(metrics.travel, handleTop - y));
            catalogScroll = localTop * metrics.maxScroll / metrics.travel;
            if (catalogScroll < 0) catalogScroll = 0;
            if (catalogScroll > metrics.maxScroll) catalogScroll = metrics.maxScroll;
        }

        private boolean isOverScrollBar(int mouseX, int mouseY) {
            int scrollBarX = x + width - SCROLLBAR_RIGHT_PADDING;
            return mouseX >= scrollBarX && mouseX < scrollBarX + SCROLLBAR_WIDTH && mouseY >= y && mouseY < y + height;
        }

        private ScrollBarMetrics scrollBarMetrics(int totalEntries) {
            int contentHeight = contentHeight(totalEntries);
            if (contentHeight <= height || height <= 0) return null;
            int maxScroll = Math.max(1, contentHeight - height);
            int handleHeight = Math.max(10, height * height / contentHeight);
            if (handleHeight > height) handleHeight = height;
            int travel = Math.max(0, height - handleHeight);
            int clampedScroll = Math.max(0, Math.min(catalogScroll, maxScroll));
            int handleTop = y + (travel * clampedScroll / maxScroll);
            return new ScrollBarMetrics(maxScroll, handleHeight, travel, handleTop);
        }

        private void renderTile(int tileX, int tileY, int tileWidth, int tileHeight, CosmeticItem item, int mouseX, int mouseY) {
            boolean selected = isSelected(item);
            boolean hover = mouseX >= tileX && mouseX < tileX + tileWidth && mouseY >= tileY && mouseY < tileY + tileHeight;
            boolean locked = item != null && item.locked();
            drawRect(tileX, tileY, tileX + tileWidth, tileY + tileHeight,
                    selected ? (hover ? 0xFF1F3528 : 0xFF172A20) : item == null ? 0xFF202020 : hover ? 0xFF1F2A30 : 0xFF172026);
            drawRect(tileX + 4, tileY + 22, tileX + tileWidth - 4, tileY + tileHeight - 6,
                    selected ? 0xFF10281A : item == null ? 0xFF151515 : 0xFF102030);

            if (item == null) {
                drawCenteredString(font(), I18n.format("minetogether.gui.cosmetics.none"), tileX + tileWidth / 2, tileY + 44, 0xAAAAAA);
            } else if (locked) {
                // Locked cards have no live preview, but their foreground text is still drawn below.
            } else if (activeTab == CosmeticTypes.EMOTES) {
                renderEmotePreview(item, tileX + 4, tileY + 22, tileWidth - 8, tileHeight - 28);
            } else {
                ensurePreviewAsset(item);
                if (isItemAssetLoaded(activeTab, item.id())) {
                    resetGuiGlState();
                    renderCardPreview(item, tileX + 4, tileY + 22, tileWidth - 8, tileHeight - 28);
                } else {
                    drawCenteredString(font(), I18n.format("minetogether.gui.cosmetics.preview.loading"),
                            tileX + tileWidth / 2, tileY + 44, MTStyle.Flat.TEXT_MUTED);
                    resetGlColor();
                }
            }

            resetGuiGlState();
            if (selected) {
                drawBorder(tileX, tileY, tileWidth, tileHeight, 0xFF00AA33);
            } else {
                drawRect(tileX + 4, tileY + tileHeight - 3, tileX + tileWidth - 4, tileY + tileHeight - 1, 0xFF005A9C);
            }
            drawTileForeground(tileX, tileY, tileWidth, tileHeight, item, locked, selected, hover);
            resetGuiGlState();
        }

        private void drawTileForeground(int tileX, int tileY, int tileWidth, int tileHeight, CosmeticItem item, boolean locked, boolean selected, boolean hover) {
            int nameColor = selected ? 0xFF55FF55 : item == null ? 0xFFE0E0E0 : locked ? 0xFF777777 : MTStyle.Flat.TEXT;
            drawScrollingTitle(displayName(item), tileX + 6, tileY + 8, tileWidth - 12, nameColor, hover);
            resetGlColor();
            if (item != null && locked) {
                font().drawString(trimToWidth(subtitle(item), tileWidth - 12), tileX + 6, tileY + tileHeight - 15, 0x777777);
                resetGlColor();
            } else if (item != null && selected) {
                font().drawString("v", tileX + tileWidth - 14, tileY + tileHeight - 15, 0xFF00FF55);
                resetGlColor();
            } else if (item != null && activeTab == CosmeticTypes.EMOTES) {
                int actionY = tileY + tileHeight - 18;
                drawRect(tileX + 6, actionY, tileX + 24, actionY + 14, 0xFF1F4A2A);
                drawPlayIcon(tileX + 6, actionY, 0xFF44FF66);
                boolean favorite = EmoteFavorites.isFavorite(item.id());
                int favoriteColor = favorite ? 0xFFFFD94A : EmoteFavorites.canAddMore() ? 0xFFAAAAAA : 0xFF666666;
                int favoriteX = tileX + tileWidth - 24;
                drawRect(favoriteX, actionY, favoriteX + 18, actionY + 14, favorite ? 0xFF4A3E16 : 0xFF2B2B2B);
                drawRadialIcon(favoriteX, actionY, favoriteColor);
                resetGlColor();
            }
        }

        private void renderEmotePreview(CosmeticItem item, int x, int y, int width, int height) {
            if (item == null || item.locked()) return;
            Emote emote = EmoteRegistry.getLoaded(item.id());
            boolean loaded = emote != null;
            boolean favorite = EmoteFavorites.isFavorite(item.id());
            String state = loaded
                    ? I18n.format("minetogether.gui.cosmetics.emote.ready")
                    : I18n.format("minetogether.gui.cosmetics.preview.loading");
            int stateColor = loaded ? 0xFF7DFF91 : 0xFFFFDD55;
            font().drawString(trimToWidth(state, width - 12), x + 6, y + 9, stateColor);
            font().drawString(trimToWidth(favorite
                    ? I18n.format("minetogether.gui.cosmetics.emote.radial")
                    : I18n.format("minetogether.gui.cosmetics.emote.add"), width - 12), x + 6, y + 23,
                    favorite ? 0xFFFFD94A : 0xFFAAAAAA);
        }

        private void drawPlayIcon(int x, int y, int color) {
            int px = x + 6;
            int py = y + 4;
            drawRect(px, py, px + 2, py + 6, color);
            drawRect(px + 2, py + 1, px + 4, py + 5, color);
            drawRect(px + 4, py + 2, px + 6, py + 4, color);
        }

        private void drawRadialIcon(int x, int y, int color) {
            int sx = x + 4;
            int sy = y + 2;
            drawRect(sx + 4, sy, sx + 6, sy + 2, color);
            drawRect(sx + 2, sy + 1, sx + 4, sy + 3, color);
            drawRect(sx + 6, sy + 1, sx + 8, sy + 3, color);
            drawRect(sx + 1, sy + 3, sx + 3, sy + 5, color);
            drawRect(sx + 7, sy + 3, sx + 9, sy + 5, color);
            drawRect(sx + 3, sy + 4, sx + 7, sy + 8, color);
            drawRect(sx, sy + 6, sx + 2, sy + 8, color);
            drawRect(sx + 8, sy + 6, sx + 10, sy + 8, color);
            drawRect(sx + 2, sy + 9, sx + 4, sy + 11, color);
            drawRect(sx + 6, sy + 9, sx + 8, sy + 11, color);
        }

        private void ensurePreviewAsset(CosmeticItem item) {
            if (item == null || item.locked() || isItemAssetLoaded(activeTab, item.id())) return;
            CosmeticDownloader.instance().ensureAssetLoaded(activeTab.slotName(), item.id());
        }

        private boolean isSelected(CosmeticItem item) {
            if (activeTab == CosmeticTypes.EMOTES) return false;
            return item == null ? pendingFor(activeTab).isEmpty() : item.id().equals(pendingFor(activeTab));
        }

        private String displayName(CosmeticItem item) {
            return item == null ? noneLabel(activeTab) : item.displayName();
        }

        private String subtitle(CosmeticItem item) {
            if (item == null) return "";
            if (item.locked()) {
                return item.howToUnlock() == null || item.howToUnlock().isEmpty()
                        ? I18n.format("minetogether.gui.cosmetics.locked")
                        : item.howToUnlock();
            }
            return buildSubtitle(item.author(), item.mod());
        }

        private void drawScrollingTitle(String title, int titleX, int titleY, int titleWidth, int color, boolean hover) {
            if (title == null || title.isEmpty() || titleWidth <= 0) return;
            int textWidth = font().getStringWidth(title);
            if (textWidth <= titleWidth) {
                font().drawString(title, titleX, titleY, color);
                return;
            }
            if (!hover) {
                font().drawString(trimToWidth(title, titleWidth), titleX, titleY, color);
                return;
            }

            GuiClip.push(titleX, titleY - 1, titleWidth, font().FONT_HEIGHT + 2);
            try {
                int gap = 24;
                int travel = textWidth - titleWidth;
                int holdTicks = 45;
                int scrollTicks = Math.max(1, travel);
                int period = holdTicks + scrollTicks + holdTicks;
                int tick = (int) ((System.currentTimeMillis() / 40L) % period);
                int offset = tick < holdTicks ? 0 : tick < holdTicks + scrollTicks ? tick - holdTicks : travel;
                font().drawString(title, titleX - offset, titleY, color);
                if (offset >= travel) {
                    font().drawString(title, titleX - offset + textWidth + gap, titleY, color);
                }
            } finally {
                GuiClip.pop();
            }
        }

        private int tileWidth() {
            int tileArea = Math.max(44, width - 8);
            return Math.max(44, (tileArea - (GRID_COLUMNS - 1) * TILE_GAP) / GRID_COLUMNS);
        }

        private int rowCount(int totalEntries) {
            return Math.max(1, (totalEntries + GRID_COLUMNS - 1) / GRID_COLUMNS);
        }

        private int contentHeight(int totalEntries) {
            int rows = rowCount(totalEntries);
            return rows * TILE_HEIGHT + Math.max(0, rows - 1) * TILE_GAP;
        }

        private int maxScroll(int totalEntries) {
            return Math.max(0, contentHeight(totalEntries) - height);
        }

        private class ScrollBarMetrics {
            private final int maxScroll;
            private final int handleHeight;
            private final int travel;
            private final int handleTop;

            private ScrollBarMetrics(int maxScroll, int handleHeight, int travel, int handleTop) {
                this.maxScroll = maxScroll;
                this.handleHeight = handleHeight;
                this.travel = travel;
                this.handleTop = handleTop;
            }
        }
    }

    private String noneLabel(CosmeticTypes type) {
        switch (type) {
            case CAPE:
                return I18n.format("minetogether.gui.cosmetics.cape.none");
            case TAIL:
                return I18n.format("minetogether.gui.cosmetics.tail.none");
            case WINGS:
                return I18n.format("minetogether.gui.cosmetics.wing.none");
            case EMOTES:
                return I18n.format("minetogether.gui.cosmetics.emote.none");
            case HAT:
            default:
                return I18n.format("minetogether.gui.cosmetics.hat.none");
        }
    }

    private String buildSubtitle(String author, String mod) {
        boolean hasAuthor = author != null && !author.isEmpty();
        boolean hasMod = mod != null && !mod.isEmpty();
        if (hasAuthor && hasMod) return author + " - " + mod;
        if (hasAuthor) return author;
        return hasMod ? mod : "";
    }

    private String trimToWidth(String value, int width) {
        if (value == null) return "";
        return fontTrim(value, width);
    }

    private String fontTrim(String value, int width) {
        if (Minecraft.getMinecraft().fontRendererObj.getStringWidth(value) <= width) return value;
        return Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(value, Math.max(1, width - Minecraft.getMinecraft().fontRendererObj.getStringWidth("..."))) + "...";
    }

    private void drawBorder(int x, int y, int width, int height, int color) {
        net.minecraft.client.gui.Gui.drawRect(x, y, x + width, y + 1, color);
        net.minecraft.client.gui.Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + 1, y + height, color);
        net.minecraft.client.gui.Gui.drawRect(x + width - 1, y, x + width, y + height, color);
    }

    private void initializePendingSelections() {
        if (pendingInitialized) return;
        CosmeticSelections selections = CosmeticSelections.instance();
        savedHatId = safeId(selections.selectedHatId);
        savedCapeId = safeId(selections.selectedCapeId);
        savedTailId = safeId(selections.selectedTailId);
        savedWingId = safeId(selections.selectedWingId);
        pendingHatId = savedHatId;
        pendingCapeId = savedCapeId;
        pendingTailId = savedTailId;
        pendingWingId = savedWingId;
        pendingInitialized = true;
    }

    private String pendingFor(CosmeticTypes type) {
        switch (type) {
            case HAT:
                return pendingHatId;
            case CAPE:
                return pendingCapeId;
            case TAIL:
                return pendingTailId;
            case WINGS:
                return pendingWingId;
            case EMOTES:
                return previewEmoteId;
            default:
                return "";
        }
    }

    private void setPending(CosmeticTypes type, String id) {
        String safeId = safeId(id);
        if (type == CosmeticTypes.EMOTES) {
            previewEmoteId = safeId;
            if (!safeId.isEmpty()) {
                CosmeticDownloader.instance().ensureAssetLoaded("emote", safeId);
                EmotePlayer.playLocal(safeId);
            }
            return;
        }
        userTouchedSelections = true;
        switch (type) {
            case HAT:
                pendingHatId = safeId;
                break;
            case CAPE:
                pendingCapeId = safeId;
                break;
            case TAIL:
                pendingTailId = safeId;
                break;
            case WINGS:
                pendingWingId = safeId;
                break;
            default:
                break;
        }
        CosmeticSelections.instance().setLocal(type, safeId);
        if (!safeId.isEmpty()) {
            CosmeticDownloader.instance().ensureAssetLoaded(type.slotName(), safeId);
        }
    }

    private void syncExternalSelectionsIfClean() {
        if (userTouchedSelections || hasPendingChanges()) return;
        CosmeticSelections selections = CosmeticSelections.instance();
        savedHatId = safeId(selections.selectedHatId);
        savedCapeId = safeId(selections.selectedCapeId);
        savedTailId = safeId(selections.selectedTailId);
        savedWingId = safeId(selections.selectedWingId);
        pendingHatId = savedHatId;
        pendingCapeId = savedCapeId;
        pendingTailId = savedTailId;
        pendingWingId = savedWingId;
        ensurePendingSelectionAssets();
    }

    private void ensurePendingSelectionAssets() {
        CosmeticDownloader downloader = CosmeticDownloader.instance();
        if (!pendingHatId.isEmpty()) downloader.ensureAssetLoaded(CosmeticTypes.HAT.slotName(), pendingHatId);
        if (!pendingCapeId.isEmpty()) downloader.ensureAssetLoaded(CosmeticTypes.CAPE.slotName(), pendingCapeId);
        if (!pendingTailId.isEmpty()) downloader.ensureAssetLoaded(CosmeticTypes.TAIL.slotName(), pendingTailId);
        if (!pendingWingId.isEmpty()) downloader.ensureAssetLoaded(CosmeticTypes.WINGS.slotName(), pendingWingId);
        if (!previewEmoteId.isEmpty()) downloader.ensureAssetLoaded(CosmeticTypes.EMOTES.slotName(), previewEmoteId);
    }

    private void applyPendingSelections() {
        CosmeticApiClient.selectAsync("hat", nullIfEmpty(pendingHatId));
        CosmeticApiClient.selectAsync("cape", nullIfEmpty(pendingCapeId));
        CosmeticApiClient.selectAsync("tail", nullIfEmpty(pendingTailId));
        CosmeticApiClient.selectAsync("wing", nullIfEmpty(pendingWingId));
        savedHatId = pendingHatId;
        savedCapeId = pendingCapeId;
        savedTailId = pendingTailId;
        savedWingId = pendingWingId;
        userTouchedSelections = false;
    }

    private boolean hasPendingChanges() {
        return !equalsSafe(savedHatId, pendingHatId)
                || !equalsSafe(savedCapeId, pendingCapeId)
                || !equalsSafe(savedTailId, pendingTailId)
                || !equalsSafe(savedWingId, pendingWingId);
    }

    private static String safeId(String id) {
        return id == null || "none".equals(id) ? "" : id;
    }

    private static String nullIfEmpty(String id) {
        String safeId = safeId(id);
        return safeId.isEmpty() ? null : safeId;
    }

    private static boolean equalsSafe(String a, String b) {
        return safeId(a).equals(safeId(b));
    }

    private List<CosmeticItem> catalogFor(CosmeticTypes type) {
        CosmeticDownloader downloader = CosmeticDownloader.instance();
        switch (type) {
            case HAT:
                return downloader.getHatCatalog();
            case CAPE:
                return downloader.getCapeCatalog();
            case TAIL:
                return downloader.getTailCatalog();
            case WINGS:
                return downloader.getWingCatalog();
            case EMOTES:
                return downloader.getEmoteCatalog();
            default:
                return downloader.getHatCatalog();
        }
    }

    private List<CosmeticItem> filteredCatalogFor(CosmeticTypes type) {
        List<CosmeticItem> catalog = catalogFor(type);
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return catalog;

        List<CosmeticItem> filtered = new ArrayList<CosmeticItem>();
        for (CosmeticItem item : catalog) {
            if (matches(item.id(), query)
                    || matches(item.displayName(), query)
                    || matches(item.author(), query)
                    || matches(item.mod(), query)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean matches(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String currentPendingName() {
        String id = pendingFor(activeTab);
        if (activeTab == CosmeticTypes.EMOTES) {
            if (id.isEmpty()) return I18n.format("minetogether.gui.cosmetics.emote.hint");
        }
        if (id.isEmpty()) return I18n.format("minetogether.gui.cosmetics.none");
        CosmeticItem item = catalogEntry(activeTab, id);
        return item == null ? id : item.displayName();
    }

    private String currentAuthorLine() {
        String id = pendingFor(activeTab);
        if (id.isEmpty()) return "";
        CosmeticItem item = catalogEntry(activeTab, id);
        if (item == null || item.author() == null || item.author().trim().isEmpty()) return "";
        return I18n.format("minetogether.gui.cosmetics.author", item.author());
    }

    private CosmeticItem catalogEntry(CosmeticTypes type, String id) {
        for (CosmeticItem item : catalogFor(type)) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    private class PreviewElement extends GuiElement<PreviewElement> {
        private PreviewElement(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            drawRect(x, y, x + width, y + height, 0xAA000000);
            drawRect(x, y, x + width, y + 1, 0xFF25384A);

            EntityLivingBase entity = Minecraft.getMinecraft().thePlayer;
            if (entity == null) {
                drawCenteredString(font(), I18n.format("minetogether.gui.cosmetics.preview.unavailable"),
                        x + width / 2, y + height / 2, 0xAAAAAA);
                return;
            }

            int renderBottom = y + Math.round(height * PLAYER_PREVIEW_BOTTOM_RATIO);
            int availableHeight = Math.max(40, height - 24);
            int scale = Math.max(28, Math.min(PREVIEW_MAX_SCALE, Math.min(width / 2, availableHeight / 2)));
            try {
                pushScissor(x, y, width, height);
                try {
                    drawEntityPreview(x + width / 2, renderBottom, scale, entity, mouseX, mouseY);
                } finally {
                    popScissor();
                }
            } catch (Throwable ignored) {
                resetGuiGlState();
                drawCenteredString(font(), I18n.format("minetogether.gui.cosmetics.preview.unavailable"),
                        x + width / 2, y + height / 2, 0xAAAAAA);
            }
        }
    }

    private void renderCardPreview(CosmeticItem item, int x, int y, int width, int height) {
        EntityLivingBase entity = Minecraft.getMinecraft().thePlayer;
        if (item == null) return;
        if (entity == null) {
            pushScissor(x, y, width, height);
            try {
                String label = trimToWidth(I18n.format("minetogether.gui.cosmetics.preview.card_unavailable"), width - 8);
                Minecraft.getMinecraft().fontRendererObj.drawString(label,
                        x + (width - Minecraft.getMinecraft().fontRendererObj.getStringWidth(label)) / 2,
                        y + height / 2 - 4, MTStyle.Flat.TEXT_MUTED);
            } finally {
                popScissor();
                resetGlColor();
            }
            return;
        }

        CosmeticSelections selections = CosmeticSelections.instance();
        String previousHat = selections.selectedHatId;
        String previousCape = selections.selectedCapeId;
        String previousTail = selections.selectedTailId;
        String previousWing = selections.selectedWingId;
        boolean previousSuppressVanillaCapeForPreview = selections.suppressVanillaCapeForPreview;
        boolean previousFullBrightPreview = selections.fullBrightPreview;

        try {
            selections.selectedHatId = "";
            selections.selectedCapeId = "";
            selections.selectedTailId = "";
            selections.selectedWingId = "";
            selections.set(activeTab, item.id());
            selections.suppressVanillaCapeForPreview = true;
            selections.fullBrightPreview = true;

            resetGuiGlState();
            pushScissor(x, y, width, height);
            try {
                float entityHeight = Math.max(0.1F, ((Entity) entity).height);
                int scale = Math.max(18, Math.round(Math.min((height / entityHeight) * 1.45F, 46.0F) * cardPreviewScale()));
                int yPos = Math.round(y + height + scale * cardPreviewYOffset());
                try {
                    final int previewX = x + width / 2;
                    final int previewY = yPos;
                    final int previewScale = scale;
                    final float yaw = cardPreviewYaw();
                    EmotePlayer.withoutPose(new Runnable() {
                        @Override
                        public void run() {
                            drawStaticEntityPreview(previewX, previewY, previewScale, entity, yaw);
                        }
                    });
                } catch (Throwable ignored) {
                    resetGuiGlState();
                    String label = trimToWidth(I18n.format("minetogether.gui.cosmetics.preview.card_unavailable"), width - 8);
                    Minecraft.getMinecraft().fontRendererObj.drawString(label,
                            x + (width - Minecraft.getMinecraft().fontRendererObj.getStringWidth(label)) / 2,
                            y + height / 2 - 4, MTStyle.Flat.TEXT_MUTED);
                }
            } finally {
                popScissor();
            }
        } finally {
            selections.selectedHatId = previousHat;
            selections.selectedCapeId = previousCape;
            selections.selectedTailId = previousTail;
            selections.selectedWingId = previousWing;
            selections.suppressVanillaCapeForPreview = previousSuppressVanillaCapeForPreview;
            selections.fullBrightPreview = previousFullBrightPreview;
            resetGuiGlState();
        }
    }

    private float cardPreviewYaw() {
        switch (activeTab) {
            case HAT:
                return mirrorCardYaw(155.0F);
            case CAPE:
            case TAIL:
            case WINGS:
                return mirrorCardYaw(35.0F);
            default:
                return mirrorCardYaw(180.0F);
        }
    }

    private float mirrorCardYaw(float modernYaw) {
        return 180.0F - modernYaw;
    }

    private float cardPreviewScale() {
        switch (activeTab) {
            case CAPE:
                return 0.88F;
            case TAIL:
                return 0.86F;
            case WINGS:
                return 0.82F;
            default:
                return 1.0F;
        }
    }

    private float cardPreviewYOffset() {
        switch (activeTab) {
            case HAT:
                return 1.35F;
            case CAPE:
                return 0.05F;
            case TAIL:
                return -0.16F;
            case WINGS:
                return -0.34F;
            default:
                return -0.40F;
        }
    }

    private void drawStaticEntityPreview(int x, int y, int scale, EntityLivingBase entity, float yaw) {
        Entity baseEntity = entity;
        float previousRenderYawOffset = entity.renderYawOffset;
        float previousRotationYaw = baseEntity.rotationYaw;
        float previousRotationPitch = baseEntity.rotationPitch;
        float previousPrevRotationYawHead = entity.prevRotationYawHead;
        float previousRotationYawHead = entity.rotationYawHead;
        int previousHurtTime = entity.hurtTime;
        int previousDeathTime = entity.deathTime;
        float previousAttackedAtYaw = entity.attackedAtYaw;

        resetGuiGlState();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y, 50.0F);
            GlStateManager.scale((float) (-scale), (float) scale, (float) scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableDepth();

            entity.renderYawOffset = yaw;
            baseEntity.rotationYaw = yaw;
            baseEntity.rotationPitch = 0.0F;
            entity.rotationYawHead = yaw;
            entity.prevRotationYawHead = yaw;
            entity.hurtTime = 0;
            entity.deathTime = 0;
            entity.attackedAtYaw = 0.0F;

            final RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
            float previousPlayerViewY = renderManager.playerViewY;
            try {
                renderManager.playerViewY = 180.0F;
                CosmeticPreviewTime.withAnimatedPreview(new Runnable() {
                    @Override
                    public void run() {
                        renderManager.doRenderEntity(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
                    }
                });
            } finally {
                renderManager.playerViewY = previousPlayerViewY;
            }
        } finally {
            entity.renderYawOffset = previousRenderYawOffset;
            baseEntity.rotationYaw = previousRotationYaw;
            baseEntity.rotationPitch = previousRotationPitch;
            entity.prevRotationYawHead = previousPrevRotationYawHead;
            entity.rotationYawHead = previousRotationYawHead;
            entity.hurtTime = previousHurtTime;
            entity.deathTime = previousDeathTime;
            entity.attackedAtYaw = previousAttackedAtYaw;

            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            resetGuiGlState();
        }
    }

    private void pushScissor(int x, int y, int width, int height) {
        GuiClip.push(x, y, width, height);
    }

    private void popScissor() {
        GuiClip.pop();
    }

    private void drawEntityPreview(int x, int y, int scale, EntityLivingBase entity, int mouseX, int mouseY) {
        Entity baseEntity = entity;
        float previousRenderYawOffset = entity.renderYawOffset;
        float previousRotationYaw = baseEntity.rotationYaw;
        float previousRotationPitch = baseEntity.rotationPitch;
        float previousPrevRotationYawHead = entity.prevRotationYawHead;
        float previousRotationYawHead = entity.rotationYawHead;
        int previousHurtTime = entity.hurtTime;
        int previousDeathTime = entity.deathTime;
        float previousAttackedAtYaw = entity.attackedAtYaw;
        boolean previousSuppressVanillaCapeForPreview = CosmeticSelections.instance().suppressVanillaCapeForPreview;

        resetGuiGlState();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        try {
            CosmeticSelections.instance().suppressVanillaCapeForPreview = activeTab == CosmeticTypes.CAPE;
            GlStateManager.translate((float) x, (float) y, 50.0F);
            GlStateManager.scale((float) (-scale), (float) scale, (float) scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableDepth();

            float xAngle = previewTracking ? (float) Math.atan((x - mouseX) / 40.0F) : 0.0F;
            float yAngle = previewTracking ? (float) Math.atan((y - mouseY - entity.getEyeHeight() * scale) / 40.0F) : 0.0F;
            entity.renderYawOffset = previewYaw + xAngle * 20.0F;
            baseEntity.rotationYaw = previewYaw + xAngle * 40.0F;
            baseEntity.rotationPitch = -yAngle * 20.0F;
            entity.rotationYawHead = baseEntity.rotationYaw;
            entity.prevRotationYawHead = baseEntity.rotationYaw;
            entity.hurtTime = 0;
            entity.deathTime = 0;
            entity.attackedAtYaw = 0.0F;

            final RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
            float previousPlayerViewY = renderManager.playerViewY;
            try {
                renderManager.playerViewY = 180.0F;
                Runnable render = new Runnable() {
                    @Override
                    public void run() {
                        CosmeticPreviewTime.withAnimatedPreview(new Runnable() {
                            @Override
                            public void run() {
                                renderManager.doRenderEntity(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
                            }
                        });
                    }
                };
                if (activeTab == CosmeticTypes.EMOTES && previewEmoteId != null && !previewEmoteId.isEmpty()) {
                    EmotePlayer.withPreviewPose(previewEmoteId, render);
                } else {
                    EmotePlayer.withoutPose(render);
                }
            } finally {
                renderManager.playerViewY = previousPlayerViewY;
            }
        } finally {
            CosmeticSelections.instance().suppressVanillaCapeForPreview = previousSuppressVanillaCapeForPreview;
            entity.renderYawOffset = previousRenderYawOffset;
            baseEntity.rotationYaw = previousRotationYaw;
            baseEntity.rotationPitch = previousRotationPitch;
            entity.prevRotationYawHead = previousPrevRotationYawHead;
            entity.rotationYawHead = previousRotationYawHead;
            entity.hurtTime = previousHurtTime;
            entity.deathTime = previousDeathTime;
            entity.attackedAtYaw = previousAttackedAtYaw;

            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            resetGuiGlState();
        }
    }

    private void resetGlColor() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void resetGuiGlState() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.enableTexture2D();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        resetGlColor();
    }

    private String previewStatus() {
        if (activeTab == CosmeticTypes.EMOTES) {
            return I18n.format("minetogether.gui.cosmetics.emote.favorites",
                    EmoteFavorites.ids().size(), EmoteFavorites.MAX_FAVORITES);
        }
        String id = pendingFor(activeTab);
        if (id.isEmpty()) return noneEquippedLabel(activeTab);
        if (isSelectedAssetLoaded(activeTab, id)) {
            CosmeticItem item = catalogEntry(activeTab, id);
            return I18n.format("minetogether.gui.cosmetics.equipped", item == null ? id : item.displayName());
        }
        if (CosmeticDownloader.instance().isAssetLoading(activeTab.slotName(), id)) {
            return spinnerFrame() + " " + I18n.format("minetogether.gui.cosmetics.preview.loading");
        }
        return I18n.format("minetogether.gui.cosmetics.preview.waiting");
    }

    private String noneEquippedLabel(CosmeticTypes type) {
        switch (type) {
            case CAPE:
                return I18n.format("minetogether.gui.cosmetics.cape.none_equipped");
            case TAIL:
                return I18n.format("minetogether.gui.cosmetics.tail.none_equipped");
            case WINGS:
                return I18n.format("minetogether.gui.cosmetics.wing.none_equipped");
            case EMOTES:
                return I18n.format("minetogether.gui.cosmetics.emote.none_equipped");
            case HAT:
            default:
                return I18n.format("minetogether.gui.cosmetics.none_equipped");
        }
    }

    private char spinnerFrame() {
        return SPINNER[(int) ((System.currentTimeMillis() / 150L) % SPINNER.length)];
    }

    private boolean isSelectedAssetLoaded(CosmeticTypes type, String id) {
        return isItemAssetLoaded(type, id);
    }

    private boolean isItemAssetLoaded(CosmeticTypes type, String id) {
        switch (type) {
            case HAT:
                return CosmeticDownloader.instance().getLoadedHat(id) != null;
            case CAPE:
                return CosmeticDownloader.instance().getLoadedCape(id) != null;
            case TAIL:
                return CosmeticDownloader.instance().getLoadedTail(id) != null;
            case WINGS:
                return CosmeticDownloader.instance().getLoadedWing(id) != null;
            case EMOTES:
                return CosmeticDownloader.instance().getLoadedEmote(id) != null;
            default:
                return false;
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new CosmeticsGui(), parentScreen);
        }
    }
}
