package net.creeperhost.minetogethercommunity.cosmetic;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteFavorites;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingRegistry;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.*;
import net.creeperhost.polylib.client.modulargui.lib.*;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Align;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Axis;
import net.creeperhost.polylib.client.modulargui.lib.geometry.GuiParent;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Rectangle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Locale;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class CosmeticsGui implements GuiProvider {

    private static final int CATEGORY_WIDTH = 62;
    private static final int PREVIEW_WIDTH = 150;
    private static final int GAP = 4;
    private static final int HEADER_HEIGHT = 38;
    private static final int TAB_HEIGHT = 18;
    private static final int TILE_GAP = 6;
    private static final int GRID_COLUMNS = 3;

    /** Spinner frames cycled in the preview panel while a cosmetic asset is downloading. */
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private static boolean isImplemented(CosmeticTypes type) {
        return type == CosmeticTypes.HAT || type == CosmeticTypes.CAPE || type == CosmeticTypes.TAIL || type == CosmeticTypes.WINGS || type == CosmeticTypes.EMOTES;
    }

    @Override
    public GuiElement<?> createRootElement(ModularGui gui) {
        return MTStyle.Flat.background(gui);
    }

    @Override
    public void buildGui(ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        gui.initFullscreenGui();
        gui.setGuiTitle(Component.translatable("minetogether:gui.cosmetics.title"));

        GuiElement<?> root = gui.getRoot();
        final CosmeticTypes[] activeTab = {CosmeticTypes.HAT};

        // Pending selections — updated on every list-entry click, flushed to the server on Save.
        // Initialised from the in-memory profile so the current selection is pre-highlighted.
        final String[] pendingHatId  = {CosmeticSelections.instance().selectedHatId};
        final String[] pendingCapeId = {CosmeticSelections.instance().selectedCapeId};
        final String[] pendingTailId = {CosmeticSelections.instance().selectedTailId};
        final String[] pendingWingId = {CosmeticSelections.instance().selectedWingId};
        final String[] previewEmoteId = {""};

        CosmeticDownloader d = CosmeticDownloader.instance();
        if (!pendingHatId[0].isEmpty())  d.ensureAssetLoaded("hat",  pendingHatId[0]);
        if (!pendingCapeId[0].isEmpty()) d.ensureAssetLoaded("cape", pendingCapeId[0]);
        if (!pendingTailId[0].isEmpty()) d.ensureAssetLoaded("tail", pendingTailId[0]);
        if (!pendingWingId[0].isEmpty()) d.ensureAssetLoaded("wing", pendingWingId[0]);

        GuiElement<?> container = new GuiElement<>(root)
                .constrain(TOP, match(root.get(TOP)))
                .constrain(LEFT, match(root.get(LEFT)))
                .constrain(RIGHT, match(root.get(RIGHT)))
                .constrain(BOTTOM, match(root.get(BOTTOM)));

        GuiElement<?> header = new HeaderPanel(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, match(container.get(LEFT)))
                .constrain(RIGHT, match(container.get(RIGHT)))
                .constrain(HEIGHT, literal(HEADER_HEIGHT));

        new GuiText(header, gui.getGuiTitle())
                .setAlignment(Align.LEFT)
                .setShadow(false)
                .constrain(TOP, relative(header.get(TOP), 14))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(header.get(LEFT), 8))
                .constrain(WIDTH, literal(120));

        GuiButton saveButton = MTStyle.Flat.buttonPrimary(header, Component.empty())
                .onPress(() -> {
                    String hatId  = pendingHatId[0];
                    String capeId = pendingCapeId[0];
                    String tailId = pendingTailId[0];
                    String wingId = pendingWingId[0];
                    CosmeticApiClient.selectAsync("hat",  hatId  == null || hatId.isEmpty()  ? null : hatId);
                    CosmeticApiClient.selectAsync("cape", capeId == null || capeId.isEmpty() ? null : capeId);
                    CosmeticApiClient.selectAsync("tail", tailId == null || tailId.isEmpty() ? null : tailId);
                    CosmeticApiClient.selectAsync("wing", wingId == null || wingId.isEmpty() ? null : wingId);
                });
        saveButton
                .constrain(TOP, relative(header.get(TOP), 8))
                .constrain(RIGHT, relative(header.get(RIGHT), -8))
                .constrain(WIDTH, literal(18))
                .constrain(HEIGHT, literal(18));
        Constraints.bind(new HeaderIcon(saveButton, HeaderIcon.Type.SAVE, 0xFFFFFFFF), saveButton);

        GuiButton backButton = MTStyle.Flat.button(header, Component.empty())
                .onPress(() -> gui.mc().setScreen(gui.getParentScreen()))
                .constrain(TOP, relative(header.get(TOP), 8))
                .constrain(RIGHT, relative(header.get(RIGHT), -32))
                .constrain(WIDTH, literal(18))
                .constrain(HEIGHT, literal(18));
        Constraints.bind(new HeaderIcon(backButton, HeaderIcon.Type.BACK, 0xFFEAEAEA), backButton);

        // ── Far left: vertical category sidebar ──────────────────────────────

        GuiElement<?> categoryPanel = new SidebarPanel(container)
                .constrain(TOP, relative(header.get(BOTTOM), 0))
                .constrain(LEFT, match(container.get(LEFT)))
                .constrain(WIDTH, literal(CATEGORY_WIDTH))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        CosmeticTypes[] allTypes = CosmeticTypes.values();
        int tabIndex = 0;
        for (CosmeticTypes type : allTypes) {
            boolean implemented = isImplemented(type);
            if (!implemented) continue;
            int topOffset = 8 + tabIndex++ * (TAB_HEIGHT + 3);
            String tabKey = "minetogether:gui.cosmetics.tab." + type.name().toLowerCase();
            GuiButton.flatColourButton(
                            categoryPanel,
                            () -> Component.translatable(tabKey),
                            isHovered -> {
                                if (activeTab[0] == type) return isHovered ? 0xFF2B5F94 : 0xFF17456F;
                                return isHovered ? 0xFF242424 : 0x00151515;
                            })
                    .onPress(() -> activeTab[0] = type)
                    .constrain(TOP, relative(categoryPanel.get(TOP), topOffset))
                    .constrain(LEFT, relative(categoryPanel.get(LEFT), 4))
                    .constrain(RIGHT, relative(categoryPanel.get(RIGHT), -4))
                    .constrain(HEIGHT, literal(TAB_HEIGHT));
        }

        // ── Centre: list panel ────────────────────────────────────────────────

        GuiElement<?> listPanel = new CatalogPanel(container)
                .constrain(TOP, relative(header.get(BOTTOM), 0))
                .constrain(LEFT, relative(categoryPanel.get(RIGHT), GAP))
                .constrain(RIGHT, relative(container.get(RIGHT), -(PREVIEW_WIDTH + GAP)))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        // Loading indicator — visible while the catalog is still being fetched
        new GuiText(listPanel, () ->
                CosmeticDownloader.instance().isCatalogLoading()
                        ? Component.translatable("minetogether:gui.cosmetics.loading").withStyle(ChatFormatting.YELLOW)
                        : Component.empty())
                .setShadow(false)
                .setAlignment(Align.LEFT)
                .constrain(BOTTOM, relative(listPanel.get(BOTTOM), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(listPanel.get(LEFT), 6))
                .constrain(RIGHT, relative(listPanel.get(RIGHT), -6));

        // Search bar
        final String[] searchQuery = {""};

        GuiRectangle searchBg = new GuiRectangle(listPanel)
                .fill(0xA0202020)
                .constrain(TOP, relative(listPanel.get(TOP), 8))
                .constrain(LEFT, relative(listPanel.get(LEFT), 8))
                .constrain(RIGHT, relative(listPanel.get(RIGHT), -8))
                .constrain(HEIGHT, literal(18));

        GuiTextField searchField = new GuiTextField(searchBg)
                .setTextState(TextState.simpleState("", s -> searchQuery[0] = s))
                .setSuggestion(Component.translatable("minetogether:gui.cosmetics.search.suggestion"));
        Constraints.bind(searchField, searchBg, 1, 5, 1, 5);

        GuiElement<?> listArea = new GuiElement<>(listPanel)
                .constrain(TOP, relative(searchBg.get(BOTTOM), 10))
                .constrain(LEFT, match(listPanel.get(LEFT)))
                .constrain(RIGHT, match(listPanel.get(RIGHT)))
                .constrain(BOTTOM, relative(listPanel.get(BOTTOM), -14));

        GuiList<CosmeticRow> catalogGrid = new GuiList<CosmeticRow>(listArea)
                .setDisplayBuilder((parent, row) -> new CosmeticGridRow(parent, row, activeTab, pendingHatId, pendingCapeId, pendingTailId, pendingWingId, previewEmoteId))
                .setItemSpacing(TILE_GAP);
        catalogGrid
                .constrain(TOP, relative(listArea.get(TOP), 0))
                .constrain(BOTTOM, match(listArea.get(BOTTOM)))
                .constrain(LEFT, relative(listArea.get(LEFT), 8))
                .constrain(RIGHT, relative(listArea.get(RIGHT), -14));

        var catalogScrollBar = MTStyle.Flat.scrollBar(listArea, Axis.Y);
        catalogScrollBar.container
                .setEnabled(() -> catalogGrid.hiddenSize() > 0)
                .constrain(TOP, match(catalogGrid.get(TOP)))
                .constrain(BOTTOM, match(catalogGrid.get(BOTTOM)))
                .constrain(RIGHT, relative(listArea.get(RIGHT), -5))
                .constrain(WIDTH, literal(5));
        catalogScrollBar.primary
                .setScrollableElement(catalogGrid)
                .setSliderState(catalogGrid.scrollState());

        // ── Right: player preview panel ───────────────────────────────────────

        GuiElement<?> previewPanel = new PreviewPanel(container)
                .constrain(TOP, relative(header.get(BOTTOM), 0))
                .constrain(LEFT, relative(listPanel.get(RIGHT), GAP))
                .constrain(RIGHT, match(container.get(RIGHT)))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        final boolean[] trackingEnabled = {true};

        new GuiText(previewPanel, () -> currentEquippedName(activeTab[0], pendingHatId[0], pendingCapeId[0], pendingTailId[0], pendingWingId[0]))
                .setShadow(false)
                .setAlignment(Align.LEFT)
                .constrain(TOP, relative(previewPanel.get(TOP), 12))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 8))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -34));

        MTStyle.Flat.button(previewPanel, () -> Component.literal("T").withStyle(trackingEnabled[0] ? ChatFormatting.GREEN : ChatFormatting.RED))
                .onPress(() -> trackingEnabled[0] = !trackingEnabled[0])
                .constrain(TOP, relative(previewPanel.get(TOP), 7))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -8))
                .constrain(WIDTH, literal(18))
                .constrain(HEIGHT, literal(18));

        final float[] previewRotation = {-20.0F};

        new OffsetFollowRenderer(previewPanel, Minecraft.getInstance().player, activeTab, previewEmoteId, previewRotation, trackingEnabled)
                .constrain(TOP, relative(previewPanel.get(TOP), 42))
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -48))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 8))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -8));

        MTStyle.Flat.button(previewPanel, Component.literal("Front"))
                .onPress(() -> previewRotation[0] = -20.0F)
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -26))
                .constrain(LEFT, midPoint(previewPanel.get(LEFT), previewPanel.get(RIGHT), -43))
                .constrain(WIDTH, literal(40))
                .constrain(HEIGHT, literal(18));

        MTStyle.Flat.button(previewPanel, Component.literal("Back"))
                .onPress(() -> previewRotation[0] = 160.0F)
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -26))
                .constrain(LEFT, midPoint(previewPanel.get(LEFT), previewPanel.get(RIGHT), 3))
                .constrain(WIDTH, literal(40))
                .constrain(HEIGHT, literal(18));

        // Status / spinner text at the bottom of the preview panel.
        // Shows a loading spinner while the selected cosmetic's asset is downloading,
        // then the "equipped" name once ready (or "none" when nothing is selected).
        new GuiText(previewPanel, () -> {
            if (activeTab[0] == CosmeticTypes.HAT) {
                String id = pendingHatId[0];
                if (id == null || id.isEmpty())
                    return Component.translatable("minetogether:gui.cosmetics.none_equipped")
                            .withStyle(ChatFormatting.GRAY);
                // Asset not yet available — show spinner
                if (CosmeticDownloader.instance().getLoadedHat(id) == null) {
                    int frame = (int) ((System.currentTimeMillis() / 150) % SPINNER.length);
                    return Component.literal(SPINNER[frame] + " Loading...")
                            .withStyle(ChatFormatting.YELLOW);
                }
                CosmeticItem item = HatRegistry.getCatalogEntry(id);
                String name = item != null ? item.displayName() : id;
                return Component.translatable("minetogether:gui.cosmetics.equipped",
                        Component.literal(name).withStyle(ChatFormatting.GREEN));

            } else if (activeTab[0] == CosmeticTypes.CAPE) {
                String id = pendingCapeId[0];
                if (id == null || id.isEmpty())
                    return Component.translatable("minetogether:gui.cosmetics.cape.none_equipped")
                            .withStyle(ChatFormatting.GRAY);
                // Asset not yet available — show spinner
                if (CosmeticDownloader.instance().getLoadedCape(id) == null) {
                    int frame = (int) ((System.currentTimeMillis() / 150) % SPINNER.length);
                    return Component.literal(SPINNER[frame] + " Loading...")
                            .withStyle(ChatFormatting.YELLOW);
                }
                CosmeticItem item = CapeRegistry.getCatalogEntry(id);
                String name = item != null ? item.displayName() : id;
                return Component.translatable("minetogether:gui.cosmetics.equipped",
                        Component.literal(name).withStyle(ChatFormatting.GREEN));

            } else if (activeTab[0] == CosmeticTypes.TAIL) {
                String id = pendingTailId[0];
                if (id == null || id.isEmpty())
                    return Component.translatable("minetogether:gui.cosmetics.tail.none_equipped")
                            .withStyle(ChatFormatting.GRAY);
                if (CosmeticDownloader.instance().getLoadedTail(id) == null) {
                    int frame = (int) ((System.currentTimeMillis() / 150) % SPINNER.length);
                    return Component.literal(SPINNER[frame] + " Loading...")
                            .withStyle(ChatFormatting.YELLOW);
                }
                CosmeticItem item = TailRegistry.getCatalogEntry(id);
                String name = item != null ? item.displayName() : id;
                return Component.translatable("minetogether:gui.cosmetics.equipped",
                        Component.literal(name).withStyle(ChatFormatting.GREEN));

            } else if (activeTab[0] == CosmeticTypes.WINGS) {
                String id = pendingWingId[0];
                if (id == null || id.isEmpty())
                    return Component.translatable("minetogether:gui.cosmetics.wing.none_equipped")
                            .withStyle(ChatFormatting.GRAY);
                if (CosmeticDownloader.instance().getLoadedWing(id) == null) {
                    int frame = (int) ((System.currentTimeMillis() / 150) % SPINNER.length);
                    return Component.literal(SPINNER[frame] + " Loading...")
                            .withStyle(ChatFormatting.YELLOW);
                }
                CosmeticItem item = WingRegistry.getCatalogEntry(id);
                String name = item != null ? item.displayName() : id;
                return Component.translatable("minetogether:gui.cosmetics.equipped",
                        Component.literal(name).withStyle(ChatFormatting.GREEN));

            } else if (activeTab[0] == CosmeticTypes.EMOTES) {
                return Component.literal("Radial favorites: " + EmoteFavorites.ids().size() + "/" + EmoteFavorites.MAX_FAVORITES)
                        .withStyle(ChatFormatting.GRAY);
            } else {
                return Component.translatable("minetogether:gui.cosmetics.coming_soon")
                        .withStyle(ChatFormatting.GRAY);
            }
        })
                .setShadow(false)
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -6))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 12))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -12));

        // Start the catalog fetch (idempotent — no-op if already started by the world-join hook).
        CosmeticDownloader.instance().startCatalogFetch();
        CosmeticApiClient.fetchProfileAsync();

        final int[] lastSizes = {0, 0, 0, 0, 0};
        final String[] lastQuery = {""};
        final CosmeticTypes[] lastType = {null};
        gui.onTick(() -> {
            String q = searchQuery[0].toLowerCase(Locale.ROOT);
            boolean queryChanged = !q.equals(lastQuery[0]);
            if (queryChanged) lastQuery[0] = q;

            List<CosmeticItem> availableHats = HatRegistry.catalog();
            List<CosmeticItem> availableCapes = CapeRegistry.catalog();
            List<CosmeticItem> availableTails = TailRegistry.catalog();
            List<CosmeticItem> availableWings = WingRegistry.catalog();
            List<CosmeticItem> availableEmotes = CosmeticDownloader.instance().getEmoteCatalog();
            boolean sizeChanged = availableHats.size() != lastSizes[0]
                    || availableCapes.size() != lastSizes[1]
                    || availableTails.size() != lastSizes[2]
                    || availableWings.size() != lastSizes[3]
                    || availableEmotes.size() != lastSizes[4];
            boolean typeChanged = activeTab[0] != lastType[0];

            if (sizeChanged || queryChanged || typeChanged) {
                lastSizes[0] = availableHats.size();
                lastSizes[1] = availableCapes.size();
                lastSizes[2] = availableTails.size();
                lastSizes[3] = availableWings.size();
                lastSizes[4] = availableEmotes.size();
                lastType[0] = activeTab[0];

                List<CosmeticItem> activeItems = switch (activeTab[0]) {
                    case HAT -> availableHats;
                    case CAPE -> availableCapes;
                    case TAIL -> availableTails;
                    case WINGS -> availableWings;
                    case EMOTES -> availableEmotes;
                    default -> List.of();
                };
                List<CosmeticItem> filtered = activeItems.stream()
                        .filter(item -> q.isEmpty() || item.displayName().toLowerCase(Locale.ROOT).contains(q))
                        .toList();
                filtered.forEach(item -> ensureAssetLoaded(activeTab[0], item.id()));
                catalogGrid.getList().clear();
                addRows(catalogGrid.getList(), filtered, activeTab[0] != CosmeticTypes.EMOTES);
                catalogGrid.markDirty();
            }
        });
    }

    // ── List entry inner classes ───────────────────────────────────────────────

    private static void addRows(List<CosmeticRow> rows, List<CosmeticItem> items, boolean includeNone) {
        int start = 0;
        if (includeNone) {
            rows.add(new CosmeticRow(null, items.isEmpty() ? null : items.get(0), items.size() > 1 ? items.get(1) : null));
            start = 2;
        }
        for (int i = start; i < items.size(); i += GRID_COLUMNS) {
            rows.add(new CosmeticRow(
                    items.get(i),
                    i + 1 < items.size() ? items.get(i + 1) : null,
                    i + 2 < items.size() ? items.get(i + 2) : null));
        }
    }

    private static Component currentEquippedName(CosmeticTypes type, String hatId, String capeId, String tailId, String wingId) {
        String id = switch (type) {
            case HAT -> hatId;
            case CAPE -> capeId;
            case TAIL -> tailId;
            case WINGS -> wingId;
            case EMOTES -> "";
            default -> "";
        };
        if (type == CosmeticTypes.EMOTES) {
            return Component.translatable("minetogether:gui.cosmetics.emote.hint").withStyle(ChatFormatting.GRAY);
        }
        if (id == null || id.isEmpty()) return Component.literal("Feeling Cute").withStyle(ChatFormatting.GRAY);
        CosmeticItem item = switch (type) {
            case HAT -> HatRegistry.getCatalogEntry(id);
            case CAPE -> CapeRegistry.getCatalogEntry(id);
            case TAIL -> TailRegistry.getCatalogEntry(id);
            case WINGS -> WingRegistry.getCatalogEntry(id);
            case EMOTES -> null;
            default -> null;
        };
        return Component.literal(item != null ? item.displayName() : id);
    }

    private static void ensureAssetLoaded(CosmeticTypes type, String id) {
        if (id == null || id.isEmpty()) return;
        switch (type) {
            case HAT -> CosmeticDownloader.instance().ensureAssetLoaded("hat", id);
            case CAPE -> CosmeticDownloader.instance().ensureAssetLoaded("cape", id);
            case TAIL -> CosmeticDownloader.instance().ensureAssetLoaded("tail", id);
            case WINGS -> CosmeticDownloader.instance().ensureAssetLoaded("wing", id);
            case EMOTES -> CosmeticDownloader.instance().ensureAssetLoaded("emote", id);
            default -> {
            }
        }
    }

    private record CosmeticRow(CosmeticItem first, CosmeticItem second, CosmeticItem third) {
        CosmeticItem item(int index) {
            return switch (index) {
                case 0 -> first;
                case 1 -> second;
                case 2 -> third;
                default -> null;
            };
        }
    }

    private static class PanelElement<T extends PanelElement<T>> extends GuiElement<T> implements BackgroundRender {
        private final int fill;
        private final int border;

        public PanelElement(@NotNull GuiParent<?> parent, int fill, int border) {
            super(parent);
            this.fill = fill;
            this.border = border;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), fill);
            render.borderRect(getRectangle(), 1, 0x00000000, border);
        }
    }

    private static class HeaderPanel extends PanelElement<HeaderPanel> {
        public HeaderPanel(@NotNull GuiParent<?> parent) {
            super(parent, 0xF0202020, 0xFF2B2B2B);
        }
    }

    private static class SidebarPanel extends PanelElement<SidebarPanel> {
        public SidebarPanel(@NotNull GuiParent<?> parent) {
            super(parent, 0xD9141414, 0xFF242424);
        }
    }

    private static class CatalogPanel extends PanelElement<CatalogPanel> {
        public CatalogPanel(@NotNull GuiParent<?> parent) {
            super(parent, 0xD91A1A1A, 0xFF242424);
        }
    }

    private static class PreviewPanel extends PanelElement<PreviewPanel> {
        public PreviewPanel(@NotNull GuiParent<?> parent) {
            super(parent, 0xE0101010, 0xFF242424);
        }
    }

    private static class HeaderIcon extends GuiElement<HeaderIcon> implements ForegroundRender {
        private final Type type;
        private final int color;

        public HeaderIcon(@NotNull GuiParent<?> parent, Type type, int color) {
            super(parent);
            this.type = type;
            this.color = color;
        }

        @Override
        public void renderInFront(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            double x = xMin();
            double y = yMin();
            double w = xSize();
            double h = ySize();
            double cx = x + w / 2.0D;
            double cy = y + h / 2.0D;
            if (type == Type.BACK) {
                drawBackArrow(render, cx, cy);
            } else {
                drawSave(render, cx, cy);
            }
        }

        private void drawBackArrow(GuiRender render, double cx, double cy) {
            double x = Math.floor(cx - 4.0D);
            double y = Math.floor(cy - 5.0D);
            render.rect(x + 3, y, 2, 2, color);
            render.rect(x + 2, y + 2, 2, 2, color);
            render.rect(x + 1, y + 4, 8, 2, color);
            render.rect(x + 2, y + 6, 2, 2, color);
            render.rect(x + 3, y + 8, 2, 2, color);
        }

        private void drawSave(GuiRender render, double cx, double cy) {
            double x = Math.floor(cx - 5.0D);
            double y = Math.floor(cy - 5.0D);
            render.rect(x, y, 10, 10, color);
            render.rect(x + 1, y + 1, 8, 8, 0xFF166B22);
            render.rect(x + 2, y + 1, 4, 3, color);
            render.rect(x + 7, y + 1, 1, 3, 0xFF0D3F15);
            render.rect(x + 2, y + 6, 6, 3, color);
            render.rect(x + 3, y + 7, 4, 2, 0xFF166B22);
        }

        private enum Type {
            BACK,
            SAVE
        }
    }

    private static class CosmeticGridRow extends GuiElement<CosmeticGridRow> implements BackgroundRender, ForegroundRender {
        private final CosmeticRow row;
        private final CosmeticTypes[] activeTab;
        private final String[] pendingHatId;
        private final String[] pendingCapeId;
        private final String[] pendingTailId;
        private final String[] pendingWingId;
        private final String[] previewEmoteId;

        public CosmeticGridRow(@NotNull GuiParent<?> parent, CosmeticRow row, CosmeticTypes[] activeTab,
                               String[] pendingHatId, String[] pendingCapeId, String[] pendingTailId, String[] pendingWingId,
                               String[] previewEmoteId) {
            super(parent);
            this.row = row;
            this.activeTab = activeTab;
            this.pendingHatId = pendingHatId;
            this.pendingCapeId = pendingCapeId;
            this.pendingTailId = pendingTailId;
            this.pendingWingId = pendingWingId;
            this.previewEmoteId = previewEmoteId;
            this.constrain(HEIGHT, literal(88));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver()) return false;
            for (int i = 0; i < GRID_COLUMNS; i++) {
                CosmeticItem item = row.item(i);
                if (mouseX >= tileX(i) && mouseX <= tileX(i) + tileWidth() && mouseY >= yMin() && mouseY <= yMax()) {
                    if (activeTab[0] == CosmeticTypes.EMOTES && item != null && !item.locked() && isFavoriteToggleClick(mouseX, mouseY, i)) {
                        EmoteFavorites.toggle(item.id());
                        return true;
                    }
                    select(item);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            for (int i = 0; i < GRID_COLUMNS; i++) {
                CosmeticItem item = row.item(i);
                if (i > 0 && item == null) continue;
                double x = tileX(i);
                double w = tileWidth();
                boolean selected = isSelected(item);
                boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= yMin() && mouseY <= yMax();
                render.rect(x, yMin(), w, ySize(), item == null ? 0xFF202020 : hover ? 0xFF1F2A30 : 0xFF172026);
                render.rect(x + 4, yMin() + 22, w - 8, ySize() - 28, item == null ? 0xFF151515 : 0xFF102030);
                if (activeTab[0] == CosmeticTypes.EMOTES) {
                    renderEmotePreview(render, item, x + 4, yMin() + 22, w - 8, ySize() - 28);
                } else {
                    renderCardPreview(render, item, x + 4, yMin() + 22, w - 8, ySize() - 28);
                }
                render.rect(x + 4, yMax() - 3, w - 8, 2, selected ? 0xFF00AA33 : 0xFF005A9C);
                if (selected) {
                    render.rect(x, yMin(), w, 1, 0xFFE0E0E0);
                    render.rect(x, yMax() - 1, w, 1, 0xFFE0E0E0);
                    render.rect(x, yMin(), 1, ySize(), 0xFFE0E0E0);
                    render.rect(x + w - 1, yMin(), 1, ySize(), 0xFFE0E0E0);
                }
            }
        }

        @Override
        public void renderInFront(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            for (int i = 0; i < GRID_COLUMNS; i++) {
                CosmeticItem item = row.item(i);
                if (i > 0 && item == null) continue;
                double x = tileX(i);
                int textColor = item == null ? 0xFFE0E0E0 : item.locked() ? 0xFF777777 : 0xFFFFFFFF;
                drawCardTitle(render, displayName(item), x + 6, yMin() + 8, tileWidth() - 12, textColor);
                if (item != null && !item.locked() && isSelected(item)) {
                    render.drawString(Component.literal("v").withStyle(ChatFormatting.GREEN).getVisualOrderText(), x + tileWidth() - 14, yMax() - 14, 0xFF00FF55);
                }
                if (item != null && !item.locked() && activeTab[0] == CosmeticTypes.EMOTES) {
                    boolean favorite = EmoteFavorites.isFavorite(item.id());
                    double actionY = yMax() - 18;
                    render.rect(x + 5, actionY, 31, 14, 0xFF1F4A2A);
                    render.drawString(Component.literal("Play").withStyle(ChatFormatting.GREEN).getVisualOrderText(), x + 9, actionY + 3, 0xFF44FF66);

                    String favoriteText = favorite ? "Fav" : "+";
                    int favoriteColor = favorite ? 0xFFFFD94A : EmoteFavorites.canAddMore() ? 0xFFAAAAAA : 0xFF666666;
                    double favoriteX = x + tileWidth() - 30;
                    render.rect(favoriteX, actionY, 25, 14, favorite ? 0xFF4A3E16 : 0xFF2B2B2B);
                    render.drawString(Component.literal(favoriteText).getVisualOrderText(), favoriteX + (25 - render.font().width(favoriteText)) / 2.0D, actionY + 3, favoriteColor);
                }
                if (item != null && item.locked()) {
                    render.drawString(Component.literal("Locked").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText(), x + 6, yMax() - 14, 0xFF777777);
                }
            }
        }

        private void drawCardTitle(GuiRender render, String title, double x, double y, double width, int color) {
            if (render.font().width(title) <= width) {
                render.drawString(title, x, y, color);
                return;
            }

            render.pushScissorRect(x, y - 1, width, render.font().lineHeight + 2);
            try {
                render.drawScrollingString(Component.literal(title), x, y, x + width, color, false);
            } finally {
                render.popScissor();
            }
        }

        private void renderCardPreview(GuiRender render, CosmeticItem item, double x, double y, double width, double height) {
            if (item == null || item.locked() || !isLoaded(item)) return;

            LivingEntity entity = Minecraft.getInstance().player;
            if (entity == null) return;

            CosmeticSelections selections = CosmeticSelections.instance();
            String previousHat = selections.selectedHatId;
            String previousCape = selections.selectedCapeId;
            String previousTail = selections.selectedTailId;
            String previousWing = selections.selectedWingId;
            boolean previousSuppressVanillaCapeForPreview = selections.suppressVanillaCapeForPreview;
            boolean previousFullBrightPreview = selections.fullBrightPreview;

            float prevBodyRot = entity.yBodyRot;
            float prevYRot = entity.getYRot();
            float prevXRot = entity.getXRot();
            float prevHeadRotO = entity.yHeadRotO;
            float prevHeadRot = entity.yHeadRot;

            try {
                selections.selectedHatId = "";
                selections.selectedCapeId = "";
                selections.selectedTailId = "";
                selections.selectedWingId = "";
                selections.suppressVanillaCapeForPreview = true;
                selections.fullBrightPreview = true;
                switch (activeTab[0]) {
                    case HAT -> selections.selectedHatId = item.id();
                    case CAPE -> selections.selectedCapeId = item.id();
                    case TAIL -> selections.selectedTailId = item.id();
                    case WINGS -> selections.selectedWingId = item.id();
                    default -> {
                        return;
                    }
                }

                float cardYaw = switch (activeTab[0]) {
                    case HAT -> 155.0F;
                    case CAPE, TAIL, WINGS -> 35.0F;
                    default -> 180.0F;
                };
                entity.yBodyRot = cardYaw;
                entity.setYRot(entity.yBodyRot);
                entity.setXRot(0.0F);
                entity.yHeadRot = entity.getYRot();
                entity.yHeadRotO = entity.getYRot();

                float scale = Math.min((float) (height / entity.getBbHeight()) * 1.45F, 46.0F);
                float xPos = (float) (x + (width / 2D));
                float yOffset = switch (activeTab[0]) {
                    case HAT -> 1.30F;
                    case TAIL, WINGS -> -0.05F;
                    default -> 0.20F;
                };
                float yPos = (float) (y + height + scale * yOffset);
                Quaternionf entityRotation = new Quaternionf().rotateZ((float) Math.PI);
                Quaternionf cameraRotation = new Quaternionf();

                render.pushScissorRect(x, y, width, height);
                try {
                    renderBrightEntityInInventory(render, xPos, yPos, scale, entityRotation, cameraRotation, entity);
                } finally {
                    render.popScissor();
                }
            } finally {
                Lighting.setupFor3DItems();
                selections.selectedHatId = previousHat;
                selections.selectedCapeId = previousCape;
                selections.selectedTailId = previousTail;
                selections.selectedWingId = previousWing;
                selections.suppressVanillaCapeForPreview = previousSuppressVanillaCapeForPreview;
                selections.fullBrightPreview = previousFullBrightPreview;
                entity.yBodyRot = prevBodyRot;
                entity.setYRot(prevYRot);
                entity.setXRot(prevXRot);
                entity.yHeadRotO = prevHeadRotO;
                entity.yHeadRot = prevHeadRot;
            }
        }

        private boolean isLoaded(CosmeticItem item) {
            return switch (activeTab[0]) {
                case HAT -> HatRegistry.getLoaded(item.id()) != null;
                case CAPE -> CapeRegistry.getLoaded(item.id()) != null;
                case TAIL -> TailRegistry.getLoaded(item.id()) != null;
                case WINGS -> WingRegistry.getLoaded(item.id()) != null;
                case EMOTES -> EmoteRegistry.getLoaded(item.id()) != null;
                default -> false;
            };
        }

        private void renderEmotePreview(GuiRender render, CosmeticItem item, double x, double y, double width, double height) {
            if (item == null || item.locked()) return;
            boolean loaded = EmoteRegistry.getLoaded(item.id()) != null;
            boolean favorite = EmoteFavorites.isFavorite(item.id());
            render.drawString(Component.literal(loaded ? "Ready" : "Loading").withStyle(loaded ? ChatFormatting.AQUA : ChatFormatting.YELLOW).getVisualOrderText(), x + 6, y + 9, loaded ? 0xFF66DDEE : 0xFFFFDD55);
            render.drawString(Component.literal(favorite ? "Radial" : "Add").getVisualOrderText(), x + 6, y + 23, favorite ? 0xFFFFD94A : 0xFFAAAAAA);
        }

        private void select(CosmeticItem item) {
            if (item != null && item.locked()) return;
            String id = item == null ? "" : item.id();
            switch (activeTab[0]) {
                case HAT -> {
                    pendingHatId[0] = id;
                    CosmeticSelections.instance().selectedHatId = id;
                    if (!id.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("hat", id);
                }
                case CAPE -> {
                    pendingCapeId[0] = id;
                    CosmeticSelections.instance().selectedCapeId = id;
                    if (!id.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("cape", id);
                }
                case TAIL -> {
                    pendingTailId[0] = id;
                    CosmeticSelections.instance().selectedTailId = id;
                    if (!id.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("tail", id);
                }
                case WINGS -> {
                    pendingWingId[0] = id;
                    CosmeticSelections.instance().selectedWingId = id;
                    if (!id.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("wing", id);
                }
                case EMOTES -> {
                    if (!id.isEmpty()) {
                        previewEmoteId[0] = id;
                        CosmeticDownloader.instance().ensureAssetLoaded("emote", id);
                        EmotePlayer.playLocal(id);
                    }
                }
                default -> {
                }
            }
        }

        private boolean isSelected(CosmeticItem item) {
            String selected = switch (activeTab[0]) {
                case HAT -> pendingHatId[0];
                case CAPE -> pendingCapeId[0];
                case TAIL -> pendingTailId[0];
                case WINGS -> pendingWingId[0];
                case EMOTES -> "";
                default -> "";
            };
            if (activeTab[0] == CosmeticTypes.EMOTES) return false;
            if (item == null) return selected == null || selected.isEmpty();
            return item.id().equals(selected);
        }

        private boolean isFavoriteToggleClick(double mouseX, double mouseY, int index) {
            double x = tileX(index);
            double width = tileWidth();
            return mouseX >= x + width - 34 && mouseX <= x + width - 4 && mouseY >= yMax() - 20 && mouseY <= yMax();
        }

        private String displayName(CosmeticItem item) {
            if (item == null) {
                return switch (activeTab[0]) {
                    case HAT -> "None";
                    case CAPE -> "No Cape";
                    case TAIL -> "No Tail";
                    case WINGS -> "No Wings";
                    case EMOTES -> "No Emote";
                    default -> "None";
                };
            }
            return item.displayName();
        }

        private double tileWidth() {
            return (xSize() - (TILE_GAP * (GRID_COLUMNS - 1))) / GRID_COLUMNS;
        }

        private double tileX(int index) {
            return xMin() + index * (tileWidth() + TILE_GAP);
        }
    }

    private static class NoneEntry extends GuiElement<NoneEntry> implements BackgroundRender {

        private final String[] pendingId;

        public NoneEntry(@NotNull GuiParent<?> parent, String[] pendingId) {
            super(parent);
            this.pendingId = pendingId;
            this.constrain(HEIGHT, literal(20));

            new GuiText(this, () -> Component.translatable("minetogether:gui.cosmetics.hat.none")
                    .withStyle(isNoneSelected() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver()) return false;
            pendingId[0] = "";
            CosmeticSelections.instance().selectedHatId = "";
            return true;
        }

        private boolean isNoneSelected() {
            String id = pendingId[0];
            return id == null || id.isEmpty();
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (isNoneSelected()) {
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
            }
        }
    }

    private static class HatEntry extends GuiElement<HatEntry> implements BackgroundRender {

        private final CosmeticItem item;
        private final String[] pendingId;

        public HatEntry(@NotNull GuiParent<?> parent, CosmeticItem item, String[] pendingId) {
            super(parent);
            this.item = item;
            this.pendingId = pendingId;
            String subtitle = item.locked()
                    ? (item.howToUnlock() != null ? item.howToUnlock() : "Locked")
                    : buildSubtitle(item.author(), item.mod());
            boolean hasSubtitle = !subtitle.isEmpty();
            this.constrain(HEIGHT, literal(hasSubtitle ? 28 : 20));

            new GuiText(this, () -> {
                if (item.locked()) return Component.literal(item.displayName()).withStyle(ChatFormatting.DARK_GRAY);
                boolean selected = item.id().equals(pendingId[0]);
                return Component.literal(item.displayName())
                        .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            })
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), hasSubtitle ? 4 : 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));

            if (hasSubtitle) {
                new GuiText(this, Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
                        .setAlignment(Align.LEFT)
                        .setShadow(false)
                        .constrain(TOP, relative(get(TOP), 14))
                        .constrain(LEFT, relative(get(LEFT), 6))
                        .constrain(RIGHT, relative(get(RIGHT), -6))
                        .constrain(HEIGHT, literal(7));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver() || item.locked()) return false;
            pendingId[0] = item.id();
            CosmeticSelections.instance().selectedHatId = item.id();
            // Kick off asset download so the hat renders on the player immediately
            CosmeticDownloader.instance().ensureAssetLoaded("hat", item.id());
            return true;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (!item.locked() && item.id().equals(pendingId[0])) {
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
            }
        }
    }

    private static class NoCapeEntry extends GuiElement<NoCapeEntry> implements BackgroundRender {

        private final String[] pendingId;

        public NoCapeEntry(@NotNull GuiParent<?> parent, String[] pendingId) {
            super(parent);
            this.pendingId = pendingId;
            this.constrain(HEIGHT, literal(20));

            new GuiText(this, () -> Component.translatable("minetogether:gui.cosmetics.cape.none")
                    .withStyle(isNoneSelected() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver()) return false;
            pendingId[0] = "";
            CosmeticSelections.instance().selectedCapeId = "";
            return true;
        }

        private boolean isNoneSelected() {
            String id = pendingId[0];
            return id == null || id.isEmpty();
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (isNoneSelected()) {
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
            }
        }
    }

    private static class CapeEntry extends GuiElement<CapeEntry> implements BackgroundRender {

        private final CosmeticItem item;
        private final String[] pendingId;

        public CapeEntry(@NotNull GuiParent<?> parent, CosmeticItem item, String[] pendingId) {
            super(parent);
            this.item = item;
            this.pendingId = pendingId;
            String subtitle = item.locked()
                    ? (item.howToUnlock() != null ? item.howToUnlock() : "Locked")
                    : buildSubtitle(item.author(), item.mod());
            boolean hasSubtitle = !subtitle.isEmpty();
            this.constrain(HEIGHT, literal(hasSubtitle ? 28 : 20));

            new GuiText(this, () -> {
                if (item.locked()) return Component.literal(item.displayName()).withStyle(ChatFormatting.DARK_GRAY);
                boolean selected = item.id().equals(pendingId[0]);
                return Component.literal(item.displayName())
                        .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            })
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), hasSubtitle ? 4 : 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));

            if (hasSubtitle) {
                new GuiText(this, Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
                        .setAlignment(Align.LEFT)
                        .setShadow(false)
                        .constrain(TOP, relative(get(TOP), 14))
                        .constrain(LEFT, relative(get(LEFT), 6))
                        .constrain(RIGHT, relative(get(RIGHT), -6))
                        .constrain(HEIGHT, literal(7));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver() || item.locked()) return false;
            pendingId[0] = item.id();
            CosmeticSelections.instance().selectedCapeId = item.id();
            // Kick off asset download so the cape renders on the player immediately
            CosmeticDownloader.instance().ensureAssetLoaded("cape", item.id());
            return true;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (!item.locked() && item.id().equals(pendingId[0])) {
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
            }
        }
    }

    private static class NoTailEntry extends GuiElement<NoTailEntry> implements BackgroundRender {

        private final String[] pendingId;

        public NoTailEntry(@NotNull GuiParent<?> parent, String[] pendingId) {
            super(parent);
            this.pendingId = pendingId;
            this.constrain(HEIGHT, literal(20));

            new GuiText(this, () -> Component.translatable("minetogether:gui.cosmetics.tail.none")
                    .withStyle(isNoneSelected() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver()) return false;
            pendingId[0] = "";
            CosmeticSelections.instance().selectedTailId = "";
            return true;
        }

        private boolean isNoneSelected() {
            String id = pendingId[0];
            return id == null || id.isEmpty();
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (isNoneSelected()) render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
        }
    }

    private static class TailEntry extends GuiElement<TailEntry> implements BackgroundRender {

        private final CosmeticItem item;
        private final String[] pendingId;

        public TailEntry(@NotNull GuiParent<?> parent, CosmeticItem item, String[] pendingId) {
            super(parent);
            this.item = item;
            this.pendingId = pendingId;
            String subtitle = item.locked()
                    ? (item.howToUnlock() != null ? item.howToUnlock() : "Locked")
                    : buildSubtitle(item.author(), item.mod());
            boolean hasSubtitle = !subtitle.isEmpty();
            this.constrain(HEIGHT, literal(hasSubtitle ? 28 : 20));

            new GuiText(this, () -> {
                if (item.locked()) return Component.literal(item.displayName()).withStyle(ChatFormatting.DARK_GRAY);
                boolean selected = item.id().equals(pendingId[0]);
                return Component.literal(item.displayName())
                        .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            })
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), hasSubtitle ? 4 : 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -6))
                    .constrain(HEIGHT, literal(8));

            if (hasSubtitle) {
                new GuiText(this, Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
                        .setAlignment(Align.LEFT)
                        .setShadow(false)
                        .constrain(TOP, relative(get(TOP), 14))
                        .constrain(LEFT, relative(get(LEFT), 6))
                        .constrain(RIGHT, relative(get(RIGHT), -6))
                        .constrain(HEIGHT, literal(7));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver() || item.locked()) return false;
            pendingId[0] = item.id();
            CosmeticSelections.instance().selectedTailId = item.id();
            CosmeticDownloader.instance().ensureAssetLoaded("tail", item.id());
            return true;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (!item.locked() && item.id().equals(pendingId[0]))
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
        }
    }

    private static String buildSubtitle(String author, String mod) {
        if (!author.isEmpty() && !mod.isEmpty()) return author + " · " + mod;
        if (!author.isEmpty()) return author;
        return mod;
    }

    // ── Player preview renderer ────────────────────────────────────────────────

    private static void renderBrightEntityInInventory(GuiRender render, double x, double y, double scale, Quaternionf pose, @Nullable Quaternionf cameraOrientation, LivingEntity entity) {
        render.pose().pushPose();
        render.pose().translate(x, y, 50.0D);
        render.pose().scale((float) scale, (float) scale, (float) -scale);
        render.pose().mulPose(pose);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        Lighting.setupLevel();

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (cameraOrientation != null) {
            dispatcher.overrideCameraOrientation(cameraOrientation.conjugate(new Quaternionf()));
        }

        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, render.pose(), render.buffers(), 15728880));
        render.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        dispatcher.setRenderShadow(true);
        render.pose().popPose();
        Lighting.setupFor3DItems();
    }

    private static class OffsetFollowRenderer extends GuiElement<OffsetFollowRenderer> implements BackgroundRender {
        private final LivingEntity entity;
        private final CosmeticTypes[] activeTab;
        private final String[] previewEmoteId;
        private final float[] yRotOffset;
        private final boolean[] trackingEnabled;

        public OffsetFollowRenderer(@NotNull GuiParent<?> parent, LivingEntity entity, CosmeticTypes[] activeTab,
                                    String[] previewEmoteId, float[] yRotOffset, boolean[] trackingEnabled) {
            super(parent);
            this.entity = entity;
            this.activeTab = activeTab;
            this.previewEmoteId = previewEmoteId;
            this.yRotOffset = yRotOffset;
            this.trackingEnabled = trackingEnabled;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            if (entity == null) return;
            Rectangle rect = getRectangle();
            float scale = Math.min((float) (rect.height() / entity.getBbHeight()), 58.0F);
            float xPos = (float) (rect.x() + (rect.width() / 2D));
            float yPos = (float) (rect.y() + rect.height() - 8.0D);
            int eyeOffset = (int) (entity.getEyeHeight() * scale);
            double effectiveMouseX = trackingEnabled[0] ? mouseX : xPos;
            double effectiveMouseY = trackingEnabled[0] ? mouseY : (yPos - eyeOffset);
            float xAngle = (float) Math.atan((xPos - effectiveMouseX) / 40.0F);
            float yAngle = (float) Math.atan((yPos - effectiveMouseY - eyeOffset) / 40.0F);

            Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf quaternionf1 = new Quaternionf().rotateX(yAngle * 20.0F * ((float) Math.PI / 180F));
            quaternionf.mul(quaternionf1);

            float prevBodyRot = entity.yBodyRot;
            float prevYRot = entity.getYRot();
            float prevXRot = entity.getXRot();
            float prevHeadRotO = entity.yHeadRotO;
            float prevHeadRot = entity.yHeadRot;
            boolean previousFullBrightPreview = CosmeticSelections.instance().fullBrightPreview;

            entity.yBodyRot = 180.0F + yRotOffset[0] + xAngle * 20.0F;
            entity.setYRot(180.0F + yRotOffset[0] + xAngle * 40.0F);
            entity.setXRot(-yAngle * 20.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();

            try {
                CosmeticSelections.instance().fullBrightPreview = true;
                if (activeTab[0] == CosmeticTypes.EMOTES && previewEmoteId[0] != null && !previewEmoteId[0].isEmpty()) {
                    EmotePlayer.withPreviewPose(previewEmoteId[0], () -> renderBrightEntityInInventory(render, xPos, yPos, scale, quaternionf, quaternionf1, entity));
                } else {
                    renderBrightEntityInInventory(render, xPos, yPos, scale, quaternionf, quaternionf1, entity);
                }
            } finally {
                CosmeticSelections.instance().fullBrightPreview = previousFullBrightPreview;
                Lighting.setupFor3DItems();
                entity.yBodyRot = prevBodyRot;
                entity.setYRot(prevYRot);
                entity.setXRot(prevXRot);
                entity.yHeadRotO = prevHeadRotO;
                entity.yHeadRot = prevHeadRot;
            }
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(net.minecraft.client.gui.screens.Screen parent) {
            super(new CosmeticsGui(), parent);
        }
    }
}
