package net.creeperhost.minetogethercommunity.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.hat.HatCuboid;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class TechneLoaderTest {

    @Test
    public void preservesTechnePositionAndOffset() throws Exception {
        TechneLoader.ParsedTechne parsed = TechneLoader.parseModel(archive("{\n"
                + "  \"Techne\": {\n"
                + "    \"Models\": [{\"Model\": {\n"
                + "      \"TextureSize\": \"64,32\",\n"
                + "      \"@texture\": \"Newtexture.png\",\n"
                + "      \"Geometry\": {\"Shape\": [{\n"
                + "        \"@Type\": \"d9e621f7-957f-4b77-b1ae-20dcd0da7751\",\n"
                + "        \"Position\": \"-5,15,-1\",\n"
                + "        \"Offset\": \"1,2,3\",\n"
                + "        \"Rotation\": \"0.1,0.2,0.3\",\n"
                + "        \"Size\": \"4,5,6\",\n"
                + "        \"TextureOffset\": \"7,8\"\n"
                + "      }]}\n"
                + "    }}]\n"
                + "  }\n"
                + "}"), "positioned");

        assertEquals(64, parsed.texW());
        assertEquals(32, parsed.texH());
        assertEquals(1, parsed.cuboids().size());
        HatCuboid cuboid = parsed.cuboids().get(0);
        assertCuboid(cuboid, -5.0F, 15.0F, -1.0F, 0.1F, 0.2F, 0.3F, 1.0F, 2.0F, 3.0F);
        assertEquals(7, cuboid.texU());
        assertEquals(8, cuboid.texV());
    }

    @Test
    public void loadsTurboModelThingyWithCenteredXZAndAuthoredY() throws Exception {
        TechneLoader.ParsedTechne parsed = TechneLoader.parseModel(archive("{\n"
                + "  \"Techne\": {\n"
                + "    \"ProjectType\": \"TurboModelThingy\",\n"
                + "    \"Models\": [{\"Model\": {\n"
                + "      \"TextureSize\": \"64,32\",\n"
                + "      \"@texture\": \"Newtexture.png\",\n"
                + "      \"Geometry\": {\"Shape\": [{\n"
                + "        \"@Type\": \"d9e621f7-957f-4b77-b1ae-20dcd0da7751\",\n"
                + "        \"Position\": \"-5,16,0\",\n"
                + "        \"Offset\": \"0,0,0\",\n"
                + "        \"Rotation\": \"0,0,0\",\n"
                + "        \"Size\": \"1,4,1\",\n"
                + "        \"TextureOffset\": \"12,0\"\n"
                + "      }, {\n"
                + "        \"@Type\": \"d9e621f7-957f-4b77-b1ae-20dcd0da7751\",\n"
                + "        \"Position\": \"0,13,-8\",\n"
                + "        \"Offset\": \"0,0,0\",\n"
                + "        \"Rotation\": \"0,-0.7853982,0\",\n"
                + "        \"Size\": \"12,3,12\",\n"
                + "        \"TextureOffset\": \"0,6\"\n"
                + "      }]}\n"
                + "    }}]\n"
                + "  }\n"
                + "}"), "turbo");

        List<HatCuboid> cuboids = parsed.cuboids();
        assertEquals(2, cuboids.size());
        assertCuboid(cuboids.get(0), -5.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5F, 0.0F, -0.5F);
        assertCuboid(cuboids.get(1), 0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F, -6.0F, 0.0F, -6.0F);
    }

    @Test
    public void readsShapesInsideNestedNullContainers() throws Exception {
        TechneLoader.ParsedTechne parsed = TechneLoader.parseModel(archive("{\n"
                + "  \"Techne\": {\n"
                + "    \"Models\": [{\"Model\": {\n"
                + "      \"TextureSize\": \"64,32\",\n"
                + "      \"@texture\": \"Newtexture.png\",\n"
                + "      \"Geometry\": {\"Null\": [{\n"
                + "        \"Position\": \"1,2,3\",\n"
                + "        \"Rotation\": \"0.1,0.2,0.3\",\n"
                + "        \"Children\": {\"Null\": {\n"
                + "          \"Position\": \"4,5,6\",\n"
                + "          \"Rotation\": \"0.4,0.5,0.6\",\n"
                + "          \"Children\": {\"Shape\": {\n"
                + "            \"@Type\": \"d9e621f7-957f-4b77-b1ae-20dcd0da7751\",\n"
                + "            \"Position\": \"7,8,9\",\n"
                + "            \"Offset\": \"1,2,3\",\n"
                + "            \"Rotation\": \"0.7,0.8,0.9\",\n"
                + "            \"Size\": \"4,5,6\",\n"
                + "            \"TextureOffset\": \"9,10\"\n"
                + "          }}\n"
                + "        }}\n"
                + "      }]}\n"
                + "    }}]\n"
                + "  }\n"
                + "}"), "nested");

        assertEquals(1, parsed.cuboids().size());
        HatCuboid cuboid = parsed.cuboids().get(0);
        assertCuboid(cuboid,
                12.0F, 15.0F, 18.0F,
                1.2F, 1.5F, 1.8F,
                1.0F, 2.0F, 3.0F);
        assertEquals(9, cuboid.texU());
        assertEquals(10, cuboid.texV());
    }

    private static byte[] archive(String modelJson) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        try {
            zip.putNextEntry(new ZipEntry("model.json"));
            zip.write(modelJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Newtexture.png"));
            zip.write(new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x00
            });
            zip.closeEntry();
        } finally {
            zip.close();
        }
        return bytes.toByteArray();
    }

    private static void assertCuboid(HatCuboid cuboid, float pivotX, float pivotY, float pivotZ,
                                     float rotX, float rotY, float rotZ,
                                     float x, float y, float z) {
        assertEquals(pivotX, cuboid.pivotX(), 0.0001F);
        assertEquals(pivotY, cuboid.pivotY(), 0.0001F);
        assertEquals(pivotZ, cuboid.pivotZ(), 0.0001F);
        assertEquals(rotX, cuboid.rotX(), 0.0001F);
        assertEquals(rotY, cuboid.rotY(), 0.0001F);
        assertEquals(rotZ, cuboid.rotZ(), 0.0001F);
        assertEquals(x, cuboid.x(), 0.0001F);
        assertEquals(y, cuboid.y(), 0.0001F);
        assertEquals(z, cuboid.z(), 0.0001F);
    }
}
