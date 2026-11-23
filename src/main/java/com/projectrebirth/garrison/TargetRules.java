package com.projectrebirth.garrison;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Whom a machine may attack (1.7.0, block C; the concept's section 8), decided on the server, one place for every machine:
 *
 * <ul>
 *   <li>never: its owner, its faction, allies, players in creative or spectator mode, peaceful creatures;</li>
 *   <li>always: hostile creatures (monsters, mutants, the machines of the ruins);</li>
 *   <li>neutral creatures and any player or player's machine: only in self-defence, when they attacked the machine, its owner or
 *   a member of its faction nearby a short while ago ({@link MachineCore#attackedBy});</li>
 *   <li>players and machines of an enemy faction while the war is active, but not on their own land while none of them is
 *   online: the protection of the territories holds for machines as for players;</li>
 *   <li>with the intruder mode on (a console's switch; the server may forbid it): any outsider on the faction's own land,
 *   in peace too, while the machine itself stands on that land.</li>
 * </ul>
 * 1.8.0: a console may switch off the monsters, the enemies at war or fighting back (OrderRules bits, by group); "never" stays never.
 */
public final class TargetRules {
    private TargetRules() {
    }

    /** Whether the machine may attack this target now. */
    public static boolean mayAttack(GarrisonMachine machine, @Nullable Entity target) {
        Entity self = machine.asEntity();
        MinecraftServer server = self.getServer();
        if (server == null || target == null || target == self || !target.isAlive() || !(target instanceof LivingEntity)) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        MachineCore core = machine.machineCore();
        long now = self.level().getGameTime();
        UUID mine = core.owner();
        UUID theirs = FactionService.ownerOf(target);
        if (theirs == null) {
            // a creature: monsters always, neutral ones when they attacked, peaceful ones never
            if (target instanceof Enemy) {
                return (core.rules() & OrderRules.MONSTERS) != 0 || (core.rules() & OrderRules.RETALIATE) != 0 && core.attackedBy(target, now);
            }
            // wanderers are neutral too: answered only when they attacked
            return (target instanceof NeutralMob || target instanceof com.projectrebirth.wanderers.WandererEntity) && (core.rules() & OrderRules.RETALIATE) != 0 && core.attackedBy(target, now);
        }
        if (mine == null) {
            // a machine nobody owns (only in tests) defends itself and fights monsters only
            return core.attackedBy(target, now);
        }
        if (mine.equals(theirs)) {
            return false;
        }
        FactionData data = FactionData.get(server);
        Optional<Faction> ours = data.of(mine);
        Optional<Faction> other = data.of(theirs);
        if (ours.isPresent() && other.isPresent() && (ours.get().id().equals(other.get().id()) || ours.get().allies().contains(other.get().id()))) {
            return false;
        }
        if ((core.rules() & OrderRules.RETALIATE) != 0 && core.attackedBy(target, now)) {
            return true;
        }
        if ((core.rules() & OrderRules.WAR) != 0 && ours.isPresent() && other.isPresent() && atWar(server, self, target, ours.get(), other.get())) {
            return true;
        }
        return ours.isPresent() && intruder(server, machine, target, ours.get());
    }

    /**
     * Whether the target is one of the machine's own (1.7.0, block G: a rocket turret holds fire near them): a player, a body, armor
     * or a machine of its owner, of its owner's faction or of an allied one.
     */
    public static boolean friendly(GarrisonMachine machine, @Nullable Entity target) {
        MinecraftServer server = machine.asEntity().getServer();
        UUID mine = machine.machineCore().owner();
        UUID theirs = target == null ? null : FactionService.ownerOf(target);
        if (server == null || mine == null || theirs == null) {
            return false;
        }
        if (mine.equals(theirs)) {
            return true;
        }
        FactionData data = FactionData.get(server);
        Optional<Faction> ours = data.of(mine);
        Optional<Faction> other = data.of(theirs);
        return ours.isPresent() && other.isPresent() && (ours.get().id().equals(other.get().id()) || ours.get().allies().contains(other.get().id()));
    }

    /** The war is active, and the target is not on its own faction's land while nobody of that faction is online. */
    private static boolean atWar(MinecraftServer server, Entity self, Entity target, Faction ours, Faction theirs) {
        if (FactionService.warState(ours, theirs, System.currentTimeMillis()) != FactionService.WarState.ACTIVE) {
            return false;
        }
        Optional<Faction> land = FactionService.territoryAt(server, target.level(), target.blockPosition());
        return land.isEmpty() || !land.get().id().equals(theirs.id()) || FactionService.anyoneOnline(server, theirs);
    }

    /** The intruder mode: allowed on the server, switched on for this machine, both of them on the machine's faction's land. */
    private static boolean intruder(MinecraftServer server, GarrisonMachine machine, Entity target, Faction ours) {
        if (!RebirthConfig.ALLOW_INTRUDER_MODE.get() || !machine.machineCore().intruderMode()) {
            return false;
        }
        Entity self = machine.asEntity();
        Optional<Faction> here = FactionService.territoryAt(server, self.level(), self.blockPosition());
        Optional<Faction> there = FactionService.territoryAt(server, target.level(), target.blockPosition());
        return here.isPresent() && here.get().id().equals(ours.id()) && there.isPresent() && there.get().id().equals(ours.id());
    }
}
