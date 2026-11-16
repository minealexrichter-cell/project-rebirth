package com.projectrebirth.water;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Stack sizes of bottles (1.6.0, block C): bottles of water and of polluted water stack by 16, potions by 4 (config [water]).
 * Read on both sides; the server config reaches the client on joining, and until then the defaults hold.
 */
public final class BottleStacking {
    private BottleStacking() {
    }

    /** The stack size for a bottle, or 0 for any other item (which keeps its own). */
    public static int maxStackSize(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.POTION) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents != null && contents.is(Potions.WATER) ? value(RebirthConfig.WATER_BOTTLE_STACK) : value(RebirthConfig.POTION_STACK);
        }
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            return value(RebirthConfig.POTION_STACK);
        }
        if (item instanceof PollutedWaterBottleItem) {
            return value(RebirthConfig.WATER_BOTTLE_STACK);
        }
        return 0;
    }

    private static int value(ModConfigSpec.IntValue config) {
        return RebirthConfig.SERVER_SPEC.isLoaded() ? config.get() : config.getDefault();
    }
}
