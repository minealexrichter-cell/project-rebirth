package com.projectrebirth.mixin;

import com.projectrebirth.water.BottleStacking;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets bottles of water and potions stack (the vanilla potion items are fixed at one). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void projectrebirth$bottleStack(CallbackInfoReturnable<Integer> cir) {
        int size = BottleStacking.maxStackSize((ItemStack) (Object) this);
        if (size > 0) {
            cir.setReturnValue(size);
        }
    }
}
