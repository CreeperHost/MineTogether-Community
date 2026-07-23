package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelEmoteMixin {

    @Unique
    private boolean minetogether$hadEmotePose;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
    private void minetogether$resetEmotePose(AvatarRenderState state, CallbackInfo ci) {
        if (!minetogether$hadEmotePose) return;
        PlayerModel model = (PlayerModel) (Object) this;
        minetogether$resetRotations(model);
        minetogether$hadEmotePose = false;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void minetogether$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        EmotePlayer.Pose pose = ((MineTogetherCosmeticRenderState) state).minetogether$emotePose();
        if (pose == null) return;

        PlayerModel model = (PlayerModel) (Object) this;
        minetogether$hadEmotePose = true;
        float weight = pose.weight();
        model.rightArm.xRot = lerp(model.rightArm.xRot, pose.rightArmPitch(), weight);
        model.rightArm.yRot = lerp(model.rightArm.yRot, pose.rightArmYaw(), weight);
        model.rightArm.zRot = lerp(model.rightArm.zRot, pose.rightArmRoll(), weight);
        model.leftArm.xRot = lerp(model.leftArm.xRot, pose.leftArmPitch(), weight);
        model.leftArm.yRot = lerp(model.leftArm.yRot, pose.leftArmYaw(), weight);
        model.leftArm.zRot = lerp(model.leftArm.zRot, pose.leftArmRoll(), weight);
        model.rightLeg.xRot = lerp(model.rightLeg.xRot, pose.rightLegPitch(), weight);
        model.rightLeg.yRot = lerp(model.rightLeg.yRot, pose.rightLegYaw(), weight);
        model.rightLeg.zRot = lerp(model.rightLeg.zRot, pose.rightLegRoll(), weight);
        model.leftLeg.xRot = lerp(model.leftLeg.xRot, pose.leftLegPitch(), weight);
        model.leftLeg.yRot = lerp(model.leftLeg.yRot, pose.leftLegYaw(), weight);
        model.leftLeg.zRot = lerp(model.leftLeg.zRot, pose.leftLegRoll(), weight);
        model.head.xRot = lerp(model.head.xRot, pose.headPitch(), weight);
        model.head.yRot = lerp(model.head.yRot, pose.headYaw(), weight);
        model.head.zRot = lerp(model.head.zRot, pose.headRoll(), weight);
        model.body.xRot = lerp(model.body.xRot, pose.bodyPitch(), weight);
        model.body.yRot = lerp(model.body.yRot, pose.bodyYaw(), weight);
        model.body.zRot = lerp(model.body.zRot, pose.bodyRoll(), weight);
        minetogether$syncWearLayers(model);
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    @Unique
    private static void minetogether$resetRotations(PlayerModel model) {
        model.head.xRot = 0.0F;
        model.head.yRot = 0.0F;
        model.head.zRot = 0.0F;
        model.body.xRot = 0.0F;
        model.body.yRot = 0.0F;
        model.body.zRot = 0.0F;
        model.rightArm.xRot = 0.0F;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.xRot = 0.0F;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = 0.0F;
        model.rightLeg.xRot = 0.0F;
        model.rightLeg.yRot = 0.0F;
        model.rightLeg.zRot = 0.0F;
        model.leftLeg.xRot = 0.0F;
        model.leftLeg.yRot = 0.0F;
        model.leftLeg.zRot = 0.0F;
        minetogether$syncWearLayers(model);
    }

    @Unique
    private static void minetogether$syncWearLayers(PlayerModel model) {
        model.hat.loadPose(model.head.storePose());
        model.jacket.loadPose(model.body.storePose());
        model.rightSleeve.loadPose(model.rightArm.storePose());
        model.leftSleeve.loadPose(model.leftArm.storePose());
        model.rightPants.loadPose(model.rightLeg.storePose());
        model.leftPants.loadPose(model.leftLeg.storePose());
    }
}
