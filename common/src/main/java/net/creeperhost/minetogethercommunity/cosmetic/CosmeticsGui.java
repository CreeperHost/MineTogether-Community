package net.creeperhost.minetogethercommunity.cosmetic;

import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Locale;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class CosmeticsGui implements GuiProvider {

    private static final int CATEGORY_WIDTH = 44;
    private static final int LIST_WIDTH = 190;
    private static final int PREVIEW_WIDTH = 150;
    private static final int GAP = 6;
    private static final int TOTAL_WIDTH = CATEGORY_WIDTH + GAP + LIST_WIDTH + GAP + PREVIEW_WIDTH;
    private static final int BUTTON_HEIGHT = 14;
    private static final int TAB_HEIGHT = 18;

    /** Spinner frames cycled in the preview panel while a cosmetic asset is downloading. */
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private static boolean isImplemented(CosmeticTypes type) {
        return type == CosmeticTypes.HAT || type == CosmeticTypes.CAPE;
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

        // Kick off asset downloads for whatever the player already has equipped,
        // in case they weren't downloaded yet (e.g. the GUI was opened before the profile fetch completed).
        CosmeticDownloader d = CosmeticDownloader.instance();
        if (!pendingHatId[0].isEmpty())  d.ensureAssetLoaded("hat",  pendingHatId[0]);
        if (!pendingCapeId[0].isEmpty()) d.ensureAssetLoaded("cape", pendingCapeId[0]);

        GuiElement<?> container = new GuiElement<>(root)
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), -(TOTAL_WIDTH / 2D)))
                .constrain(WIDTH, literal(TOTAL_WIDTH))
                .constrain(TOP, relative(root.get(TOP), 26))
                .constrain(BOTTOM, relative(root.get(BOTTOM), -(BUTTON_HEIGHT + 10)));

        new GuiText(root, gui.getGuiTitle())
                .constrain(BOTTOM, relative(container.get(TOP), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, match(container.get(LEFT)))
                .constrain(RIGHT, match(container.get(RIGHT)));

        // ── Far left: vertical category sidebar ──────────────────────────────

        GuiElement<?> categoryPanel = MTStyle.Flat.contentArea(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, match(container.get(LEFT)))
                .constrain(WIDTH, literal(CATEGORY_WIDTH))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        CosmeticTypes[] allTypes = CosmeticTypes.values();
        for (int i = 0; i < allTypes.length; i++) {
            CosmeticTypes type = allTypes[i];
            boolean implemented = isImplemented(type);
            int topOffset = 4 + i * (TAB_HEIGHT + 3);
            MTStyle.Flat.buttonPrimary(categoryPanel, Component.translatable("minetogether:gui.cosmetics.tab." + type.name().toLowerCase()))
                    .setDisabled(() -> !implemented || activeTab[0] == type)
                    .onPress(() -> activeTab[0] = type)
                    .constrain(TOP, relative(categoryPanel.get(TOP), topOffset))
                    .constrain(LEFT, relative(categoryPanel.get(LEFT), 4))
                    .constrain(RIGHT, relative(categoryPanel.get(RIGHT), -4))
                    .constrain(HEIGHT, literal(TAB_HEIGHT));
        }

        // ── Centre: list panel ────────────────────────────────────────────────

        GuiElement<?> listPanel = MTStyle.Flat.contentArea(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, relative(categoryPanel.get(RIGHT), GAP))
                .constrain(WIDTH, literal(LIST_WIDTH))
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
                .constrain(TOP, relative(listPanel.get(TOP), 4))
                .constrain(LEFT, relative(listPanel.get(LEFT), 4))
                .constrain(RIGHT, relative(listPanel.get(RIGHT), -4))
                .constrain(HEIGHT, literal(14));

        GuiTextField searchField = new GuiTextField(searchBg)
                .setTextState(TextState.simpleState("", s -> searchQuery[0] = s))
                .setSuggestion(Component.translatable("minetogether:gui.cosmetics.search.suggestion"));
        Constraints.bind(searchField, searchBg, 0, 3, 0, 3);

        GuiElement<?> listArea = new GuiElement<>(listPanel)
                .constrain(TOP, relative(searchBg.get(BOTTOM), 4))
                .constrain(LEFT, match(listPanel.get(LEFT)))
                .constrain(RIGHT, match(listPanel.get(RIGHT)))
                .constrain(BOTTOM, relative(listPanel.get(BOTTOM), -14));

        // Hat list — uses CosmeticItem (lightweight catalog metadata)
        GuiList<CosmeticItem> hatList = new GuiList<CosmeticItem>(listArea)
                .setDisplayBuilder((parent, item) -> item == null
                        ? new NoneEntry(parent, pendingHatId)
                        : new HatEntry(parent, item, pendingHatId))
                .setItemSpacing(2)
                .setEnabled(() -> activeTab[0] == CosmeticTypes.HAT);
        Constraints.bind(hatList, listArea, 4);

        var hatScrollBar = MTStyle.Flat.scrollBar(listArea, Axis.Y);
        hatScrollBar.container
                .setEnabled(() -> activeTab[0] == CosmeticTypes.HAT && hatList.hiddenSize() > 0)
                .constrain(TOP, match(hatList.get(TOP)))
                .constrain(BOTTOM, match(hatList.get(BOTTOM)))
                .constrain(RIGHT, match(listArea.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        hatScrollBar.primary
                .setScrollableElement(hatList)
                .setSliderState(hatList.scrollState());

        // Cape list — uses CosmeticItem
        GuiList<CosmeticItem> capeList = new GuiList<CosmeticItem>(listArea)
                .setDisplayBuilder((parent, item) -> item == null
                        ? new NoCapeEntry(parent, pendingCapeId)
                        : new CapeEntry(parent, item, pendingCapeId))
                .setItemSpacing(2)
                .setEnabled(() -> activeTab[0] == CosmeticTypes.CAPE);
        Constraints.bind(capeList, listArea, 4);

        var capeScrollBar = MTStyle.Flat.scrollBar(listArea, Axis.Y);
        capeScrollBar.container
                .setEnabled(() -> activeTab[0] == CosmeticTypes.CAPE && capeList.hiddenSize() > 0)
                .constrain(TOP, match(capeList.get(TOP)))
                .constrain(BOTTOM, match(capeList.get(BOTTOM)))
                .constrain(RIGHT, match(listArea.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        capeScrollBar.primary
                .setScrollableElement(capeList)
                .setSliderState(capeList.scrollState());

        // ── Right: player preview panel ───────────────────────────────────────

        GuiElement<?> previewPanel = MTStyle.Flat.contentArea(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, relative(listPanel.get(RIGHT), GAP))
                .constrain(WIDTH, literal(PREVIEW_WIDTH))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        final boolean[] trackingEnabled = {true};

        new GuiText(previewPanel, Component.translatable("minetogether:gui.cosmetics.section.preview").withStyle(ChatFormatting.GRAY))
                .setShadow(false)
                .constrain(TOP, relative(previewPanel.get(TOP), 4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -18));

        MTStyle.Flat.button(previewPanel, () -> Component.literal("T").withStyle(trackingEnabled[0] ? ChatFormatting.GREEN : ChatFormatting.RED))
                .onPress(() -> trackingEnabled[0] = !trackingEnabled[0])
                .constrain(TOP, relative(previewPanel.get(TOP), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -2))
                .constrain(WIDTH, literal(13))
                .constrain(HEIGHT, literal(11));

        // Mirror view (left)
        new OffsetFollowRenderer(previewPanel, Minecraft.getInstance().player, 120.0F, trackingEnabled)
                .constrain(HEIGHT, literal(70))
                .constrain(TOP, midPoint(previewPanel.get(TOP), previewPanel.get(BOTTOM), -35))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 4))
                .constrain(RIGHT, midPoint(previewPanel.get(LEFT), previewPanel.get(RIGHT), -2));

        // Front view (right)
        new OffsetFollowRenderer(previewPanel, Minecraft.getInstance().player, -20.0F, trackingEnabled)
                .constrain(HEIGHT, literal(70))
                .constrain(TOP, midPoint(previewPanel.get(TOP), previewPanel.get(BOTTOM), -35))
                .constrain(LEFT, midPoint(previewPanel.get(LEFT), previewPanel.get(RIGHT), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -4));

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

            } else {
                return Component.translatable("minetogether:gui.cosmetics.coming_soon")
                        .withStyle(ChatFormatting.GRAY);
            }
        })
                .setShadow(false)
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -2));

        // ── Bottom buttons ────────────────────────────────────────────────────

        MTStyle.Flat.buttonPrimary(root, Component.translatable("minetogether:gui.cosmetics.button.save"))
                .onPress(() -> {
                    // CosmeticSelections is already up-to-date (written on every click for live rendering).
                    // Just sync the current pending values to the server.
                    String hatId  = pendingHatId[0];
                    String capeId = pendingCapeId[0];
                    CosmeticApiClient.selectAsync("hat",  hatId  == null || hatId.isEmpty()  ? null : hatId);
                    CosmeticApiClient.selectAsync("cape", capeId == null || capeId.isEmpty() ? null : capeId);
                })
                .constrain(BOTTOM, relative(root.get(BOTTOM), -6))
                .constrain(LEFT, match(container.get(LEFT)))
                .constrain(WIDTH, literal((TOTAL_WIDTH / 2) - 2))
                .constrain(HEIGHT, literal(BUTTON_HEIGHT));

        MTStyle.Flat.button(root, Component.translatable("minetogether:gui.button.back"))
                .onPress(() -> gui.mc().setScreen(gui.getParentScreen()))
                .constrain(BOTTOM, relative(root.get(BOTTOM), -6))
                .constrain(RIGHT, match(container.get(RIGHT)))
                .constrain(WIDTH, literal((TOTAL_WIDTH / 2) - 2))
                .constrain(HEIGHT, literal(BUTTON_HEIGHT));

        // Start the catalog fetch (idempotent — no-op if already started by the world-join hook).
        CosmeticDownloader.instance().startCatalogFetch();
        CosmeticApiClient.fetchProfileAsync();

        final int[] lastSizes = {0, 0};
        final String[] lastQuery = {""};
        gui.onTick(() -> {
            String q = searchQuery[0].toLowerCase(Locale.ROOT);
            boolean queryChanged = !q.equals(lastQuery[0]);
            if (queryChanged) lastQuery[0] = q;

            // Refresh hat list when new catalog entries arrive or search query changes
            List<CosmeticItem> availableHats = HatRegistry.catalog();
            if (availableHats.size() != lastSizes[0] || queryChanged) {
                lastSizes[0] = availableHats.size();
                hatList.getList().clear();
                hatList.getList().add(null); // None entry
                availableHats.stream()
                        .filter(h -> q.isEmpty() || h.displayName().toLowerCase(Locale.ROOT).contains(q))
                        .forEach(hatList.getList()::add);
                hatList.markDirty();
            }

            // Refresh cape list similarly
            List<CosmeticItem> availableCapes = CapeRegistry.catalog();
            if (availableCapes.size() != lastSizes[1] || queryChanged) {
                lastSizes[1] = availableCapes.size();
                capeList.getList().clear();
                capeList.getList().add(null); // None entry
                availableCapes.stream()
                        .filter(c -> q.isEmpty() || c.displayName().toLowerCase(Locale.ROOT).contains(q))
                        .forEach(capeList.getList()::add);
                capeList.markDirty();
            }
        });
    }

    // ── List entry inner classes ───────────────────────────────────────────────

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

    private static String buildSubtitle(String author, String mod) {
        if (!author.isEmpty() && !mod.isEmpty()) return author + " · " + mod;
        if (!author.isEmpty()) return author;
        return mod;
    }

    // ── Player preview renderer ────────────────────────────────────────────────

    private static class OffsetFollowRenderer extends GuiElement<OffsetFollowRenderer> implements BackgroundRender {
        private final LivingEntity entity;
        private final float yRotOffset;
        private final boolean[] trackingEnabled;

        public OffsetFollowRenderer(@NotNull GuiParent<?> parent, LivingEntity entity, float yRotOffset, boolean[] trackingEnabled) {
            super(parent);
            this.entity = entity;
            this.yRotOffset = yRotOffset;
            this.trackingEnabled = trackingEnabled;
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            if (entity == null) return;
            Rectangle rect = getRectangle();
            float scale = (float) (rect.height() / entity.getBbHeight());
            float xPos = (float) (rect.x() + (rect.width() / 2D));
            float yPos = (float) ((yMin() + (ySize() / 2)) + (rect.height() / 2));
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

            entity.yBodyRot = 180.0F + yRotOffset + xAngle * 20.0F;
            entity.setYRot(180.0F + yRotOffset + xAngle * 40.0F);
            entity.setXRot(-yAngle * 20.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();

            GuiEntityRenderer.renderEntityInInventory(render, xPos, yPos, scale, quaternionf, quaternionf1, entity);

            entity.yBodyRot = prevBodyRot;
            entity.setYRot(prevYRot);
            entity.setXRot(prevXRot);
            entity.yHeadRotO = prevHeadRotO;
            entity.yHeadRot = prevHeadRot;
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(net.minecraft.client.gui.screens.Screen parent) {
            super(new CosmeticsGui(), parent);
        }
    }
}
