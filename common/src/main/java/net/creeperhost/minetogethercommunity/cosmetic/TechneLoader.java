package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatCuboid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static net.creeperhost.minetogethercommunity.MineTogether.MOD_ID;

public class TechneLoader {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CUBE_TYPE = "d9e621f7-957f-4b77-b1ae-20dcd0da7751";

    public static Hat load(String displayName, String author, String mod, byte[] tc2Data) throws IOException {
        byte[] modelJsonBytes = null;
        byte[] textureBytes = null;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(tc2Data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.equals("model.json")) {
                    modelJsonBytes = zip.readAllBytes();
                } else if (entryName.endsWith(".png")) {
                    textureBytes = zip.readAllBytes();
                }
                zip.closeEntry();
            }
        }

        if (modelJsonBytes == null) throw new IOException("No model.json in " + displayName);
        if (textureBytes == null) throw new IOException("No texture PNG in " + displayName);

        JsonObject root = JsonParser.parseString(new String(modelJsonBytes)).getAsJsonObject();

        // Structure: root["Techne"]["Models"][0]["Model"]
        JsonObject techne = root.getAsJsonObject("Techne");
        if (techne == null) throw new IOException("No 'Techne' root in model.json for " + displayName);

        JsonArray models = techne.getAsJsonArray("Models");
        if (models == null || models.isEmpty()) throw new IOException("No 'Models' array in " + displayName);

        JsonObject model = models.get(0).getAsJsonObject().getAsJsonObject("Model");
        if (model == null) throw new IOException("No 'Model' in Models[0] for " + displayName);

        // TextureSize is a comma-separated string e.g. "64,32"
        String[] texSize = model.get("TextureSize").getAsString().split(",");
        int texW = Integer.parseInt(texSize[0].trim());
        int texH = Integer.parseInt(texSize[1].trim());

        List<HatCuboid> cuboids = new ArrayList<>();

        JsonObject geometry = model.getAsJsonObject("Geometry");
        if (geometry != null) {
            JsonElement shapesEl = geometry.get("Shape");
            if (shapesEl != null) {
                // Shape can be a single object or an array
                JsonArray shapes = shapesEl.isJsonArray() ? shapesEl.getAsJsonArray() : null;
                if (shapes == null) {
                    JsonObject single = shapesEl.getAsJsonObject();
                    shapes = new JsonArray();
                    shapes.add(single);
                }

                for (JsonElement el : shapes) {
                    JsonObject shape = el.getAsJsonObject();
                    JsonElement typeEl = shape.get("@Type");
                    if (typeEl == null || !CUBE_TYPE.equals(typeEl.getAsString())) continue;

                    float[] pos  = parseVec3(shape.get("Position").getAsString());
                    float[] off  = shape.has("Offset")   ? parseVec3(shape.get("Offset").getAsString())   : new float[3];
                    float[] rot  = shape.has("Rotation") ? parseVec3(shape.get("Rotation").getAsString()) : new float[3];
                    float[] size = parseVec3(shape.get("Size").getAsString());
                    int[] texOff = parseVec2i(shape.get("TextureOffset").getAsString());

                    // Store raw Techne values unchanged.
                    // HatLayer applies scale(-1,-1,1) which exactly replicates the iChunUtil
                    // rendering convention, so no coordinate transformation is needed here.
                    // Position = the ModelRenderer rotation pivot (setRotationPoint).
                    // Offset   = the box corner relative to that pivot (addBox x/y/z args).
                    cuboids.add(new HatCuboid(
                            pos[0], pos[1], pos[2],          // pivot = Position
                            rot[0], rot[1], rot[2],          // rotation = Rotation (radians)
                            off[0], off[1], off[2],          // box origin = Offset
                            size[0], size[1], size[2],       // box size = Size
                            texOff[0], texOff[1]));
                }
            }
        }

        String id = displayName.toLowerCase().replaceAll("[^a-z0-9]", "_");
        ResourceLocation texLoc = ResourceLocation.fromNamespaceAndPath(MOD_ID, "dynamic/hats/" + id);

        final byte[] finalTexBytes = textureBytes;
        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(finalTexBytes));
                Minecraft.getInstance().getTextureManager().register(texLoc, new DynamicTexture(img));
            } catch (IOException e) {
                LOGGER.warn("Failed to load texture for hat: {}", displayName, e);
            }
        });

        return new Hat(id, displayName, author, mod, texLoc, texW, texH, cuboids);
    }

    private static float[] parseVec3(String s) {
        String[] parts = s.split(",");
        return new float[]{
                Float.parseFloat(parts[0].trim()),
                Float.parseFloat(parts[1].trim()),
                Float.parseFloat(parts[2].trim())
        };
    }

    private static int[] parseVec2i(String s) {
        String[] parts = s.split(",");
        return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
        };
    }
}
