package net.creeperhost.minetogethercommunity.cosmetic.tail;

import net.minecraft.client.renderer.Tessellator;

import java.util.ArrayList;
import java.util.List;

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

    public void render() {
        render(TailPose.none());
    }

    public void render(TailPose pose) {
        render(pose, TailElementPose.none());
    }

    public void render(TailElementPose elementPose) {
        render(TailPose.none(), elementPose);
    }

    public void render(TailPose pose, TailElementPose elementPose) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        if (animatedChain) {
            renderAnimatedChain(tessellator, pose, elementPose);
        } else {
            for (TailElement element : elements) {
                emitElement(tessellator, element, null, null, elementPose);
            }
        }
        tessellator.draw();
    }

    public List<TailElement> elements() {
        return elements;
    }

    private void emitElement(Tessellator tessellator, TailElement element, float[] dynamicOrigin, float[] dynamicAngles, TailElementPose elementPose) {
        float x0 = element.from()[0], y0 = element.from()[1], z0 = element.from()[2];
        float x1 = element.to()[0], y1 = element.to()[1], z1 = element.to()[2];
        if (element.south() != null) emitFace(tessellator, element.south(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}), normal(element, dynamicAngles, elementPose, 0, 0, 1));
        if (element.north() != null) emitFace(tessellator, element.north(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}), normal(element, dynamicAngles, elementPose, 0, 0, -1));
        if (element.east() != null) emitFace(tessellator, element.east(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}), normal(element, dynamicAngles, elementPose, 1, 0, 0));
        if (element.west() != null) emitFace(tessellator, element.west(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}), normal(element, dynamicAngles, elementPose, -1, 0, 0));
        if (element.up() != null) emitFace(tessellator, element.up(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}), normal(element, dynamicAngles, elementPose, 0, 1, 0));
        if (element.down() != null) emitFace(tessellator, element.down(), verts(element, dynamicOrigin, dynamicAngles, elementPose, new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}), normal(element, dynamicAngles, elementPose, 0, -1, 0));
    }

    private void renderAnimatedChain(Tessellator tessellator, TailPose pose, TailElementPose elementPose) {
        float[] previousStaticOrigin = null;
        float[] previousDynamicOrigin = null;
        float[] previousStaticAngles = null;
        float[] previousDynamicAngles = null;
        List<ChainState> chainStates = new ArrayList<ChainState>();

        for (TailElement element : elements) {
            int animationIndex = animationIndex(element.name());
            if (animationIndex < 0) continue;
            float[] staticOrigin = elementOrigin(element);
            float[] staticAngles = elementAngles(element, 0.0F, 0.0F, 0.0F);
            float[] dynamicAngles = elementAngles(element,
                    pose.x(animationIndex) * animationScale + elementPose.x(element.name()),
                    pose.y(animationIndex) * animationScale + elementPose.y(element.name()),
                    pose.z(animationIndex) * animationScale + elementPose.z(element.name()));
            float[] dynamicOrigin = dynamicOrigin(staticOrigin, previousStaticOrigin, previousDynamicOrigin, previousStaticAngles, previousDynamicAngles);
            emitElement(tessellator, element, dynamicOrigin, dynamicAngles, TailElementPose.none());
            previousStaticOrigin = staticOrigin;
            previousDynamicOrigin = dynamicOrigin;
            previousStaticAngles = staticAngles;
            previousDynamicAngles = dynamicAngles;
            chainStates.add(new ChainState(staticOrigin, dynamicOrigin, staticAngles, dynamicAngles));
        }

        for (TailElement element : elements) {
            if (animationIndex(element.name()) >= 0) continue;
            ChainState parent = nearestChainState(element, chainStates);
            if (parent == null) continue;
            float[] staticOrigin = elementOrigin(element);
            float[] staticAngles = elementAngles(element, 0.0F, 0.0F, 0.0F);
            float[] deltaAngles = new float[] {
                    parent.dynamicAngles[0] - parent.staticAngles[0],
                    parent.dynamicAngles[1] - parent.staticAngles[1],
                    parent.dynamicAngles[2] - parent.staticAngles[2]
            };
            float[] dynamicAngles = new float[] {
                    staticAngles[0] + deltaAngles[0] + elementPose.x(element.name()),
                    staticAngles[1] + deltaAngles[1] + elementPose.y(element.name()),
                    staticAngles[2] + deltaAngles[2] + elementPose.z(element.name())
            };
            float[] dynamicOrigin = dynamicOrigin(staticOrigin, parent.staticOrigin, parent.dynamicOrigin, parent.staticAngles, parent.dynamicAngles);
            emitElement(tessellator, element, dynamicOrigin, dynamicAngles, TailElementPose.none());
        }
    }

    private ChainState nearestChainState(TailElement element, List<ChainState> chainStates) {
        if (chainStates.isEmpty()) return null;
        float[] origin = elementOrigin(element);
        ChainState nearest = chainStates.get(0);
        float nearestDistance = distanceSquared(origin, nearest.staticOrigin);
        for (int i = 1; i < chainStates.size(); i++) {
            ChainState state = chainStates.get(i);
            float distance = distanceSquared(origin, state.staticOrigin);
            if (distance < nearestDistance) {
                nearest = state;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private float distanceSquared(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private float[][] verts(TailElement element, float[] dynamicOrigin, float[] dynamicAngles, TailElementPose elementPose, float[][] verts) {
        if (dynamicOrigin != null && dynamicAngles != null) {
            float[] staticOrigin = elementOrigin(element);
            return transformAround(verts, staticOrigin, dynamicOrigin, dynamicAngles);
        }
        TailRotation rotation = element.rotation();
        float xOffset = elementPose.x(element.name());
        float yOffset = elementPose.y(element.name());
        float zOffset = elementPose.z(element.name());
        if ((rotation == null || (rotation.x() == 0.0F && rotation.y() == 0.0F && rotation.z() == 0.0F))
                && xOffset == 0.0F && yOffset == 0.0F && zOffset == 0.0F) return verts;
        float[] origin = elementOrigin(element);
        return transformAround(verts, origin, origin, elementAngles(element, xOffset, yOffset, zOffset));
    }

    private float[][] transformAround(float[][] verts, float[] sourceOrigin, float[] targetOrigin, float[] angles) {
        float[][] out = new float[verts.length][3];
        for (int i = 0; i < verts.length; i++) {
            float[] rotated = rotate(verts[i][0] - sourceOrigin[0], verts[i][1] - sourceOrigin[1], verts[i][2] - sourceOrigin[2], angles);
            out[i][0] = rotated[0] + targetOrigin[0];
            out[i][1] = rotated[1] + targetOrigin[1];
            out[i][2] = rotated[2] + targetOrigin[2];
        }
        return out;
    }

    private float[] normal(TailElement element, float[] dynamicAngles, TailElementPose elementPose, float x, float y, float z) {
        if (dynamicAngles != null) return rotate(x, y, z, dynamicAngles);
        TailRotation rotation = element.rotation();
        float xOffset = elementPose.x(element.name());
        float yOffset = elementPose.y(element.name());
        float zOffset = elementPose.z(element.name());
        if ((rotation == null || (rotation.x() == 0.0F && rotation.y() == 0.0F && rotation.z() == 0.0F))
                && xOffset == 0.0F && yOffset == 0.0F && zOffset == 0.0F) return new float[] {x, y, z};
        return rotate(x, y, z, elementAngles(element, xOffset, yOffset, zOffset));
    }

    private void emitFace(Tessellator tessellator, TailFace face, float[][] verts, float[] normal) {
        float u0 = face.u0() / texW, v0 = face.v0() / texH;
        float u1 = face.u1() / texW, v1 = face.v1() / texH;
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        tessellator.setNormal(normal[0], normal[1], normal[2]);
        for (int i = 0; i < 4; i++) {
            tessellator.addVertexWithUV(verts[i][0] / 16.0F, verts[i][1] / 16.0F, verts[i][2] / 16.0F, uvs[i][0], uvs[i][1]);
        }
    }

    private float[] dynamicOrigin(float[] staticOrigin, float[] previousStaticOrigin, float[] previousDynamicOrigin,
                                  float[] previousStaticAngles, float[] previousDynamicAngles) {
        if (previousDynamicOrigin == null) return staticOrigin;
        float[] staticOffset = new float[] {
                staticOrigin[0] - previousStaticOrigin[0],
                staticOrigin[1] - previousStaticOrigin[1],
                staticOrigin[2] - previousStaticOrigin[2]
        };
        float[] localOffset = inverseRotate(staticOffset[0], staticOffset[1], staticOffset[2], previousStaticAngles);
        float[] dynamicOffset = rotate(localOffset[0], localOffset[1], localOffset[2], previousDynamicAngles);
        return new float[] {
                previousDynamicOrigin[0] + dynamicOffset[0],
                previousDynamicOrigin[1] + dynamicOffset[1],
                previousDynamicOrigin[2] + dynamicOffset[2]
        };
    }

    private boolean isAnimatedChain(List<TailElement> elements) {
        boolean hasTail1 = false;
        for (TailElement element : elements) {
            String name = element.name();
            if ("tail1".equals(name)) hasTail1 = true;
        }
        return hasTail1;
    }

    private boolean isTailSegment(String name) {
        if (name == null || !name.startsWith("tail") || name.length() <= 4) return false;
        for (int i = 4; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }

    private boolean isTailSubSegment(String name) {
        if (name == null || !name.startsWith("tailSub") || name.length() <= 7) return false;
        for (int i = 7; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
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
        if ("tailSubBase".equals(name)) return 0;
        if ("tailTip".equals(name)) return tailTipAnimationIndex;
        return segmentIndex(name);
    }

    private int segmentIndex(String name) {
        if (isTailSegment(name)) return Integer.parseInt(name.substring(4));
        if (isTailSubSegment(name)) return Integer.parseInt(name.substring(7));
        return -1;
    }

    private float[] elementOrigin(TailElement element) {
        return element.rotation() != null ? element.rotation().origin() : center(element);
    }

    private float[] elementAngles(TailElement element, float xOffset, float yOffset, float zOffset) {
        TailRotation rotation = element.rotation();
        return new float[] {
                (rotation != null ? (float) Math.toRadians(rotation.x()) : 0.0F) + xOffset,
                (rotation != null ? (float) Math.toRadians(rotation.y()) : 0.0F) + yOffset,
                (rotation != null ? (float) Math.toRadians(rotation.z()) : 0.0F) + zOffset
        };
    }

    private float[] center(TailElement element) {
        return new float[] {
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
        if (angle == 0.0F) return new float[] {x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[] {x, y * cos - z * sin, y * sin + z * cos};
    }

    private float[] rotateY(float x, float y, float z, float angle) {
        if (angle == 0.0F) return new float[] {x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[] {x * cos + z * sin, y, -x * sin + z * cos};
    }

    private float[] rotateZ(float x, float y, float z, float angle) {
        if (angle == 0.0F) return new float[] {x, y, z};
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new float[] {x * cos - y * sin, x * sin + y * cos, z};
    }

    private static class ChainState {
        private final float[] staticOrigin;
        private final float[] dynamicOrigin;
        private final float[] staticAngles;
        private final float[] dynamicAngles;

        private ChainState(float[] staticOrigin, float[] dynamicOrigin, float[] staticAngles, float[] dynamicAngles) {
            this.staticOrigin = staticOrigin;
            this.dynamicOrigin = dynamicOrigin;
            this.staticAngles = staticAngles;
            this.dynamicAngles = dynamicAngles;
        }
    }
}
