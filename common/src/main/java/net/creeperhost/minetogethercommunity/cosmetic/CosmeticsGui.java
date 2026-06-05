package net.creeperhost.minetogethercommunity.cosmetic;

import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.*;
import net.creeperhost.polylib.client.modulargui.lib.*;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Align;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Axis;
import net.creeperhost.polylib.client.modulargui.lib.geometry.GuiParent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class CosmeticsGui implements GuiProvider {

    private static final int CATEGORY_WIDTH = 44;
    private static final int LIST_WIDTH = 190;
    private static final int PREVIEW_WIDTH = 140;
    private static final int GAP = 6;
    private static final int TOTAL_WIDTH = CATEGORY_WIDTH + GAP + LIST_WIDTH + GAP + PREVIEW_WIDTH;
    private static final int BUTTON_HEIGHT = 14;
    private static final int TAB_HEIGHT = 18;

    private enum CosmeticTab { HATS, CAPES }

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
        final CosmeticTab[] activeTab = {CosmeticTab.HATS};

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

        MTStyle.Flat.buttonPrimary(categoryPanel, Component.translatable("minetogether:gui.cosmetics.tab.hats"))
                .setDisabled(() -> activeTab[0] == CosmeticTab.HATS)
                .onPress(() -> activeTab[0] = CosmeticTab.HATS)
                .constrain(TOP, relative(categoryPanel.get(TOP), 4))
                .constrain(LEFT, relative(categoryPanel.get(LEFT), 4))
                .constrain(RIGHT, relative(categoryPanel.get(RIGHT), -4))
                .constrain(HEIGHT, literal(TAB_HEIGHT));

        MTStyle.Flat.buttonPrimary(categoryPanel, Component.translatable("minetogether:gui.cosmetics.tab.capes"))
                .setDisabled(() -> activeTab[0] == CosmeticTab.CAPES)
                .onPress(() -> activeTab[0] = CosmeticTab.CAPES)
                .constrain(TOP, relative(categoryPanel.get(TOP), 4 + TAB_HEIGHT + 3))
                .constrain(LEFT, relative(categoryPanel.get(LEFT), 4))
                .constrain(RIGHT, relative(categoryPanel.get(RIGHT), -4))
                .constrain(HEIGHT, literal(TAB_HEIGHT));

        // ── Centre: list panel ────────────────────────────────────────────────

        GuiElement<?> listPanel = MTStyle.Flat.contentArea(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, relative(categoryPanel.get(RIGHT), GAP))
                .constrain(WIDTH, literal(LIST_WIDTH))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        // Loading indicator (hats only)
        new GuiText(listPanel, () ->
                activeTab[0] == CosmeticTab.HATS && CosmeticDownloader.instance().isLoading()
                        ? Component.translatable("minetogether:gui.cosmetics.loading").withStyle(ChatFormatting.YELLOW)
                        : Component.empty())
                .setShadow(false)
                .setAlignment(Align.LEFT)
                .constrain(BOTTOM, relative(listPanel.get(BOTTOM), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(listPanel.get(LEFT), 6))
                .constrain(RIGHT, relative(listPanel.get(RIGHT), -6));

        GuiElement<?> listArea = new GuiElement<>(listPanel)
                .constrain(TOP, match(listPanel.get(TOP)))
                .constrain(LEFT, match(listPanel.get(LEFT)))
                .constrain(RIGHT, match(listPanel.get(RIGHT)))
                .constrain(BOTTOM, relative(listPanel.get(BOTTOM), -14));

        // Hat list
        GuiList<Hat> hatList = new GuiList<Hat>(listArea)
                .setDisplayBuilder((parent, hat) -> hat == null ? new NoneEntry(parent) : new HatEntry(parent, hat))
                .setItemSpacing(2)
                .setEnabled(() -> activeTab[0] == CosmeticTab.HATS);
        Constraints.bind(hatList, listArea, 4);

        var hatScrollBar = MTStyle.Flat.scrollBar(listArea, Axis.Y);
        hatScrollBar.container
                .setEnabled(() -> activeTab[0] == CosmeticTab.HATS && hatList.hiddenSize() > 0)
                .constrain(TOP, match(hatList.get(TOP)))
                .constrain(BOTTOM, match(hatList.get(BOTTOM)))
                .constrain(RIGHT, match(listArea.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        hatScrollBar.primary
                .setScrollableElement(hatList)
                .setSliderState(hatList.scrollState());

        // Capes placeholder
        new GuiText(listArea, Component.translatable("minetogether:gui.cosmetics.capes_soon").withStyle(ChatFormatting.GRAY))
                .setShadow(false)
                .setEnabled(() -> activeTab[0] == CosmeticTab.CAPES)
                .constrain(TOP, midPoint(listArea.get(TOP), listArea.get(BOTTOM), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, match(listArea.get(LEFT)))
                .constrain(RIGHT, match(listArea.get(RIGHT)));

        // ── Right: player preview panel ───────────────────────────────────────

        GuiElement<?> previewPanel = MTStyle.Flat.contentArea(container)
                .constrain(TOP, match(container.get(TOP)))
                .constrain(LEFT, relative(listPanel.get(RIGHT), GAP))
                .constrain(WIDTH, literal(PREVIEW_WIDTH))
                .constrain(BOTTOM, match(container.get(BOTTOM)));

        new GuiText(previewPanel, Component.translatable("minetogether:gui.cosmetics.section.preview").withStyle(ChatFormatting.GRAY))
                .setShadow(false)
                .constrain(TOP, relative(previewPanel.get(TOP), 4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -2));

        GuiEntityRenderer playerRenderer = new GuiEntityRenderer(previewPanel)
                .setEntity(Minecraft.getInstance().player)
                .setTrackMouse(true);
        playerRenderer
                .constrain(HEIGHT, literal(70))
                .constrain(TOP, midPoint(previewPanel.get(TOP), previewPanel.get(BOTTOM), -35))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 4))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -4));

        new GuiText(previewPanel, () -> {
            String id = LocalConfig.instance().selectedHatId;
            if (id == null || id.isEmpty())
                return Component.translatable("minetogether:gui.cosmetics.none_equipped").withStyle(ChatFormatting.GRAY);
            Hat hat = HatRegistry.get(id);
            String name = hat != null ? hat.displayName() : id;
            return Component.translatable("minetogether:gui.cosmetics.equipped", Component.literal(name).withStyle(ChatFormatting.GREEN));
        })
                .setShadow(false)
                .constrain(BOTTOM, relative(previewPanel.get(BOTTOM), -4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(previewPanel.get(LEFT), 2))
                .constrain(RIGHT, relative(previewPanel.get(RIGHT), -2));

        // ── Bottom buttons ────────────────────────────────────────────────────

        MTStyle.Flat.buttonPrimary(root, Component.translatable("minetogether:gui.cosmetics.button.random"))
                .onPress(() -> {
                    List<Hat> available = HatRegistry.all();
                    if (available.isEmpty()) return;
                    Hat pick = available.get((int) (Math.random() * available.size()));
                    LocalConfig.instance().selectedHatId = pick.id();
                    LocalConfig.save();
                })
                .setDisabled(() -> HatRegistry.all().isEmpty())
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

        CosmeticDownloader.instance().startDownload();
        final int[] lastSize = {0};
        gui.onTick(() -> {
            List<Hat> available = HatRegistry.all();
            if (available.size() != lastSize[0]) {
                lastSize[0] = available.size();
                hatList.getList().clear();
                hatList.getList().add(null); // None entry
                hatList.getList().addAll(available);
                hatList.markDirty();
            }
        });
    }

    private static class NoneEntry extends GuiElement<NoneEntry> implements BackgroundRender {

        public NoneEntry(@NotNull GuiParent<?> parent) {
            super(parent);
            this.constrain(HEIGHT, literal(20));

            new GuiText(this, () -> Component.translatable("minetogether:gui.cosmetics.hat.none")
                    .withStyle(isNoneSelected() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -54))
                    .constrain(HEIGHT, literal(8));

            MTStyle.Flat.buttonPrimary(this, () -> isNoneSelected()
                    ? Component.translatable("minetogether:gui.cosmetics.button.wearing")
                    : Component.translatable("minetogether:gui.cosmetics.button.wear"))
                    .onPress(() -> { LocalConfig.instance().selectedHatId = ""; LocalConfig.save(); })
                    .setDisabled(NoneEntry::isNoneSelected)
                    .constrain(TOP, relative(get(TOP), 3))
                    .constrain(BOTTOM, relative(get(BOTTOM), -3))
                    .constrain(RIGHT, relative(get(RIGHT), -2))
                    .constrain(WIDTH, literal(50));
        }

        private static boolean isNoneSelected() {
            String id = LocalConfig.instance().selectedHatId;
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

        private final Hat hat;

        public HatEntry(@NotNull GuiParent<?> parent, Hat hat) {
            super(parent);
            this.hat = hat;
            this.constrain(HEIGHT, literal(20));

            new GuiText(this, () -> {
                boolean equipped = hat.id().equals(LocalConfig.instance().selectedHatId);
                return Component.literal(hat.displayName())
                        .withStyle(equipped ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            })
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 6))
                    .constrain(LEFT, relative(get(LEFT), 6))
                    .constrain(RIGHT, relative(get(RIGHT), -54))
                    .constrain(HEIGHT, literal(8));

            MTStyle.Flat.buttonPrimary(this, () -> {
                boolean equipped = hat.id().equals(LocalConfig.instance().selectedHatId);
                return equipped
                        ? Component.translatable("minetogether:gui.cosmetics.button.wearing")
                        : Component.translatable("minetogether:gui.cosmetics.button.wear");
            })
                    .onPress(() -> {
                        LocalConfig.instance().selectedHatId = hat.id();
                        LocalConfig.save();
                    })
                    .setDisabled(() -> hat.id().equals(LocalConfig.instance().selectedHatId))
                    .constrain(TOP, relative(get(TOP), 3))
                    .constrain(BOTTOM, relative(get(BOTTOM), -3))
                    .constrain(RIGHT, relative(get(RIGHT), -2))
                    .constrain(WIDTH, literal(50));
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
            if (hat.id().equals(LocalConfig.instance().selectedHatId)) {
                render.borderRect(getRectangle(), 1, 0x2000CC44, 0xFF00AA33);
            }
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(net.minecraft.client.gui.screens.Screen parent) {
            super(new CosmeticsGui(), parent);
        }
    }
}
