package com.projectrebirth.environment;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyWear;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.shelter.Shelters;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Electromagnetic interference of an electromagnetic storm (version 1.5.0, block E). A hazard of the place like the others
 * ({@link Hazard#EMI}): deep ground weakens it, a sealed room keeps it out. People hardly notice it; what it disturbs is
 * electronics:
 * <ul>
 * <li>an artificial body (better classes less, the electronics shielding gadget nearly not at all): more charge used, no
 * self-repair, a jetpack that cuts out, and now and then a short fault that slows the body down and wears it; never a loss of
 * control, and an empty body standing in the world takes nothing;</li>
 * <li>a power armor (the EMI screen upgrade keeps most of it off): its HUD, a flickering floodlight, a shakier aim;</li>
 * <li>the stations of the mod outside a sealed room stand still, keeping their progress (generators, batteries, cables, solar
 * panels and life support work on);</li>
 * <li>a dosimeter and the shelter panel show interference.</li>
 * </ul>
 * The interference a player feels is counted once a second and kept in their data (synced to them for their HUD).
 */
public final class Emi {
    /** Upgrade kind of the electronics shielding gadget of a body. */
    public static final String SHIELDING = "emi_shielding";
    /** Upgrade kind of the EMI screen of a power armor. */
    public static final String SCREEN = "emi_screen";

    private Emi() {
    }

    /** The interference this player feels now, after shielding and the body's class; 0 for nobody disturbed. Works on both sides. */
    public static float level(Player player) {
        return player.getData(TemperatureRegistry.EMI_LEVEL);
    }

    /** Whether it is strong enough to disturb (and for the HUD to mark it). */
    public static boolean disturbs(float level) {
        return level > RebirthConfig.EMI_THRESHOLD.get();
    }

    public static boolean disturbed(Player player) {
        return disturbs(level(player));
    }

    /** What a player feels of this much interference around them. */
    public static float felt(Player player, float around) {
        if (around <= 0.0f) {
            return 0.0f;
        }
        float shield = RebirthConfig.EMI_SHIELD.get().floatValue();
        if (player.getVehicle() instanceof PowerArmorEntity armor) {
            boolean screened = armor.upgrades().entries().stream().anyMatch(entry ->
                    Upgrades.definition(player.level().registryAccess(), entry.stack()).filter(found -> found.kind().equals(SCREEN)).isPresent());
            return screened ? around * shield : around;
        }
        BodyClass bodyClass = TransferService.state(player).body().orElse(null);
        if (bodyClass == null) {
            return around;
        }
        float felt = around * RebirthConfig.bodyStats(bodyClass).emiSensitivity().get().floatValue();
        return BodyGadgets.has(player.level().registryAccess(), BodyGadgets.of(player), SHIELDING) ? felt * shield : felt;
    }

    /** One second of a player, from the exposure where they are. Public for tests. */
    public static void second(ServerPlayer player, Exposure exposure) {
        float level = Environment.isEnabled() && player.isAlive() ? felt(player, exposure.get(Hazard.EMI)) : 0.0f;
        if (player.getData(TemperatureRegistry.EMI_LEVEL) != level) {
            player.setData(TemperatureRegistry.EMI_LEVEL, level);
        }
        if (disturbs(level) && TransferService.isInArtificialBody(player) && !(player.getVehicle() instanceof PowerArmorEntity)) {
            bodySecond(player, level);
        }
    }

    /** A disturbed body: more charge, and now and then a fault. Self-repair and the jetpack ask {@link #disturbed} themselves. */
    private static void bodySecond(ServerPlayer player, float level) {
        BodyPower.spend(player, level * RebirthConfig.EMI_CHARGE.get());
        double chance = level * RebirthConfig.EMI_FAULT_CHANCE.get() * BodyWear.faultMultiplier(player);
        if (player.getRandom().nextDouble() < chance) {
            fault(player);
        }
    }

    /** A short fault: much slower for a moment, a little wear. The body stays under control. Public for tests. */
    public static void fault(ServerPlayer player) {
        TransferService.state(player).body().ifPresent(bodyClass -> {
            MobEffectInstance slow = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RebirthConfig.EMI_FAULT_TICKS.get(), 3, true, false, false);
            slow.getCures().clear();
            com.projectrebirth.bodies.BodyRules.addOwnEffect(player, slow);
            BodyWear.add(player, (float) (RebirthConfig.EMI_FAULT_WEAR.get() * BodyWear.classMultiplier(bodyClass)));
            player.displayClientMessage(Component.translatable("message.projectrebirth.emi.fault").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            // placeholder until the sounds of block H
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
        });
    }

    // ---------------------------------------------------------------- stations

    /**
     * How fast a station of the mod may work here now: 1 normally, the config's stationFactor in an electromagnetic storm outside
     * a sealed room (0 = not at all). Depth does not help, only a shelter. Public for tests.
     */
    public static float stationFactor(ServerLevel level, BlockPos pos) {
        if (!Environment.isEnabled() || insideShelter(level, pos)) {
            return 1.0f;
        }
        float emi = Environment.ordinaryExposureAt(level, pos.above()).get(Hazard.EMI);
        // a station deep underground still counts the storm at full strength: only a shelter keeps it out
        Environment.Shelter shelter = Environment.shelterAt(level, pos.above());
        if (shelter.underground() && RebirthConfig.EMI_UNDERGROUND.get() > 0.0) {
            emi /= RebirthConfig.EMI_UNDERGROUND.get().floatValue();
        }
        return disturbs(emi) ? RebirthConfig.EMI_STATION_FACTOR.get().floatValue() : 1.0f;
    }

    /** A block of a sealed room's wall or inside it: one of its neighbours is the room's air. */
    public static boolean insideShelter(ServerLevel level, BlockPos pos) {
        if (Shelters.sealedRoomAt(level, pos) != null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (Shelters.sealedRoomAt(level, pos.relative(direction)) != null) {
                return true;
            }
        }
        return false;
    }
}
