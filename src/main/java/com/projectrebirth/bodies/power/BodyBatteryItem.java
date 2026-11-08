package com.projectrebirth.bodies.power;

import java.util.List;

import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A portable charge for the body a player controls: hold use for a few seconds and it hands over what it holds (up to what
 * the body is missing). Damage interrupts it, like the repair kit ({@code BodyEvents}). Filled on a charging pad or by the
 * chargers of other mods (it is an FE item); it gives energy to nothing but a body.
 */
public class BodyBatteryItem extends Item {
    public BodyBatteryItem(Properties properties) {
        super(properties);
    }

    public static int capacity() {
        return RebirthConfig.BODY_BATTERY_CAPACITY.get();
    }

    public static int stored(ItemStack stack) {
        return Math.min(stack.getOrDefault(BodyPowerRegistry.BATTERY_ENERGY.get(), 0), capacity());
    }

    public static void setStored(ItemStack stack, int energy) {
        stack.set(BodyPowerRegistry.BATTERY_ENERGY.get(), Mth.clamp(energy, 0, capacity()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String refusal = null;
        if (!TransferService.isInArtificialBody(player)) {
            refusal = "not_in_body";
        } else if (!BodyPower.enabled() || BodyPower.charge(player) >= BodyPower.capacity(player)) {
            refusal = "full";
        } else if (stored(stack) <= 0) {
            refusal = "empty";
        }
        if (refusal != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.body_battery." + refusal), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Math.max(1, (int) Math.round(RebirthConfig.BODY_BATTERY_USE_SECONDS.get() * 20));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!level.isClientSide && remainingTicks % 10 == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && TransferService.isInArtificialBody(player)) {
            int given = Math.round(BodyPower.addCharge(player, stored(stack)));
            setStored(stack, stored(stack) - given);
            BodyPower.applyLimits(player);
            player.displayClientMessage(Component.translatable("message.projectrebirth.body_battery.done",
                    Math.round(BodyPower.fraction(player) * 100)), true);
        }
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * stored(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFFD54F;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.projectrebirth.body_battery.charge", EnergyUtil.format(stored(stack)),
                EnergyUtil.format(capacity())).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.projectrebirth.body_battery.hint").withStyle(ChatFormatting.GRAY));
    }
}
