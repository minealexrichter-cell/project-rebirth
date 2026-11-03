package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person arms of the power armor. The arm, shoulder and fist boxes copy the armor's geo model
 * (same sizes and texture UVs), arranged like a player arm so that the vanilla first-person hand motion fits.
 */
public class ArmorArmRenderer {
    private static final ResourceLocation TEXTURE = ProjectRebirth.id("textures/entity/power_armor.png");
    /** The armor arm is almost twice as long as a player arm. */
    private static final float SCALE = 0.62f;

    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public ArmorArmRenderer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // UVs must match geo/entity/power_armor.geo.json (shoulder, arm, fist).
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(24, 24).addBox(-4.5f, -4.0f, -4.5f, 7, 7, 9)
                        .texOffs(56, 0).addBox(-3.5f, 3.0f, -3.0f, 5, 16, 6)
                        .texOffs(30, 56).addBox(-4.0f, 19.0f, -3.5f, 6, 4, 7),
                PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(56, 24).addBox(-2.5f, -4.0f, -4.5f, 7, 7, 9)
                        .texOffs(78, 0).addBox(-1.5f, 3.0f, -3.0f, 5, 16, 6)
                        .texOffs(56, 56).addBox(-2.0f, 19.0f, -3.5f, 6, 4, 7),
                PartPose.offset(5.0f, 2.0f, 0.0f));
        ModelPart baked = LayerDefinition.create(mesh, 128, 128).bakeRoot();
        this.rightArm = baked.getChild("right_arm");
        this.leftArm = baked.getChild("left_arm");
    }

    /**
     * Resting pose is the vanilla first-person arm pose (ItemInHandRenderer#renderPlayerArm without swing).
     * The vanilla swing would sweep the long armor arm off the bottom of the screen, so a punch is drawn instead:
     * the arm is thrust forward, up and towards the crosshair, then pulled back.
     */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, float equipProgress, float swingProgress,
                       HumanoidArm side) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0f : -1.0f;
        // Fast thrust out, slower pull back.
        float punch = swingProgress < 0.3f ? swingProgress / 0.3f : Mth.cos((swingProgress - 0.3f) / 0.7f * Mth.HALF_PI);
        poseStack.pushPose();
        poseStack.translate(f * -0.22f * punch, 0.18f * punch, -0.55f * punch);
        poseStack.translate(f * 0.64f, -0.6f + equipProgress * -0.6f, -0.72f);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-25.0f * punch));
        poseStack.translate(f * -1.0f, 3.6f, 3.5f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
        poseStack.translate(f * 5.6f, 0.0f, 0.0f);
        poseStack.scale(SCALE, SCALE, SCALE);
        ModelPart arm = right ? rightArm : leftArm;
        arm.xRot = 0.0f;
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
