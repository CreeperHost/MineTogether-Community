package net.creeperhost.minetogethercommunity.activity;

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancementManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.client.resources.IResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ActivityTelemetry {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("mt-activity-telemetry")
            .setDaemon(true)
            .build());

    private static final long PREFERENCE_REFRESH_MS = 5L * 60L * 1000L;
    private static final long HEARTBEAT_MS = 60L * 1000L;
    private static final long ADVANCEMENT_SCAN_MS = 1000L;
    private static final long RETRY_BASE_MS = 5L * 1000L;
    private static final long RETRY_MAX_MS = 5L * 60L * 1000L;
    private static final int MAX_PENDING_BATCHES = 128;
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    private static final Set<String> COMPLETED_ADVANCEMENTS = new HashSet<>();
    private static ActivityModels.QueueState state;
    private static Field advancementProgressField;
    private static boolean advancementProgressFieldMissing;
    private static boolean advancementSnapshotSeen;
    private static volatile boolean enabled = true;
    private static volatile boolean initialized;
    private static volatile boolean preferenceRequestRunning;
    private static volatile boolean visibilityRequestRunning;
    private static volatile boolean visibilitySaveRunning;
    private static volatile boolean flushRunning;
    private static volatile boolean sendingDisabled;
    private static volatile String profileVisibility = "public";
    private static volatile String currentAuthKey = "";
    private static int consecutiveFailures;
    private static long nextFlushAllowedAt;
    private static long lastPreferenceRequest;
    private static long lastHeartbeat;
    private static long lastAdvancementScan;
    private static long playtimeStarted;
    private static boolean inWorld;
    private static boolean englishLanguageLoadAttempted;
    private static Map<String, String> englishTranslations;

    private ActivityTelemetry() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        state = freshState();
        if (!currentAuthKey.isEmpty()) {
            applyAuthKey(currentAuthKey);
        }
        refreshPreference();
        refreshProfileVisibility();
    }

    public static void authChanged(Object token) {
        currentAuthKey = authKey(token);
        if (state == null) return;
        applyAuthKey(currentAuthKey);
        lastPreferenceRequest = 0;
        refreshPreference();
        refreshProfileVisibility();
        flush();
    }

    public static void clientTick() {
        if (state == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPreferenceRequest > PREFERENCE_REFRESH_MS) {
            refreshPreference();
        }
        if (inWorld && now - lastAdvancementScan >= ADVANCEMENT_SCAN_MS) {
            lastAdvancementScan = now;
            scanClientAdvancements();
        }
        if (inWorld && now - lastHeartbeat >= HEARTBEAT_MS) {
            queuePlaytime(now);
            playtimeStarted = now;
            lastHeartbeat = now;
            flush();
        } else if (hasPending()) {
            flush();
        }
    }

    public static void onWorldEnter() {
        if (state == null) return;
        long now = System.currentTimeMillis();
        inWorld = true;
        playtimeStarted = now;
        lastHeartbeat = now;
        lastAdvancementScan = 0;
        advancementSnapshotSeen = false;
        synchronized (COMPLETED_ADVANCEMENTS) {
            COMPLETED_ADVANCEMENTS.clear();
        }
    }

    public static void onWorldExit() {
        if (state == null) return;
        long now = System.currentTimeMillis();
        if (inWorld) {
            queuePlaytime(now);
            inWorld = false;
            playtimeStarted = 0;
        }
        advancementSnapshotSeen = false;
        synchronized (COMPLETED_ADVANCEMENTS) {
            COMPLETED_ADVANCEMENTS.clear();
        }
        flushSync();
    }

    public static void queueQuest(String questId, String title, String description, String iconItemId) {
        if (!enabled || shouldSkipTelemetry()) return;

        ActivityModels.Metadata metadata = new ActivityModels.Metadata();
        metadata.type = "quest";
        metadata.provider = "ftbquests";
        metadata.contentId = safe(questId);
        metadata.locale = "en_us";
        metadata.titleEn = safe(title);
        metadata.descriptionEn = safe(description);
        metadata.iconItemId = safe(iconItemId);
        metadata.metadataRef = hash("quest:ftbquests:" + metadata.contentId + ":" + metadata.titleEn + ":" + metadata.descriptionEn);

        ActivityModels.QuestEvent event = new ActivityModels.QuestEvent();
        event.metadataRef = metadata.metadataRef;
        event.provider = "ftbquests";
        event.completedAt = System.currentTimeMillis();
        event.source = "incremental";
        event.eventId = hash(state.clientSessionId + ":" + currentWorld().key + ":" + modpackIdentity() + ":" + metadata.contentId);

        ActivityModels.Batch batch = newBaseBatch();
        batch.metadata.add(metadata);
        batch.questCompletions.add(event);
        queue(batch);
        flush();
    }

    public static void queueAdvancement(String advancementId, String title, String description, String iconItemId, String source) {
        if (!enabled || shouldSkipTelemetry()) return;

        ActivityModels.Metadata metadata = new ActivityModels.Metadata();
        metadata.type = "advancement";
        metadata.provider = "vanilla";
        metadata.contentId = safe(advancementId);
        metadata.locale = "en_us";
        metadata.titleEn = safe(title);
        metadata.descriptionEn = safe(description);
        metadata.iconItemId = safe(iconItemId);
        metadata.metadataRef = hash("advancement:vanilla:" + metadata.contentId + ":" + metadata.titleEn + ":" + metadata.descriptionEn);

        ActivityModels.AdvancementEvent event = new ActivityModels.AdvancementEvent();
        event.metadataRef = metadata.metadataRef;
        event.completedAt = System.currentTimeMillis();
        event.source = source == null || source.isEmpty() ? "incremental" : source;
        event.eventId = hash(state.clientSessionId + ":" + currentWorld().key + ":" + modpackIdentity() + ":" + metadata.contentId);

        ActivityModels.Batch batch = newBaseBatch();
        batch.metadata.add(metadata);
        batch.advancements.add(event);
        queue(batch);
        flush();
    }

    private static void scanClientAdvancements() {
        if (!enabled || shouldSkipTelemetry()) return;
        if (state == null || state.authKey == null || state.authKey.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.connection == null) return;

        Map<Advancement, AdvancementProgress> progress = advancementProgress(mc.player.connection.getAdvancementManager());
        if (progress == null || progress.isEmpty()) return;

        String source = advancementSnapshotSeen ? "incremental" : "snapshot";
        for (Map.Entry<Advancement, AdvancementProgress> entry : progress.entrySet()) {
            Advancement advancement = entry.getKey();
            AdvancementProgress advancementProgress = entry.getValue();
            if (advancement == null || advancementProgress == null || !advancementProgress.isDone()) continue;

            String id = advancementId(advancement);
            if (id.isEmpty() || id.startsWith("minecraft:recipes/")) continue;

            synchronized (COMPLETED_ADVANCEMENTS) {
                if (COMPLETED_ADVANCEMENTS.contains(id)) continue;
            }
            if (queueAdvancement(advancement, source)) {
                synchronized (COMPLETED_ADVANCEMENTS) {
                    COMPLETED_ADVANCEMENTS.add(id);
                }
            }
        }
        advancementSnapshotSeen = true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Advancement, AdvancementProgress> advancementProgress(ClientAdvancementManager manager) {
        if (manager == null || advancementProgressFieldMissing) return null;
        try {
            if (advancementProgressField == null) {
                advancementProgressField = findField(ClientAdvancementManager.class, "advancementToProgress", "field_192803_d");
            }
            return (Map<Advancement, AdvancementProgress>) advancementProgressField.get(manager);
        } catch (Throwable t) {
            advancementProgressFieldMissing = true;
            LOGGER.warn("MineTogether activity telemetry cannot inspect 1.12 advancement progress; advancement telemetry will be limited.", t);
            return null;
        }
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getName());
    }

    private static boolean queueAdvancement(Advancement advancement, String source) {
        if (!enabled || shouldSkipTelemetry()) return false;

        ActivityModels.Metadata metadata = metadataForAdvancement(advancement);
        if (metadata == null) return false;

        ActivityModels.AdvancementEvent event = new ActivityModels.AdvancementEvent();
        event.metadataRef = metadata.metadataRef;
        event.completedAt = System.currentTimeMillis();
        event.source = source == null || source.isEmpty() ? "incremental" : source;
        event.eventId = hash(state.clientSessionId + ":" + currentWorld().key + ":" + modpackIdentity() + ":" + metadata.contentId);

        ActivityModels.Batch batch = newBaseBatch();
        batch.metadata.add(metadata);
        batch.advancements.add(event);
        queue(batch);
        flush();
        return true;
    }

    private static ActivityModels.Metadata metadataForAdvancement(Advancement advancement) {
        if (advancement == null) return null;

        String id = advancementId(advancement);
        if (id.isEmpty() || id.startsWith("minecraft:recipes/")) return null;

        DisplayInfo display = advancement.getDisplay();
        if (display == null) return null;

        ActivityModels.Metadata metadata = new ActivityModels.Metadata();
        metadata.type = "advancement";
        metadata.provider = "vanilla";
        metadata.contentId = id;
        metadata.locale = "en_us";
        fillDisplay(metadata, display);
        metadata.metadataRef = hash(metadata.type + ":" + metadata.provider + ":" + metadata.contentId + ":" + metadata.titleKey + ":" + metadata.descriptionKey + ":" + metadata.titleEn + ":" + metadata.descriptionEn);
        return metadata;
    }

    private static void fillDisplay(ActivityModels.Metadata metadata, DisplayInfo display) {
        ITextComponent title = display.getTitle();
        ITextComponent description = display.getDescription();
        metadata.titleKey = translationKey(title);
        metadata.descriptionKey = translationKey(description);
        metadata.titleEn = englishText(title);
        metadata.descriptionEn = englishText(description);
        metadata.titleComponentJson = componentJson(title);
        metadata.descriptionComponentJson = componentJson(description);

        ItemStack icon = display.getIcon();
        if (icon != null && !icon.isEmpty()) {
            ResourceLocation itemId = Item.REGISTRY.getNameForObject(icon.getItem());
            metadata.iconItemId = itemId == null ? "" : itemId.toString();
        }
        if (display.getFrame() != null) {
            metadata.frameOrType = display.getFrame().getName();
        }
    }

    private static String advancementId(Advancement advancement) {
        ResourceLocation id = advancement == null ? null : advancement.getId();
        return id == null ? "" : id.toString();
    }

    private static String translationKey(ITextComponent component) {
        return component instanceof TextComponentTranslation ? ((TextComponentTranslation) component).getKey() : "";
    }

    private static String text(ITextComponent component) {
        return component == null ? "" : component.getUnformattedText();
    }

    private static String englishText(ITextComponent component) {
        if (!(component instanceof TextComponentTranslation)) {
            return text(component);
        }

        TextComponentTranslation translated = (TextComponentTranslation) component;
        String fallback = text(component);
        String pattern = englishTranslations().get(translated.getKey());
        if (pattern == null || pattern.isEmpty()) {
            return fallback;
        }

        Object[] args = translated.getFormatArgs();
        Object[] formatted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            formatted[i] = arg instanceof ITextComponent ? englishText((ITextComponent) arg) : String.valueOf(arg);
        }

        try {
            return String.format(Locale.ROOT, pattern, formatted);
        } catch (IllegalFormatException ignored) {
            return fallback;
        }
    }

    private static Map<String, String> englishTranslations() {
        if (!englishLanguageLoadAttempted) {
            englishLanguageLoadAttempted = true;
            englishTranslations = loadEnglishTranslations();
        }
        return englishTranslations == null ? new HashMap<String, String>() : englishTranslations;
    }

    private static Map<String, String> loadEnglishTranslations() {
        Map<String, String> translations = new HashMap<String, String>();
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("minecraft", "lang/en_us.lang"));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
                    int equals = trimmed.indexOf('=');
                    if (equals <= 0) continue;
                    translations.put(trimmed.substring(0, equals), trimmed.substring(equals + 1));
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to load en_us language resources for MineTogether activity metadata.", ex);
        }
        return translations;
    }

    private static String componentJson(ITextComponent component) {
        return component == null ? "" : ITextComponent.Serializer.componentToJson(component);
    }

    public static boolean isProfileVisibilityBusy() {
        return visibilityRequestRunning || visibilitySaveRunning;
    }

    public static boolean isProfileVisibilityLoading() {
        return visibilityRequestRunning;
    }

    public static boolean isProfileVisibilitySaving() {
        return visibilitySaveRunning;
    }

    public static String getProfileVisibility() {
        return profileVisibility;
    }

    public static void refreshProfileVisibility() {
        if (visibilityRequestRunning || state == null || state.authKey == null || state.authKey.isEmpty()) return;
        visibilityRequestRunning = true;
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GetProfileVisibilityRequest.Response response = MineTogether.API.execute(new GetProfileVisibilityRequest()).apiResponse();
                    profileVisibility = normalizeVisibility(response.visibility);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to fetch MineTogether profile visibility", t);
                } finally {
                    visibilityRequestRunning = false;
                }
            }
        });
    }

    public static void setProfileVisibility(final String visibility) {
        if (visibilitySaveRunning || state == null || state.authKey == null || state.authKey.isEmpty()) return;
        visibilitySaveRunning = true;
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PutProfileVisibilityRequest.Response response = MineTogether.API.execute(new PutProfileVisibilityRequest(normalizeVisibility(visibility))).apiResponse();
                    profileVisibility = normalizeVisibility(response.visibility);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to save MineTogether profile visibility", t);
                } finally {
                    visibilitySaveRunning = false;
                }
            }
        });
    }

    public static void setTelemetryPreference(final boolean telemetryEnabled) {
        LocalConfig.instance().activityTelemetry = telemetryEnabled;
        LocalConfig.save();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PutTelemetryPreferencesRequest.Response response = MineTogether.API.execute(new PutTelemetryPreferencesRequest(telemetryEnabled)).apiResponse();
                    enabled = response.enabled;
                    LocalConfig.instance().activityTelemetry = response.enabled;
                    LocalConfig.save();
                    if (!enabled) {
                        synchronized (ActivityTelemetry.class) {
                            state.pending.clear();
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to save MineTogether telemetry preference", t);
                }
            }
        });
    }

    private static void refreshPreference() {
        if (preferenceRequestRunning || state == null || state.authKey == null || state.authKey.isEmpty()) return;
        lastPreferenceRequest = System.currentTimeMillis();
        preferenceRequestRunning = true;
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GetTelemetryPreferencesRequest.Response response = MineTogether.API.execute(new GetTelemetryPreferencesRequest()).apiResponse();
                    enabled = response.enabled;
                    LocalConfig.instance().activityTelemetry = response.enabled;
                    LocalConfig.save();
                    if (!enabled) {
                        synchronized (ActivityTelemetry.class) {
                            state.pending.clear();
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to fetch MineTogether telemetry preference", t);
                } finally {
                    preferenceRequestRunning = false;
                }
            }
        });
    }

    private static boolean shouldSkipTelemetry() {
        if (!LocalConfig.instance().activityTelemetry) return true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.capabilities.isCreativeMode) return true;
        return mc.isSingleplayer() && mc.world != null && mc.world.getWorldInfo().areCommandsAllowed();
    }

    private static void queuePlaytime(long now) {
        if (!enabled || shouldSkipTelemetry() || playtimeStarted <= 0 || now <= playtimeStarted) return;
        int deltaSeconds = (int) Math.min((now - playtimeStarted) / 1000L, 600L);
        if (deltaSeconds <= 0) return;

        ActivityModels.Batch batch = newBaseBatch();
        batch.playtime = new ActivityModels.Playtime();
        batch.playtime.from = playtimeStarted;
        batch.playtime.to = now;
        batch.playtime.deltaSeconds = deltaSeconds;
        queue(batch);
    }

    private static synchronized void queue(ActivityModels.Batch batch) {
        if (state == null || state.authKey == null || state.authKey.isEmpty()) return;
        batch.sequence = state.nextSequence++;
        batch.sentAt = System.currentTimeMillis();
        state.pending.add(batch);
        while (state.pending.size() > MAX_PENDING_BATCHES) {
            state.pending.remove(0);
        }
    }

    private static void flush() {
        if (!markFlushRunning(false)) return;
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = drainQueue();
                } catch (Throwable t) {
                    LOGGER.warn("MineTogether activity telemetry flush failed", t);
                } finally {
                    recordFlushResult(success);
                    flushRunning = false;
                }
            }
        });
    }

    private static void flushSync() {
        try {
            EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    if (!markFlushRunning(true)) return;
                    boolean success = false;
                    try {
                        success = drainQueue();
                    } catch (Throwable ignored) {
                    } finally {
                        recordFlushResult(success);
                        flushRunning = false;
                    }
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static synchronized boolean markFlushRunning(boolean ignoreBackoff) {
        if (!enabled || flushRunning || sendingDisabled) return false;
        if (state == null || state.authKey == null || state.authKey.isEmpty()) return false;
        if (!ignoreBackoff && System.currentTimeMillis() < nextFlushAllowedAt) return false;
        flushRunning = true;
        return true;
    }

    private static synchronized void recordFlushResult(boolean success) {
        if (success) {
            consecutiveFailures = 0;
            nextFlushAllowedAt = 0;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            sendingDisabled = true;
            nextFlushAllowedAt = 0;
            LOGGER.warn("MineTogether activity telemetry disabled for this session after {} failed sends", consecutiveFailures);
            return;
        }
        long delay = Math.min(RETRY_BASE_MS << Math.min(consecutiveFailures - 1, 20), RETRY_MAX_MS);
        nextFlushAllowedAt = System.currentTimeMillis() + delay;
    }

    private static synchronized void resetBackoff() {
        consecutiveFailures = 0;
        nextFlushAllowedAt = 0;
        sendingDisabled = false;
    }

    private static boolean drainQueue() throws Exception {
        while (true) {
            ActivityModels.Batch batch;
            synchronized (ActivityTelemetry.class) {
                if (state.pending.isEmpty()) return true;
                batch = state.pending.get(0);
            }
            ApiClientResponse<PostActivityBatchRequest.Response> response = MineTogether.API.execute(new PostActivityBatchRequest(batch));
            PostActivityBatchRequest.Response body = response.apiResponse();
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300
                    && body != null
                    && "success".equals(body.getStatus())
                    && body.accepted;
            if (!accepted) {
                LOGGER.warn("MineTogether activity telemetry batch rejected: status={} message={}", response.statusCode(), response.message());
                return false;
            }
            synchronized (ActivityTelemetry.class) {
                state.pending.remove(0);
            }
        }
    }

    private static synchronized boolean hasPending() {
        return state != null && !state.pending.isEmpty();
    }

    private static ActivityModels.Batch newBaseBatch() {
        ActivityModels.Batch batch = new ActivityModels.Batch();
        batch.clientSessionId = state.clientSessionId;
        batch.modpack = currentModpack();
        batch.world = currentWorld();
        return batch;
    }

    private static ActivityModels.Modpack currentModpack() {
        ActivityModels.Modpack modpack = new ActivityModels.Modpack();
        ModPackInfo.VersionInfo info = ModPackInfo.getInfo();
        if (!info.ftbPackID.isEmpty()) {
            modpack.source = "ftb";
            modpack.packId = info.ftbPackID;
            modpack.versionId = info.base64FTBID;
        } else if (!info.curseID.isEmpty()) {
            modpack.source = "curse";
            modpack.packId = info.curseID;
        }
        modpack.websiteId = info.websiteID;
        modpack.minecraftVersion = "1.12.2";
        modpack.loader = "forge";
        modpack.modVersion = MineTogether.VERSION;
        return modpack;
    }

    private static String modpackIdentity() {
        ActivityModels.Modpack modpack = currentModpack();
        return modpack.source + ":" + modpack.packId + ":" + modpack.versionId + ":" + modpack.websiteId + ":" + modpack.minecraftVersion + ":" + modpack.loader + ":" + modpack.modVersion;
    }

    private static ActivityModels.World currentWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        ActivityModels.World world = new ActivityModels.World();
        if (mc.isSingleplayer()) {
            world.kind = "singleplayer";
            String worldName = mc.world == null ? "unknown" : mc.world.getWorldInfo().getWorldName();
            world.key = hash("singleplayer:" + worldName);
        } else {
            ServerData server = mc.getCurrentServerData();
            world.kind = "multiplayer";
            world.key = hash("multiplayer:" + (server == null ? "unknown" : server.serverIP));
        }
        return world;
    }

    private static ActivityModels.QueueState freshState() {
        ActivityModels.QueueState fresh = new ActivityModels.QueueState();
        fresh.clientSessionId = UUID.randomUUID().toString();
        return fresh;
    }

    private static synchronized void applyAuthKey(String authKey) {
        if (state == null) return;
        if (authKey == null || authKey.isEmpty()) {
            state.pending.clear();
            state.authKey = "";
            return;
        }
        if (!authKey.equals(state.authKey)) {
            state.pending.clear();
            state.clientSessionId = UUID.randomUUID().toString();
            state.nextSequence = 1;
            state.authKey = authKey;
            resetBackoff();
        }
    }

    private static String authKey(Object token) {
        if (token == null) return "";
        String compact = token.toString();
        String[] parts = compact.split("\\.");
        if (parts.length < 2) return "";
        try {
            String payload = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
            Map<?, ?> claims = GSON.fromJson(payload, Map.class);
            Object sha = claims == null ? null : claims.get("sha");
            if (sha == null && claims != null) sha = claims.get("sub");
            return sha == null ? "" : hash(sha.toString());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) return value;
        StringBuilder builder = new StringBuilder(value);
        for (int i = 0; i < 4 - remainder; i++) {
            builder.append('=');
        }
        return builder.toString();
    }

    private static String hash(String value) {
        return Hashing.sha256().hashString(value == null ? "" : value, StandardCharsets.UTF_8).toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeVisibility(String visibility) {
        if ("private".equals(visibility) || "friends".equals(visibility) || "friends_of_friends".equals(visibility)) {
            return visibility;
        }
        return "public";
    }
}
