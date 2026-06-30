package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.connect.ConnectPackResolver;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.*;
import net.creeperhost.polylib.client.modulargui.lib.Constraints;
import net.creeperhost.polylib.client.modulargui.lib.GuiProvider;
import net.creeperhost.polylib.client.modulargui.lib.TextState;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class ConnectPackSelectionScreen implements GuiProvider {

    private static final int MAX_RESULTS = 30;
    private static boolean promptShown;

    private Component status = Component.translatable("minetogether.connect.pack_select.idle");
    private List<ConnectPackResolver.SearchResult> results = List.of();
    private @Nullable CompletableFuture<List<ConnectPackResolver.SearchResult>> activeSearch;
    private @Nullable CompletableFuture<ConnectPackResolver.ManualSelection> activeSelection;
    private @Nullable CompletableFuture<Boolean> detectedPackCheck;
    private @Nullable GuiList<ConnectPackResolver.SearchResult> resultList;
    private @Nullable GuiTextField searchField;
    private final @Nullable net.minecraft.client.gui.screens.Screen parent;
    private String searchQuery = "";
    private boolean detectedPackAvailable;

    private ConnectPackSelectionScreen(@Nullable net.minecraft.client.gui.screens.Screen parent) {
        this.parent = parent;
    }

    public static void promptIfNeeded(net.minecraft.client.gui.screens.Screen parent) {
        if (promptShown || !ModPackInfo.shouldPromptForManualSelection()) {
            return;
        }
        promptShown = true;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new Screen(parent)));
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
        gui.setGuiTitle(Component.translatable("minetogether.connect.pack_select.title"));

        GuiElement<?> root = gui.getRoot();

        GuiElement<?> bounds = new GuiElement<>(root);
        Constraints.size(bounds, 360, 238);
        Constraints.center(bounds, root);

        GuiText title = new GuiText(root, gui.getGuiTitle())
                .constrain(TOP, match(bounds.get(TOP)))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(10));

        GuiText description = new GuiText(root, Component.translatable("minetogether.connect.pack_select.description"))
                .setWrap(true)
                .autoHeight()
                .constrain(TOP, relative(title.get(BOTTOM), 8))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)));

        GuiRectangle searchBg = new GuiRectangle(root)
                .fill(0xA0202020)
                .constrain(TOP, relative(description.get(BOTTOM), 8))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, relative(bounds.get(RIGHT), -84))
                .constrain(HEIGHT, literal(16));

        searchField = new GuiTextField(searchBg)
                .setTextState(TextState.simpleState("", value -> searchQuery = value))
                .setSuggestion(Component.translatable("minetogether.connect.pack_select.search"))
                .setEnterPressed(this::startSearch);
        Constraints.bind(searchField, searchBg, 0, 3, 0, 3);

        MTStyle.Flat.buttonPrimary(root, Component.translatable("minetogether.connect.pack_select.search_button"))
                .onPress(this::startSearch)
                .setDisabled(this::isBusy)
                .constrain(TOP, match(searchBg.get(TOP)))
                .constrain(LEFT, relative(searchBg.get(RIGHT), 4))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, match(searchBg.get(HEIGHT)));

        GuiElement<?> resultBg = MTStyle.Flat.contentArea(root)
                .constrain(TOP, relative(searchBg.get(BOTTOM), 7))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(112));

        resultList = new GuiList<ConnectPackResolver.SearchResult>(resultBg)
                .setItemSpacing(2);
        Constraints.bind(resultList, resultBg, 4);
        resultList.setDisplayBuilder((list, result) -> MTStyle.Flat.button(list, Component.literal(result.label()))
                .onPress(() -> selectResult(result))
                .setDisabled(this::isBusy)
                .constrain(HEIGHT, literal(16)));

        var scrollBar = MTStyle.Flat.scrollBar(resultBg, Axis.Y);
        scrollBar.container
                .setEnabled(() -> resultList != null && resultList.hiddenSize() > 0)
                .constrain(TOP, match(resultBg.get(TOP)))
                .constrain(BOTTOM, match(resultBg.get(BOTTOM)))
                .constrain(RIGHT, match(resultBg.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        scrollBar.primary
                .setScrollableElement(resultList)
                .setSliderState(resultList.scrollState());

        GuiText statusText = new GuiText(root, Component.empty())
                .setTextSupplier(() -> status)
                .constrain(TOP, relative(resultBg.get(BOTTOM), 6))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(10));

        MTStyle.Flat.button(root, () -> Component.translatable(detectedPackAvailable
                        ? "minetogether.connect.pack_select.detected"
                        : "minetogether.connect.pack_select.no_detected"))
                .onPress(this::clearManualSelection)
                .setDisabled(() -> isBusy() || !detectedPackAvailable)
                .constrain(TOP, relative(statusText.get(BOTTOM), 10))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, dynamic(() -> bounds.xMin() + ((bounds.xSize() - 8) / 3)).precise())
                .constrain(HEIGHT, literal(16));

        MTStyle.Flat.button(root, Component.translatable("minetogether.connect.pack_select.custom"))
                .onPress(this::saveBypass)
                .setDisabled(this::isBusy)
                .constrain(TOP, relative(statusText.get(BOTTOM), 10))
                .constrain(LEFT, dynamic(() -> bounds.xMin() + ((bounds.xSize() - 8) / 3) + 4).precise())
                .constrain(RIGHT, dynamic(() -> bounds.xMin() + (((bounds.xSize() - 8) / 3) * 2) + 4).precise())
                .constrain(HEIGHT, literal(16));

        MTStyle.Flat.button(root, Component.translatable("minetogether:gui.button.cancel"))
                .onPress(() -> close(gui))
                .constrain(TOP, relative(statusText.get(BOTTOM), 10))
                .constrain(LEFT, dynamic(() -> bounds.xMin() + (((bounds.xSize() - 8) / 3) * 2) + 8).precise())
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(16));

        refreshResultList();
        startDetectedPackCheck();
        gui.onTick(this::tick);
    }

    private void tick() {
        if (detectedPackCheck != null && detectedPackCheck.isDone()) {
            try {
                detectedPackAvailable = detectedPackCheck.join();
            } catch (Throwable ignored) {
                detectedPackAvailable = false;
            }
            detectedPackCheck = null;
        }

        if (activeSearch != null && activeSearch.isDone()) {
            try {
                results = activeSearch.join();
                status = results.isEmpty()
                        ? Component.translatable("minetogether.connect.pack_select.no_results")
                        : Component.translatable("minetogether.connect.pack_select.results", results.size());
            } catch (Throwable ex) {
                results = List.of();
                status = Component.translatable("minetogether.connect.pack_select.search_failed", failureMessage(ex));
            }
            activeSearch = null;
            refreshResultList();
        }

        if (activeSelection != null && activeSelection.isDone()) {
            try {
                ConnectPackResolver.ManualSelection selection = activeSelection.join();
                if (selection == null) {
                    status = Component.translatable("minetogether.connect.pack_select.mapping_failed");
                } else {
                    saveSelection(selection);
                    Minecraft.getInstance().gui.setScreen(parent);
                }
            } catch (Throwable ex) {
                status = Component.translatable("minetogether.connect.pack_select.mapping_failed");
            }
            activeSelection = null;
            refreshResultList();
        }
    }

    private void startDetectedPackCheck() {
        if (detectedPackCheck != null) return;
        String manualKey = LocalConfig.instance().connectPackKey;
        detectedPackAvailable = !LocalConfig.instance().connectPackBypass
                && (manualKey == null || manualKey.isEmpty())
                && ModPackInfo.getInfo().hasConnectPackKey();
        detectedPackCheck = ModPackInfo.detectLauncherInfo().thenApply(ModPackInfo.VersionInfo::hasConnectPackKey);
    }

    private void startSearch() {
        if (isBusy()) return;
        String query = searchField != null ? searchField.getValue().trim() : searchQuery.trim();
        if (query.isEmpty()) {
            status = Component.translatable("minetogether.connect.pack_select.empty_search");
            return;
        }

        status = Component.translatable("minetogether.connect.pack_select.searching");
        results = List.of();
        refreshResultList();
        activeSearch = CompletableFuture.supplyAsync(() -> {
            try {
                return ConnectPackResolver.search(query);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, ModPackInfo.EXECUTOR);
    }

    private void selectResult(ConnectPackResolver.SearchResult result) {
        if (isBusy()) return;
        status = Component.translatable("minetogether.connect.pack_select.mapping", result.label());
        activeSelection = CompletableFuture.supplyAsync(() -> {
            try {
                return ConnectPackResolver.resolveSelection(result);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, ModPackInfo.EXECUTOR);
    }

    private void refreshResultList() {
        if (resultList == null) return;
        resultList.getList().clear();
        for (int i = 0; i < Math.min(results.size(), MAX_RESULTS); i++) {
            resultList.add(results.get(i));
        }
        resultList.markDirty();
    }

    private boolean isBusy() {
        return activeSearch != null || activeSelection != null;
    }

    private void saveSelection(ConnectPackResolver.ManualSelection selection) {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = false;
        config.connectPackKey = selection.connectKey();
        config.connectPackDisplayName = selection.displayName();
        config.connectPackProjectType = selection.projectType();
        config.connectPackProjectId = selection.projectId();
        config.connectPackProjectVersion = selection.projectVersion();
        config.connectPackMinecraftVersion = selection.minecraftVersion();
        config.connectPackCreeperHostVersionId = selection.creeperHostVersionId();
        LocalConfig.save();
        ModPackInfo.reload();
    }

    private void clearManualSelection() {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = false;
        config.connectPackKey = "";
        config.connectPackDisplayName = "";
        config.connectPackProjectType = "";
        config.connectPackProjectId = "";
        config.connectPackProjectVersion = "";
        config.connectPackMinecraftVersion = "";
        config.connectPackCreeperHostVersionId = -1;
        LocalConfig.save();
        ModPackInfo.reload();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void saveBypass() {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = true;
        config.connectPackKey = "";
        config.connectPackDisplayName = "";
        config.connectPackProjectType = "";
        config.connectPackProjectId = "";
        config.connectPackProjectVersion = "";
        config.connectPackMinecraftVersion = "";
        config.connectPackCreeperHostVersionId = -1;
        LocalConfig.save();
        ModPackInfo.reload();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void close(ModularGui gui) {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        LocalConfig.save();
        gui.mc().gui.setScreen(parent);
    }

    private static String failureMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(@Nullable net.minecraft.client.gui.screens.Screen parentScreen) {
            super(new ConnectPackSelectionScreen(parentScreen), parentScreen);
        }
    }
}
