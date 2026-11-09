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
    /** The shoulder cap and the forearm plate (1.5.2), shared by both arms: ARM_UV.cap and ARM_UV.guard in build_models.js. */
    private static final int CAP_U = 140;
    private static final int GUARD_U = 190;

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
            case COMBAT -> new Box[] {Box.own(SHOULDER_U, 8, 7, 11, -4), Box.own(ARM_U, 5, 17, 6, 2), Box.own(FIST_U, 6, 5, 7, 18.5f),
                    Box.shared(CAP_U, RIGHT_V, 9, 2, 12, -5.6f), Box.shared(GUARD_U, RIGHT_V, 7, 7, 8, 11.2f)};
            case MOBILE -> new Box[] {Box.own(SHOULDER_U, 6, 6, 7, -3), Box.own(ARM_U, 4, 15, 4, 2), Box.own(FIST_U, 5, 5, 5, 16),
                    Box.shared(GUARD_U, RIGHT_V, 5, 5, 6, 11.3f), Box.shared(CAP_U, RIGHT_V, 7, 1, 6, -3.7f)};
            case WORKER -> new Box[] {Box.own(SHOULDER_U, 9, 9, 10, -5), Box.own(ARM_U, 6, 16, 6, -1), Box.own(FIST_U, 8, 6, 8, 17.2f),
                    Box.shared(GUARD_U, RIGHT_V, 8, 6, 9, 11.4f), Box.shared(CAP_U, RIGHT_V, 7, 3, 7, 8.8f)};
        };
    }

    /** Size of the variant's texture: all three armors have the detailed 256 models of 1.5.2. */
    private static int textureSize(ArmorVariant variant) {
        return 256;
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
            case MOBILE -> new PlateSize(4, 4, 6, 4);
            case WORKER -> new PlateSize(6, 6, 5, 5);
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

    /** Where an arm moves in a strike: an offset in the view (blocks) and turns (degrees) about its own placement. */
    private record Motion(float x, float y, float z, float pitch, float yaw, float roll) {
        static final Motion REST = new Motion(0, 0, 0, 0, 0, 0);

        Motion scaled(float k) {
            return new Motion(x * k, y * k, z * k, pitch * k, yaw * k, roll * k);
        }
    }

    /** 0 to 1 fast, then back to 0 slower: one blow over a phase 0..1. */
    private static float pulse(float phase, float out) {
        if (phase <= 0.0f || phase >= 1.0f) {
            return 0.0f;
        }
        return phase < out ? phase / out : Mth.cos((phase - out) / (1.0f - out) * Mth.HALF_PI);
    }

    /** Ticks of one mining blow of the combat and mobile armors; the two arms take turns. */
    private static final float MINING_CYCLE = 8.0f;

    /**
     * The strike of one arm, by variant (1.5.2). Combat: a straight cross of the lead arm while the other guards; mining is
     * left and right punches in turn. Mobile: the lead arm's claws rake across the view from up outside to down inside;
     * mining is short rakes in turn. Worker: both arms aim at the crosshair and hammer in quick short blows, like a
     * jackhammer, all the time it mines. f: +1 for the right arm, -1 for the left; lead: the arm of the pilot's main hand;
     * swing: the vanilla swing progress; time: ticks with the partial tick.
     */
    private static Motion motion(ArmorVariant variant, float f, boolean lead, float swing, boolean mining, float time) {
        switch (variant) {
            case COMBAT -> {
                if (mining) {
                    float phase = (time % MINING_CYCLE) / MINING_CYCLE;
                    float k = pulse(lead ? phase * 2.0f : phase * 2.0f - 1.0f, 0.3f);
                    return new Motion(f * -0.2f, 0.3f, -0.5f, -24.0f, f * 6.0f, 0.0f).scaled(k);
                }
                float k = pulse(swing, 0.25f);
                return lead ? new Motion(f * -0.32f, 0.32f, -0.7f, -26.0f, f * 10.0f, 0.0f).scaled(k)
                        : new Motion(f * -0.08f, 0.12f, 0.1f, 12.0f, f * -6.0f, 0.0f).scaled(k);
            }
            case MOBILE -> {
                if (mining) {
                    float phase = (time % MINING_CYCLE) / MINING_CYCLE;
                    float p = lead ? phase * 2.0f : phase * 2.0f - 1.0f;
                    return rake(f, p).scaled(0.85f);
                }
                return lead ? rake(f, swing) : new Motion(f * 0.05f, -0.05f, 0.12f, 10.0f, 0.0f, 0.0f).scaled(pulse(swing, 0.3f));
            }
            case WORKER -> {
                // aim at the crosshair; in a single strike rise, hammer four times, come back; while mining stay aimed
                float aim = mining ? 1.0f : swing < 0.15f ? swing / 0.15f : swing > 0.85f ? (1.0f - swing) / 0.15f : 1.0f;
                float blows = mining ? Math.abs(Mth.sin(time * Mth.PI / 2.5f)) : Math.abs(Mth.sin((swing - 0.15f) / 0.7f * 4.0f * Mth.PI));
                if (!mining && (swing < 0.15f || swing > 0.85f)) {
                    blows = 0.0f;
                }
                float jolt = 0.012f * Mth.sin(time * 5.0f);
                return new Motion(f * -0.2f * aim, (0.32f + jolt) * aim, (-0.3f - 0.2f * blows) * aim, (-24.0f - 6.0f * blows) * aim, f * 12.0f * aim, 0.0f);
            }
        }
        return Motion.REST;
    }

    /** The mobile armor's claw rake over a phase 0..1: up and out, a fast cut down and across, back. */
    private static Motion rake(float f, float phase) {
        if (phase <= 0.0f || phase >= 1.0f) {
            return Motion.REST;
        }
        float raise = Math.min(1.0f, phase / 0.25f);
        float sweep = Mth.clamp((phase - 0.25f) / 0.3f, 0.0f, 1.0f);
        float back = Mth.clamp((phase - 0.55f) / 0.45f, 0.0f, 1.0f);
        float w = raise * (1.0f - back);
        return new Motion(f * (0.02f - 0.6f * sweep) * w, (0.85f - 0.55f * sweep) * w, -0.45f * w, -30.0f * w, f * 10.0f * w, f * (35.0f - 85.0f * sweep) * w);
    }

    /**
     * Resting pose is the vanilla first-person arm pose (ItemInHandRenderer#renderPlayerArm without swing).
     * The vanilla swing would sweep the long armor arm off the bottom of the screen, so each armor draws its own strike
     * and mining (see motion). {@code plateColor}: the colour of the plate on this arm, 0 for none.
     */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, float equipProgress, float swingProgress,
                       HumanoidArm side, boolean lead, boolean mining, float time, ArmorVariant variant, int plateColor) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0f : -1.0f;
        Motion m = motion(variant, f, lead, swingProgress, mining, time);
        poseStack.pushPose();
        poseStack.translate(m.x(), m.y(), m.z());
        poseStack.translate(f * 0.64f, -0.6f + equipProgress * -0.6f, -0.72f);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(m.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(m.yaw()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(m.roll()));
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
