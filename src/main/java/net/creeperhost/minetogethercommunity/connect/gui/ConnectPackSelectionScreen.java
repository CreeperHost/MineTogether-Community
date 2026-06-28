package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.connect.ConnectPackResolver;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiList;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextField;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConnectPackSelectionScreen implements GuiProvider {

    private static final int MAX_RESULTS = 30;
    private static boolean promptShown;

    private final GuiScreen parent;
    private String status = I18n.format("minetogether.connect.pack_select.idle");
    private List<ConnectPackResolver.SearchResult> results = new ArrayList<ConnectPackResolver.SearchResult>();
    private CompletableFuture<List<ConnectPackResolver.SearchResult>> activeSearch;
    private CompletableFuture<ConnectPackResolver.ManualSelection> activeSelection;
    private GuiList<ConnectPackResolver.SearchResult> resultList;
    private GuiTextField searchField;
    private String searchQuery = "";

    private ConnectPackSelectionScreen(GuiScreen parent) {
        this.parent = parent;
    }

    public static void promptIfNeeded(final GuiScreen parent) {
        if (promptShown || !ModPackInfo.shouldPromptForManualSelection()) {
            return;
        }
        promptShown = true;
        runOnClient(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().displayGuiScreen(new Screen(parent));
            }
        });
    }

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        gui.setGuiTitle(new TextComponentString(I18n.format("minetogether.connect.pack_select.title")));

        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        GuiElement<?> root = new GuiElement<>(gui);
        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);

        int width = Math.min(360, screenWidth - 20);
        int left = screenWidth / 2 - width / 2;
        int top = Math.max(12, screenHeight / 2 - 116);

        new GuiText(root, () -> I18n.format("minetogether.connect.pack_select.title"))
                .centered()
                .setBounds(left, top, width, 10);
        new GuiText(root, () -> I18n.format("minetogether.connect.pack_select.description"))
                .setWrap(true)
                .setBounds(left, top + 18, width, 28);

        int searchTop = top + 52;
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, searchTop, width - 84, 16);
        searchField = new GuiTextField(root)
                .setSuggestion(I18n.format("minetogether.connect.pack_select.search"))
                .onChanged(value -> searchQuery = value)
                .setBounds(left + 3, searchTop + 3, width - 90, 10);
        new GuiButton(root, () -> I18n.format("minetogether.connect.pack_select.search_button"))
                .primary()
                .setEnabled(() -> !isBusy())
                .setBounds(left + width - 78, searchTop, 78, 16)
                .onPress(this::startSearch);

        int resultTop = searchTop + 24;
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, resultTop, width, 112);
        resultList = new GuiList<ConnectPackResolver.SearchResult>(root)
                .setRowHeight(18)
                .setDisplayBuilder((list, result) -> new GuiButton(list, () -> result.label())
                        .setEnabled(() -> !isBusy())
                        .onPress(() -> selectResult(result)))
                .setBounds(left + 4, resultTop + 4, width - 8, 104);

        new GuiText(root, () -> status)
                .setBounds(left, resultTop + 118, width, 10);

        int buttonTop = resultTop + 138;
        int buttonWidth = (width - 8) / 3;
        new GuiButton(root, () -> I18n.format("minetogether.connect.pack_select.detected"))
                .setEnabled(() -> !isBusy())
                .setBounds(left, buttonTop, buttonWidth, 16)
                .onPress(this::clearManualSelection);
        new GuiButton(root, () -> I18n.format("minetogether.connect.pack_select.custom"))
                .setEnabled(() -> !isBusy())
                .setBounds(left + buttonWidth + 4, buttonTop, buttonWidth, 16)
                .onPress(this::saveBypass);
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.cancel"))
                .setBounds(left + buttonWidth * 2 + 8, buttonTop, width - buttonWidth * 2 - 8, 16)
                .onPress(() -> close(gui));

        refreshResultList();
        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        if (activeSearch != null && activeSearch.isDone()) {
            try {
                results = activeSearch.join();
                status = results.isEmpty()
                        ? I18n.format("minetogether.connect.pack_select.no_results")
                        : I18n.format("minetogether.connect.pack_select.results", results.size());
            } catch (Throwable ex) {
                results = new ArrayList<ConnectPackResolver.SearchResult>();
                status = I18n.format("minetogether.connect.pack_select.search_failed", failureMessage(ex));
            }
            activeSearch = null;
            refreshResultList();
        }

        if (activeSelection != null && activeSelection.isDone()) {
            try {
                ConnectPackResolver.ManualSelection selection = activeSelection.join();
                if (selection == null) {
                    status = I18n.format("minetogether.connect.pack_select.mapping_failed");
                } else {
                    saveSelection(selection);
                    Minecraft.getMinecraft().displayGuiScreen(parent);
                }
            } catch (Throwable ex) {
                status = I18n.format("minetogether.connect.pack_select.mapping_failed");
            }
            activeSelection = null;
            refreshResultList();
        }
    }

    private void startSearch() {
        if (isBusy()) return;
        String query = searchField != null ? searchField.getText().trim() : searchQuery.trim();
        if (query.isEmpty()) {
            status = I18n.format("minetogether.connect.pack_select.empty_search");
            return;
        }

        status = I18n.format("minetogether.connect.pack_select.searching");
        results = new ArrayList<ConnectPackResolver.SearchResult>();
        refreshResultList();
        activeSearch = CompletableFuture.supplyAsync(() -> {
            try {
                return ConnectPackResolver.search(query);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, ModPackInfo.EXECUTOR);
    }

    private void selectResult(final ConnectPackResolver.SearchResult result) {
        if (isBusy()) return;
        status = I18n.format("minetogether.connect.pack_select.mapping", result.label());
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
        List<ConnectPackResolver.SearchResult> visible = new ArrayList<ConnectPackResolver.SearchResult>();
        for (int i = 0; i < Math.min(results.size(), MAX_RESULTS); i++) {
            visible.add(results.get(i));
        }
        resultList.setValues(visible);
    }

    private boolean isBusy() {
        return activeSearch != null || activeSelection != null;
    }

    private void saveSelection(ConnectPackResolver.ManualSelection selection) {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = false;
        config.connectPackKey = selection.getConnectKey();
        config.connectPackDisplayName = selection.getDisplayName();
        config.connectPackProjectType = selection.getProjectType();
        config.connectPackProjectId = selection.getProjectId();
        config.connectPackProjectVersion = selection.getProjectVersion();
        config.connectPackMinecraftVersion = selection.getMinecraftVersion();
        config.connectPackCreeperHostVersionId = selection.getCreeperHostVersionId();
        LocalConfig.save();
        ModPackInfo.reload();
    }

    private void clearManualSelection() {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = false;
        clearSelectionFields(config);
        LocalConfig.save();
        ModPackInfo.reload();
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    private void saveBypass() {
        LocalConfig config = LocalConfig.instance();
        config.connectPackPrompted = true;
        config.connectPackBypass = true;
        clearSelectionFields(config);
        LocalConfig.save();
        ModPackInfo.reload();
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    private void close(ModularGui gui) {
        LocalConfig.instance().connectPackPrompted = true;
        LocalConfig.save();
        gui.mc().displayGuiScreen(parent);
    }

    private void clearSelectionFields(LocalConfig config) {
        config.connectPackKey = "";
        config.connectPackDisplayName = "";
        config.connectPackProjectType = "";
        config.connectPackProjectId = "";
        config.connectPackProjectVersion = "";
        config.connectPackMinecraftVersion = "";
        config.connectPackCreeperHostVersionId = -1;
    }

    private static String failureMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void runOnClient(Runnable task) {
        Minecraft minecraft = Minecraft.getMinecraft();
        for (String name : new String[]{"addScheduledTask", "func_152344_a"}) {
            try {
                Method method = Minecraft.class.getMethod(name, Runnable.class);
                method.invoke(minecraft, task);
                return;
            } catch (Throwable ignored) {
            }
        }
        task.run();
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new ConnectPackSelectionScreen(parentScreen), parentScreen);
        }
    }
}
