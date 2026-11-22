package com.projectrebirth.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projectrebirth.environment.ContaminatedFood;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * 1.9.0: cooked contaminated food keeps a share of its toxins. The furnace, the smoker and the campfire all take their result
 * from here (the furnace also to see whether the result fits its output slot), so a contaminated result never merges with a
 * clean one.
 */
@Mixin(AbstractCookingRecipe.class)
public abstract class AbstractCookingRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void projectrebirth$keepContamination(SingleRecipeInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ContaminatedFood.afterCooking(input.item(), cir.getReturnValue());
    }
}
