package com.projectrebirth.mixin;

import com.projectrebirth.client.garrison.TabletClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8.0 block F (client only): with the tactical tablet in hand the machines of its console near the player are outlined, seen
 * through walls like glowing creatures; nothing changes for other players or on the server.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void projectrebirth$tabletOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TabletClient.lit(entity)) {
            cir.setReturnValue(true);
        }
    }
}
