package com.projectrebirth.bodies;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Field repair for the artificial body the player controls: hold use for a few seconds to restore part of its
 * integrity. Any damage interrupts it ({@link BodyEvents}); the kit is spent only when the repair completes.
 */
public class RepairKitItem extends Item {
    public RepairKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String refusal = null;
        if (!TransferService.isInArtificialBody(player)) {
            refusal = "not_in_body";
        } else if (player.getHealth() >= player.getMaxHealth()) {
            refusal = "intact";
        }
        if (refusal != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.repair_kit." + refusal), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Math.max(1, (int) Math.round(RebirthConfig.REPAIR_KIT_USE_SECONDS.get() * 20));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!level.isClientSide && remainingTicks % 10 == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.4f, 1.3f);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && TransferService.isInArtificialBody(player)) {
            BodyRules.repair(player, (float) (player.getMaxHealth() * RebirthConfig.REPAIR_KIT_FRACTION.get()));
            stack.consume(1, player);
            player.displayClientMessage(Component.translatable("message.projectrebirth.repair_kit.done"), true);
        }
        return stack;
    }
}
