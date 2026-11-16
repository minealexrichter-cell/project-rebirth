package com.projectrebirth.garrison;

import java.util.List;

import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The AI core (1.7.0): the mind of every android, turret and mech, one core one machine. It does not stack and glows. It binds
 * to the first player who carries it (the one who made or restored it) and remembers their faction then; the machine built
 * around it serves that player.
 */
public class AiCoreItem extends Item {
    public AiCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!level.isClientSide && holder instanceof ServerPlayer player && !stack.has(GarrisonRegistry.CORE_OWNER.get())) {
            bind(stack, player);
        }
    }

    /** Binds an unbound core to the player and their faction now. */
    public static void bind(ItemStack stack, ServerPlayer player) {
        String faction = FactionData.get(player.server).of(player.getUUID()).map(Faction::name).orElse("");
        stack.set(GarrisonRegistry.CORE_OWNER.get(), new GarrisonRegistry.CoreOwner(player.getUUID(), player.getGameProfile().getName(), faction));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GarrisonRegistry.CoreOwner owner = stack.get(GarrisonRegistry.CORE_OWNER.get());
        if (owner == null) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.ai_core.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.ai_core.owner", owner.name()).withStyle(ChatFormatting.AQUA));
        if (!owner.faction().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.ai_core.faction", owner.faction()).withStyle(ChatFormatting.GRAY));
        }
    }
}
