package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * In MC 1.21.3+ Model.renderToBuffer is final and Model requires a root ModelPart.
 * HatModel manages its own ModelPart and renders via a custom method.
 */
public class HatModel {

    private final ModelPart hat;

    public HatModel(Hat hat) {
        if (hat.isJsonModel()) {
            this.hat = null;
            return;
        }

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // A single "hat" part whose children are the individual Techne shapes.
        PartDefinition hatPart = root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        int idx = 0;
        for (HatCuboid c : hat.cuboids()) {
            CubeListBuilder builder = CubeListBuilder.create()
                    .texOffs(c.texU(), c.texV())
                    .addBox(c.x(), c.y(), c.z(), c.sizeX(), c.sizeY(), c.sizeZ());

            PartPose pose = PartPose.offsetAndRotation(
                    c.pivotX(), c.pivotY(), c.pivotZ(),
                    c.rotX(),   c.rotY(),   c.rotZ());

            hatPart.addOrReplaceChild("shape" + idx++, builder, pose);
        }

        this.hat = LayerDefinition.create(mesh, hat.texWidth(), hat.texHeight())
                .bakeRoot()
                .getChild("hat");
    }

    /**
     * Custom render method that bypasses the final Model.renderToBuffer.
     */
    public void renderHat(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        if (hat == null) return;
        hat.render(poseStack, consumer, packedLight, packedOverlay);
    }
}
