package com.projectrebirth.environment;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * The thermal liner on the crafting table (1.5.0). A piece of armor or a suit with a thermal liner beside it: the piece with
 * the liner sewn in. A lined piece alone: the piece without it, and the liner stays in the grid. Nothing else changes on the
 * piece (damage, enchantments, filter).
 */
public class ThermalLinerRecipe extends CustomRecipe {
    public ThermalLinerRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() != EquipmentSlot.BODY;
    }

    private static boolean lined(ItemStack stack) {
        return stack.has(TemperatureRegistry.THERMAL_LINER_INSIDE.get());
    }

    /** The armor piece of the grid and whether a liner lies beside it; null if the grid is anything else. */
    private record Contents(ItemStack armor, int armorSlot, boolean liner) {
    }

    private static Contents contents(CraftingInput input) {
        ItemStack armor = ItemStack.EMPTY;
        int armorSlot = -1;
        int liners = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(TemperatureRegistry.THERMAL_LINER.get())) {
                liners++;
            } else if (isArmor(stack) && armor.isEmpty()) {
                armor = stack;
                armorSlot = i;
            } else {
                return null;
            }
        }
        if (armor.isEmpty() || liners > 1 || liners == 1 && lined(armor) || liners == 0 && !lined(armor)) {
            return null;
        }
        return new Contents(armor, armorSlot, liners == 1);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return contents(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Contents contents = contents(input);
        if (contents == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = contents.armor().copyWithCount(1);
        if (contents.liner()) {
            result.set(TemperatureRegistry.THERMAL_LINER_INSIDE.get(), net.minecraft.util.Unit.INSTANCE);
        } else {
            result.remove(TemperatureRegistry.THERMAL_LINER_INSIDE.get());
        }
        return result;
    }

    /** Taking the liner out leaves it in the grid where the piece was. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        Contents contents = contents(input);
        if (contents != null && !contents.liner()) {
            remaining.set(contents.armorSlot(), new ItemStack(TemperatureRegistry.THERMAL_LINER.get()));
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TemperatureRegistry.LINER_RECIPE.get();
    }
}
