package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.google.gson.JsonObject;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;

public final class WingPlacement {
    public static final WingPlacement DEFAULT = new WingPlacement(
            new ModelPlacement(0.0F, -7.0F, 4.2F, 0.58F), 2.4F, 27.0F);
    private final ModelPlacement transform;
    private final float hingeXPixels;
    private final float restTiltDegrees;

    public WingPlacement(ModelPlacement transform, float hingeXPixels, float restTiltDegrees) {
        this.transform = transform;
        this.hingeXPixels = hingeXPixels;
        this.restTiltDegrees = restTiltDegrees;
    }

    public static WingPlacement fromMetadata(JsonObject metadata) {
        JsonObject placement = ModelPlacement.placementObject(metadata);
        if (placement == null) return DEFAULT;
        return new WingPlacement(ModelPlacement.fromMetadata(metadata, DEFAULT.transform),
                ModelPlacement.getFloat(placement, "hingeX", DEFAULT.hingeXPixels),
                ModelPlacement.getFloat(placement, "restTiltDegrees", DEFAULT.restTiltDegrees));
    }

    public ModelPlacement transform() { return transform; }
    public float hingeXPixels() { return hingeXPixels; }
    public float restTiltDegrees() { return restTiltDegrees; }
}
