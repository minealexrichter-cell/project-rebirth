package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Power armor; a wreck uses a burnt texture. The pilot does not see their own armor in first person.
 * The pilot's held items are drawn in the armor's fists (bones "right_hand" and "left_hand").
 */
public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    private static final ResourceLocation WRECK_TEXTURE = ProjectRebirth.id("textures/entity/power_armor_wrecked.png");
    private static final String RIGHT_HAND = "right_hand";
    private static final String LEFT_HAND = "left_hand";
    /** Items look tiny in the big fists at normal size. */
    private static final float ITEM_SCALE = 1.5f;

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ProjectRebirth.id("power_armor"), false));
        this.shadowRadius = 0.8f;
        addRenderLayer(new HeldItemLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(PowerArmorEntity armor) {
        return armor.isWrecked() ? WRECK_TEXTURE : super.getTextureLocation(armor);
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
            return stack.isEmpty() ? null : stack;
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, PowerArmorEntity armor) {
            // Swapped on purpose: the half-turn in renderStackForBone mirrors the item sideways, which turns
            // a left-hand layout into a right-hand one and back.
            return bone.getName().equals(RIGHT_HAND) ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, PowerArmorEntity armor,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            // GeckoLib's own example ("-90 around X") holds items upside down on this model (sword pointing to the
            // ground, shield above the shoulder), so the item is first turned half a circle around the forward axis.
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            super.renderStackForBone(poseStack, bone, stack, armor, bufferSource, partialTick, packedLight, packedOverlay);
        }
    }
}
