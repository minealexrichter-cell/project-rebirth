package com.projectrebirth.mixin;

import com.projectrebirth.client.garrison.TabletClient;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8.0 block F (client only): the colour of the tablet's outline: amber for the chosen machines, turquoise for the others of the
 * console. Only while the tablet lights them (TabletClient.lit); team colours stay as they are otherwise.
 */
@Mixin(Entity.class)
public abstract class EntityOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void projectrebirth$tabletColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && TabletClient.lit(self)) {
            cir.setReturnValue(TabletClient.selected(self.getUUID()) ? 0xFFB648 : 0x4FD1C5);
        }
    }
}
