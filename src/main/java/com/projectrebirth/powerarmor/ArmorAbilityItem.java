package com.projectrebirth.powerarmor;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The phantom "item" in hotbar slot 9 of a power armor pilot: only an icon for the armor's ability. It exists only
 * there and only while the player pilots the armor; {@link ArmorAbility} removes it anywhere else. Not in any
 * creative tab or JEI, no recipe.
 */
public class ArmorAbilityItem extends Item {
    private final ArmorVariant variant;

    public ArmorAbilityItem(ArmorVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public ArmorVariant variant() {
        return variant;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.projectrebirth." + variant.abilityItemId() + ".description").withStyle(ChatFormatting.GRAY));
    }
}
