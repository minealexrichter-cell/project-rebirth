package com.projectrebirth.environment;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A replaceable filter for a respirator or the helmet of a suit. Right click with it puts it into the worn head piece;
 * in a window, right click it onto the head piece. The rest of the old filter comes back.
 */
public class FilterItem extends Item {
    public FilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack filter = player.getItemInHand(hand);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!Protection.breathes(head)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.environment.no_respirator"), true);
            return InteractionResultHolder.fail(filter);
        }
        if (!level.isClientSide) {
            ItemStack back = Protection.insertFilter(head, filter);
            if (!back.isEmpty() && !player.getInventory().add(back)) {
                player.drop(back, false);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        return InteractionResultHolder.sidedSuccess(filter, level.isClientSide);
    }

    /** In a window: the filter held by the mouse, right clicked onto a head piece. */
    @Override
    public boolean overrideStackedOnOther(ItemStack filter, Slot slot, ClickAction action, Player player) {
        ItemStack head = slot.getItem();
        if (action != ClickAction.SECONDARY || !Protection.breathes(head)) {
            return false;
        }
        ItemStack back = Protection.insertFilter(head, filter);
        slot.setChanged();
        if (!back.isEmpty() && !player.getInventory().add(back)) {
            player.drop(back, false);
        }
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(EnvironmentRegistry.FILTER_LIFE.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * Mth.clamp(Protection.filterLeft(stack) / RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue(), 0.0f, 1.0f));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x7FE8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.projectrebirth.filter.how").withStyle(ChatFormatting.GRAY));
    }
}
