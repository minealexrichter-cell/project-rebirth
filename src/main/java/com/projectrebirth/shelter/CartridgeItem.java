package com.projectrebirth.shelter;

import java.util.List;
import java.util.Locale;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A filter cartridge of an air filtration unit: not the respirator's filter, a larger one. A new cartridge has no life
 * component and counts as full; the unit writes what is left into it.
 */
public class CartridgeItem extends Item {
    public CartridgeItem(Properties properties) {
        super(properties);
    }

    public static float life(ItemStack stack) {
        Float left = stack.get(ShelterRegistry.CARTRIDGE_LIFE.get());
        return left == null ? RebirthConfig.SHELTER_CARTRIDGE_LIFE.get().floatValue() : left;
    }

    public static void setLife(ItemStack stack, float life) {
        stack.set(ShelterRegistry.CARTRIDGE_LIFE.get(), Math.max(0.0f, life));
    }

    /** What is left of a cartridge, 0..1; 0 for anything else. */
    public static float lifeShare(ItemStack stack) {
        if (!stack.is(ShelterRegistry.FILTER_CARTRIDGE.get())) {
            return 0.0f;
        }
        return Mth.clamp(life(stack) / RebirthConfig.SHELTER_CARTRIDGE_LIFE.get().floatValue(), 0.0f, 1.0f);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(ShelterRegistry.CARTRIDGE_LIFE.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * lifeShare(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8B77A;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(ShelterRegistry.CARTRIDGE_LIFE.get())) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.cartridge.left",
                    String.format(Locale.ROOT, "%.0f", lifeShare(stack) * 100.0f)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.cartridge.how").withStyle(ChatFormatting.GRAY));
    }
}
