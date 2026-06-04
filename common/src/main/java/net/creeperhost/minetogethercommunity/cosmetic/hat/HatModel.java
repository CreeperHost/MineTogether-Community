package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

public class HatModel extends Model {

    private final ModelPart hat;

    public HatModel(Hat hat) {
        super(RenderType::entityCutoutNoCull);
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder builder = CubeListBuilder.create();
        for (HatCuboid c : hat.cuboids()) {
            builder.texOffs(c.texU(), c.texV())
                    .addBox(c.x(), c.y(), c.z(), c.sizeX(), c.sizeY(), c.sizeZ());
        }
        mesh.getRoot().addOrReplaceChild("hat", builder, PartPose.ZERO);
        this.hat = LayerDefinition.create(mesh, hat.texWidth(), hat.texHeight()).bakeRoot().getChild("hat");
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {
        hat.render(poseStack, consumer, packedLight, packedOverlay, color);
    }
}
