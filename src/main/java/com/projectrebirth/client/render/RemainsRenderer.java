package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.RemainsEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The remains of a machine or a brute: the creature's own model in its "dead" animation, turned the way it fell. A machine's wreck
 * wears the battered texture &lt;kind&gt;_wreck.png; a brute's body its own skin.
 */
public class RemainsRenderer extends GeoEntityRenderer<RemainsEntity> {
    public RemainsRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(RemainsEntity remains) {
                return ProjectRebirth.id("geo/entity/" + remains.kind().id + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(RemainsEntity remains) {
                RemainsEntity.Kind kind = remains.kind();
                return ProjectRebirth.id("textures/entity/" + kind.id + (kind.machine ? "_wreck" : "") + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(RemainsEntity remains) {
                return ProjectRebirth.id("animations/entity/" + remains.kind().id + ".animation.json");
            }

            /** A mech is always destroyed in its second phase: no chest plate, no rocket pod. */
            @Override
            public void setCustomAnimations(RemainsEntity remains, long instanceId, software.bernie.geckolib.animation.AnimationState<RemainsEntity> state) {
                boolean mech = remains.kind() == RemainsEntity.Kind.MECH;
                for (String part : new String[] {"plate_front", "left_pod"}) {
                    software.bernie.geckolib.cache.object.GeoBone piece = getAnimationProcessor().getBone(part);
                    if (piece != null) {
                        piece.setHidden(mech);
                    }
                }
            }
        });
        this.shadowRadius = 0.8f;
    }

    @Override
    protected void applyRotations(RemainsEntity remains, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        // a brute's body sinks into the ground in the last fifth of its time
        float sink = Math.max(0.0f, (remains.decay() - 0.8f) / 0.2f);
        if (sink > 0.0f) {
            poseStack.translate(0.0f, -sink * 1.1f, 0.0f);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - remains.getYRot()));
    }
}
