package com.projectrebirth.weapons;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A gun: the same class for all of them, the differences are data and config. It keeps its rounds in the magazine component. */
public class WeaponItem extends Item {
    public WeaponItem(Properties properties) {
        super(properties);
    }

    /** Spending a round changes the item; that must not look like taking out another weapon. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Tooltips are also built while the game loads, before the server config exists.
        if (RebirthConfig.SERVER_SPEC.isLoaded()) {
            RebirthConfig.WeaponStats stats = RebirthConfig.weapon(BuiltInRegistries.ITEM.getKey(this).getPath());
            if (stats != null) {
                tooltip.add(Component.translatable("tooltip.projectrebirth.weapon.magazine", Weapons.ammoIn(stack), stats.magazine().get())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.weapon.needs_armor").withStyle(ChatFormatting.GRAY));
    }
}
