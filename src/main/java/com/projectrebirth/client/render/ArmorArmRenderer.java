package com.projectrebirth.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.powerarmor.ArmorVariant;

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

import java.util.EnumMap;
import java.util.Map;

/**
 * First-person arms of the power armor. The arm, shoulder and fist boxes copy the armor's geo model
 * (same sizes and texture UVs), arranged like a player arm so that the vanilla first-person hand motion fits.
 */
public class ArmorArmRenderer {
    /** The armor arm is almost twice as long as a player arm. */
    private static final float SCALE = 0.62f;
    private static final float REACHING_SCALE = 0.45f;
    private static final float FIST_MIDDLE = 21.0f;

    /** Atlas positions of the arm boxes, the same in every variant's texture and geo model. */
    private static final int RIGHT_V = 100;
    private static final int LEFT_V = 70;
    private static final int SHOULDER_U = 0;
    private static final int ARM_U = 40;
    private static final int FIST_U = 70;

    /** A box of the arm, centred on the arm's axis; top is measured down from the shoulder joint. */
    private record Box(int u, int rightV, int leftV, int width, int height, int depth, float top) {
        /** Boxes with their own texture area on each arm. */
        static Box own(int u, int width, int height, int depth, float top) {
            return new Box(u, RIGHT_V, LEFT_V, width, height, depth, top);
        }

        /** Plates painted once and used by both arms. */
        static Box shared(int u, int v, int width, int height, int depth, float top) {
            return new Box(u, v, v, width, height, depth, top);
        }
    }

    /** Must match the arm boxes of geo/entity/&lt;item id&gt;.geo.json; small details out of the first-person view are left out. */
    private static Box[] boxes(ArmorVariant variant) {
        return switch (variant) {
            case COMBAT -> new Box[] {Box.own(SHOULDER_U, 8, 7, 10, -4), Box.own(ARM_U, 5, 16, 6, 3), Box.own(FIST_U, 6, 4, 7, 19),
                    Box.shared(140, 0, 9, 3, 11, -5), Box.shared(111, 14, 7, 7, 8, 12)};
            case MOBILE -> new Box[] {Box.own(SHOULDER_U, 5, 5, 7, -3), Box.own(ARM_U, 4, 15, 5, 2), Box.own(FIST_U, 5, 4, 6, 17),
                    Box.shared(70, 19, 5, 5, 6, 11)};
            case WORKER -> new Box[] {Box.own(SHOULDER_U, 8, 8, 10, -4), Box.own(ARM_U, 6, 14, 7, 4), Box.own(FIST_U, 8, 5, 9, 18),
                    Box.shared(32, 34, 8, 5, 8, 13)};
        };
    }

    /** Size of the variant's texture: the combat armor has the detailed 256 model of 1.5.2, the others 128. */
    private static int textureSize(ArmorVariant variant) {
        return variant == ArmorVariant.COMBAT ? 256 : 128;
    }

    /** The arm plate's texture area, fixed like the arm boxes (see ARM_UV in blockbench/tools/build_models.js). */
    private static final int PLATE_U = 110;
    private static final int PLATE_V = 100;

    /** Width and depth of the upper arm, and the plate's height and top measured from the shoulder joint; must match the geo model. */
    private record PlateSize(int armWidth, int armDepth, int height, int top) {
    }

    private static PlateSize plate(ArmorVariant variant) {
        return switch (variant) {
            case COMBAT -> new PlateSize(5, 6, 6, 4);
            case MOBILE -> new PlateSize(4, 5, 6, 3);
            case WORKER -> new PlateSize(6, 7, 5, 5);
        };
    }

    private final Map<ArmorVariant, ModelPart> rightPlates = new EnumMap<>(ArmorVariant.class);
    private final Map<ArmorVariant, ModelPart> leftPlates = new EnumMap<>(ArmorVariant.class);
    private final Map<ArmorVariant, ModelPart> rightArms = new EnumMap<>(ArmorVariant.class);
    private final Map<ArmorVariant, ModelPart> leftArms = new EnumMap<>(ArmorVariant.class);

    public ArmorArmRenderer() {
        for (ArmorVariant variant : ArmorVariant.values()) {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("right_arm", cubes(variant, true), PartPose.offset(-5.0f, 2.0f, 0.0f));
            root.addOrReplaceChild("left_arm", cubes(variant, false), PartPose.offset(5.0f, 2.0f, 0.0f));
            root.addOrReplaceChild("right_plate", plateCube(variant, true), PartPose.offset(-5.0f, 2.0f, 0.0f));
            root.addOrReplaceChild("left_plate", plateCube(variant, false), PartPose.offset(5.0f, 2.0f, 0.0f));
            ModelPart baked = LayerDefinition.create(mesh, textureSize(variant), textureSize(variant)).bakeRoot();
            rightArms.put(variant, baked.getChild("right_arm"));
            leftArms.put(variant, baked.getChild("left_arm"));
            rightPlates.put(variant, baked.getChild("right_plate"));
            leftPlates.put(variant, baked.getChild("left_plate"));
        }
    }

    /** On the outer side of the upper arm. */
    private static CubeListBuilder plateCube(ArmorVariant variant, boolean right) {
        PlateSize size = plate(variant);
        float outer = right ? -size.armWidth() / 2.0f - 2.0f : size.armWidth() / 2.0f + 1.0f;
        return CubeListBuilder.create().texOffs(PLATE_U, PLATE_V)
                .addBox(outer, size.top(), -size.armDepth() / 2.0f, 1, size.height(), size.armDepth());
    }

    private static CubeListBuilder cubes(ArmorVariant variant, boolean right) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (Box box : boxes(variant)) {
            cubes.texOffs(box.u(), right ? box.rightV() : box.leftV()).addBox(-box.width() / 2.0f + (right ? -1.0f : 1.0f), box.top(), -box.depth() / 2.0f,
                    box.width(), box.height(), box.depth());
        }
        return cubes;
    }

    /**
     * An arm whose fist is at a given point of the view, reaching there from the shoulder at the side of the screen: it holds
     * a weapon. The point comes from the weapon's own pose, so the hands go wherever the weapon goes.
     */
    public void renderReaching(PoseStack poseStack, MultiBufferSource buffers, int packedLight, HumanoidArm side, ArmorVariant variant, int plateColor,
                               Vector3f fist, Vector3f shoulder) {
        boolean right = side != HumanoidArm.LEFT;
        Vector3f direction = new Vector3f(fist).sub(shoulder).normalize();
        poseStack.pushPose();
        poseStack.translate(fist.x, fist.y, fist.z);
        // The arm's boxes run along +Y, from the shoulder down to the fist.
        poseStack.mulPose(new Quaternionf().rotationTo(0.0f, 1.0f, 0.0f, direction.x, direction.y, direction.z));
        poseStack.scale(REACHING_SCALE, REACHING_SCALE, REACHING_SCALE);
        // From the part's own origin to the middle of its fist.
        poseStack.translate((right ? 6.0f : -6.0f) / 16.0f, -(2.0f + FIST_MIDDLE) / 16.0f, 0.0f);
        ModelPart arm = (right ? rightArms : leftArms).get(variant);
        arm.xRot = 0.0f;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(PowerArmorRenderer.texture(variant)));
        arm.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        if (plateColor != 0) {
            ModelPart plate = (right ? rightPlates : leftPlates).get(variant);
            plate.xRot = 0.0f;
            plate.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, plateColor);
        }
        poseStack.popPose();
    }

    /**
     * Resting pose is the vanilla first-person arm pose (ItemInHandRenderer#renderPlayerArm without swing).
     * The vanilla swing would sweep the long armor arm off the bottom of the screen, so a punch is drawn instead:
     * the arm is thrust forward, up and towards the crosshair, then pulled back.
     * {@code plateColor}: the colour of the plate on this arm, 0 for none.
     */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, float equipProgress, float swingProgress,
                       HumanoidArm side, ArmorVariant variant, int plateColor) {
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
        ModelPart arm = (right ? rightArms : leftArms).get(variant);
        arm.xRot = 0.0f;
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(PowerArmorRenderer.texture(variant))), packedLight, OverlayTexture.NO_OVERLAY);
        if (plateColor != 0) {
            ModelPart plate = (right ? rightPlates : leftPlates).get(variant);
            plate.xRot = 0.0f;
            plate.render(poseStack, buffers.getBuffer(RenderType.entitySolid(PowerArmorRenderer.texture(variant))), packedLight, OverlayTexture.NO_OVERLAY, plateColor);
        }
        poseStack.popPose();
    }
}
