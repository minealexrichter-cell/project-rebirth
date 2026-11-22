package com.projectrebirth.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.client.PowerArmorClient;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: a pilot in a power armor holds nothing in their own hands. The armor's fists already draw the items
 * (PowerArmorRenderer.HeldItemLayer); without this the worker's cab showed every item twice.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void projectrebirth$emptyPilotHands(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity,
                                                float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                                float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (PowerArmorClient.armorHoldsItems(entity)) {
            ci.cancel();
        }
    }
}
