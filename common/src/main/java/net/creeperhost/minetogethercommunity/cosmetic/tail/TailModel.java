package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders a tail by emitting quads directly from the parsed block-model elements.
 * <p>
 * Coordinates in the element JSON are in "model-pixel" units (same as vanilla
 * Minecraft model-part coordinates: 1 unit = 1/16 block).
 */
public class TailModel {

    private static final Logger LOGGER = LogManager.getLogger();

    private final List<TailElement> elements;
    private final float texW;
    private final float texH;
    private boolean loggedOnce = false;

    public TailModel(List<TailElement> elements, float texWidth, float texHeight) {
        this.elements = elements;
        this.texW = texWidth;
        this.texH = texHeight;
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        render(poseStack, consumer, packedLight, TailPose.none());
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, TailPose tailPose) {
        if (!loggedOnce) {
            loggedOnce = true;
            LOGGER.info("[TailModel] render called: {} elements, texSize={}x{}", elements.size(), (int)texW, (int)texH);
            for (TailElement el : elements) {
                LOGGER.info("[TailModel]   '{}'  from=[{},{},{}] to=[{},{},{}]",
                        el.name(), el.from()[0], el.from()[1], el.from()[2],
                        el.to()[0], el.to()[1], el.to()[2]);
                if (el.rotation() != null) {
                    LOGGER.info("[TailModel]     rotation origin=[{},{},{}] axis={} angle={}",
                            el.rotation().origin()[0], el.rotation().origin()[1], el.rotation().origin()[2],
                            el.rotation().axis(), el.rotation().angle());
                }
                logFace("north", el.north());
                logFace("south", el.south());
                logFace("east",  el.east());
                logFace("west",  el.west());
                logFace("up",    el.up());
                logFace("down",  el.down());
            }
        }
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < elements.size(); i++) {
            TailElement el = elements.get(i);
            float x0 = el.from()[0], y0 = el.from()[1], z0 = el.from()[2];
            float x1 = el.to()[0],   y1 = el.to()[1],   z1 = el.to()[2];

            if (el.south() != null) emitFace(pose, consumer, packedLight, el.south(),
                    rotate(el, new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}}, tailPose, i), 0, 0, 1);

            if (el.north() != null) emitFace(pose, consumer, packedLight, el.north(),
                    rotate(el, new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}}, tailPose, i), 0, 0, -1);

            if (el.east() != null) emitFace(pose, consumer, packedLight, el.east(),
                    rotate(el, new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}}, tailPose, i), 1, 0, 0);

            if (el.west() != null) emitFace(pose, consumer, packedLight, el.west(),
                    rotate(el, new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}}, tailPose, i), -1, 0, 0);

            // "up" in block-model = top face (y = to.y); "down" = bottom face (y = from.y).
            // In entity model-space y increases downward, so normals keep block-model convention —
            // the PoseStack normal matrix (with its -y scale) handles the transformation.
            if (el.up() != null) emitFace(pose, consumer, packedLight, el.up(),
                    rotate(el, new float[][]{{x0,y1,z1},{x1,y1,z1},{x1,y1,z0},{x0,y1,z0}}, tailPose, i), 0, 1, 0);

            if (el.down() != null) emitFace(pose, consumer, packedLight, el.down(),
                    rotate(el, new float[][]{{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1}}, tailPose, i), 0, -1, 0);
        }
    }

    private float[][] rotate(TailElement element, float[][] verts, TailPose tailPose, int index) {
        TailRotation rotation = element.rotation();
        float xOffset = tailPose.x(index);
        float yOffset = tailPose.y(index);
        float zOffset = tailPose.z(index);
        if ((rotation == null || rotation.angle() == 0.0F)
                && xOffset == 0.0F && yOffset == 0.0F && zOffset == 0.0F) return verts;

        float[] origin = rotation != null ? rotation.origin() : center(element);
        float baseX = rotation != null && "x".equals(rotation.axis()) ? (float) Math.toRadians(rotation.angle()) : 0.0F;
        float baseY = rotation != null && "y".equals(rotation.axis()) ? (float) Math.toRadians(rotation.angle()) : 0.0F;
        float baseZ = rotation != null && "z".equals(rotation.axis()) ? (float) Math.toRadians(rotation.angle()) : 0.0F;

        float[][] out = new float[verts.length][3];
        for (int i = 0; i < verts.length; i++) {
            float x = verts[i][0] - origin[0];
            float y = verts[i][1] - origin[1];
            float z = verts[i][2] - origin[2];

            float[] rotated = rotateX(x, y, z, baseX + xOffset);
            rotated = rotateY(rotated[0], rotated[1], rotated[2], baseY + yOffset);
            rotated = rotateZ(rotated[0], rotated[1], rotated[2], baseZ + zOffset);
            out[i][0] = rotated[0] + origin[0];
            out[i][1] = rotated[1] + origin[1];
            out[i][2] = rotated[2] + origin[2];
        }
        return out;
    }

    private float[] center(TailElement element) {
        return new float[]{
                (element.from()[0] + element.to()[0]) / 2.0F,
                (element.from()[1] + element.to()[1]) / 2.0F,
                (element.from()[2] + element.to()[2]) / 2.0F
        };
    }

    private float[] rotateX(float x, float y, float z, float angle) {
        if (angle == 0.0F) return new float[]{x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[]{x, y * cos - z * sin, y * sin + z * cos};
    }

    private float[] rotateY(float x, float y, float z, float angle) {
        if (angle == 0.0F) return new float[]{x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[]{x * cos + z * sin, y, -x * sin + z * cos};
    }

    private float[] rotateZ(float x, float y, float z, float angle) {
        if (angle == 0.0F) return new float[]{x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[]{x * cos - y * sin, x * sin + y * cos, z};
    }

    private void logFace(String dir, TailFace face) {
        if (face == null) return;
        float u0n = face.u0() / texW, v0n = face.v0() / texH;
        float u1n = face.u1() / texW, v1n = face.v1() / texH;
        LOGGER.info("[TailModel]     {} uv=[{},{},{},{}] -> norm=[{},{},{},{}]",
                dir, face.u0(), face.v0(), face.u1(), face.v1(), u0n, v0n, u1n, v1n);
    }

    private void emitFace(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                           TailFace face, float[][] verts, float nx, float ny, float nz) {
        // UV corners: (u0,v0) top-left → (u1,v0) top-right → (u1,v1) bottom-right → (u0,v1) bottom-left
        float u0 = face.u0() / texW, v0 = face.v0() / texH;
        float u1 = face.u1() / texW, v1 = face.v1() / texH;
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};

        Vector3f transformedNormal = pose.normal().transform(new Vector3f(nx, ny, nz));
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose.pose(), verts[i][0] / 16.0F, verts[i][1] / 16.0F, verts[i][2] / 16.0F)
                    .setColor(255, 255, 255, 255)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
        }
    }
}
