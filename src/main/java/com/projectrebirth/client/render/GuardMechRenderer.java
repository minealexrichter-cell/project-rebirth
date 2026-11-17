package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionView;
import com.projectrebirth.garrison.GuardMechEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * The guard mech (1.7.0, block H): the torso turns to where it looks, the weapon mounts tilt toward its target and hang when it
 * aims at nothing or is down; on each side only the node installed shows (machine gun or rocket pod). The chest stripe and the
 * banner on the back are in the owner's faction colour (amber with no faction). In a multiblock only the stages reached show.
 */
public class GuardMechRenderer extends GeoEntityRenderer<GuardMechEntity> {
    private static final int NO_FACTION = 0xE0B33A;
    private final java.util.List<PoseStack.Pose> flashes = new java.util.ArrayList<>();
    private int factionColor = NO_FACTION;

    public GuardMechRenderer(EntityRendererProvider.Context context) {
        super(context, new MechModel());
        this.shadowRadius = 1.4f;
    }

    @Override
    public void preRender(PoseStack poseStack, GuardMechEntity mech, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        com.projectrebirth.client.multiblock.AssemblyPreview.apply(getGeoModel(), mech);
        if (!isReRender) {
            factionColor = mech.shownOwner().flatMap(ClientFactionCache::of).map(GuardMechRenderer::colorOf).orElse(NO_FACTION);
        }
        super.preRender(poseStack, mech, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static int colorOf(FactionView view) {
        Integer rgb = view.color().getColor();
        return rgb != null ? rgb : NO_FACTION;
    }

    @Override
    public void render(GuardMechEntity mech, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        flashes.clear();
        super.render(mech, entityYaw, partialTick, poseStack, buffers, light);
        MachineBars.draw(mech, mech.shownOwner(), mech.shownStructural(), mech.machineCore().maxStructural(), mech.shownCritical(),
                mech.machineCore().maxCritical(), poseStack, buffers);
        for (PoseStack.Pose pose : flashes) {
            MuzzleFlash.draw(pose, buffers, MuzzleFlash.age(mech.getId(), partialTick), MuzzleFlash.seed(mech.getId()), 1.0f);
        }
        flashes.clear();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, GuardMechEntity mech, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if (!isReRender && name.endsWith("_gun_muzzle") && !bone.isHidden() && MuzzleFlash.age(mech.getId(), partialTick) < 1.0f) {
            flashes.add(MuzzleFlash.capture(poseStack, bone));
        }
        if (name.equals("stripes") || name.equals("banner")) {
            super.renderRecursively(poseStack, mech, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                    Color.ofOpaque(factionColor).argbInt());
            return;
        }
        super.renderRecursively(poseStack, mech, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static class MechModel extends GeoModel<GuardMechEntity> {
        @Override
        public ResourceLocation getModelResource(GuardMechEntity mech) {
            return ProjectRebirth.id("geo/entity/guard_mech.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(GuardMechEntity mech) {
            return ProjectRebirth.id("textures/entity/guard_mech.png");
        }

        @Override
        public ResourceLocation getAnimationResource(GuardMechEntity mech) {
            return ProjectRebirth.id("animations/entity/guard_mech.animation.json");
        }

        @Override
        public void setCustomAnimations(GuardMechEntity mech, long instanceId, AnimationState<GuardMechEntity> state) {
            String[] sides = {"right", "left"};
            for (int side = 0; side < 2; side++) {
                GuardMechEntity.Node node = mech.node(side);
                int s = side;
                getBone(sides[side] + "_gun").ifPresent(bone -> bone.setHidden(mech.node(s) != GuardMechEntity.Node.GUN));
                getBone(sides[side] + "_pod").ifPresent(bone -> bone.setHidden(node != GuardMechEntity.Node.ROCKET));
            }
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            boolean down = mech.down();
            float pitch = data == null || down || !mech.aiming() ? (down ? -0.5f : -0.25f) : Mth.clamp(data.headPitch(), -50.0f, 50.0f) * Mth.DEG_TO_RAD;
            for (String side : sides) {
                getBone(side + "_mount").ifPresent(bone -> bone.setRotX(pitch));
            }
            GeoBone torso = getAnimationProcessor().getBone("torso");
            if (torso != null && data != null && !down) {
                torso.setRotY(Mth.clamp(data.netHeadYaw(), -75.0f, 75.0f) * Mth.DEG_TO_RAD);
            }
        }
    }
}
