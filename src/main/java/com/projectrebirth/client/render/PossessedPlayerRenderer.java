package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.weapons.WeaponItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a player who controls an artificial body with that body's model instead of the player model, with the
 * items the player holds in the body's hands and the arm poses of a normal player (see PilotArmPose).
 */
public class PossessedPlayerRenderer extends GeoReplacedEntityRenderer<Player, PossessedPlayerRenderer.BodyAnimatable> {
    private int platingColor = UpgradeVisuals.NO_TINT;
    private static final String RIGHT_ARM = "right_arm";
    private static final String LEFT_ARM = "left_arm";
    private static final String RIGHT_FOREARM = "right_forearm";
    private static final String LEFT_FOREARM = "left_forearm";

    public PossessedPlayerRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        this(context, new PosedModel(bodyClass));
    }

    private PossessedPlayerRenderer(EntityRendererProvider.Context context, PosedModel model) {
        super(context, model, new BodyAnimatable());
        model.renderer = this;
        this.shadowRadius = 0.4f;
        addRenderLayer(new HeldItemLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, BodyAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        Player player = getCurrentEntity();
        if (player != null) {
            platingColor = UpgradeVisuals.applyToBody(getGeoModel(), player.registryAccess(), player.getData(UpgradeRegistry.CONTROLLED_BODY));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, BodyAnimatable animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                bone.getName().startsWith(UpgradeVisuals.BODY_PLATING_PREFIX) ? platingColor : colour);
    }

    /** The body's model; the head follows where the player looks, like a normal player head. */
    private static class PosedModel extends DefaultedEntityGeoModel<BodyAnimatable> {
        private PossessedPlayerRenderer renderer;
        private boolean leaning;

        PosedModel(BodyClass bodyClass) {
            super(ProjectRebirth.id(bodyClass.id()), true);
        }

        /**
         * An animation that does not move the torso leaves it as it was, so the lean starts from the rest pose instead of
         * adding up frame after frame, and that pose comes back when the weapon is put away.
         */
        private void carryLean(Player player, GeoBone torso) {
            if (player == null || torso == null) {
                return;
            }
            boolean carrying = player.getMainHandItem().getItem() instanceof WeaponItem;
            if ((carrying || leaning) && !torso.hasRotationChanged()) {
                torso.setRotX(torso.getInitialSnapshot().getRotX());
            }
            if (carrying) {
                torso.setRotX(torso.getRotX() - HeavyWeaponPose.boneLean(player));
            }
            leaning = carrying;
        }

        @Override
        public void setCustomAnimations(BodyAnimatable animatable, long instanceId, AnimationState<BodyAnimatable> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            float lookX = 0.0f;
            float lookY = 0.0f;
            GeoBone looking = getAnimationProcessor().getBone("head");
            if (looking != null) {
                lookX = looking.getRotX();
                lookY = looking.getRotY();
            }
            WindowPose.apply(this);
            if (looking != null) {
                looking.setRotX(lookX);
                looking.setRotY(lookY);
            }
            Player player = renderer.getCurrentEntity();
            GeoBone rightArm = getAnimationProcessor().getBone(RIGHT_ARM);
            GeoBone leftArm = getAnimationProcessor().getBone(LEFT_ARM);
            GeoBone torso = getAnimationProcessor().getBone("torso");
            GeoBone root = getAnimationProcessor().getBone("root");
            GeoBone head = getAnimationProcessor().getBone("head");
            carryLean(player, torso);
            if (player != null && rightArm != null && leftArm != null) {
                PilotArmPose.apply(player, state.getPartialTick(), rightArm, leftArm,
                        getAnimationProcessor().getBone(RIGHT_FOREARM), getAnimationProcessor().getBone(LEFT_FOREARM), torso);
            }
            // The head looks where the player looks, however the walk leans and turns the body under it.
            if (head != null && torso != null && root != null) {
                head.setRotX(head.getRotX() - torso.getRotX());
                head.setRotY(head.getRotY() - torso.getRotY() - root.getRotY());
            }
        }
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
                    state -> state.setAndContinue(animationOf(state.getData(DataTickets.ENTITY), state.isMoving()))));
        }

        private static RawAnimation animationOf(Entity entity, boolean moving) {
            if (entity != null && entity.isCrouching()) {
                return moving ? ArtificialBodyEntity.SNEAK : ArtificialBodyEntity.CROUCH;
            }
            if (!moving) {
                return ArtificialBodyEntity.IDLE;
            }
            return entity != null && entity.isSprinting() ? ArtificialBodyEntity.RUN : ArtificialBodyEntity.WALK;
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    /**
     * Items hang from the forearm, so they follow the elbow. The body's arms have the proportions of a player's arm, so
     * the place is the vanilla one from the shoulder, shortened by the upper arm.
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
            boolean right = bone.getName().equals(RIGHT_FOREARM);
            if (player == null || !right && !bone.getName().equals(LEFT_FOREARM)) {
                return null;
            }
            boolean mainHandIsRight = player.getMainArm() == HumanoidArm.RIGHT;
            ItemStack stack = right == mainHandIsRight ? player.getMainHandItem() : player.getOffhandItem();
            return stack.isEmpty() ? null : stack;
        }

        /** The arm's rotation is already applied by the time layers run: only move to the elbow pivot here. */
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
            boolean left = bone.getName().equals(LEFT_FOREARM);
            float upperArm = (bone.getParent().getPivotY() - bone.getPivotY()) / 16.0f;
            // GeckoLib space -> vanilla model space (upside down), then the vanilla shoulder-to-hand transform.
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            poseStack.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, upperArm - 0.625f);
            WeaponItemRenderer.setHolder(player);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, bufferSource, packedLight);
            WeaponItemRenderer.setHolder(null);
        }
    }
}
