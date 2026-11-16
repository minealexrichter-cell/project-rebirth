package com.projectrebirth.monitoring;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A contamination map printed by a monitoring station (1.6.0, block E): the scan of the day it was printed, for an expedition.
 * Used, it opens the picture; it does not change afterwards.
 */
public class ContaminationMapItem extends Item {
    /** Set by the client side: opens the picture of a map. */
    public static Consumer<Snapshot> opener = snapshot -> {
    };

    public ContaminationMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Snapshot snapshot = stack.get(MonitoringRegistry.SNAPSHOT.get());
        if (snapshot == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            opener.accept(snapshot);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Snapshot snapshot = stack.get(MonitoringRegistry.SNAPSHOT.get());
        if (snapshot == null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.contamination_map.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.contamination_map.day", snapshot.day()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.projectrebirth.contamination_map.center", snapshot.centerX() * 16 + 8, snapshot.centerZ() * 16 + 8,
                snapshot.side(), snapshot.side()).withStyle(ChatFormatting.GRAY));
    }
}
