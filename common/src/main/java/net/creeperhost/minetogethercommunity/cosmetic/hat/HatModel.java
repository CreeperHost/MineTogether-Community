package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class HatModel extends Model<AvatarRenderState> {

    public HatModel(Hat hat) {
        super(createRoot(hat), RenderTypes::entityCutout);
    }

    private static ModelPart createRoot(Hat hat) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition hatPart = root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        int idx = 0;
        for (HatCuboid c : hat.cuboids()) {
            CubeListBuilder builder = CubeListBuilder.create()
                    .texOffs(c.texU(), c.texV())
                    .addBox(c.x(), c.y(), c.z(), c.sizeX(), c.sizeY(), c.sizeZ());

            PartPose pose = PartPose.offsetAndRotation(
                    c.pivotX(), c.pivotY(), c.pivotZ(),
                    c.rotX(), c.rotY(), c.rotZ());

            hatPart.addOrReplaceChild("shape" + idx++, builder, pose);
        }

        return LayerDefinition.create(mesh, hat.texWidth(), hat.texHeight()).bakeRoot();
    }
}
