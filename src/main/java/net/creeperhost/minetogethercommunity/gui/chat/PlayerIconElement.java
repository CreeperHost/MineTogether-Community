package net.creeperhost.minetogethercommunity.gui.chat;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.util.ProfileUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class PlayerIconElement extends GuiElement<PlayerIconElement> {

    private static final int AVATAR_TEXTURE_SIZE = 64;
    private static final ResourceLocation OFFLINE = new ResourceLocation(MineTogether.MOD_ID, "textures/gui/player_offline.png");
    private static final Map<UUID, SkinLoader> SKINS = new ConcurrentHashMap<UUID, SkinLoader>();
    private static final Map<String, AvatarLoader> MT_AVATARS = new ConcurrentHashMap<String, AvatarLoader>();

    private final Profile profile;

    public PlayerIconElement(GuiElement<?> parent, Profile profile) {
        super(parent);
        this.profile = profile;
    }

    public static void invalidate(Profile profile) {
        if (profile == null) return;
        if (profile.hasFriendUUID()) {
            SKINS.remove(profile.getFriendUUID());
        }
        AvatarKey key = AvatarKey.from(profile);
        if (key != null) {
            MT_AVATARS.remove(key.cacheKey);
        }
    }

    public static void invalidateAll() {
        SKINS.clear();
        MT_AVATARS.clear();
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        AvatarLoader avatar = preferMineTogetherAvatar() ? loadAvatar() : null;
        if (drawAvatar(avatar)) return;

        SkinLoader skin = loadSkin();
        ResourceLocation skinTexture = skin == null ? null : skin.texture();
        if (skinTexture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(skinTexture);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1F, 1F, 1F, 1F);
            drawSkinHead();
            return;
        }

        if (avatar == null) avatar = loadAvatar();
        if (drawAvatar(avatar)) return;

        drawOfflineIcon();
    }

    private boolean preferMineTogetherAvatar() {
        return profile != null && profile.hasAccount() && profile.hasFullHash();
    }

    private boolean drawAvatar(AvatarLoader avatar) {
        ResourceLocation texture = avatar == null ? null : avatar.texture();
        if (texture == null) return false;
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawAspectFit(avatar.width(), avatar.height());
        return true;
    }

    private void drawOfflineIcon() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(OFFLINE);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawAspectFit(20, 20);
    }

    private void drawAspectFit(int textureWidth, int textureHeight) {
        if (textureWidth <= 0 || textureHeight <= 0 || width <= 0 || height <= 0) return;
        float scale = Math.min((float) width / (float) textureWidth, (float) height / (float) textureHeight);
        int drawWidth = Math.max(1, Math.round(textureWidth * scale));
        int drawHeight = Math.max(1, Math.round(textureHeight * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        Gui.drawScaledCustomSizeModalRect(drawX, drawY, 0, 0, textureWidth, textureHeight,
                drawWidth, drawHeight, textureWidth, textureHeight);
    }

    private void drawSkinHead() {
        if (width <= 0 || height <= 0) return;
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, width, height, 64, 64);
    }

    private SkinLoader loadSkin() {
        if (profile == null || !profile.hasFriendUUID()) return null;
        UUID uuid = profile.getFriendUUID();
        SkinLoader loader = SKINS.get(uuid);
        if (loader == null) {
            SkinLoader created = new SkinLoader(uuid);
            SkinLoader existing = SKINS.putIfAbsent(uuid, created);
            loader = existing == null ? created : existing;
            if (existing == null) created.start();
        }
        return loader;
    }

    private AvatarLoader loadAvatar() {
        AvatarKey key = AvatarKey.from(profile);
        if (key == null) return null;
        AvatarLoader loader = MT_AVATARS.get(key.cacheKey);
        if (loader == null) {
            AvatarLoader created = new AvatarLoader(key.urls);
            AvatarLoader existing = MT_AVATARS.putIfAbsent(key.cacheKey, created);
            loader = existing == null ? created : existing;
            if (existing == null) created.start();
        }
        return loader;
    }

    private static class SkinLoader {
        private final UUID uuid;
        private volatile long failedAt;
        private volatile ResourceLocation texture;

        private SkinLoader(UUID uuid) {
            this.uuid = uuid;
        }

        private void start() {
            ProfileUpdater.updateProfile(uuid, new java.util.function.Consumer<GameProfile>() {
                @Override
                public void accept(GameProfile gameProfile) {
                    if (gameProfile == null) {
                        failedAt = System.currentTimeMillis();
                        return;
                    }
                    Minecraft mc = Minecraft.getMinecraft();
                    SkinManager skinManager = mc.getSkinManager();
                    skinManager.func_152790_a(gameProfile, new SkinManager.SkinAvailableCallback() {
                        @Override
                        public void onSkinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location) {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                SkinLoader.this.texture = location;
                                failedAt = 0L;
                            }
                        }
                    }, true);
                }
            });
        }

        private ResourceLocation texture() {
            if (texture == null && failedAt > 0L && System.currentTimeMillis() - failedAt > TimeUnit.MINUTES.toMillis(2L)) {
                failedAt = 0L;
                start();
            }
            return texture;
        }
    }

    private static class AvatarLoader implements Runnable {
        private final String[] urls;
        private volatile BufferedImage image;
        private volatile long failedAt;
        private ResourceLocation texture;

        private AvatarLoader(String[] urls) {
            this.urls = urls;
        }

        private void start() {
            Thread thread = new Thread(this, "minetogether-avatar");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            try {
                BufferedImage loaded = tryLoad(urls);
                if (loaded == null) {
                    failedAt = System.currentTimeMillis();
                } else {
                    image = loaded;
                    failedAt = 0L;
                }
            } catch (Throwable ignored) {
                failedAt = System.currentTimeMillis();
            }
        }

        private BufferedImage tryLoad(String... urls) {
            for (String urlString : urls) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("Accept", "image/png,image/*");
                    connection.setRequestProperty("User-Agent", "MineTogetherCommunity/1.7.10");
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) continue;
                    InputStream input = connection.getInputStream();
                    try {
                        BufferedImage loaded = ImageIO.read(input);
                        if (loaded != null) return normalizeAvatar(loaded);
                    } finally {
                        input.close();
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            return null;
        }

        private BufferedImage normalizeAvatar(BufferedImage loaded) {
            int width = loaded.getWidth();
            int height = loaded.getHeight();
            if (width <= 0 || height <= 0) return loaded;
            BufferedImage squared = new BufferedImage(AVATAR_TEXTURE_SIZE, AVATAR_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
            double scale = Math.min((double) AVATAR_TEXTURE_SIZE / (double) width, (double) AVATAR_TEXTURE_SIZE / (double) height);
            int drawWidth = Math.max(1, (int) Math.round(width * scale));
            int drawHeight = Math.max(1, (int) Math.round(height * scale));
            int drawX = (AVATAR_TEXTURE_SIZE - drawWidth) / 2;
            int drawY = (AVATAR_TEXTURE_SIZE - drawHeight) / 2;
            Graphics2D graphics = squared.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(loaded, drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                        0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            return squared;
        }

        private ResourceLocation texture() {
            if (image == null) {
                if (failedAt > 0L && System.currentTimeMillis() - failedAt > TimeUnit.MINUTES.toMillis(2L)) {
                    failedAt = 0L;
                    start();
                }
                return null;
            }
            if (texture == null) {
                texture = Minecraft.getMinecraft().getTextureManager()
                        .getDynamicTextureLocation(MineTogether.MOD_ID + "_avatar", new DynamicTexture(image));
            }
            return texture;
        }

        private int width() {
            BufferedImage loaded = image;
            return loaded == null ? 20 : loaded.getWidth();
        }

        private int height() {
            BufferedImage loaded = image;
            return loaded == null ? 20 : loaded.getHeight();
        }
    }

    private static class AvatarKey {
        private final String cacheKey;
        private final String[] urls;

        private AvatarKey(String cacheKey, String... urls) {
            this.cacheKey = cacheKey;
            this.urls = urls;
        }

        private static AvatarKey from(Profile profile) {
            if (profile == null) return null;
            String token = cacheToken();
            List<String> urls = new ArrayList<String>();
            String primaryKey = null;
            if (profile.hasFullHash()) {
                String hash = profile.getFullHash().toLowerCase(Locale.ROOT);
                if (primaryKey == null) primaryKey = "mt:" + hash;
                urls.add(mtAvatarUrl(hash, token));
            }
            if (profile.hasFriendUUID()) {
                String uuid = normalize(profile.getFriendUUID());
                if (primaryKey == null) primaryKey = "uuid:" + uuid;
                urls.add(uuidAvatarUrl(uuid, token));
            }
            if (urls.isEmpty() || primaryKey == null) return null;
            return new AvatarKey(primaryKey + ":" + token, urls.toArray(new String[urls.size()]));
        }

        private static String mtAvatarUrl(String hash, String token) {
            return "https://blockatar.net/mt/avatars/" + hash + "?size=64&overlay&cosmetics&default=MHF_Steve&mt=" + token;
        }

        private static String uuidAvatarUrl(String uuid, String token) {
            return "https://blockatar.net/avatars/" + uuid + "?size=64&overlay&default=MHF_Steve&mt=" + token;
        }

        private static String normalize(UUID uuid) {
            return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
        }

        private static String cacheToken() {
            return Long.toString(System.currentTimeMillis() / TimeUnit.MINUTES.toMillis(15L));
        }
    }
}
