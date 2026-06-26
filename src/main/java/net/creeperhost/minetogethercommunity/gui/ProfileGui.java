package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiDialog;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextField;
import net.creeperhost.minetogethercommunity.modulargui.ItemSelectDialog;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.List;
import java.util.Random;

public class ProfileGui implements GuiProvider {

    private static final Random RANDOM = new Random();
    private static final int UI_WIDTH = 250;

    private GuiTextField nameField;
    private GuiTextField appealField;
    private GuiButton submitAppeal;
    private String feedback = "";
    private String lastAppliedName = "";
    private String nameLeft = "";
    private String nameRight = "";
    private boolean selectingName;
    private boolean requested;
    private boolean fetchingProfile;
    private boolean lastBanned;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = (screenWidth - UI_WIDTH) / 2;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> I18n.format("minetogether.gui.profile.title")).centered()
                .setBounds(10, 10, screenWidth - 20, 8);

        Profile profile = safeProfile();
        boolean premium = profile != null && profile.isPremium();
        int nameTitleTop = 68;
        new GuiText(root, () -> I18n.format("minetogether.gui.profile.display_name")).setColor(MTStyle.Flat.TEXT_WARN)
                .setBounds(left, nameTitleTop, UI_WIDTH, 8);

        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, nameTitleTop + 26, UI_WIDTH - 50, 14);
        nameField = new GuiTextField(root)
                .setMaxLength(32)
                .setText(initialName())
                .setEnabled(premium)
                .setBounds(left, nameTitleTop + 26, UI_WIDTH - 50, 14)
                .onChanged(value -> lastAppliedName = value);

        new GuiButton(root, () -> ProfileRequests.requestsInProgress()
                ? I18n.format("minetogether.gui.profile.busy")
                : I18n.format("minetogether.gui.profile.button." + (premium ? "save" : "change")))
                .primary()
                .setBounds(left + UI_WIDTH - 49, nameTitleTop + 26, 49, 14)
                .setEnabled(() -> !ProfileRequests.requestsInProgress())
                .onPress(() -> handleNameChange(gui, premium));

        new GuiText(root, () -> ProfileRequests.canChangeName()
                ? I18n.format("minetogether.gui.profile.name_can_be_changed")
                : I18n.format("minetogether.gui.profile.next_name_change", ProfileRequests.getCanChangeText()))
                .setColor(MTStyle.Flat.TEXT_MUTED)
                .setBounds(left, nameTitleTop + 46, UI_WIDTH, 20);

        new GuiText(root, () -> feedback).centered().setColor(MTStyle.Flat.TEXT_WARN)
                .setBounds(left, nameTitleTop + 78, UI_WIDTH, 24);

        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back"))
                .setBounds((screenWidth - 150) / 2, screenHeight - 46, 150, 16)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));

        if (selectingName) {
            buildNameSelectionOverlay(root, gui);
        }
        if (ProfileRequests.isBanned()) {
            buildBanOverlay(root, gui);
        }
        new BusyOverlay(root).setBounds(0, 0, screenWidth, screenHeight);

        openRequests();
        lastBanned = ProfileRequests.isBanned();
        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        ProfileRequests.updateRequests();
        if (fetchingProfile && !ProfileRequests.requestsInProgress()) {
            if (I18n.format("minetogether.gui.profile.fetching_data").equals(feedback)) {
                feedback = "";
            }
            fetchingProfile = false;
        }
        applyFetchedName();
        if (submitAppeal != null) {
            submitAppeal.setEnabled(ProfileRequests.isBanned() && !ProfileRequests.requestsInProgress());
        }
        if (lastBanned != ProfileRequests.isBanned()) {
            gui.getScreen().initGui();
        }
    }

    private void buildNameSelectionOverlay(GuiElement<?> root, ModularGui gui) {
        initializeNameOptions();
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = (screenWidth - UI_WIDTH) / 2;
        int top = screenHeight / 2 - 40;

        new GuiRectangle(root, 0xC0000000).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> I18n.format("minetogether.gui.profile.choose_name")).centered()
                .setColor(MTStyle.Flat.TEXT_WARN)
                .setBounds(left, top, UI_WIDTH, 8);

        int optionWidth = (UI_WIDTH - 50) / 2;
        new GuiButton(root, () -> optionLabel(nameLeft))
                .setBounds(left, top + 28, optionWidth, 14)
                .onPress(() -> new ItemSelectDialog<String>(root, () -> I18n.format("minetogether.gui.profile.select_option"), ProfileRequests.getNameOptions())
                        .setCloseOnOutsideClick(true)
                        .setOnItemSelected(value -> {
                            nameLeft = value;
                            lastAppliedName = nameLeft + nameRight;
                        }));
        new GuiButton(root, () -> optionLabel(nameRight))
                .setBounds(left + optionWidth + 1, top + 28, optionWidth, 14)
                .onPress(() -> new ItemSelectDialog<String>(root, () -> I18n.format("minetogether.gui.profile.select_option"), ProfileRequests.getNameOptions())
                        .setCloseOnOutsideClick(true)
                        .setOnItemSelected(value -> {
                            nameRight = value;
                            lastAppliedName = nameLeft + nameRight;
                        }));
        new GuiButton(root, () -> I18n.format("minetogether.gui.profile.button.save"))
                .primary()
                .setBounds(left + optionWidth * 2 + 2, top + 28, UI_WIDTH - optionWidth * 2 - 2, 14)
                .onPress(() -> {
                    ProfileRequests.setName(nameLeft + nameRight, this::applyFetchedName);
                    selectingName = false;
                    gui.getScreen().initGui();
                });

        new GuiButton(root, () -> I18n.format("minetogether.gui.profile.button.randomize"))
                .setBounds(left, top + 68, UI_WIDTH / 2 - 1, 14)
                .onPress(this::randomizeName);
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.cancel"))
                .caution()
                .setBounds(left + UI_WIDTH / 2 + 1, top + 68, UI_WIDTH / 2 - 1, 14)
                .onPress(() -> {
                    selectingName = false;
                    gui.getScreen().initGui();
                });
    }

    private void buildBanOverlay(GuiElement<?> root, ModularGui gui) {
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = (screenWidth - UI_WIDTH) / 2;
        int top = 20;

        new GuiRectangle(root, 0xFF000000).setBounds(0, 0, screenWidth, screenHeight);
        addBanLine(root, "minetogether.gui.profile.ban.you_are_banned", left, top, 0xFF5555, true);
        addBanLine(root, "minetogether.gui.profile.ban.current_ban", left, top + 28, 0xFF5555, true);
        addBanText(root, I18n.format("minetogether.gui.profile.ban.ban_id", ProfileRequests.getBanId()), left, top + 44, 0xFFFF55);
        addBanText(root, I18n.format("minetogether.gui.profile.ban.moderator", ProfileRequests.getModerator()), left, top + 56, 0xFFFF55);
        addBanText(root, I18n.format("minetogether.gui.profile.ban.reason", ProfileRequests.getReason()), left, top + 68, 0xFFFF55);
        addBanText(root, I18n.format("minetogether.gui.profile.ban.timestamp", ProfileRequests.getTimestamp()), left, top + 80, 0xFFFF55);
        addBanText(root, I18n.format("minetogether.gui.profile.ban.appeal_status", ProfileRequests.getAppealStatus()), left, top + 92, 0x5555FF);
        String notes = ProfileRequests.getAppealNotesText();
        if (!notes.isEmpty()) {
            new GuiText(root, () -> I18n.format("minetogether.gui.profile.ban.appeal_notes"))
                    .setColor(0xFFAA00)
                    .setTooltip(() -> ProfileRequests.getAppealNotesText())
                    .setBounds(left, top + 106, UI_WIDTH, 8);
        }
        addBanText(root, I18n.format("minetogether.gui.profile.ban.next_appeal", ProfileRequests.getNextAppealText()), left, top + 122, 0xFFFF55);
        addBanLine(root, "minetogether.gui.profile.ban.submit_an_appeal", left, top + 150, 0x5555FF, false);

        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, top + 164, UI_WIDTH, 14);
        appealField = new GuiTextField(root)
                .setMaxLength(140)
                .setSuggestion(I18n.format("minetogether.gui.profile.ban.submit_an_appeal_hint"))
                .setBounds(left, top + 164, UI_WIDTH, 14);
        submitAppeal = new GuiButton(root, () -> ProfileRequests.requestsInProgress()
                ? I18n.format("minetogether.gui.profile.busy")
                : I18n.format("minetogether.gui.profile.button.submit"))
                .primary()
                .setBounds((screenWidth - 150) / 2, top + 182, 150, 16)
                .onPress(() -> ProfileRequests.submitAppeal(appealField.getText(), (success, message) -> onAppealSubmitted(root, gui, success, message)));
        new GuiText(root, () -> feedback).centered().setColor(MTStyle.Flat.TEXT_WARN)
                .setBounds(left, top + 204, UI_WIDTH, 18);
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back"))
                .setBounds((screenWidth - 150) / 2, screenHeight - 46, 150, 16)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));
    }

    private void addBanLine(GuiElement<?> root, String key, int left, int top, int color, boolean centered) {
        GuiText text = new GuiText(root, () -> I18n.format(key)).setColor(color);
        if (centered) text.centered();
        text.setBounds(left, top, UI_WIDTH, 8);
    }

    private void addBanText(GuiElement<?> root, String value, int left, int top, int color) {
        new GuiText(root, () -> value).setColor(color).setBounds(left, top, UI_WIDTH, 8);
    }

    private void handleNameChange(ModularGui gui, boolean premium) {
        if (premium) {
            ProfileRequests.setName(nameField.getText(), this::applyFetchedName);
        } else {
            selectingName = true;
            gui.getScreen().initGui();
        }
    }

    private void onAppealSubmitted(GuiElement<?> root, ModularGui gui, boolean success, String message) {
        feedback = message;
        if (success && submitAppeal != null) {
            submitAppeal.setEnabled(false);
        }
        final GuiDialog[] dialog = new GuiDialog[1];
        dialog[0] = new GuiDialog(root,
                () -> I18n.format("minetogether.gui.profile.ban.submit_an_appeal"),
                () -> message)
                .boxWidth(UI_WIDTH)
                .addButton(() -> I18n.format("minetogether.gui.button.ok"), () -> {
                    dialog[0].setVisible(false);
                    if (success) {
                        gui.mc().displayGuiScreen(gui.getParentScreen());
                    }
                });
    }

    private void openRequests() {
        ProfileRequests.guiOpened(value -> feedback = value);
        if (requested) return;
        requested = true;
        fetchingProfile = true;
        feedback = I18n.format("minetogether.gui.profile.fetching_data");
        ProfileRequests.fetchBannedStatus(null);
        ProfileRequests.fetchNameOptions(this::randomizeName);
        ProfileRequests.fetchName(this::applyFetchedName);
        if (!ProfileRequests.requestsInProgress()) {
            fetchingProfile = false;
            feedback = "";
        }
    }

    private void applyFetchedName() {
        String fetched = ProfileRequests.getCustomName();
        if (fetched.isEmpty()) fetched = initialName();
        if (nameField == null) return;
        if (!fetched.equals(lastAppliedName) && (lastAppliedName.isEmpty() || lastAppliedName.equals(nameField.getText()))) {
            nameField.setText(fetched);
            splitCurrentName(fetched);
            lastAppliedName = fetched;
        }
    }

    private Profile safeProfile() {
        try {
            return MineTogetherChat.getOurProfile();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String initialName() {
        Profile profile = safeProfile();
        return profile == null ? "" : trimHash(MineTogetherChat.displayName(profile));
    }

    private void initializeNameOptions() {
        if (!nameLeft.isEmpty() && !nameRight.isEmpty()) return;
        splitCurrentName(initialName());
        if (nameLeft.isEmpty() || nameRight.isEmpty()) {
            randomizeName();
        }
    }

    private void splitCurrentName(String name) {
        String value = trimHash(name);
        List<String> options = ProfileRequests.getNameOptions();
        nameLeft = "";
        nameRight = "";
        for (String option : options) {
            if (value.startsWith(option)) {
                String tail = value.substring(option.length());
                if (options.contains(tail)) {
                    nameLeft = option;
                    nameRight = tail;
                    return;
                }
            }
        }
        if (options.size() >= 2) {
            nameLeft = options.get(0);
            nameRight = options.get(1);
        }
    }

    private void randomizeName() {
        List<String> options = ProfileRequests.getNameOptions();
        if (options.isEmpty()) {
            feedback = I18n.format("minetogether.gui.profile.fetching_name_options");
            return;
        }
        nameLeft = options.get(RANDOM.nextInt(options.size()));
        nameRight = options.get(RANDOM.nextInt(options.size()));
        lastAppliedName = nameLeft + nameRight;
    }

    private String optionLabel(String value) {
        return value == null || value.isEmpty() ? I18n.format("minetogether.gui.profile.select_option") : value;
    }

    private static String trimHash(String displayName) {
        if (displayName == null) return "";
        return displayName.contains("#") ? displayName.substring(0, displayName.lastIndexOf("#")) : displayName;
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new ProfileGui(), parentScreen);
        }
    }

    private static class BusyOverlay extends GuiElement<BusyOverlay> {
        private static final String[] SPINNER = {"|", "/", "-", "\\"};

        private BusyOverlay(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            if (!ProfileRequests.requestsInProgress()) return;
            drawRect(x, y, x + width, y + height, 0x80000000);
            String frame = SPINNER[(int) ((System.currentTimeMillis() / 120L) % SPINNER.length)];
            drawCenteredString(font(), frame, x + width / 2, y + height / 2 - 12, MTStyle.Flat.TEXT);
            drawCenteredString(font(), I18n.format("minetogether.gui.profile.busy"), x + width / 2, y + height / 2 + 2, MTStyle.Flat.TEXT_MUTED);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            return ProfileRequests.requestsInProgress();
        }

        @Override
        public boolean mouseReleased(int mouseX, int mouseY, int state) {
            return ProfileRequests.requestsInProgress();
        }

        @Override
        public boolean keyTyped(char typedChar, int keyCode) {
            return ProfileRequests.requestsInProgress();
        }
    }
}
