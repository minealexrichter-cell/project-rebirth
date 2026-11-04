package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.upgrades.ArmorSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Power armor. Each variant has its own model, animations and textures, named after its item id; a wreck uses the
 * variant's burnt texture. The pilot does not see their own armor in first person.
 * The pilot's held items are drawn in the armor's fists (bones "right_hand" and "left_hand"), and the arms take
 * the pilot's item poses (bow, crossbow, trident, shield).
 */
public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    public static ResourceLocation texture(ArmorVariant variant) {
        return ProjectRebirth.id("textures/entity/" + variant.itemId() + ".png");
    }

    private static ResourceLocation wreckTexture(ArmorVariant variant) {
        return ProjectRebirth.id("textures/entity/" + variant.itemId() + "_wrecked.png");
    }

    private static final String RIGHT_HAND = "right_hand";
    private static final String LEFT_HAND = "left_hand";

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new ArmorModel());
        this.shadowRadius = 0.8f;
        addRenderLayer(new HeldItemLayer(this));
        addRenderLayer(new BannerLayer(this, context.bakeLayer(ModelLayers.BANNER)));
    }

    @Override
    public ResourceLocation getTextureLocation(PowerArmorEntity armor) {
        return armor.isWrecked() ? wreckTexture(armor.getVariant()) : texture(armor.getVariant());
    }

    @Override
    public void render(PowerArmorEntity armor, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && armor.hasPassenger(minecraft.player) && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        super.render(armor, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static class ArmorModel extends GeoModel<PowerArmorEntity> {
        @Override
        public ResourceLocation getModelResource(PowerArmorEntity armor) {
            return ProjectRebirth.id("geo/entity/" + armor.getVariant().itemId() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(PowerArmorEntity armor) {
            return texture(armor.getVariant());
        }

        @Override
        public ResourceLocation getAnimationResource(PowerArmorEntity armor) {
            return ProjectRebirth.id("animations/entity/" + armor.getVariant().itemId() + ".animation.json");
        }

        @Override
        public void setCustomAnimations(PowerArmorEntity armor, long instanceId, AnimationState<PowerArmorEntity> state) {
            super.setCustomAnimations(armor, instanceId, state);
            WindowPose.apply(this);
            Player pilot = armor.getPilot();
            if (pilot == null || !armor.isActive()) {
                return;
            }
            GeoBone rightArm = getAnimationProcessor().getBone("right_arm");
            GeoBone leftArm = getAnimationProcessor().getBone("left_arm");
            if (rightArm != null && leftArm != null) {
                PilotArmPose.apply(armor, pilot, state.getPartialTick(), rightArm, leftArm,
                        getAnimationProcessor().getBone("right_forearm"), getAnimationProcessor().getBone("left_forearm"));
            }
        }
    }

    /**
     * A whole vanilla banner, with its pole and bar, standing on the armor's back and rising above its head. The flag
     * has its pattern and waves, more so while the armor walks.
     */
    private static class BannerLayer extends GeoRenderLayer<PowerArmorEntity> {
        private static final String TORSO = "torso";
        private static final float SCALE = 2.0f / 3.0f;
        /** Height of the banner's top above the banner model's origin, in model pixels (see BannerRenderer). */
        private static final float BANNER_TOP = 32.0f * SCALE;
        private static final float TORSO_PIVOT = 16.0f;
        /** How far the banner rises above the top of the armor. */
        private static final float ABOVE_ARMOR = 14.0f;
        private final ModelPart flag;
        private final ModelPart pole;
        private final ModelPart bar;

        BannerLayer(PowerArmorRenderer renderer, ModelPart banner) {
            super(renderer);
            this.flag = banner.getChild("flag");
            this.pole = banner.getChild("pole");
            this.bar = banner.getChild("bar");
        }

        /** Height of the top of each armor model. */
        private static float armorTop(ArmorVariant variant) {
            return switch (variant) {
                case COMBAT -> 41.0f;
                case MOBILE -> 40.0f;
                case WORKER -> 38.0f;
            };
        }

        /** How far behind the armor's middle its back ends, per model. */
        private static float back(ArmorVariant variant) {
            return switch (variant) {
                case COMBAT -> 8.5f;
                case MOBILE -> 7.5f;
                case WORKER -> 9.5f;
            };
        }

        @Override
        public void renderForBone(PoseStack poseStack, PowerArmorEntity armor, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!bone.getName().equals(TORSO) || armor.isWrecked()) {
                return;
            }
            int index = armor.upgrades().indexOf(ArmorSlot.CHEST_BACK);
            if (index < 0 || !(armor.upgrades().entries().get(index).stack().getItem() instanceof BannerItem banner)) {
                return;
            }
            ItemStack stack = armor.upgrades().entries().get(index).stack();
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, bone);
            float top = armorTop(armor.getVariant()) + ABOVE_ARMOR;
            poseStack.translate(0.0f, (top - TORSO_PIVOT - BANNER_TOP) / 16.0f, back(armor.getVariant()) / 16.0f);
            poseStack.scale(SCALE, -SCALE, -SCALE);
            VertexConsumer wood = ModelBakery.BANNER_BASE.buffer(bufferSource, RenderType::entitySolid);
            pole.render(poseStack, wood, packedLight, packedOverlay);
            bar.render(poseStack, wood, packedLight, packedOverlay);
            float time = armor.tickCount + partialTick;
            float walking = armor.walkAnimation.speed(partialTick);
            flag.xRot = (-0.0125f + 0.01f * Mth.cos(Mth.TWO_PI * time / 100.0f) - walking * 0.03f * (1.0f + Mth.sin(time * 0.4f))) * Mth.PI;
            flag.y = -32.0f;
            BannerRenderer.renderPatterns(poseStack, bufferSource, packedLight, packedOverlay, flag, ModelBakery.BANNER_BASE, true,
                    banner.getColor(), stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
            poseStack.popPose();
        }
    }

    private static class HeldItemLayer extends BlockAndItemGeoLayer<PowerArmorEntity> {
        HeldItemLayer(PowerArmorRenderer renderer) {
            super(renderer);
        }

        /** Main hand goes to the fist on the pilot's main-arm side, the off hand to the other fist. */
        @Override
        protected ItemStack getStackForBone(GeoBone bone, PowerArmorEntity armor) {
            Player pilot = armor.getPilot();
            if (pilot == null || armor.isWrecked()) {
                return null;
            }
            boolean rightFist = bone.getName().equals(RIGHT_HAND);
            if (!rightFist && !bone.getName().equals(LEFT_HAND)) {
                return null;
            }
            boolean mainHandIsRight = pilot.getMainArm() == HumanoidArm.RIGHT;
            ItemStack stack = rightFist == mainHandIsRight ? pilot.getMainHandItem() : pilot.getOffhandItem();
            return ArmorAbility.isBareHand(stack) ? null : stack;
        }

        /**
         * Same transform as the vanilla held-item layer, so every item (sword handle, shield, bow, trident, crossbow)
         * sits in the fist as in a player's hand, at the same size, and turns with the fist. The bone is in the upper
         * part of the fist (higher than in a player's hand, so hanging items clear the ground under the long arms).
         */
        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, PowerArmorEntity armor,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            Player pilot = armor.getPilot();
            if (pilot == null) {
                return;
            }
            boolean left = bone.getName().equals(LEFT_HAND);
            // GeckoLib space -> vanilla model space (which is upside down).
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            poseStack.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, 0.0f);
            // Rendering for the pilot gives the item its live look: bow pulling, loaded crossbow and so on.
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(pilot, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, bufferSource, packedLight);
        }
    }
}
