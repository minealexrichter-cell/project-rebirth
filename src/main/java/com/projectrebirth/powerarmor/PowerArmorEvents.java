package com.projectrebirth.powerarmor;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Pilot protection, the shutdown lock and pilot bonus bookkeeping. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class PowerArmorEvents {
    private PowerArmorEvents() {
    }

    /** The pilot of a working (or shut-down) armor is fully protected: the armor takes the damage instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof PowerArmorEntity armor && !armor.isWrecked()
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide) {
                armor.hurt(event.getSource(), event.getAmount());
            }
        }
    }

    /** A shut-down armor does not let its pilot out by the normal exit; see the hatch, rescue tool and repair. */
    @SubscribeEvent
    static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof PowerArmorEntity armor && armor.isPilotLocked()
                && event.getEntityMounting().isAlive() && !event.getEntityMounting().isRemoved()) {
            event.setCanceled(true);
        }
    }

    /** Removes a stale pilot bonus if the player logged in outside an armor (e.g. it was destroyed meanwhile). */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        PowerArmorEntity.setPilotAttackBonus(player, player.getVehicle() instanceof PowerArmorEntity);
    }
}
