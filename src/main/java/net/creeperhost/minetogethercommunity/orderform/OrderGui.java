package net.creeperhost.minetogethercommunity.orderform;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogether.lib.util.Countries;
import net.creeperhost.minetogether.lib.web.ApiResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiList;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiScrollPanel;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextField;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.creeperhost.minetogethercommunity.modulargui.ItemSelectDialog;
import net.creeperhost.minetogethercommunity.modulargui.OptionDialog;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.creeperhost.minetogethercommunity.orderform.data.Order;
import net.creeperhost.minetogethercommunity.orderform.data.OrderSummary;
import net.creeperhost.minetogethercommunity.orderform.requests.GetDataCentresRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostLoginRequest;
import net.creeperhost.minetogethercommunity.orderform.requests.PostOrderRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class OrderGui implements GuiProvider {

    private static final int MARGIN = 5;
    private static final int GUTTER = 5;
    private static final int CONTROL_HEIGHT = 14;
    private static final int FIELD_HEIGHT = 14;
    private static final int DETAIL_FIELD_HEIGHT = 12;
    private static final int DETAIL_FIELD_GAP = 4;
    private static final int RAIL_WIDTH = 170;
    private static final int RAIL_MIN_WIDTH = 126;
    private static final int PRICE_HEIGHT = 48;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2,
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Order Requests %d").build());
    private static final ExecutorService PING_EXECUTOR = Executors.newFixedThreadPool(8,
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Order Ping %d").build());
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\".+\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9]{3,16}");
    private static final Random RAND = new Random();

    private final boolean suggestWorld;
    private final Order order = new Order();
    private final Map<String, String> dcIdMap = new ConcurrentHashMap<>();
    private final Map<String, GetDataCentresRequest.DC> dcMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> dcPing = new ConcurrentHashMap<>();
    private final Map<String, Long> dcDistance = new ConcurrentHashMap<>();

    private GuiElement<?> root;
    private GuiList<GetDataCentresRequest.DC> locationList;
    private GuiButton quoteButton;
    private GuiButton placeButton;
    private GuiButton worldButton;
    private GuiButton loginButton;
    private GuiButton processingButton;
    private GuiButton processingCloseButton;
    private GuiTextField nameField;

    private CompletableFuture<?> initTask;
    private CompletableFuture<?> pingTask;
    private CompletableFuture<?> summaryTask;
    private CompletableFuture<?> orderTask;
    private WorldUploader worldUploader;

    private volatile boolean initialized;
    private volatile boolean pingUpdated;
    private volatile boolean nameChecking;
    private volatile boolean emailChecking;
    private volatile boolean nameValid;
    private volatile boolean emailValid;
    private volatile boolean loginMode;
    private volatile boolean loggingIn;
    private volatile boolean loggedIn;
    private volatile boolean summaryUpdateRequired = true;
    private volatile boolean summaryUpdating;
    private volatile boolean inputsValid;
    private volatile boolean processing;
    private volatile boolean processingButtonEnabled;
    private volatile boolean processingShowCloseButton;

    private volatile String nameMessage = "";
    private volatile String emailMessage = "";
    private volatile String loginMessage = "";
    private volatile String invalidMessage = "";
    private volatile String orderMessage = "";
    private volatile String processingText = "";
    private volatile String processingButtonText = "";
    private volatile Runnable processingAction = null;
    private volatile OrderSummary summary = new OrderSummary("Loading Summary...");

    private String confirmPassword = "";
    private String promoCode = "";
    private String invoiceID = "";
    private int nameCheckTimer = 20;
    private int emailCheckTimer = 20;
    private int pingTimer;
    private boolean worldSuggestionDismissed;

    public OrderGui() {
        this(false);
    }

    public OrderGui(boolean suggestWorld) {
        this.suggestWorld = suggestWorld;
        order.name = getDefaultName();
        order.country = "US";
        nameDirty();
        emailDirty();
    }

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        if (initTask == null && !initialized) {
            startInit();
        }

        GuiElement<?> root = new GuiElement<>(gui);
        this.root = root;
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = MARGIN;
        int top = MARGIN;
        int width = Math.max(320, screenWidth - MARGIN * 2);
        int height = Math.max(220, screenHeight - MARGIN * 2);
        int railWidth = railWidth(width);
        int orderWidth = width - railWidth - GUTTER;
        int railLeft = left + orderWidth + GUTTER;
        int panelTop = top + 22;
        int panelBottom = top + height - MARGIN;
        int placeTop = panelBottom - CONTROL_HEIGHT;
        int priceTop = placeTop - PRICE_HEIGHT - 2;
        int summaryBottom = priceTop - 8;
        int panelHeight = panelBottom - panelTop;
        int summaryHeight = Math.max(42, summaryBottom - panelTop);

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> I18n.format("minetogether.gui.order.title")).centered().setBounds(left, top, width, 12);
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back_arrow"))
                .setBounds(left, top + 3, 50, CONTROL_HEIGHT)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));

        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, panelTop, orderWidth, panelHeight);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(railLeft, panelTop, railWidth, summaryHeight);
        new GuiRectangle(root, 0xE0000000).setBounds(railLeft, priceTop, railWidth, PRICE_HEIGHT);

        GuiScrollPanel configScroll = new GuiScrollPanel(root).setBounds(left, panelTop, orderWidth, panelHeight);
        int configBottom = buildConfigPanel(configScroll, left + 5, panelTop + 4, orderWidth - 14);
        configScroll.setContentBottom(configBottom + 8);
        GuiScrollPanel summaryScroll = new GuiScrollPanel(root).setBounds(railLeft, panelTop, railWidth, summaryHeight);
        int railContentBottom = buildSummaryPanel(summaryScroll, railLeft + 4, panelTop + 4, railWidth - 12);
        summaryScroll.setContentBottom(railContentBottom + 8);
        buildPricePanel(root, railLeft + 3, priceTop + 4, railWidth - 6);

        placeButton = new GuiButton(root, this::getOrderButtonText)
                .primary()
                .setBounds(railLeft, placeTop, railWidth, CONTROL_HEIGHT)
                .onPress(this::confirmPlaceOrder);

        buildProcessingOverlay(root, gui, screenWidth, screenHeight);
        refreshLocationList();
        return root;
    }

    private int buildConfigPanel(GuiElement<?> root, int left, int top, int width) {
        new GuiText(root, () -> I18n.format("minetogether.gui.order.configure")).setColor(0xFFD060).setBounds(left, top, width, 12);

        int y = top + 16;
        int randomWidth = Math.min(70, Math.max(52, width / 4));
        int nameLabelWidth = width < 230 ? 64 : 88;
        int nameFieldWidth = Math.max(40, width - nameLabelWidth - randomWidth - 4);
        new GuiText(root, () -> I18n.format("minetogether.gui.order.server_name")).setColor(0xCCCCCC).setBounds(left, y + 3, nameLabelWidth, 12);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left + nameLabelWidth, y, nameFieldWidth, FIELD_HEIGHT);
        nameField = new GuiTextField(root)
                .setBounds(left + nameLabelWidth, y, nameFieldWidth, FIELD_HEIGHT)
                .setText(order.name)
                .setMaxLength(16)
                .onChanged(value -> {
                    order.name = value.trim();
                    nameDirty();
                });
        new GuiButton(root, () -> I18n.format("minetogether.gui.order.button.randomize"))
                .setBounds(left + width - randomWidth, y, randomWidth, CONTROL_HEIGHT)
                .onPress(() -> {
                    order.name = getDefaultName();
                    if (nameField != null) nameField.setText(order.name);
                    nameDirty();
                });
        int nameStatusHeight = 30;
        new GuiText(root, this::nameStatusText).setWrap(true).setScale(0.82F).setColor(0xAAAAAA).setBounds(left + nameLabelWidth, y + 16, width - nameLabelWidth, nameStatusHeight);

        y += 20 + nameStatusHeight;
        new GuiText(root, () -> I18n.format("minetogether.gui.order.player_count")).setColor(0xCCCCCC).setBounds(left, y, width, 12);
        y += 12;
        int playerInfoHeight = wrappedHeight(I18n.format("minetogether.gui.order.player_count.info"), width, 0.82F, 18, 46);
        new GuiText(root, () -> I18n.format("minetogether.gui.order.player_count.info"))
                .setWrap(true)
                .setScale(0.82F)
                .setColor(0xAAAAAA)
                .setBounds(left, y, width, playerInfoHeight);
        y += playerInfoHeight + 4;
        int halfButtonWidth = (width - 2) / 2;
        addPlayerButton(root, left, y, halfButtonWidth, 5);
        addPlayerButton(root, left + halfButtonWidth + 2, y, halfButtonWidth, 10);
        y += CONTROL_HEIGHT + 1;
        addPlayerButton(root, left, y, halfButtonWidth, 15);
        addPlayerButton(root, left + halfButtonWidth + 2, y, halfButtonWidth, 20);
        y += CONTROL_HEIGHT + 1;
        addPlayerButton(root, left + (width - halfButtonWidth) / 2, y, halfButtonWidth, 25);

        y += CONTROL_HEIGHT + 10;
        new GuiText(root, () -> I18n.format("minetogether.gui.order.location")).setColor(0xFFD060).setBounds(left, y, width, 12);
        new GuiText(root, this::locationStatusText).setColor(0xAAAAAA).setBounds(left + 86, y, width - 86, 12);
        locationList = new GuiList<GetDataCentresRequest.DC>(root)
                .setRowHeight(14)
                .setDisplayBuilder((parent, dc) -> new GuiButton(parent, () -> dcLabel(dc))
                        .setToggleMode(() -> StringUtils.equals(dc.slug, order.serverLocation))
                        .onPress(() -> {
                            order.serverLocation = dc.slug;
                            summaryDirty();
                            refreshLocationList();
                        }))
                .setBounds(left, y + 12, width, 92);

        y += 110;
        new GuiButton(root, () -> toggleText("minetogether.gui.order.fallback", order.useFallback))
                .setBounds(left, y, (width - 6) / 3, CONTROL_HEIGHT)
                .setToggleMode(() -> order.useFallback)
                .onPress(() -> order.useFallback = !order.useFallback);
        new GuiButton(root, () -> toggleText("minetogether.gui.order.pregen", order.pregen))
                .setBounds(left + (width - 6) / 3 + 3, y, (width - 6) / 3, CONTROL_HEIGHT)
                .setToggleMode(() -> order.pregen)
                .onPress(() -> order.pregen = !order.pregen);
        new GuiButton(root, () -> I18n.format("minetogether.gui.order.refresh_locations"))
                .setBounds(left + ((width - 6) / 3 + 3) * 2, y, (width - 6) / 3, CONTROL_HEIGHT)
                .onPress(() -> {
                    initialized = false;
                    startInit();
                });

        y += 24;
        new GuiText(root, () -> I18n.format("minetogether.gui.order.account")).setColor(0xFFD060).setBounds(left, y, 70, 12);
        int emailStatusHeight = 30;
        new GuiText(root, this::emailStatusText).setWrap(true).setScale(0.82F).setColor(0xAAAAAA).setBounds(left + 74, y, width - 74, emailStatusHeight);
        y += Math.max(16, emailStatusHeight + 2);
        boolean compact = width < 430;
        GuiElement<?> newAccountFields = new GuiElement<>(root).setVisible(() -> !loginMode);
        if (compact) {
            addPlaceholderField(root, left, y, width, "minetogether.info.e_mail", order.emailAddress, value -> {
                order.emailAddress = value.trim();
                emailDirty();
            });
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(root, left, y, width - 58, "minetogether.info.password", order.password, value -> {
                order.password = value;
                resetLoginState(false);
            }).setPasswordMode(true);
            loginButton = new GuiButton(root, this::loginButtonText)
                    .setBounds(left + width - 54, y, 54, DETAIL_FIELD_HEIGHT)
                    .primary()
                    .setVisible(false)
                    .onPress(this::doLogin);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.password_confirm", confirmPassword, value -> confirmPassword = value)
                    .setPasswordMode(true);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.first_name", order.firstName, value -> order.firstName = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.last_name", order.lastName, value -> order.lastName = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.address", order.address, value -> order.address = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.city", order.city, value -> order.city = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.zip", order.zip, value -> order.zip = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.state", order.state, value -> order.state = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addCountryButton(newAccountFields, left, y, width);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, width, "minetogether.info.phone", order.phone, value -> order.phone = value);
        } else {
            int halfGap = 4;
            int half = (width - halfGap) / 2;
            addPlaceholderField(root, left, y, width, "minetogether.info.e_mail", order.emailAddress, value -> {
                order.emailAddress = value.trim();
                emailDirty();
            });
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(root, left, y, half, "minetogether.info.password", order.password, value -> {
                order.password = value;
                resetLoginState(false);
            }).setPasswordMode(true);
            loginButton = new GuiButton(root, this::loginButtonText)
                    .setBounds(left + half + halfGap, y, half, DETAIL_FIELD_HEIGHT)
                    .primary()
                    .setVisible(false)
                    .onPress(this::doLogin);
            addPlaceholderField(newAccountFields, left + half + halfGap, y, half, "minetogether.info.password_confirm", confirmPassword, value -> confirmPassword = value)
                    .setPasswordMode(true);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, half, "minetogether.info.first_name", order.firstName, value -> order.firstName = value);
            addPlaceholderField(newAccountFields, left + half + halfGap, y, half, "minetogether.info.last_name", order.lastName, value -> order.lastName = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, half, "minetogether.info.address", order.address, value -> order.address = value);
            addPlaceholderField(newAccountFields, left + half + halfGap, y, half, "minetogether.info.city", order.city, value -> order.city = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addPlaceholderField(newAccountFields, left, y, half, "minetogether.info.zip", order.zip, value -> order.zip = value);
            addPlaceholderField(newAccountFields, left + half + halfGap, y, half, "minetogether.info.state", order.state, value -> order.state = value);
            y += DETAIL_FIELD_HEIGHT + DETAIL_FIELD_GAP;
            addCountryButton(newAccountFields, left, y, half);
            addPlaceholderField(newAccountFields, left + half + halfGap, y, half, "minetogether.info.phone", order.phone, value -> order.phone = value);
        }
        y += DETAIL_FIELD_HEIGHT + 10;
        return buildWorldPanel(root, left, y, width);
    }

    private int buildSummaryPanel(GuiElement<?> root, int left, int top, int width) {
        new GuiText(root, () -> I18n.format("minetogether.gui.order.summary")).setColor(0xFFD060).setBounds(left, top, width, 12);
        int y = top + 16;
        y = addWrappedSummaryText(root, this::summaryLineOne, left, y, width, 0xA8FF90, 18, 40, 4);
        y = addWrappedSummaryText(root, this::summaryLocationLine, left, y, width, 0xA8FF90, 18, 42, 6);

        new GuiText(root, () -> I18n.format("minetogether.gui.order.summary.plan")).setColor(0xCCCCCC).setBounds(left, y, width, 12);
        y += 12;
        y = addWrappedSummaryText(root, this::summaryLineTwo, left, y, width, 0xA8FF90, 14, 32, 6);

        new GuiText(root, () -> I18n.format("minetogether.gui.order.summary.features")).setColor(0xCCCCCC).setBounds(left, y, width, 12);
        y += 12;
        for (int i = 0; i < 5; i++) {
            final int index = i;
            y = addWrappedSummaryText(root, () -> summaryFeatureLine(index), left, y, width, 0xA8FF90, 10, 24, 1);
        }
        y += 4;

        y = addWrappedSummaryText(root, () -> I18n.format("minetogether.gui.order.summary.paying_for"), left, y, width, 0xCCCCCC, 18, 42, 3);
        y = addWrappedSummaryText(root, () -> I18n.format("minetogether.gui.order.summary.paying_for_details1"), left, y, width, 0xA8FF90, 14, 32, 3);
        y = addWrappedSummaryText(root, () -> I18n.format("minetogether.gui.order.summary.paying_for_details2"), left, y, width, 0xA8FF90, 18, 46, 3);
        y = addWrappedSummaryText(root, () -> I18n.format("minetogether.gui.order.summary.cancel_any_time"), left, y, width, 0xCCCCCC, 14, 32, 6);

        new GuiText(root, () -> I18n.format("minetogether.gui.order.promo")).setColor(0xCCCCCC).setBounds(left, y + 3, 42, 12);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left + 42, y, width - 42, FIELD_HEIGHT);
        new GuiTextField(root)
                .setBounds(left + 42, y, width - 42, FIELD_HEIGHT)
                .setText(promoCode)
                .onChanged(value -> {
                    promoCode = value.trim();
                    summaryDirty();
                });
        y += 18;

        y = addWrappedText(root, this::orderStatusText, left, y, width, 0.82F, 0xFFFFAA, 28, 58, 0);
        return y + 4;
    }

    private int addWrappedSummaryText(GuiElement<?> root, Supplier<String> text, int left, int y, int width,
                                      int color, int minHeight, int maxHeight, int gap) {
        return addWrappedText(root, text, left, y, width, 0.72F, color, minHeight, maxHeight, gap);
    }

    private int addWrappedText(GuiElement<?> root, Supplier<String> text, int left, int y, int width,
                               float scale, int color, int minHeight, int maxHeight, int gap) {
        int height = maxHeight;
        new GuiText(root, text).setWrap(true).setScale(scale).setColor(color).setBounds(left, y, width, height);
        return y + height + gap;
    }

    private void buildPricePanel(GuiElement<?> root, int left, int top, int width) {
        priceLine(root, left, top, width,
                () -> I18n.format("minetogether.gui.order.summary.sub_total"),
                () -> moneyOrBlank(summary == null ? 0.0D : summary.preDiscount),
                0xFFFFFF);
        priceLine(root, left, top + 10, width,
                () -> I18n.format("minetogether.gui.order.summary.tax"),
                () -> moneyOrBlank(summary == null ? 0.0D : summary.tax),
                0xFFFFFF);
        priceLine(root, left, top + 20, width,
                () -> I18n.format("minetogether.gui.order.summary.discount"),
                () -> moneyOrBlank(summary == null ? 0.0D : summary.discount),
                0xFFFFFF);
        priceLine(root, left, top + 34, width,
                () -> I18n.format("minetogether.gui.order.summary.total_plain"),
                () -> moneyOrBlank(summary == null ? 0.0D : summary.total),
                0xA8FF90);
    }

    private void priceLine(GuiElement<?> root, int left, int top, int width, Supplier<String> label, Supplier<String> value, int valueColor) {
        int valueWidth = Math.min(82, Math.max(54, width / 2));
        int labelWidth = Math.max(20, width - valueWidth - 4);
        new GuiText(root, label).setScale(0.75F).setBounds(left, top, labelWidth, 10);
        new GuiText(root, value).rightAligned().setScale(0.75F).setColor(valueColor).setBounds(left + labelWidth + 4, top, valueWidth, 10);
    }

    @Override
    public void tick(ModularGui gui) {
        if (initTask != null && initTask.isDone()) {
            initTask = null;
            refreshLocationList();
            summaryDirty();
        }

        updateNameCheck();
        updateEmailCheck();
        updatePings();
        updateSummary();
        updateWorldUpload();

        if (orderTask != null && orderTask.isDone()) {
            orderTask = null;
        }

        validateInputs();
        if (quoteButton != null) quoteButton.setEnabled(!summaryUpdating);
        if (placeButton != null) placeButton.setEnabled(inputsValid && orderTask == null && !summaryUpdating && !summaryUpdateRequired);
        if (worldButton != null) worldButton.setEnabled(worldButtonEnabled());
        if (loginButton != null) {
            loginButton.setVisible(loginMode);
            loginButton.setEnabled(loginMode && !loggingIn && !loggedIn && StringUtils.isNotBlank(order.password));
        }
        updateProcessingControls();
    }

    private void updateProcessingControls() {
        if (processingButton != null) {
            processingButton.setVisible(processing && StringUtils.isNotBlank(processingButtonText));
            processingButton.setEnabled(processing && processingButtonEnabled);
        }
        if (processingCloseButton != null) {
            processingCloseButton.setVisible(processing && processingShowCloseButton);
            processingCloseButton.setEnabled(processing && processingShowCloseButton);
        }
    }

    private void buildProcessingOverlay(GuiElement<?> root, ModularGui gui, int screenWidth, int screenHeight) {
        ProcessingOverlay overlay = new ProcessingOverlay(root);
        overlay.setBounds(0, 0, screenWidth, screenHeight);
        int buttonWidth = 100;
        int buttonLeft = (screenWidth - buttonWidth) / 2;
        int buttonTop = screenHeight / 2 + 5;

        new GuiRectangle(overlay, 0xE0000000).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(overlay, () -> processingText)
                .centered()
                .setWrap(true)
                .setBounds(10, screenHeight / 2 - 48, screenWidth - 20, 48);
        processingButton = new GuiButton(overlay, () -> processingButtonText)
                .primary()
                .setBounds(buttonLeft, buttonTop, buttonWidth, 14)
                .onPress(() -> {
                    Runnable action = processingAction;
                    if (action != null) action.run();
                });
        processingCloseButton = new GuiButton(overlay, () -> I18n.format("minetogether.gui.button.close"))
                .setBounds(buttonLeft, buttonTop + 24, buttonWidth, 14)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));
    }

    private void setProcessing(String buttonText, Runnable action, String text) {
        processing = true;
        processingText = StringUtils.defaultString(text);
        processingButtonText = StringUtils.defaultString(buttonText);
        processingAction = action;
        processingButtonEnabled = action != null && StringUtils.isNotBlank(buttonText);
        processingShowCloseButton = false;
    }

    private void clearProcessing() {
        processing = false;
        processingText = "";
        processingButtonText = "";
        processingAction = null;
        processingButtonEnabled = false;
        processingShowCloseButton = false;
    }

    private GuiTextField addPlaceholderField(GuiElement<?> root, int left, int y, int fieldWidth, String key, String initialValue, Consumer<String> changed) {
        fieldWidth = Math.max(24, fieldWidth);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, y, fieldWidth, DETAIL_FIELD_HEIGHT);
        return new GuiTextField(root)
                .setBounds(left + 3, y + 2, Math.max(1, fieldWidth - 6), DETAIL_FIELD_HEIGHT - 3)
                .setSuggestion(() -> I18n.format(key))
                .setSuggestionColor(0xFFFFFF)
                .setText(initialValue)
                .onChanged(changed);
    }

    private GuiButton addCountryButton(GuiElement<?> root, int left, int y, int fieldWidth) {
        fieldWidth = Math.max(24, fieldWidth);
        return new GuiButton(root, this::selectedCountryName)
                .setBounds(left, y, fieldWidth, DETAIL_FIELD_HEIGHT)
                .onPress(() -> showCountrySelector(root));
    }

    private void showCountrySelector(GuiElement<?> parent) {
        Map<String, Country> countryMap = getCountries();
        GuiElement<?> dialogParent = root == null ? parent : root;
        new ItemSelectDialog<Country>(dialogParent,
                () -> I18n.format("minetogether.gui.order.select_country"),
                new ArrayList<Country>(countryMap.values()),
                countryMap.get(order.country))
                .setLabel(country -> country == null ? "" : country.toString())
                .setOnItemSelected(country -> {
                    if (country != null) {
                        order.country = country.key();
                        summaryDirty();
                    }
                })
                .setCloseOnOutsideClick(true);
    }

    private String selectedCountryName() {
        return getSelectedCountry().toString();
    }

    public Country getSelectedCountry() {
        return new Country(order.country, Countries.COUNTRIES.get(order.country));
    }

    private Map<String, Country> getCountries() {
        Map<String, Country> map = new LinkedHashMap<String, Country>();
        for (Map.Entry<String, String> entry : Countries.COUNTRIES.entrySet()) {
            map.put(entry.getKey(), new Country(entry.getKey(), entry.getValue()));
        }
        return map;
    }

    private int buildWorldPanel(GuiElement<?> root, int left, int top, int width) {
        int y = top;
        new GuiText(root, () -> I18n.format("minetogether.gui.order.world")).setColor(0xFFD060).setBounds(left, y, width, 12);
        y += 14;

        int infoHeight = wrappedHeight(I18n.format("minetogether.gui.order.world.info"), width, 0.82F, 18, 46);
        new GuiText(root, () -> I18n.format("minetogether.gui.order.world.info"))
                .setWrap(true)
                .setScale(0.82F)
                .setColor(0xAAAAAA)
                .setBounds(left, y, width, infoHeight);
        y += infoHeight + 4;

        int labelWidth = width < 230 ? 54 : 72;
        new GuiText(root, () -> I18n.format("minetogether.gui.order.world_url")).setColor(0xCCCCCC).setBounds(left, y + 3, labelWidth, 12);
        int fieldWidth = Math.max(24, width - labelWidth);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left + labelWidth, y, fieldWidth, FIELD_HEIGHT);
        new GuiTextField(root)
                .setBounds(left + labelWidth, y, fieldWidth, FIELD_HEIGHT)
                .setText(order.worldUrl)
                .onChanged(value -> order.worldUrl = value.trim());
        y += 18;

        worldButton = new GuiButton(root, this::worldUploadText)
                .setBounds(left, y, width, CONTROL_HEIGHT)
                .onPress(this::worldUploadAction);
        y += CONTROL_HEIGHT + 4;

        int statusHeight = 58;
        new GuiText(root, this::worldStatusText).setWrap(true).setScale(0.82F).setColor(0xFFFFAA).setBounds(left, y, width, statusHeight);
        return y + statusHeight + 4;
    }

    private void addPlayerButton(GuiElement<?> root, int left, int y, int width, int count) {
        new GuiButton(root, () -> I18n.format("minetogether.gui.order.player_count." + count))
                .setBounds(left, y, width, CONTROL_HEIGHT)
                .setToggleMode(() -> order.playerAmount == count)
                .onPress(() -> {
                    order.playerAmount = count;
                    summaryDirty();
                });
    }

    private int railWidth(int screenContentWidth) {
        int third = screenContentWidth / 3;
        if (screenContentWidth < 380) {
            return Math.min(148, Math.max(RAIL_MIN_WIDTH, third));
        }
        return Math.min(RAIL_WIDTH, Math.max(148, third));
    }

    private int wrappedHeight(String text, int width, float scale, int minHeight, int maxHeight) {
        int fitWidth = Math.max(1, Math.round(width / Math.max(0.25F, scale)));
        int lines = Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(StringUtils.defaultString(text), fitWidth).size();
        int height = Math.max(1, Math.round(lines * Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT * Math.max(0.25F, scale)));
        return Math.max(minHeight, Math.min(maxHeight, height));
    }

    private void startInit() {
        if (initTask != null) return;
        initTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                dcIdMap.clear();
                dcMap.clear();
                dcPing.clear();
                dcDistance.clear();

                OrderRequests.getLocations().forEach((dc, id) -> dcIdMap.put(dc, String.valueOf(id)));
                List<GetDataCentresRequest.DC> centers = OrderRequests.getDataCenters(512);
                for (GetDataCentresRequest.DC dc : centers) {
                    if (StringUtils.isNotBlank(dc.slug)) {
                        dcMap.put(dc.slug, dc);
                        dcPing.put(dc.slug, -1);
                    }
                }

                GetClosestDCRequest.Response byDistance = OrderRequests.getDCsByDistance();
                if (byDistance != null) {
                    if (byDistance.getDataCenter() != null && StringUtils.isNotBlank(byDistance.getDataCenter().getName())) {
                        order.serverLocation = byDistance.getDataCenter().getName();
                    }
                    if (byDistance.getDataCenters() != null) {
                        byDistance.getDataCenters().forEach(dc -> dcDistance.put(dc.getName(), dc.getDistance()));
                    }
                }

                OrderRequests.initDefaults(order);
                if (StringUtils.isBlank(order.serverLocation) && !dcMap.isEmpty()) {
                    order.serverLocation = dcMap.keySet().iterator().next();
                }
                initialized = true;
                pingUpdated = true;
            }
        }, EXECUTOR);
    }

    private void updateNameCheck() {
        if (nameValid || nameChecking || nameCheckTimer <= 0 || --nameCheckTimer > 0) return;
        if (StringUtils.isBlank(order.name)) {
            nameMessage = I18n.format("minetogether.gui.order.blank.name");
            return;
        }
        if (!SERVER_NAME_PATTERN.matcher(order.name).matches()) {
            nameMessage = I18n.format("minetogether.gui.order.name_invalid");
            return;
        }

        nameChecking = true;
        nameMessage = I18n.format("minetogether.gui.order.name_checking");
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                ApiResponse response = OrderRequests.getNameAvailable(order.name);
                nameValid = response != null && "success".equals(response.getStatus());
                nameMessage = response == null ? I18n.format("minetogether.gui.order.name_error") : response.getMessage();
                nameChecking = false;
            }
        }, EXECUTOR);
    }

    private void updateEmailCheck() {
        if (emailValid || emailChecking || emailCheckTimer <= 0 || --emailCheckTimer > 0) return;
        if (StringUtils.isBlank(order.emailAddress)) {
            emailMessage = I18n.format("minetogether.gui.order.blank.email");
            return;
        }
        if (!EMAIL_PATTERN.matcher(order.emailAddress).matches()) {
            emailMessage = I18n.format("minetogether.gui.order.email_invalid");
            return;
        }

        emailChecking = true;
        emailMessage = I18n.format("minetogether.gui.order.email_checking");
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                loginMode = OrderRequests.doesAccountExist(order.emailAddress);
                resetLoginState(true);
                emailValid = true;
                emailMessage = loginMode
                        ? I18n.format("minetogether.gui.order.email_existing")
                        : I18n.format("minetogether.gui.order.email_new");
                emailChecking = false;
            }
        }, EXECUTOR);
    }

    private void updatePings() {
        if (!initialized) return;
        if (pingUpdated) {
            pingUpdated = false;
            refreshLocationList();
        }
        if (pingTask != null && pingTask.isDone()) {
            pingTask = null;
            refreshLocationList();
            return;
        }
        if (pingTask != null || pingTimer-- > 0 || dcMap.isEmpty()) return;

        pingTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                List<CompletableFuture<?>> tasks = new ArrayList<>();
                for (final GetDataCentresRequest.DC dc : dcMap.values()) {
                    if (StringUtils.isBlank(dc.slug)) continue;
                    dcPing.put(dc.slug, -1);
                    tasks.add(CompletableFuture.runAsync(new Runnable() {
                        @Override
                        public void run() {
                            long distance = dcDistance.getOrDefault(dc.slug, -1L);
                            if (StringUtils.isBlank(dc.latencyUrl) || distance < 0) {
                                dcPing.put(dc.slug, -2);
                            } else {
                                dcPing.put(dc.slug, OrderRequests.getDCLatency(dc.latencyUrl, distance));
                            }
                            pingUpdated = true;
                        }
                    }, PING_EXECUTOR));
                }
                CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
                pingTimer = 200;
            }
        }, EXECUTOR);
    }

    private void updateSummary() {
        if (summaryUpdateRequired && !summaryUpdating) {
            summaryUpdating = true;
            summaryUpdateRequired = false;
            summaryTask = CompletableFuture.runAsync(new Runnable() {
                @Override
                public void run() {
                    summary = OrderRequests.getSummary(order, getPromoCode());
                    if (StringUtils.isBlank(summary.summaryError)) {
                        order.productID = summary.productID;
                        order.currency = summary.currency;
                        for (GetDataCentresRequest.DC dc : OrderRequests.getDataCenters(summary.ram + 4096)) {
                            if (StringUtils.isNotBlank(dc.slug)) {
                                dcMap.put(dc.slug, dc);
                                dcPing.putIfAbsent(dc.slug, -1);
                            }
                        }
                    }
                    summaryUpdating = false;
                    pingUpdated = true;
                }
            }, EXECUTOR);
        }
        if (summaryTask != null && summaryTask.isDone()) {
            summaryTask = null;
        }
    }

    private void validateInputs() {
        inputsValid = false;
        if (!nameValid) {
            invalidMessage = nameStatusText();
        } else if (!emailValid) {
            invalidMessage = emailStatusText();
        } else if (StringUtils.isBlank(order.password)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.password");
        } else if (loginMode && !loggedIn) {
            invalidMessage = loginStatusText();
        } else if (!loginMode && !StringUtils.equals(order.password, confirmPassword)) {
            invalidMessage = I18n.format("minetogether.gui.order.passwords_dont_match");
        } else if (!loginMode && StringUtils.isBlank(order.firstName)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.first_name");
        } else if (!loginMode && StringUtils.isBlank(order.lastName)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.last_name");
        } else if (!loginMode && StringUtils.isBlank(order.address)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.address");
        } else if (!loginMode && StringUtils.isBlank(order.city)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.city");
        } else if (!loginMode && StringUtils.isBlank(order.state)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.state");
        } else if (!loginMode && StringUtils.isBlank(order.zip)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.zip");
        } else if (!loginMode && StringUtils.isBlank(order.phone)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.phone");
        } else if (StringUtils.isBlank(order.serverLocation)) {
            invalidMessage = I18n.format("minetogether.gui.order.blank.location");
        } else if (worldUploader != null && worldUploader.running()) {
            invalidMessage = I18n.format("minetogether.gui.order.waiting_for_world_upload");
        } else {
            inputsValid = true;
            invalidMessage = "";
        }
    }

    private void nameDirty() {
        nameValid = false;
        nameChecking = false;
        nameCheckTimer = 30;
        nameMessage = I18n.format("minetogether.gui.order.name_not_checked");
    }

    private void emailDirty() {
        emailValid = false;
        emailChecking = false;
        emailCheckTimer = 30;
        emailMessage = I18n.format("minetogether.gui.order.email_not_checked");
        resetLoginState(true);
    }

    private void resetLoginState(boolean clearClient) {
        loggingIn = false;
        loggedIn = false;
        loginMessage = "";
        if (clearClient) {
            order.clientID = "";
        }
    }

    private String loginButtonText() {
        if (loggingIn) return I18n.format("minetogether.gui.order.logging_in");
        if (loggedIn) return I18n.format("minetogether.gui.order.login_success");
        return I18n.format("minetogether.gui.button.login");
    }

    private String loginStatusText() {
        if (!loginMode) return "";
        if (loggingIn) return I18n.format("minetogether.gui.order.logging_in");
        if (loggedIn) return I18n.format("minetogether.gui.order.login_success");
        if (StringUtils.isNotBlank(loginMessage)) return loginMessage;
        return I18n.format("minetogether.gui.order.login_required");
    }

    private void doLogin() {
        if (!loginMode || loggingIn || loggedIn) return;
        loggingIn = true;
        loginMessage = "";
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                PostLoginRequest.Response response = OrderRequests.doLogin(order.emailAddress, order.password);
                if (response != null && "success".equals(response.getStatus())) {
                    order.clientID = StringUtils.defaultIfBlank(response.userid, "0");
                    order.currency = StringUtils.defaultIfBlank(response.currency, order.currency);
                    loggedIn = true;
                    loginMessage = I18n.format("minetogether.gui.order.login_success");
                    summaryDirty();
                } else {
                    loggedIn = false;
                    loginMessage = I18n.format("minetogether.gui.order.login_error",
                            response == null ? "Unknown Error" : response.getMessage());
                }
                loggingIn = false;
            }
        }, EXECUTOR);
    }

    private void summaryDirty() {
        summaryUpdateRequired = true;
        orderMessage = "";
    }

    private void refreshLocationList() {
        if (locationList != null) {
            locationList.setValues(displayedDataCenters());
        }
    }

    private List<GetDataCentresRequest.DC> displayedDataCenters() {
        List<GetDataCentresRequest.DC> result = new ArrayList<>(dcMap.values());
        result.removeIf(dc -> StringUtils.isBlank(dc.slug) || dc.slug.endsWith("(value)"));
        Collections.sort(result, new Comparator<GetDataCentresRequest.DC>() {
            @Override
            public int compare(GetDataCentresRequest.DC left, GetDataCentresRequest.DC right) {
                return Integer.compare(dcSortScore(left), dcSortScore(right));
            }
        });
        if (result.size() > 6) {
            return new ArrayList<>(result.subList(0, 6));
        }
        return result;
    }

    private int dcSortScore(GetDataCentresRequest.DC dc) {
        int ping = dcPing.getOrDefault(dc.slug, -1);
        long distance = dcDistance.getOrDefault(dc.slug, -1L);
        int score = dc.available ? 0 : 100000;
        if (StringUtils.equals(order.serverLocation, dc.slug)) score -= 10000;
        if (ping > 0) return score + ping;
        if (distance > 0) return score + (int) Math.min(distance, 9000);
        return score + 50000;
    }

    private String dcLabel(GetDataCentresRequest.DC dc) {
        String selected = StringUtils.equals(dc.slug, order.serverLocation) ? "* " : "";
        String country = StringUtils.defaultIfBlank(dc.countryName, dc.country);
        String name = StringUtils.defaultIfBlank(dc.name, dc.slug);
        int ping = dcPing.getOrDefault(dc.slug, -1);
        long distance = dcDistance.getOrDefault(dc.slug, -1L);
        String signal;
        if (!dc.available) {
            signal = I18n.format("minetogether.gui.order.low_availability");
        } else if (ping > 0) {
            signal = ping + "ms";
        } else if (ping == -1) {
            signal = I18n.format("minetogether.gui.order.region.pinging");
        } else if (distance > 0) {
            signal = I18n.format("minetogether.gui.order.region.distance", distance);
        } else {
            signal = I18n.format("minetogether.gui.order.region.pinging_fail");
        }
        return selected + name + (StringUtils.isBlank(country) ? "" : ", " + country) + " - " + signal;
    }

    private void confirmPlaceOrder() {
        if (orderTask != null) return;
        if (shouldSuggestWorldUpload()) {
            showWorldUploadSuggestion();
            return;
        }
        validateInputs();
        if (!inputsValid) {
            orderMessage = invalidMessage;
            return;
        }

        if (root == null) {
            placeOrder();
            return;
        }

        final OptionDialog[] dialog = new OptionDialog[1];
        dialog[0] = new OptionDialog(root,
                () -> I18n.format("minetogether.gui.order.place_order"),
                () -> I18n.format("minetogether.gui.order.place_order.confirm"),
                () -> I18n.format("minetogether.gui.button.confirm"),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog[0].setVisible(false);
                        placeOrder();
                    }
                },
                () -> I18n.format("minetogether.gui.button.cancel"),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog[0].setVisible(false);
                    }
                });
    }

    private void placeOrder() {
        if (orderTask != null) return;
        validateInputs();
        if (!inputsValid) {
            orderMessage = invalidMessage;
            return;
        }

        orderMessage = I18n.format("minetogether.gui.order.order_placing");
        setProcessing(null, null, orderMessage);
        orderTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                PostOrderRequest.Response response = OrderRequests.placeOrder(order, getDCId(order.serverLocation), order.pregen ? "1" : "0", computeFallbackLocation());
                if (response != null && "success".equals(response.getStatus())) {
                    String invoice = response.more == null || response.more.invoiceid == null ? "" : response.more.invoiceid;
                    String orderId = response.more == null || response.more.orderid == null ? "" : response.more.orderid;
                    orderMessage = I18n.format("minetogether.gui.order.placed", orderId, invoice);
                    invoiceID = invoice;
                    if (StringUtils.isNotBlank(invoiceID)) {
                        setProcessing(I18n.format("minetogether.gui.button.invoice"), new Runnable() {
                            @Override
                            public void run() {
                                openPaymentPage(invoiceID);
                            }
                        }, I18n.format("minetogether.gui.order.order_success"));
                    } else {
                        setProcessing(I18n.format("minetogether.gui.button.ok"), new Runnable() {
                            @Override
                            public void run() {
                                clearProcessing();
                            }
                        }, orderMessage);
                    }
                    processingShowCloseButton = true;
                } else {
                    orderMessage = I18n.format("minetogether.gui.order.place_error", response == null ? "Unknown Error" : StringUtils.defaultString(response.getMessage(), "Unknown Error"));
                    setProcessing(I18n.format("minetogether.gui.button.ok"), new Runnable() {
                        @Override
                        public void run() {
                            clearProcessing();
                        }
                    }, orderMessage);
                }
            }
        }, EXECUTOR);
    }

    private boolean shouldSuggestWorldUpload() {
        return suggestWorld
                && !worldSuggestionDismissed
                && root != null
                && worldUploader == null
                && StringUtils.isBlank(order.worldUrl)
                && worldButtonEnabled();
    }

    private void showWorldUploadSuggestion() {
        final OptionDialog[] dialog = new OptionDialog[1];
        dialog[0] = new OptionDialog(root,
                () -> I18n.format("minetogether.gui.order.world.upload_suggestion.title"),
                () -> I18n.format("minetogether.gui.order.world.upload_suggestion"),
                () -> I18n.format("minetogether.gui.order.world.upload_current"),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog[0].setVisible(false);
                        worldUploadAction();
                    }
                },
                () -> I18n.format("minetogether.gui.order.world.skip_upload"),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog[0].setVisible(false);
                        worldSuggestionDismissed = true;
                        confirmPlaceOrder();
                    }
                });
    }

    private String getOrderButtonText() {
        if (orderTask != null) return I18n.format("minetogether.gui.order.order_in_progress");
        if (summaryUpdating || summaryUpdateRequired) return I18n.format("minetogether.gui.order.summary.updating");
        if (inputsValid) return I18n.format("minetogether.gui.order.place_order");
        return StringUtils.defaultIfBlank(invalidMessage, I18n.format("minetogether.gui.order.place_order"));
    }

    private String nameStatusText() {
        if (nameChecking) return I18n.format("minetogether.gui.order.name_checking");
        if (nameValid) return StringUtils.defaultIfBlank(nameMessage, I18n.format("minetogether.gui.order.name_available"));
        return StringUtils.defaultIfBlank(nameMessage, I18n.format("minetogether.gui.order.name_not_checked"));
    }

    private String emailStatusText() {
        if (emailChecking) return I18n.format("minetogether.gui.order.email_checking");
        if (emailValid) return StringUtils.defaultIfBlank(emailMessage, I18n.format("minetogether.gui.order.email_ready"));
        return StringUtils.defaultIfBlank(emailMessage, I18n.format("minetogether.gui.order.email_not_checked"));
    }

    private String locationStatusText() {
        if (!initialized) return I18n.format("minetogether.gui.order.loading_locations");
        if (dcMap.isEmpty()) return I18n.format("minetogether.gui.order.loading_locations_fail");
        return I18n.format("minetogether.gui.order.locations_loaded", dcMap.size());
    }

    private String summaryLineOne() {
        if (orderTask != null) return I18n.format("minetogether.gui.order.order_placing");
        if (summaryUpdating || summaryUpdateRequired) return I18n.format("minetogether.gui.order.summary.loading");
        if (summary == null) return I18n.format("minetogether.gui.order.summary.empty");
        if (StringUtils.isNotBlank(summary.summaryError)) return summary.summaryError;
        return summary.serverHostName + " - " + summary.ram + "MB RAM";
    }

    private String summaryLineTwo() {
        if (summaryUpdating || summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return StringUtils.defaultIfBlank(summary.serverHostName, I18n.format("minetogether.gui.order.summary.ready"));
    }

    private String summaryLineThree() {
        if (summaryUpdating || summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        if (summary.serverFeatures.isEmpty()) return I18n.format("minetogether.gui.order.summary.ready");
        return summary.serverFeatures.get(0);
    }

    private String summaryFeatureLine(int index) {
        if (summaryUpdating || summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        if (summary.serverFeatures != null && index >= 0 && index < summary.serverFeatures.size()) {
            return summary.serverFeatures.get(index);
        }
        return I18n.format("minetogether.gui.order.summary.feature" + (index + 1));
    }

    private String summaryLocationLine() {
        if (StringUtils.isBlank(order.serverLocation)) return "";
        String fallback = computeFallbackLocation();
        if (StringUtils.isBlank(fallback)) {
            return I18n.format("minetogether.gui.order.summary.location_value", getDCName(order.serverLocation));
        }
        return I18n.format("minetogether.gui.order.summary.location_fallback", getDCName(order.serverLocation), getDCName(fallback));
    }

    private String subTotalLine() {
        if (summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return I18n.format("minetogether.gui.order.summary.sub_total") + " " + moneyWithCurrency(summary.preDiscount);
    }

    private String taxLine() {
        if (summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return I18n.format("minetogether.gui.order.summary.tax") + " " + moneyWithCurrency(summary.tax);
    }

    private String discountLine() {
        if (summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return I18n.format("minetogether.gui.order.summary.discount") + " " + moneyWithCurrency(summary.discount);
    }

    private String totalLine() {
        if (summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return I18n.format("minetogether.gui.order.summary.total_plain") + " " + moneyWithCurrency(summary.total);
    }

    private String moneyOrBlank(double value) {
        if (summary == null || StringUtils.isNotBlank(summary.summaryError)) return "";
        return moneyWithCurrency(value);
    }

    private String moneyWithCurrency(double value) {
        String amount = moneyAmount(value);
        String currency = currencyDisplay();
        if (StringUtils.isBlank(currency)) return amount;
        return amount + " " + currency;
    }

    private String moneyAmount(double value) {
        if (summary == null) return MONEY.format(value);
        String suffix = StringUtils.defaultString(summary.suffix);
        String displayCurrency = currencyDisplay();
        if (StringUtils.equalsIgnoreCase(suffix, displayCurrency)) {
            suffix = "";
        }
        return StringUtils.defaultString(summary.prefix) + MONEY.format(value) + suffix;
    }

    private String currencyDisplay() {
        if (summary == null) return "";
        if (StringUtils.isNotBlank(summary.currencyCode)) return summary.currencyCode;
        return StringUtils.isNumeric(summary.currency) ? "" : StringUtils.defaultString(summary.currency);
    }

    private String orderStatusText() {
        if (worldUploader != null && worldUploader.errored()) return worldUploader.getError();
        if (worldUploader != null && worldUploader.running()) return worldUploadText();
        if (StringUtils.isNotBlank(orderMessage)) return orderMessage;
        return inputsValid ? I18n.format("minetogether.gui.order.ready") : invalidMessage;
    }

    private String worldStatusText() {
        if (worldUploader != null && worldUploader.errored()) return worldUploader.getError();
        if (worldUploader != null && worldUploader.running()) return worldUploadText();
        if (StringUtils.isNotBlank(order.worldUrl)) return I18n.format("minetogether.gui.order.world.upload_complete");
        if (!worldButtonEnabled()) return I18n.format("minetogether.gui.order.world.no_current_world");
        return "";
    }

    private void updateWorldUpload() {
        if (worldUploader == null) return;
        if (worldUploader.isFinished()) {
            order.worldUrl = StringUtils.defaultString(worldUploader.getResultFileURL());
            orderMessage = I18n.format("minetogether.gui.order.world.upload_complete");
            worldUploader = null;
        } else if (worldUploader.errored()) {
            orderMessage = worldUploader.getError();
        }
    }

    private boolean worldButtonEnabled() {
        if (worldUploader != null) return true;
        if (StringUtils.isNotBlank(order.worldUrl)) return true;
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.getIntegratedServer() != null;
    }

    private String worldUploadText() {
        if (worldUploader != null) {
            if (worldUploader.errored()) return I18n.format("minetogether.gui.order.world.retry");
            if (worldUploader.running()) {
                if ("minetogether.gui.order.upload_stage.upload".equals(worldUploader.getStatus())) {
                    return I18n.format(worldUploader.getStatus(), Math.round(worldUploader.getUploadProgress() * 10000D) / 100D);
                }
                return I18n.format(worldUploader.getStatus());
            }
        }
        if (StringUtils.isNotBlank(order.worldUrl)) return I18n.format("minetogether.gui.order.world.remove");
        return I18n.format("minetogether.gui.order.world.upload_current");
    }

    private void worldUploadAction() {
        if (worldUploader != null && worldUploader.running()) {
            worldUploader.cancel();
            worldUploader = null;
            orderMessage = I18n.format("minetogether.gui.order.world.cancelled");
            return;
        }
        if (worldUploader != null && worldUploader.errored()) {
            worldUploader.start();
            return;
        }
        if (StringUtils.isNotBlank(order.worldUrl)) {
            order.worldUrl = "";
            orderMessage = I18n.format("minetogether.gui.order.world.removed");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getIntegratedServer() == null) {
            orderMessage = I18n.format("minetogether.gui.order.world.no_current_world");
            return;
        }
        File worldFolder = new File(new File(MineTogether.getGameDir(), "saves"), mc.getIntegratedServer().getFolderName());
        if (!worldFolder.isDirectory()) {
            orderMessage = I18n.format("minetogether.gui.order.world.no_current_world");
            return;
        }
        worldUploader = new WorldUploader(worldFolder.toPath());
        worldUploader.start();
    }

    private String toggleText(String key, boolean enabled) {
        return I18n.format(key) + (enabled ? I18n.format("minetogether.gui.settings.state.on") : I18n.format("minetogether.gui.settings.state.off"));
    }

    private String getPromoCode() {
        return StringUtils.defaultIfBlank(promoCode, "minetogetherer");
    }

    private String getDCId(String dc) {
        return dcIdMap.getOrDefault(dc, dc);
    }

    private String getDCName(String dc) {
        GetDataCentresRequest.DC info = dcMap.get(dc);
        return info == null ? dc : StringUtils.defaultIfBlank(info.name, dc);
    }

    private String computeFallbackLocation() {
        if (!order.useFallback) return "";
        String fallback = "";
        long lowest = Integer.MAX_VALUE;
        for (String dc : dcPing.keySet()) {
            int ping = dcPing.get(dc);
            if (ping > 0 && !StringUtils.equals(dc, order.serverLocation) && ping < lowest) {
                lowest = ping;
                fallback = dc;
            }
        }
        if (StringUtils.isNotBlank(fallback)) return fallback;

        for (String dc : dcDistance.keySet()) {
            long distance = dcDistance.get(dc);
            if (distance > 0 && !StringUtils.equals(dc, order.serverLocation) && distance < lowest) {
                lowest = distance;
                fallback = dc;
            }
        }
        return fallback;
    }

    private static String getDefaultName() {
        String[] first = {"amber", "angel", "spirit", "basin", "lagoon", "arrow", "autumn", "beach", "bell", "black", "bone", "boulder", "bridge", "castle", "cave", "clear", "cloud", "crystal", "dawn", "deep", "dragon", "dusk", "ember", "ever", "frost", "ghost", "gold", "green", "hollow", "iron", "lake", "light", "mist", "moon", "north", "ocean", "pine", "river", "rose", "shadow", "silver", "snow", "south", "spring", "star", "stone", "storm", "summer", "sun", "swift", "west", "white", "wild", "wind", "winter"};
        String[] second = {"acre", "barrow", "bay", "borough", "bourne", "brook", "burgh", "cairn", "cliff", "coast", "dale", "den", "field", "ford", "forest", "glen", "grove", "harbor", "haven", "helm", "hill", "hold", "hollow", "keep", "land", "meadow", "moor", "peak", "point", "reach", "rest", "rock", "shire", "shore", "spire", "summit", "town", "vale", "view", "wall", "watch", "water", "well", "wick", "wind", "wood"};
        return first[RAND.nextInt(first.length)] + second[RAND.nextInt(second.length)] + RAND.nextInt(999);
    }

    private static void openOrderPage() {
        openUri("https://www.creeperhost.net/mcm/order");
    }

    private static void openPaymentPage(String invoiceID) {
        openUri("https://billing.creeperhost.net/viewinvoice.php?id=" + invoiceID);
    }

    private class ProcessingOverlay extends GuiElement<ProcessingOverlay> {
        private ProcessingOverlay(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        public void tick() {
            setVisible(processing);
            setEnabled(processing);
            updateProcessingControls();
            super.tick();
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
            setVisible(processing);
            setEnabled(processing);
            updateProcessingControls();
            super.render(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            if (!processing) return false;
            updateProcessingControls();
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }

        @Override
        public boolean mouseReleased(int mouseX, int mouseY, int state) {
            if (!processing) return false;
            updateProcessingControls();
            super.mouseReleased(mouseX, mouseY, state);
            return true;
        }

        @Override
        public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
            if (!processing) return false;
            updateProcessingControls();
            super.mouseInput(mouseX, mouseY, dWheel);
            return true;
        }

        @Override
        public boolean keyTyped(char typedChar, int keyCode) throws IOException {
            if (!processing) return false;
            updateProcessingControls();
            super.keyTyped(typedChar, keyCode);
            return true;
        }
    }

    private static void openUri(String uri) {
        try {
            KeycloakOAuth.openURL(new URL(uri));
        } catch (MalformedURLException ignored) {
        }
    }

    public static class Country {
        private final String key;
        private final String name;

        public Country(String key, String name) {
            this.key = key;
            this.name = name;
        }

        public String key() {
            return key;
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name == null ? "" : name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Country country = (Country) other;
            return Objects.equals(key, country.key) && Objects.equals(name, country.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, name);
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new OrderGui(), parentScreen);
        }

        public Screen(GuiScreen parentScreen, boolean suggestWorld) {
            super(new OrderGui(suggestWorld), parentScreen);
        }
    }
}
