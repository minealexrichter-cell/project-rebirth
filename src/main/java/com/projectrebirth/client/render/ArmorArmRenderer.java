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

    /** Where an arm is in a strike: an offset in the view (blocks) and turns (degrees) about its own placement. */
    private record Motion(float x, float y, float z, float pitch, float yaw, float roll) {
        static final Motion REST = new Motion(0, 0, 0, 0, 0, 0);

        float get(int i) {
            return switch (i) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                case 3 -> pitch;
                case 4 -> yaw;
                default -> roll;
            };
        }

        static Motion of(float[] v) {
            return new Motion(v[0], v[1], v[2], v[3], v[4], v[5]);
        }

        Motion scaled(float k) {
            return new Motion(x * k, y * k, z * k, pitch * k, yaw * k, roll * k);
        }

        Motion plus(Motion o) {
            return new Motion(x + o.x, y + o.y, z + o.z, pitch + o.pitch, yaw + o.yaw, roll + o.roll);
        }
    }

    /** A pose at a moment of a strike, 0..1. */
    private record Key(float t, Motion m) {
    }

    private static Key key(float t, float x, float y, float z, float pitch, float yaw, float roll) {
        return new Key(t, new Motion(x, y, z, pitch, yaw, roll));
    }

    private static Key rest(float t) {
        return new Key(t, Motion.REST);
    }

    /**
     * The pose at moment t (0..1) of a movement through the keys, smooth through every key (a cubic curve whose slope at a
     * key follows its neighbours, and is still at the first and the last): the arm keeps its speed through the poses
     * instead of stopping at each, and rests only at the ends.
     */
    private static Motion track(float t, Key... keys) {
        if (t <= keys[0].t()) {
            return keys[0].m();
        }
        if (t >= keys[keys.length - 1].t()) {
            return keys[keys.length - 1].m();
        }
        int i = 0;
        while (t > keys[i + 1].t()) {
            i++;
        }
        Key k0 = keys[i], k1 = keys[i + 1];
        float h = k1.t() - k0.t(), s = (t - k0.t()) / h;
        float h00 = 2 * s * s * s - 3 * s * s + 1, h10 = s * s * s - 2 * s * s + s, h01 = -2 * s * s * s + 3 * s * s, h11 = s * s * s - s * s;
        float[] v = new float[6];
        for (int c = 0; c < 6; c++) {
            float m0 = slope(keys, i, c), m1 = slope(keys, i + 1, c);
            v[c] = h00 * k0.m().get(c) + h10 * h * m0 + h01 * k1.m().get(c) + h11 * h * m1;
        }
        return Motion.of(v);
    }

    private static float slope(Key[] keys, int i, int c) {
        if (i == 0 || i == keys.length - 1) {
            return 0.0f;
        }
        return (keys[i + 1].m().get(c) - keys[i - 1].m().get(c)) / (keys[i + 1].t() - keys[i - 1].t());
    }

    /** A smooth bump 0 -> 1 -> 0 over a phase 0..1: one hammer blow. */
    private static float blow(float phase) {
        float s = Mth.sin(phase * Mth.PI);
        return s * s;
    }

    /** Ticks of one mining cycle, in which each arm strikes once (the arms take turns); the worker hammers faster. */
    private static final float COMBAT_MINING_CYCLE = 14.0f;
    private static final float MOBILE_MINING_CYCLE = 12.0f;
    private static final float WORKER_BLOW = 8.0f;
    /** How much of a full push each mining blow makes: breaking a block is a steady hum, not a shake. */
    private static final float WORKER_MINING_PUSH = 0.3f;
    /** Blows of the worker's jackhammer in one strike; few and soft enough to read as a heavy push, not a shake. */
    private static final int WORKER_STRIKE_BLOWS = 2;

    /**
     * One arm in a strike (1.5.2), by variant; strike: 0..1 over the time until the next strike is ready (a sword's
     * cooldown), or below 0 when there is none. Combat: the lead arm draws back a little and drives a straight cross at
     * the crosshair while the other arm comes up to guard. Mobile: the lead arm rises up and out, the claws cut down and
     * across the view and follow through, the other arm pulls back. Worker: both arms come up to the crosshair and hammer
     * four quick blows, like a jackhammer. f: +1 for the right arm, -1 for the left; lead: the arm of the main hand.
     */
    private static Motion strike(ArmorVariant variant, float f, boolean lead, float strike) {
        if (strike < 0.0f || strike > 1.0f) {
            return Motion.REST;
        }
        return switch (variant) {
            case COMBAT -> lead
                    ? track(strike, rest(0.0f), key(0.1f, f * 0.06f, -0.06f, 0.14f, 10.0f, f * -5.0f, f * 4.0f),
                        key(0.28f, f * -0.3f, 0.3f, -0.72f, -26.0f, f * 11.0f, f * -8.0f), key(0.42f, f * -0.27f, 0.28f, -0.64f, -24.0f, f * 9.0f, f * -6.0f),
                        key(0.75f, f * -0.05f, 0.05f, -0.1f, -4.0f, f * 2.0f, 0.0f), rest(1.0f))
                    : track(strike, rest(0.0f), key(0.28f, f * -0.07f, 0.1f, 0.12f, 12.0f, f * -6.0f, f * -4.0f),
                        key(0.6f, f * -0.03f, 0.04f, 0.05f, 5.0f, f * -2.0f, 0.0f), rest(1.0f));
            case MOBILE -> lead
                    ? track(strike, rest(0.0f), key(0.22f, f * 0.12f, 0.7f, -0.15f, -30.0f, f * 14.0f, f * 45.0f),
                        key(0.34f, 0.0f, 0.6f, -0.35f, -30.0f, f * 8.0f, f * 25.0f), key(0.46f, f * -0.3f, 0.38f, -0.5f, -22.0f, 0.0f, f * -15.0f),
                        key(0.56f, f * -0.55f, 0.12f, -0.35f, -8.0f, f * -8.0f, f * -50.0f), key(0.78f, f * -0.15f, 0.02f, -0.08f, -2.0f, f * -2.0f, f * -10.0f),
                        rest(1.0f))
                    : track(strike, rest(0.0f), key(0.3f, f * 0.06f, -0.04f, 0.14f, 12.0f, 0.0f, f * 8.0f),
                        key(0.7f, f * 0.02f, 0.0f, 0.04f, 4.0f, 0.0f, f * 2.0f), rest(1.0f));
            case WORKER -> {
                // up to the crosshair, two heavy pushes, back; the pushes fade in and out with the aim, so nothing jumps
                Motion aim = track(strike, rest(0.0f), new Key(0.2f, workerAim(f)), new Key(0.8f, workerAim(f)), rest(1.0f));
                float window = Mth.clamp((strike - 0.2f) / 0.6f, 0.0f, 1.0f);
                float hammer = blow(window * WORKER_STRIKE_BLOWS % 1.0f) * Mth.sin(window * Mth.PI);
                yield aim.plus(workerPush(hammer));
            }
        };
    }

    /** One push of the jackhammer, 0..1: the arms drive in a little and the fists rise as they recoil. */
    private static Motion workerPush(float k) {
        return new Motion(0.0f, 0.02f * k, -0.12f * k, -3.0f * k, 0.0f, 0.0f);
    }

    private static Motion workerAim(float f) {
        return new Motion(f * -0.2f, 0.32f, -0.3f, -24.0f, f * 12.0f, 0.0f);
    }

    /**
     * One arm while mining (1.5.2): combat and mobile strike in turn (the lead arm in the first half of each cycle), the
     * worker keeps both arms at the crosshair and hammers. time: ticks with the partial tick.
     */
    private static Motion mining(ArmorVariant variant, float f, boolean lead, float time) {
        return switch (variant) {
            case COMBAT -> {
                float phase = ((time / COMBAT_MINING_CYCLE) + (lead ? 0.0f : 0.5f)) % 1.0f;
                yield track(phase, rest(0.0f), key(0.12f, f * 0.03f, -0.03f, 0.08f, 6.0f, 0.0f, 0.0f),
                        key(0.28f, f * -0.22f, 0.28f, -0.55f, -24.0f, f * 7.0f, f * -5.0f), rest(0.5f), rest(1.0f));
            }
            case MOBILE -> {
                float phase = ((time / MOBILE_MINING_CYCLE) + (lead ? 0.0f : 0.5f)) % 1.0f;
                yield track(phase, rest(0.0f), key(0.18f, f * 0.08f, 0.5f, -0.15f, -24.0f, f * 10.0f, f * 35.0f),
                        key(0.32f, f * -0.3f, 0.25f, -0.4f, -16.0f, 0.0f, f * -20.0f), key(0.42f, f * -0.42f, 0.08f, -0.25f, -6.0f, f * -6.0f, f * -40.0f),
                        rest(0.6f), rest(1.0f));
            }
            case WORKER -> {
                float hammer = blow((time / WORKER_BLOW) % 1.0f);
                yield workerAim(f).plus(workerPush(hammer * WORKER_MINING_PUSH));
            }
        };
    }

    /** How far the arms are into the mining movement, 0..1; eased in and out so that starting and stopping are smooth. */
    private float miningBlend;
    private float lastFrameTime = Float.NaN;

    private float miningBlend(boolean mining, float time) {
        if (!Float.isNaN(lastFrameTime) && time != lastFrameTime) {
            float step = Mth.clamp(Math.abs(time - lastFrameTime) / 4.0f, 0.0f, 1.0f);
            miningBlend = Mth.clamp(miningBlend + (mining ? step : -step), 0.0f, 1.0f);
        }
        lastFrameTime = time;
        return miningBlend * miningBlend * (3.0f - 2.0f * miningBlend);
    }

    /**
     * Resting pose is the vanilla first-person arm pose (ItemInHandRenderer#renderPlayerArm without swing or the dip after a
     * strike). The vanilla swing would sweep the long armor arm off the bottom of the screen, so each armor draws its own
     * strike and mining (see strike, mining). strike: 0..1 through the current strike, below 0 for none.
     * {@code plateColor}: the colour of the plate on this arm, 0 for none.
     */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, float strike,
                       HumanoidArm side, boolean lead, boolean mining, float time, ArmorVariant variant, int plateColor) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0f : -1.0f;
        float blend = miningBlend(mining, time);
        Motion m = strike(variant, f, lead, strike).scaled(1.0f - blend);
        if (blend > 0.0f) {
            m = m.plus(mining(variant, f, lead, time).scaled(blend));
        }
        poseStack.pushPose();
        poseStack.translate(m.x(), m.y(), m.z());
        poseStack.translate(f * 0.64f, -0.6f, -0.72f);
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
