package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

public class HatModel extends Model {

    private final ModelPart hat;

    public HatModel(Hat hat) {
        super(RenderType::entityCutoutNoCull);

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // A single "hat" part whose children are the individual Techne shapes.
        // hat.render() recurses into all children automatically.
        PartDefinition hatPart = root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        int idx = 0;
        for (HatCuboid c : hat.cuboids()) {
            // Raw Techne values — no coordinate transformation.
            // HatLayer's scale(-1,-1,1) replicates the old iChunUtil convention.
            CubeListBuilder builder = CubeListBuilder.create()
                    .texOffs(c.texU(), c.texV())
                    .addBox(c.x(), c.y(), c.z(), c.sizeX(), c.sizeY(), c.sizeZ());

            // Position = rotation pivot; Rotation = per-shape rotation angles.
            PartPose pose = PartPose.offsetAndRotation(
                    c.pivotX(), c.pivotY(), c.pivotZ(),
                    c.rotX(),   c.rotY(),   c.rotZ());

            hatPart.addOrReplaceChild("shape" + idx++, builder, pose);
        }

        this.hat = LayerDefinition.create(mesh, hat.texWidth(), hat.texHeight())
                .bakeRoot()
                .getChild("hat");
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {
        hat.render(poseStack, consumer, packedLight, packedOverlay, color);
    }
}
