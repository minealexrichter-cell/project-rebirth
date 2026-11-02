package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person arm of a player inside an artificial body. Body textures place the arms exactly like the
 * player skin layout, so the vanilla arm model (wide or slim) is drawn with the body texture.
 */
public class BodyArmRenderer {
    private final PlayerModel<AbstractClientPlayer> wideModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public BodyArmRenderer(EntityRendererProvider.Context context) {
        this.wideModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    /** Same steps as the vanilla first-person hand, but with the body texture and without sleeves. */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       HumanoidArm side, BodyClass bodyClass) {
        PlayerModel<AbstractClientPlayer> model = bodyClass.slimArms() ? slimModel : wideModel;
        model.attackTime = 0.0f;
        model.crouching = false;
        model.swimAmount = 0.0f;
        model.setupAnim(player, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.xRot = 0.0f;
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(BodyModels.texture(bodyClass))), packedLight, OverlayTexture.NO_OVERLAY);
    }
}
