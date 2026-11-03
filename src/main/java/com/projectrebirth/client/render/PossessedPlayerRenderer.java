package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a player who controls an artificial body with that body's model instead of the player model, with the
 * items the player holds in the body's hands.
 */
public class PossessedPlayerRenderer extends GeoReplacedEntityRenderer<Player, PossessedPlayerRenderer.BodyAnimatable> {
    private static final String RIGHT_ARM = "right_arm";
    private static final String LEFT_ARM = "left_arm";

    public PossessedPlayerRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        // The head follows where the player looks, like a normal player head.
        super(context, BodyModels.create(bodyClass, true), new BodyAnimatable());
        this.shadowRadius = 0.4f;
        addRenderLayer(new HeldItemLayer(this));
    }

    /** One shared animatable per body class; GeckoLib keeps separate animation state for each player. */
    public static class BodyAnimatable implements GeoReplacedEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public EntityType<?> getReplacingEntityType() {
            return EntityType.PLAYER;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 3,
                    state -> state.setAndContinue(state.isMoving() ? ArtificialBodyEntity.WALK : ArtificialBodyEntity.IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    /**
     * The body's arms have the proportions of a player's arm with the pivot at the shoulder, so items are placed
     * exactly like the vanilla held-item layer does it from the shoulder.
     */
    private static class HeldItemLayer extends BlockAndItemGeoLayer<BodyAnimatable> {
        private final PossessedPlayerRenderer renderer;

        HeldItemLayer(PossessedPlayerRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        protected ItemStack getStackForBone(GeoBone bone, BodyAnimatable animatable) {
            Player player = renderer.getCurrentEntity();
            boolean right = bone.getName().equals(RIGHT_ARM);
            if (player == null || !right && !bone.getName().equals(LEFT_ARM)) {
                return null;
            }
            boolean mainHandIsRight = player.getMainArm() == HumanoidArm.RIGHT;
            ItemStack stack = right == mainHandIsRight ? player.getMainHandItem() : player.getOffhandItem();
            return stack.isEmpty() ? null : stack;
        }

        /** The arm's rotation is already applied by the time layers run: only move to the shoulder pivot here. */
        @Override
        public void renderForBone(PoseStack poseStack, BodyAnimatable animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                                  int packedOverlay) {
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
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, BodyAnimatable animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            Player player = renderer.getCurrentEntity();
            boolean left = bone.getName().equals(LEFT_ARM);
            // GeckoLib space -> vanilla model space (upside down), then the vanilla shoulder-to-hand transform.
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            poseStack.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, -0.625f);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, bufferSource, packedLight);
        }
    }
}
