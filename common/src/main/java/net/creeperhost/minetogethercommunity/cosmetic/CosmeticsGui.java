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
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class CosmeticsGui implements GuiProvider {

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
        int panelWidth = 200;

        GuiText title = new GuiText(root, gui.getGuiTitle())
                .constrain(TOP, relative(root.get(TOP), 10))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(root.get(LEFT), 10))
                .constrain(RIGHT, relative(root.get(RIGHT), -10));

        GuiText equipped = new GuiText(root, () -> {
            String id = LocalConfig.instance().selectedHatId;
            if (id == null || id.isEmpty()) {
                return Component.translatable("minetogether:gui.cosmetics.none_equipped").withStyle(ChatFormatting.GRAY);
            }
            Hat hat = HatRegistry.get(id);
            String name = hat != null ? hat.displayName() : id;
            return Component.translatable("minetogether:gui.cosmetics.equipped", Component.literal(name).withStyle(ChatFormatting.GREEN));
        })
                .constrain(TOP, relative(title.get(BOTTOM), 6))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), -(panelWidth / 2D)))
                .constrain(WIDTH, literal(panelWidth));

        GuiElement<?> listBg = MTStyle.Flat.contentArea(root)
                .constrain(TOP, relative(equipped.get(BOTTOM), 8))
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), -(panelWidth / 2D)))
                .constrain(WIDTH, literal(panelWidth))
                .constrain(BOTTOM, relative(root.get(BOTTOM), -50));

        GuiList<Hat> hatList = new GuiList<Hat>(listBg)
                .setDisplayBuilder(HatEntry::new)
                .setItemSpacing(2);
        Constraints.bind(hatList, listBg, 4);

        var scrollBar = MTStyle.Flat.scrollBar(listBg, Axis.Y);
        scrollBar.container
                .setEnabled(() -> hatList.hiddenSize() > 0)
                .constrain(TOP, match(hatList.get(TOP)))
                .constrain(BOTTOM, match(hatList.get(BOTTOM)))
                .constrain(RIGHT, match(listBg.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        scrollBar.primary
                .setScrollableElement(hatList)
                .setSliderState(hatList.scrollState());

        GuiButton removeButton = MTStyle.Flat.buttonCaution(root, Component.translatable("minetogether:gui.cosmetics.button.remove"))
                .onPress(() -> {
                    LocalConfig.instance().selectedHatId = "";
                    LocalConfig.save();
                })
                .setDisabled(() -> LocalConfig.instance().selectedHatId == null || LocalConfig.instance().selectedHatId.isEmpty())
                .constrain(BOTTOM, relative(root.get(BOTTOM), -26))
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), -(panelWidth / 2D)))
                .constrain(WIDTH, literal((panelWidth / 2) - 1))
                .constrain(HEIGHT, literal(14));

        GuiButton back = MTStyle.Flat.button(root, Component.translatable("minetogether:gui.button.back"))
                .onPress(() -> gui.mc().setScreen(gui.getParentScreen()))
                .constrain(BOTTOM, relative(root.get(BOTTOM), -26))
                .constrain(RIGHT, midPoint(root.get(LEFT), root.get(RIGHT), panelWidth / 2D))
                .constrain(WIDTH, literal((panelWidth / 2) - 1))
                .constrain(HEIGHT, literal(14));

        GuiText loadingText = new GuiText(root, () ->
                CosmeticDownloader.instance().isLoading()
                        ? Component.translatable("minetogether:gui.cosmetics.loading").withStyle(ChatFormatting.YELLOW)
                        : Component.empty())
                .constrain(TOP, relative(listBg.get(BOTTOM), 4))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), -(panelWidth / 2D)))
                .constrain(WIDTH, literal(panelWidth));

        // Kick off download if not already started, then sync list each tick
        CosmeticDownloader.instance().startDownload();
        final int[] lastSize = {0};
        gui.onTick(() -> {
            List<Hat> available = HatRegistry.all();
            if (available.size() != lastSize[0]) {
                lastSize[0] = available.size();
                hatList.getList().clear();
                hatList.getList().addAll(available);
                hatList.markDirty();
            }
        });
    }

    private static class HatEntry extends GuiElement<HatEntry> implements BackgroundRender {
        public HatEntry(@NotNull GuiParent<?> parent, Hat hat) {
            super(parent);
            this.constrain(HEIGHT, literal(16));

            new GuiText(this, () -> {
                boolean equipped = hat.id().equals(LocalConfig.instance().selectedHatId);
                return Component.literal(hat.displayName()).withStyle(equipped ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            })
                    .setAlignment(Align.LEFT)
                    .setShadow(false)
                    .constrain(TOP, relative(get(TOP), 4))
                    .constrain(LEFT, relative(get(LEFT), 4))
                    .constrain(RIGHT, relative(get(RIGHT), -52))
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
                    .constrain(TOP, match(get(TOP)))
                    .constrain(BOTTOM, match(get(BOTTOM)))
                    .constrain(RIGHT, match(get(RIGHT)))
                    .constrain(WIDTH, literal(50));
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(net.minecraft.client.gui.screens.Screen parent) {
            super(new CosmeticsGui(), parent);
        }
    }
}
