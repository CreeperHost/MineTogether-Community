package net.creeperhost.minetogethercommunity.activity;

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.injectables.targets.ArchitecturyTarget;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityTelemetry {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("mt-activity-telemetry")
            .setDaemon(true)
            .build());

    private static final long PREFERENCE_REFRESH_MS = 5 * 60 * 1000L;
    private static final long HEARTBEAT_MS = 60 * 1000L;
    private static final int MAX_PENDING_BATCHES = 128;

    private static final Map<String, Object> KNOWN_ADVANCEMENTS = new HashMap<>();
    private static ActivityModels.QueueState state;
    private static volatile boolean enabled = true;
    private static volatile boolean preferenceRequestRunning = false;
    private static volatile boolean flushRunning = false;
    private static volatile String currentAuthKey = "";
    private static long lastPreferenceRequest = 0;
    private static long lastHeartbeat = 0;
    private static long playtimeStarted = 0;
    private static boolean inWorld = false;
    private static boolean englishLanguageLoadAttempted = false;
    private static ClientLanguage englishLanguage;

    public static void init() {
        state = freshState();
        if (!currentAuthKey.isEmpty()) {
            applyAuthKey(currentAuthKey);
        }
        ClientTickEvent.CLIENT_POST.register(ActivityTelemetry::tick);
        ClientLifecycleEvent.CLIENT_STOPPING.register(ActivityTelemetry::stopping);
        refreshPreference();
    }

    public static void authChanged(Object token) {
        currentAuthKey = authKey(token);
        if (state == null) return;
        applyAuthKey(currentAuthKey);
        lastPreferenceRequest = 0;
        refreshPreference();
        flush();
    }

    public static void handleAdvancementPacket(ClientboundUpdateAdvancementsPacket packet) {
        for (Object holder : asIterable(call(packet, "getAdded", "added"))) {
            Object id = call(holder, "id", "getId");
            if (id != null) {
                KNOWN_ADVANCEMENTS.put(id.toString(), holder);
            }
        }
        for (Object removed : asIterable(call(packet, "getRemoved", "removed"))) {
            KNOWN_ADVANCEMENTS.remove(removed.toString());
        }
        Map<?, ?> progress = asMap(call(packet, "getProgress", "progress"));
        boolean reset = Boolean.TRUE.equals(call(packet, "shouldReset", "reset"));
        for (Map.Entry<?, ?> entry : progress.entrySet()) {
            if (Boolean.TRUE.equals(call(entry.getValue(), "isDone", "done"))) {
                String id = entry.getKey().toString();
                Object holder = KNOWN_ADVANCEMENTS.get(id);
                queueAdvancement(id, holder, reset ? "snapshot" : "incremental");
            }
        }
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastPreferenceRequest > PREFERENCE_REFRESH_MS) {
            refreshPreference();
        }

        boolean currentlyInWorld = mc.level != null && mc.player != null;
        if (currentlyInWorld && !inWorld) {
            inWorld = true;
            playtimeStarted = now;
            lastHeartbeat = now;
        } else if (!currentlyInWorld && inWorld) {
            queuePlaytime(now);
            inWorld = false;
            playtimeStarted = 0;
            flush();
        } else if (currentlyInWorld && now - lastHeartbeat >= HEARTBEAT_MS) {
            queuePlaytime(now);
            playtimeStarted = now;
            lastHeartbeat = now;
            flush();
        } else if (hasPending()) {
            flush();
        }
    }

    private static void stopping(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (inWorld) {
            queuePlaytime(now);
            inWorld = false;
            playtimeStarted = 0;
        }
        flushBlocking();
    }

    private static void refreshPreference() {
        if (preferenceRequestRunning) return;
        lastPreferenceRequest = System.currentTimeMillis();
        preferenceRequestRunning = true;
        EXECUTOR.execute(() -> {
            try {
                GetTelemetryPreferencesRequest.Response response = MineTogether.API.execute(new GetTelemetryPreferencesRequest()).apiResponse();
                enabled = response.enabled;
                if (!enabled) {
                    synchronized (ActivityTelemetry.class) {
                        state.pending.clear();
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                preferenceRequestRunning = false;
            }
        });
    }

    private static void queuePlaytime(long now) {
        if (!enabled || playtimeStarted <= 0 || now <= playtimeStarted) return;
        int deltaSeconds = (int) Math.min((now - playtimeStarted) / 1000L, 600L);
        if (deltaSeconds <= 0) return;

        ActivityModels.Batch batch = newBaseBatch();
        batch.playtime = new ActivityModels.Playtime();
        batch.playtime.from = playtimeStarted;
        batch.playtime.to = now;
        batch.playtime.deltaSeconds = deltaSeconds;
        queue(batch);
    }

    private static void queueAdvancement(String advancementId, Object holder, String source) {
        if (!enabled) return;

        ActivityModels.Metadata metadata = metadataForAdvancement(advancementId, holder);
        ActivityModels.AdvancementEvent event = new ActivityModels.AdvancementEvent();
        event.metadataRef = metadata.metadataRef;
        event.completedAt = System.currentTimeMillis();
        event.source = source;
        event.eventId = hash(state.clientSessionId + ":" + currentWorld().key + ":" + modpackIdentity() + ":" + advancementId);

        ActivityModels.Batch batch = newBaseBatch();
        batch.metadata.add(metadata);
        batch.advancements.add(event);
        queue(batch);
        flush();
    }

    private static synchronized void queue(ActivityModels.Batch batch) {
        if (state.authKey == null || state.authKey.isEmpty()) return;
        batch.sequence = state.nextSequence++;
        batch.sentAt = System.currentTimeMillis();
        state.pending.add(batch);
        while (state.pending.size() > MAX_PENDING_BATCHES) {
            state.pending.remove(0);
        }
    }

    private static void flush() {
        if (!markFlushRunning()) return;
        EXECUTOR.execute(() -> {
            try {
                drainQueue();
            } catch (Throwable ignored) {
            } finally {
                flushRunning = false;
            }
        });
    }

    private static void flushBlocking() {
        if (!markFlushRunning()) return;
        try {
            drainQueue();
        } catch (Throwable ignored) {
        } finally {
            flushRunning = false;
        }
    }

    private static synchronized boolean markFlushRunning() {
        if (!enabled || flushRunning || state.authKey == null || state.authKey.isEmpty()) return false;
        flushRunning = true;
        return true;
    }

    private static void drainQueue() throws IOException {
        while (true) {
            ActivityModels.Batch batch;
            synchronized (ActivityTelemetry.class) {
                if (state.pending.isEmpty()) return;
                batch = state.pending.get(0);
            }
            ApiClientResponse<PostActivityBatchRequest.Response> response = MineTogether.API.execute(new PostActivityBatchRequest(batch));
            PostActivityBatchRequest.Response api = response.apiResponse();
            if (!"success".equals(api.getStatus()) || !api.accepted) {
                return;
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

    private static ActivityModels.Metadata metadataForAdvancement(String id, Object holder) {
        ActivityModels.Metadata metadata = new ActivityModels.Metadata();
        metadata.type = "advancement";
        metadata.provider = "vanilla";
        metadata.contentId = id;
        metadata.locale = "en_us";

        if (holder != null) {
            Object advancement = call(holder, "value", "getValue");
            Object display = unwrapOptional(call(advancement, "display", "getDisplay"));
            if (display != null) {
                fillDisplay(metadata, display);
            }
        }
        metadata.metadataRef = hash(metadata.type + ":" + metadata.provider + ":" + metadata.contentId + ":" + metadata.titleKey + ":" + metadata.descriptionKey + ":" + metadata.titleEn + ":" + metadata.descriptionEn);
        return metadata;
    }

    private static void fillDisplay(ActivityModels.Metadata metadata, Object display) {
        Object titleObj = call(display, "getTitle", "title");
        Object descriptionObj = call(display, "getDescription", "description");
        if (titleObj instanceof Component title) {
            metadata.titleKey = translationKey(title);
            metadata.titleEn = englishText(title);
        }
        if (descriptionObj instanceof Component description) {
            metadata.descriptionKey = translationKey(description);
            metadata.descriptionEn = englishText(description);
        }
        Object iconObj = call(display, "getIcon", "icon");
        if (iconObj instanceof ItemStack stack) {
            metadata.iconItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        Object frame = call(display, "getFrame", "frame");
        if (frame != null) {
            Object name = call(frame, "getName", "getSerializedName");
            metadata.frameOrType = name == null ? frame.toString() : name.toString();
        }
    }

    private static String translationKey(Component component) {
        if (component.getContents() instanceof TranslatableContents contents) {
            return contents.getKey();
        }
        return "";
    }

    private static String englishText(Component component) {
        if (component.getContents() instanceof TranslatableContents contents) {
            String fallback = contents.getFallback();
            if (fallback == null || fallback.isEmpty()) {
                fallback = component.getString();
            }

            ClientLanguage language = englishLanguage();
            String value = language == null ? fallback : language.getOrDefault(contents.getKey(), fallback);
            Object[] args = contents.getArgs();
            if (args.length == 0) {
                return value;
            }

            Object[] formattedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                formattedArgs[i] = args[i] instanceof Component child ? englishText(child) : String.valueOf(args[i]);
            }

            try {
                return String.format(Locale.ROOT, value, formattedArgs);
            } catch (IllegalFormatException ignored) {
                return fallback;
            }
        }
        return component.getString();
    }

    private static ClientLanguage englishLanguage() {
        if (!englishLanguageLoadAttempted) {
            englishLanguageLoadAttempted = true;
            try {
                englishLanguage = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of("en_us"), false);
            } catch (Throwable ex) {
                LOGGER.warn("Failed to load en_us language resources for MineTogether activity metadata.", ex);
            }
        }
        return englishLanguage;
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
        modpack.minecraftVersion = Platform.getMinecraftVersion();
        modpack.loader = ArchitecturyTarget.getCurrentTarget();
        modpack.modVersion = MineTogetherPlatform.getVersion();
        return modpack;
    }

    private static String modpackIdentity() {
        ActivityModels.Modpack modpack = currentModpack();
        return modpack.source + ":" + modpack.packId + ":" + modpack.versionId + ":" + modpack.websiteId + ":" + modpack.minecraftVersion + ":" + modpack.loader + ":" + modpack.modVersion;
    }

    private static ActivityModels.World currentWorld() {
        Minecraft mc = Minecraft.getInstance();
        ActivityModels.World world = new ActivityModels.World();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            world.kind = "singleplayer";
            world.key = hash("singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            ServerData server = mc.getCurrentServer();
            world.kind = "multiplayer";
            world.key = hash("multiplayer:" + (server == null ? "unknown" : server.ip));
        }
        return world;
    }

    private static ActivityModels.QueueState freshState() {
        ActivityModels.QueueState fresh = new ActivityModels.QueueState();
        fresh.clientSessionId = UUID.randomUUID().toString();
        return fresh;
    }

    private static synchronized void applyAuthKey(String authKey) {
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
            if (sha == null) sha = claims == null ? null : claims.get("sub");
            return sha == null ? "" : hash(sha.toString());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) return value;
        return value + "=".repeat(4 - remainder);
    }

    private static String hash(String value) {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }

    private static Object call(Object target, String... methodNames) {
        if (target == null) return null;
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static Iterable<?> asIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        return java.util.List.of();
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }
}
