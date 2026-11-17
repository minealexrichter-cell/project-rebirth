package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionView;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.MachineCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.util.RenderUtil;

/**
 * A combat android (1.7.0): the body of its class with a glowing visor and core antenna in the owner's faction colour,
 * and the items it holds in its hands. A shut down or wrecked android's visor goes dark; in emergency mode it blinks red.
 */
public class AndroidRenderer extends ArtificialBodyRenderer {
    private static final int NO_FACTION = 0x40E0FF;
    private static final int EMERGENCY = 0xFF3020;
    private static final int DARK = 0x303030;
    private static final String RIGHT_FOREARM = "right_forearm";
    private static final String LEFT_FOREARM = "left_forearm";

    private int glowColor = NO_FACTION;
    private boolean glowing = true;

    public AndroidRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        super(context, bodyClass);
        addRenderLayer(new HeldItemLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, ArtificialBodyEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        UpgradeVisuals.showAndroid(getGeoModel(), true);
        if (animatable instanceof AndroidEntity android) {
            glowing = android.shownState() == MachineCore.State.ACTIVE;
            if (android.shownEmergency()) {
                glowing = (android.tickCount / 10) % 2 == 0;
                glowColor = EMERGENCY;
            } else {
                glowColor = android.shownOwner().flatMap(ClientFactionCache::of).map(AndroidRenderer::factionColor).orElse(NO_FACTION);
            }
        }
    }

    private static int factionColor(FactionView view) {
        Integer rgb = view.color().getColor();
        return rgb != null ? rgb : NO_FACTION;
    }

    @Override
    public void render(ArtificialBodyEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
        if (entity instanceof com.projectrebirth.garrison.AndroidEntity android) {
            // block I: its bars; structural is its health, critical a share of the body's integrity
            MachineBars.draw(android, android.shownOwner(), android.getHealth(), android.getMaxHealth(), android.shownCritical(),
                    android.getMaxHealth() * com.projectrebirth.config.RebirthConfig.ANDROID_CRITICAL_SHARE.get().floatValue(), poseStack, buffers);
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ArtificialBodyEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if (name.equals(UpgradeVisuals.ANDROID_VISOR) || name.equals(UpgradeVisuals.ANDROID_CORE_TIP)) {
            // leaf bones: the full light and the tint do not pass on to other parts
            int rgb = glowing ? glowColor : DARK;
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                    glowing ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay, Color.ofOpaque(rgb).argbInt());
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** Main hand in the right forearm, off hand in the left, as a player holds them. */
    private static class HeldItemLayer extends BlockAndItemGeoLayer<ArtificialBodyEntity> {
        private final AndroidRenderer renderer;

        HeldItemLayer(AndroidRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        protected ItemStack getStackForBone(GeoBone bone, ArtificialBodyEntity animatable) {
            boolean right = bone.getName().equals(RIGHT_FOREARM);
            if (!right && !bone.getName().equals(LEFT_FOREARM)) {
                return null;
            }
            ItemStack stack = right ? animatable.getMainHandItem() : animatable.getOffhandItem();
            return stack.isEmpty() ? null : stack;
        }

        /** The arm's rotation is already applied by the time layers run: only move to the elbow pivot here. */
        @Override
        public void renderForBone(PoseStack poseStack, ArtificialBodyEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            ItemStack stack = getStackForBone(bone, animatable);
            if (stack == null) {
                return;
            }
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, bone);
            renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, ArtificialBodyEntity animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            boolean left = bone.getName().equals(LEFT_FOREARM);
            float upperArm = (bone.getParent().getPivotY() - bone.getPivotY()) / 16.0f;
            // GeckoLib space -> vanilla model space (upside down), then the vanilla shoulder-to-hand transform.
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            poseStack.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, upperArm - 0.625f);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(animatable, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, bufferSource, packedLight);
        }
    }
}
