package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import java.util.ArrayList;
import java.util.List;

public class HatModel extends ModelBase {

    private final List<ModelRenderer> boxes = new ArrayList<ModelRenderer>();

    public HatModel(Hat hat) {
        textureWidth = hat.texWidth();
        textureHeight = hat.texHeight();
        for (HatCuboid cuboid : hat.cuboids()) {
            ModelRenderer box = new ModelRenderer(this, cuboid.texU(), cuboid.texV());
            box.setRotationPoint(cuboid.pivotX(), cuboid.pivotY(), cuboid.pivotZ());
            box.rotateAngleX = cuboid.rotX();
            box.rotateAngleY = cuboid.rotY();
            box.rotateAngleZ = cuboid.rotZ();
            box.addBox(cuboid.x(), cuboid.y(), cuboid.z(),
                    Math.max(1, Math.round(cuboid.sizeX())),
                    Math.max(1, Math.round(cuboid.sizeY())),
                    Math.max(1, Math.round(cuboid.sizeZ())));
            boxes.add(box);
        }
    }

    public void render(float scale) {
        for (ModelRenderer box : boxes) {
            box.render(scale);
        }
    }
}
