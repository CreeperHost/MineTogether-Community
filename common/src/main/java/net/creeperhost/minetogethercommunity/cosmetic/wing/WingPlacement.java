package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.google.gson.JsonObject;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;

/** Placement and hinge settings for a mirrored JSON wing model. */
public record WingPlacement(ModelPlacement transform, float hingeXPixels, float restTiltDegrees) {
    public static final WingPlacement DEFAULT = new WingPlacement(
            new ModelPlacement(0.0F, -7.0F, 4.2F, 0.58F),
            2.4F,
            27.0F
    );

    public static WingPlacement fromMetadata(JsonObject metadata) {
        JsonObject placement = ModelPlacement.placementObject(metadata);
        if (placement == null) return DEFAULT;
        return new WingPlacement(
                ModelPlacement.fromMetadata(metadata, DEFAULT.transform),
                ModelPlacement.getFloat(placement, "hingeX", DEFAULT.hingeXPixels),
                ModelPlacement.getFloat(placement, "restTiltDegrees", DEFAULT.restTiltDegrees)
        );
    }
}
