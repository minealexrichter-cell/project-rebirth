package com.projectrebirth.client.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What the game calls to draw a weapon in the hand of a carrier: the model of the item file brings the hold, this draws the
 * weapon. Icons (windows, the ground, item frames) never come here, the same file makes them flat.
 */
public final class WeaponItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** Who is being drawn, since the game does not tell this class. */
    @Nullable
    private static LivingEntity holder;

    public WeaponItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static void setHolder(@Nullable LivingEntity entity) {
        holder = entity;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        WeaponView view = WeaponView.of(holder, stack.getItem());
        view.update(holder, partialTick);
        poseStack.pushPose();
        // The game has moved the drawing to the corner of a block.
        poseStack.translate(0.5f, 0.5f, 0.5f);
        WeaponRenderer.INSTANCE.draw(poseStack, view, buffers, light, partialTick,
                WindowPose.isDrawing() ? WeaponRenderer.Space.NONE : WeaponRenderer.Space.WORLD);
        poseStack.popPose();
    }
}
