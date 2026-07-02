package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders a tail by emitting quads directly from the parsed block-model elements.
 * Coordinates in the element JSON are in model-pixel units, where 1 unit = 1/16 block.
 */
public class TailModel {

    private final List<TailElement> elements;
    private final float texW;
    private final float texH;
    private final boolean animatedChain;
    private final int tailTipAnimationIndex;
    private final float animationScale;

    public TailModel(List<TailElement> elements, float texWidth, float texHeight) {
        this.elements = elements;
        this.texW = texWidth;
        this.texH = texHeight;
        this.animatedChain = isAnimatedChain(elements);
        this.tailTipAnimationIndex = tailTipAnimationIndex(elements);
        this.animationScale = animationScale(tailTipAnimationIndex);
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        render(poseStack, consumer, packedLight, TailPose.none());
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, TailPose tailPose) {
        PoseStack.Pose pose = poseStack.last();
        if (animatedChain) {
            renderAnimatedChain(pose, consumer, packedLight, tailPose);
            return;
        }

        for (TailElement el : elements) {
            float x0 = el.from()[0], y0 = el.from()[1], z0 = el.from()[2];
            float x1 = el.to()[0], y1 = el.to()[1], z1 = el.to()[2];

            if (el.south() != null) emitFace(pose, consumer, packedLight, el.south(),
                    rotate(el, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}),
                    normal(el, 0, 0, 1));
            if (el.north() != null) emitFace(pose, consumer, packedLight, el.north(),
                    rotate(el, new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}),
                    normal(el, 0, 0, -1));
            if (el.east() != null) emitFace(pose, consumer, packedLight, el.east(),
                    rotate(el, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}),
                    normal(el, 1, 0, 0));
            if (el.west() != null) emitFace(pose, consumer, packedLight, el.west(),
                    rotate(el, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}),
                    normal(el, -1, 0, 0));
            if (el.up() != null) emitFace(pose, consumer, packedLight, el.up(),
                    rotate(el, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}),
                    normal(el, 0, 1, 0));
            if (el.down() != null) emitFace(pose, consumer, packedLight, el.down(),
                    rotate(el, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}),
                    normal(el, 0, -1, 0));
        }
    }

    private void renderAnimatedChain(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, TailPose tailPose) {
        float[] previousStaticOrigin = null;
        float[] previousDynamicOrigin = null;
        float[] previousStaticAngles = null;
        float[] previousDynamicAngles = null;

        for (TailElement el : elements) {
            float x0 = el.from()[0], y0 = el.from()[1], z0 = el.from()[2];
            float x1 = el.to()[0], y1 = el.to()[1], z1 = el.to()[2];
            int animationIndex = animationIndex(el.name());
            float[] staticOrigin = elementOrigin(el);
            float[] staticAngles = elementAngles(el, 0.0F, 0.0F, 0.0F);
            float[] dynamicAngles = elementAngles(
                    el,
                    tailPose.x(animationIndex) * animationScale,
                    tailPose.y(animationIndex) * animationScale,
                    tailPose.z(animationIndex) * animationScale
            );
            float[] dynamicOrigin = dynamicOrigin(
                    staticOrigin, previousStaticOrigin, previousDynamicOrigin, previousStaticAngles, previousDynamicAngles);

            if (el.south() != null) emitFace(pose, consumer, packedLight, el.south(),
                    chainVerts(new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(0, 0, 1, dynamicAngles));
            if (el.north() != null) emitFace(pose, consumer, packedLight, el.north(),
                    chainVerts(new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(0, 0, -1, dynamicAngles));
            if (el.east() != null) emitFace(pose, consumer, packedLight, el.east(),
                    chainVerts(new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(1, 0, 0, dynamicAngles));
            if (el.west() != null) emitFace(pose, consumer, packedLight, el.west(),
                    chainVerts(new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(-1, 0, 0, dynamicAngles));
            if (el.up() != null) emitFace(pose, consumer, packedLight, el.up(),
                    chainVerts(new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(0, 1, 0, dynamicAngles));
            if (el.down() != null) emitFace(pose, consumer, packedLight, el.down(),
                    chainVerts(new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, staticOrigin, dynamicOrigin, dynamicAngles),
                    rotate(0, -1, 0, dynamicAngles));

            previousStaticOrigin = staticOrigin;
            previousDynamicOrigin = dynamicOrigin;
            previousStaticAngles = staticAngles;
            previousDynamicAngles = dynamicAngles;
        }
    }

    private float[] dynamicOrigin(float[] staticOrigin, float[] previousStaticOrigin, float[] previousDynamicOrigin,
                                  float[] previousStaticAngles, float[] previousDynamicAngles) {
        if (previousDynamicOrigin == null) return staticOrigin;

        float[] staticOffset = new float[]{
                staticOrigin[0] - previousStaticOrigin[0],
                staticOrigin[1] - previousStaticOrigin[1],
                staticOrigin[2] - previousStaticOrigin[2]
        };
        float[] localOffset = inverseRotate(staticOffset[0], staticOffset[1], staticOffset[2], previousStaticAngles);
        float[] dynamicOffset = rotate(localOffset[0], localOffset[1], localOffset[2], previousDynamicAngles);
        return new float[]{
                previousDynamicOrigin[0] + dynamicOffset[0],
                previousDynamicOrigin[1] + dynamicOffset[1],
                previousDynamicOrigin[2] + dynamicOffset[2]
        };
    }

    private float[][] rotate(TailElement element, float[][] verts) {
        TailRotation rotation = element.rotation();
        if (rotation == null || (rotation.x() == 0.0F && rotation.y() == 0.0F && rotation.z() == 0.0F)) return verts;

        float[] origin = rotation.origin();
        float[] angles = elementAngles(element, 0.0F, 0.0F, 0.0F);
        float[][] out = new float[verts.length][3];
        for (int i = 0; i < verts.length; i++) {
            float[] rotated = rotate(verts[i][0] - origin[0], verts[i][1] - origin[1], verts[i][2] - origin[2], angles);
            out[i][0] = rotated[0] + origin[0];
            out[i][1] = rotated[1] + origin[1];
            out[i][2] = rotated[2] + origin[2];
        }
        return out;
    }

    private float[] normal(TailElement element, float x, float y, float z) {
        TailRotation rotation = element.rotation();
        if (rotation == null || (rotation.x() == 0.0F && rotation.y() == 0.0F && rotation.z() == 0.0F)) {
            return new float[]{x, y, z};
        }
        return rotate(x, y, z, elementAngles(element, 0.0F, 0.0F, 0.0F));
    }

    private float[][] chainVerts(float[][] verts, float[] staticOrigin, float[] dynamicOrigin, float[] dynamicAngles) {
        float[][] out = new float[verts.length][3];
        for (int i = 0; i < verts.length; i++) {
            float[] rotated = rotate(verts[i][0] - staticOrigin[0], verts[i][1] - staticOrigin[1], verts[i][2] - staticOrigin[2], dynamicAngles);
            out[i][0] = rotated[0] + dynamicOrigin[0];
            out[i][1] = rotated[1] + dynamicOrigin[1];
            out[i][2] = rotated[2] + dynamicOrigin[2];
        }
        return out;
    }

    private boolean isAnimatedChain(List<TailElement> elements) {
        boolean hasTail1 = false;
        for (TailElement element : elements) {
            String name = element.name();
            if ("tail1".equals(name)) hasTail1 = true;
            if (!"tailBase".equals(name) && !"tailTip".equals(name) && !isTailSegment(name)) return false;
        }
        return hasTail1;
    }

    private boolean isTailSegment(String name) {
        if (!name.startsWith("tail")) return false;
        for (int i = 4; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return name.length() > 4;
    }

    private int tailTipAnimationIndex(List<TailElement> elements) {
        int last = 0;
        for (TailElement element : elements) {
            int index = segmentIndex(element.name());
            if (index > last) last = index;
        }
        return last;
    }

    private float animationScale(int lastSegmentIndex) {
        return lastSegmentIndex >= 5 ? 0.65F : 0.4F;
    }

    private int animationIndex(String name) {
        if ("tailBase".equals(name)) return 0;
        if ("tailTip".equals(name)) return tailTipAnimationIndex;
        return segmentIndex(name);
    }

    private int segmentIndex(String name) {
        if (!isTailSegment(name)) return -1;
        return Integer.parseInt(name.substring(4));
    }

    private float[] elementOrigin(TailElement element) {
        return element.rotation() != null ? element.rotation().origin() : center(element);
    }

    private float[] elementAngles(TailElement element, float xOffset, float yOffset, float zOffset) {
        TailRotation rotation = element.rotation();
        return new float[]{
                (rotation != null ? (float) Math.toRadians(rotation.x()) : 0.0F) + xOffset,
                (rotation != null ? (float) Math.toRadians(rotation.y()) : 0.0F) + yOffset,
                (rotation != null ? (float) Math.toRadians(rotation.z()) : 0.0F) + zOffset
        };
    }

    private float[] center(TailElement element) {
        return new float[]{
                (element.from()[0] + element.to()[0]) / 2.0F,
                (element.from()[1] + element.to()[1]) / 2.0F,
                (element.from()[2] + element.to()[2]) / 2.0F
        };
    }

    private float[] rotate(float x, float y, float z, float[] angles) {
        float[] rotated = rotateX(x, y, z, angles[0]);
        rotated = rotateY(rotated[0], rotated[1], rotated[2], angles[1]);
        return rotateZ(rotated[0], rotated[1], rotated[2], angles[2]);
    }

    private float[] inverseRotate(float x, float y, float z, float[] angles) {
        float[] rotated = rotateZ(x, y, z, -angles[2]);
        rotated = rotateY(rotated[0], rotated[1], rotated[2], -angles[1]);
        return rotateX(rotated[0], rotated[1], rotated[2], -angles[0]);
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

    private void emitFace(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                           TailFace face, float[][] verts, float nx, float ny, float nz) {
        emitFace(pose, consumer, packedLight, face, verts, new float[]{nx, ny, nz});
    }

    private void emitFace(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                           TailFace face, float[][] verts, float[] normal) {
        float u0 = face.u0() / texW, v0 = face.v0() / texH;
        float u1 = face.u1() / texW, v1 = face.v1() / texH;
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};

        Vector3f transformedNormal = CosmeticSelections.instance().fullBrightPreview
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : pose.normal().transform(new Vector3f(normal[0], normal[1], normal[2]));
        for (int i = 0; i < 4; i++) {
            consumer.vertex(pose.pose(), verts[i][0] / 16.0F, verts[i][1] / 16.0F, verts[i][2] / 16.0F)
                    .color(255, 255, 255, 255)
                    .uv(uvs[i][0], uvs[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(packedLight)
                    .normal(transformedNormal.x(), transformedNormal.y(), transformedNormal.z())
                    .endVertex();
        }
    }
}
