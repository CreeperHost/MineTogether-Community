package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatCuboid;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatModelType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class TechneLoader {

    private static final String CUBE_TYPE = "d9e621f7-957f-4b77-b1ae-20dcd0da7751";

    private TechneLoader() {
    }

    public static Hat load(String id, String displayName, String author, String mod, boolean locked, String howToUnlock, byte[] tc2Data) throws IOException {
        ParsedTechne parsed = parseModel(tc2Data, displayName);

        final ResourceLocation texture = new ResourceLocation(MineTogether.MOD_ID, "dynamic/hat/" + sanitize(id));
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(parsed.textureBytes));
        if (image == null) throw new IOException("Invalid texture PNG in " + displayName);
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().getTextureManager().loadTexture(texture, new DynamicTexture(image));
            }
        });

        return new Hat(id, displayName, author, mod, locked, howToUnlock, texture, parsed.texW, parsed.texH, HatModelType.TC2, parsed.cuboids);
    }

    static ParsedTechne parseModel(byte[] tc2Data, String displayName) throws IOException {
        byte[] modelJsonBytes = null;
        Map<String, byte[]> textureFiles = new LinkedHashMap<String, byte[]>();

        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(tc2Data));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                byte[] bytes = ByteStreams.toByteArray(zip);
                if ("model.json".equals(entryName)) {
                    modelJsonBytes = bytes;
                } else if (entryName.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    textureFiles.put(entryName, bytes);
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }

        if (modelJsonBytes == null) throw new IOException("No model.json in " + displayName);
        if (textureFiles.isEmpty()) throw new IOException("No texture PNG in " + displayName);

        JsonObject root = new JsonParser().parse(new InputStreamReader(
                new ByteArrayInputStream(modelJsonBytes), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject techne = root.getAsJsonObject("Techne");
        if (techne == null) throw new IOException("No Techne root in model.json for " + displayName);
        JsonArray models = techne.getAsJsonArray("Models");
        if (models == null || models.size() == 0) throw new IOException("No Models array in " + displayName);
        JsonObject model = models.get(0).getAsJsonObject().getAsJsonObject("Model");
        if (model == null) throw new IOException("No Model in Models[0] for " + displayName);
        String[] texSize = model.get("TextureSize").getAsString().split(",");
        int texW = Integer.parseInt(texSize[0].trim());
        int texH = Integer.parseInt(texSize[1].trim());
        List<HatCuboid> cuboids = new ArrayList<HatCuboid>();
        byte[] textureBytes = chooseTexture(textureFiles, model);

        JsonObject geometry = model.getAsJsonObject("Geometry");
        boolean turboModelThingy = isTurboModelThingy(techne);
        if (geometry != null) {
            JsonElement shapesElement = geometry.get("Shape");
            if (shapesElement != null) {
                JsonArray shapes;
                if (shapesElement.isJsonArray()) {
                    shapes = shapesElement.getAsJsonArray();
                } else {
                    shapes = new JsonArray();
                    shapes.add(shapesElement.getAsJsonObject());
                }
                for (JsonElement element : shapes) {
                    JsonObject shape = element.getAsJsonObject();
                    JsonElement typeElement = shape.get("@Type");
                    if (typeElement == null || !CUBE_TYPE.equals(typeElement.getAsString())) continue;

                    float[] pos = parseVec3(shape.get("Position").getAsString());
                    float[] off = shape.has("Offset") ? parseVec3(shape.get("Offset").getAsString()) : new float[3];
                    float[] rot = shape.has("Rotation") ? parseVec3(shape.get("Rotation").getAsString()) : new float[3];
                    float[] size = parseVec3(shape.get("Size").getAsString());
                    int[] texOff = parseVec2i(shape.get("TextureOffset").getAsString());
                    if (turboModelThingy) {
                        boolean rotated = rot[0] != 0.0F || rot[1] != 0.0F || rot[2] != 0.0F;
                        off[0] -= size[0] / 2.0F;
                        off[2] -= size[2] / 2.0F;
                        if (rotated) {
                            pos[2] += 8.0F;
                        }
                        rot[0] = 0.0F;
                        rot[1] = 0.0F;
                        rot[2] = 0.0F;
                    }

                    cuboids.add(new HatCuboid(pos[0], pos[1], pos[2], rot[0], rot[1], rot[2],
                            off[0], off[1], off[2], size[0], size[1], size[2], texOff[0], texOff[1]));
                }
            }
        }

        return new ParsedTechne(textureBytes, texW, texH, cuboids);
    }

    private static byte[] chooseTexture(Map<String, byte[]> textureFiles, JsonObject model) {
        JsonElement namedTexture = model.get("@texture");
        if (namedTexture != null) {
            byte[] bytes = textureFiles.get(namedTexture.getAsString());
            if (bytes != null) {
                return bytes;
            }
        }
        return textureFiles.values().iterator().next();
    }

    private static boolean isTurboModelThingy(JsonObject techne) {
        JsonElement projectType = techne == null ? null : techne.get("ProjectType");
        return projectType != null && "turbomodelthingy".equals(projectType.getAsString().trim().toLowerCase(Locale.ROOT));
    }

    private static float[] parseVec3(String value) {
        String[] parts = value.split(",");
        return new float[] {
                Float.parseFloat(parts[0].trim()),
                Float.parseFloat(parts[1].trim()),
                Float.parseFloat(parts[2].trim())
        };
    }

    private static int[] parseVec2i(String value) {
        String[] parts = value.split(",");
        return new int[] {
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
        };
    }

    private static String sanitize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    static final class ParsedTechne {
        private final byte[] textureBytes;
        private final int texW;
        private final int texH;
        private final List<HatCuboid> cuboids;

        private ParsedTechne(byte[] textureBytes, int texW, int texH, List<HatCuboid> cuboids) {
            this.textureBytes = textureBytes;
            this.texW = texW;
            this.texH = texH;
            this.cuboids = cuboids;
        }

        int texW() {
            return texW;
        }

        int texH() {
            return texH;
        }

        List<HatCuboid> cuboids() {
            return cuboids;
        }
    }
}
