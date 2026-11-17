package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionView;
import com.projectrebirth.garrison.DefenceTurretEntity;
import com.projectrebirth.garrison.MachineCore;

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
 * The player's defence turrets (1.7.0, block G): the guard turret's shape in service paint, its stripes (bone "stripes") in the
 * owner's faction colour (amber with no faction). The head turns, the barrel or the launcher tilts; shut down or a wreck,
 * the barrel hangs. In a multiblock it shows only the parts of the stages reached.
 */
public class DefenceTurretRenderer extends GeoEntityRenderer<DefenceTurretEntity> {
    private static final int NO_FACTION = 0xE0B33A;
    private final java.util.List<PoseStack.Pose> flashes = new java.util.ArrayList<>();
    private int stripeColor = NO_FACTION;

    public DefenceTurretRenderer(EntityRendererProvider.Context context) {
        super(context, new TurretModel());
        this.shadowRadius = 0.6f;
    }

    @Override
    public void preRender(PoseStack poseStack, DefenceTurretEntity turret, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        com.projectrebirth.client.multiblock.AssemblyPreview.apply(getGeoModel(), turret);
        if (!isReRender) {
            stripeColor = turret.shownOwner().flatMap(ClientFactionCache::of).map(DefenceTurretRenderer::factionColor).orElse(NO_FACTION);
        }
        super.preRender(poseStack, turret, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static int factionColor(FactionView view) {
        Integer rgb = view.color().getColor();
        return rgb != null ? rgb : NO_FACTION;
    }

    @Override
    public void render(DefenceTurretEntity turret, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        flashes.clear();
        super.render(turret, entityYaw, partialTick, poseStack, buffers, light);
        for (PoseStack.Pose pose : flashes) {
            MuzzleFlash.draw(pose, buffers, MuzzleFlash.age(turret.getId(), partialTick), MuzzleFlash.seed(turret.getId()), 0.8f);
        }
        flashes.clear();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, DefenceTurretEntity turret, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender && bone.getName().equals("muzzle") && MuzzleFlash.age(turret.getId(), partialTick) < 1.0f) {
            flashes.add(MuzzleFlash.capture(poseStack, bone));
        }
        if (bone.getName().equals("stripes")) {
            // a leaf bone: the tint does not pass on to other parts
            super.renderRecursively(poseStack, turret, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                    Color.ofOpaque(stripeColor).argbInt());
            return;
        }
        super.renderRecursively(poseStack, turret, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static class TurretModel extends GeoModel<DefenceTurretEntity> {
        @Override
        public ResourceLocation getModelResource(DefenceTurretEntity turret) {
            return ProjectRebirth.id("geo/entity/" + turret.turret().id() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(DefenceTurretEntity turret) {
            return ProjectRebirth.id("textures/entity/" + turret.turret().id() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(DefenceTurretEntity turret) {
            return ProjectRebirth.id("animations/entity/defence_turret.animation.json");
        }

        @Override
        public void setCustomAnimations(DefenceTurretEntity turret, long instanceId, AnimationState<DefenceTurretEntity> state) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            GeoBone head = getAnimationProcessor().getBone("head");
            GeoBone barrel = getAnimationProcessor().getBone("barrel");
            boolean down = turret.shownState() != MachineCore.State.ACTIVE;
            if (data != null && head != null) {
                head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
            }
            if (barrel != null) {
                // shut down or a wreck: the barrel hangs
                barrel.setRotX((down ? -35.0f : data != null ? data.headPitch() : 0.0f) * Mth.DEG_TO_RAD);
            }
        }
    }
}
