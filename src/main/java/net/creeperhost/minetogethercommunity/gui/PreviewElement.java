package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreviewElement extends GuiElement<PreviewElement> {

    private static final Pattern META_IMAGE_PATTERN = Pattern.compile("<meta\\s+[^>]*(?:property|name)=[\"']og:image(?::url)?[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SUPPORTED_IMAGES = new HashSet<>();
    private static final Set<String> ALLOWED_DOMAINS = new HashSet<>();
    private static final Set<URL> INVALID_URLS = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
    private static final Map<URL, ImageLoader> CACHE = new ConcurrentHashMap<>();

    static {
        SUPPORTED_IMAGES.add("image/jpeg");
        SUPPORTED_IMAGES.add("image/jpg");
        SUPPORTED_IMAGES.add("image/png");
        SUPPORTED_IMAGES.add("image/bmp");
        SUPPORTED_IMAGES.add("image/gif");
        ALLOWED_DOMAINS.add("blockshot.ch");
    }

    private URLProvider urlProvider;
    private int imageSize = 80;
    private boolean enforceDomains = true;

    public PreviewElement(GuiElement<?> parent) {
        super(parent);
    }

    public PreviewElement setUrlProvider(URLProvider urlProvider) {
        this.urlProvider = urlProvider;
        return this;
    }

    public PreviewElement setImageSize(int imageSize) {
        this.imageSize = imageSize;
        return this;
    }

    public PreviewElement setEnforceDomains(boolean enforceDomains) {
        this.enforceDomains = enforceDomains;
        return this;
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY, float partialTicks) {
        if (urlProvider == null) return;
        URLInfo info = urlProvider.getUrlUnderMouse(mouseX, mouseY);
        if (info == null) return;
        renderPreview(mc(), info, mouseX, mouseY, width, height, imageSize, enforceDomains);
    }

    public static void renderPreview(Minecraft mc, URLInfo info, int mouseX, int mouseY, int width, int height, int imageSize, boolean enforceDomains) {
        ImageLoader image = getImage(info, enforceDomains);
        if (image == null) return;

        if (!image.isLoaded()) {
            drawHoverBox(mouseX, mouseY, 110, 18);
            String loading = net.minecraft.client.resources.I18n.format("minetogether.gui.chat.loading_preview");
            mc.fontRendererObj.drawStringWithShadow(loading, mouseX + (110 - mc.fontRendererObj.getStringWidth(loading)) / 2, mouseY + 5, 0xFFFFFF);
            return;
        }

        ResourceLocation location = image.texture();
        if (location == null) return;
        double aspect = image.width() / (double) image.height();
        int drawWidth = aspect > 1 ? imageSize : Math.max(1, (int) (imageSize * aspect));
        int drawHeight = aspect > 1 ? Math.max(1, (int) (imageSize / aspect)) : imageSize;
        int border = 3;
        int left = Math.min(mouseX, width - drawWidth - border * 2);
        int top = Math.min(mouseY, height - drawHeight - border * 2);
        if (left < 0) left = 0;
        if (top < 0) top = 0;

        drawHoverBox(left, top, drawWidth + border * 2, drawHeight + border * 2);
        mc.getTextureManager().bindTexture(location);
        GlStateManager.color(1F, 1F, 1F, 1F);
        Gui.drawModalRectWithCustomSizedTexture(left + border, top + border, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
    }

    private static ImageLoader getImage(URLInfo info, boolean enforceDomains) {
        URL url = info.url;
        if (enforceDomains && !info.admin && !ALLOWED_DOMAINS.contains(url.getHost())) return null;
        if (INVALID_URLS.contains(url)) return null;

        ImageLoader loader = CACHE.get(url);
        if (loader != null) return loader;

        ImageLoader created = new ImageLoader(url);
        ImageLoader existing = CACHE.putIfAbsent(url, created);
        if (existing != null) return existing;
        created.start();
        return created;
    }

    private static void drawHoverBox(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, MTStyle.Flat.BACKGROUND);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, MTStyle.Flat.CONTENT_AREA);
    }

    public interface URLProvider {
        URLInfo getUrlUnderMouse(int mouseX, int mouseY);
    }

    public static class URLInfo {
        private final URL url;
        private final boolean admin;

        public URLInfo(URL url, boolean admin) {
            this.url = url;
            this.admin = admin;
        }

        public URL getUrl() {
            return url;
        }
    }

    private static class ImageLoader implements Runnable {
        private final URL url;
        private volatile BufferedImage image;
        private volatile boolean loaded;
        private volatile boolean started;
        private ResourceLocation location;

        private ImageLoader(URL url) {
            this.url = url;
        }

        private void start() {
            if (started) return;
            started = true;
            Thread thread = new Thread(this, "minetogether-preview");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            load(url, false);
        }

        private void load(URL target, boolean ogRedirect) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) target.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "MineTogetherCommunity/1.7.10");
                String contentType = connection.getContentType();
                if (contentType != null) {
                    contentType = contentType.split(";", 2)[0].trim().toLowerCase();
                }

                if (SUPPORTED_IMAGES.contains(contentType)) {
                    image = ImageIO.read(connection.getInputStream());
                    if (image == null) {
                        INVALID_URLS.add(url);
                    } else {
                        loaded = true;
                    }
                    return;
                }

                if (ogRedirect) {
                    INVALID_URLS.add(url);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = META_IMAGE_PATTERN.matcher(line);
                        if (matcher.find()) {
                            load(new URL(target, matcher.group(1)), true);
                            return;
                        }
                    }
                }
                INVALID_URLS.add(url);
            } catch (Throwable ignored) {
                INVALID_URLS.add(url);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private boolean isLoaded() {
            return loaded && image != null;
        }

        private int width() {
            return image == null ? 1 : image.getWidth();
        }

        private int height() {
            return image == null ? 1 : image.getHeight();
        }

        private ResourceLocation texture() {
            if (!isLoaded()) return null;
            if (location == null) {
                DynamicTexture texture = new DynamicTexture(image);
                location = net.minecraft.client.Minecraft.getMinecraft().getTextureManager()
                        .getDynamicTextureLocation(MineTogether.MOD_ID + "_preview", texture);
            }
            return location;
        }
    }
}
