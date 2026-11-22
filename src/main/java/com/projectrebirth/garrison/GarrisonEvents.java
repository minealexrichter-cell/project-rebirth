package com.projectrebirth.garrison;

import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Self-defence (1.7.0): when someone attacks a player, or anything a player owns, the machines of that player and of their
 * faction nearby remember the attacker for a while; the rules of targets then let them fight back.
 */
public final class GarrisonEvents {
    private GarrisonEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GarrisonEvents::onDamage);
        // 1.7.0, block F: androids never trample farmland (the farmer walks its fields)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent event) -> {
            if (event.getEntity() instanceof AndroidEntity) {
                event.setCanceled(true);
            }
        });
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        MinecraftServer server = victim.getServer();
        if (server == null || attacker == null || attacker == victim || victim.level().isClientSide) {
            return;
        }
        UUID victimOwner = FactionService.ownerOf(victim);
        if (victimOwner == null) {
            return;
        }
        Optional<Faction> faction = FactionData.get(server).of(victimOwner);
        int radius = RebirthConfig.SELF_DEFENCE_RADIUS.get();
        long now = victim.level().getGameTime();
        for (Entity found : victim.level().getEntities(victim, new AABB(victim.blockPosition()).inflate(radius), entity -> entity instanceof GarrisonMachine)) {
            MachineCore core = ((GarrisonMachine) found).machineCore();
            UUID owner = core.owner();
            if (owner == null) {
                continue;
            }
            boolean ours = owner.equals(victimOwner) || faction.isPresent() && faction.get().members().containsKey(owner);
            if (ours) {
                core.rememberHitOnOurs(attacker, now);
            }
        }
    }
}
