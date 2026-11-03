package com.projectrebirth.powerarmor;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
            // A landing armor hands its fall to the pilot too (vanilla does that for every vehicle); the armor already took
            // its own fall damage, by its own safe fall height, so the pilot's share is dropped instead of passed on.
            if (!event.getEntity().level().isClientSide && !event.getSource().is(DamageTypeTags.IS_FALL)) {
                armor.hurt(event.getSource(), event.getAmount());
            }
        }
    }

    /**
     * A shut-down armor does not let its pilot out by the normal exit; see the hatch, rescue tool and repair.
     * Server only: the client must always accept the server's decision to let the pilot out
     * (otherwise the client keeps the player seated after the hatch is blown).
     */
    @SubscribeEvent
    static void onMount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide() && event.isDismounting()
                && event.getEntityBeingMounted() instanceof PowerArmorEntity armor && armor.isPilotLocked()
                && event.getEntityMounting().isAlive() && !event.getEntityMounting().isRemoved()) {
            event.setCanceled(true);
        }
    }

    /** The armor punches when its pilot attacks an entity or starts hitting a block. */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity().getVehicle() instanceof PowerArmorEntity armor && armor.isActive()) {
            armor.playAttackAnimation();
        }
    }

    @SubscribeEvent
    static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getEntity().getVehicle() instanceof PowerArmorEntity armor && armor.isActive()) {
            armor.playAttackAnimation();
        }
    }

    /** Removes stale pilot bonuses if the player logged in outside an armor (e.g. it was destroyed meanwhile). */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        PowerArmorEntity.setPilotBonuses(player, player.getVehicle() instanceof PowerArmorEntity armor ? armor.getVariant() : null);
    }
}
