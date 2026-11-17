package com.projectrebirth.mixin;

import com.projectrebirth.bodies.BodyRules;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.7.1: machines and artificial bodies immune to effects are not touched by potions at all. Lasting effects are already refused
 * (BodyEvents.onEffectApplicable); this also keeps off the instant ones (healing and harming from splash and lingering potions),
 * which vanilla applies without asking.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isAffectedByPotions", at = @At("HEAD"), cancellable = true)
    private void projectrebirth$immuneToPotions(CallbackInfoReturnable<Boolean> cir) {
        if (!BodyRules.allowsEffect((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
