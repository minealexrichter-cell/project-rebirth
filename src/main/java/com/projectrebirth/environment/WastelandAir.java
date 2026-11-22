package com.projectrebirth.environment;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9.0, block B: the air of the Wasteland. Outside a sealed room a human gathers toxins everywhere, on top of the ordinary
 * background and of catastrophes: by the biome (the field {@code wasteland} of its environment profile), the stage of the
 * Wasteland and the preset; less under a roof, much less underground, more in the open at night (the toxic fog by the ground).
 * While a person breathes this air the body does not clear its toxins and dose by itself ([wasteland.air] exposedClearance).
 * Deep underground the rock gives off radiation. Protective gear keeps part of it off: the head piece while its filter lasts
 * (and the filter is used up by this air), the rest of a suit through the skin.
 */
public final class WastelandAir {
    private static final List<EquipmentSlot> SKIN = List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private WastelandAir() {
    }

    public static boolean enabled() {
        return Environment.isEnabled() && WastelandConfig.AIR_ENABLED.get();
    }

    /** The biome's share of the air of the Wasteland: 1 in an ordinary one. */
    public static float biomeFactor(ServerLevel level, BlockPos pos) {
        return Environment.profileFor(level.registryAccess(), level.getBiome(pos)).map(EnvironmentProfile::wasteland).orElse(0.0f);
    }

    /** Toxic load per second the air of the Wasteland brings at a place, before any gear; 0 in a sealed room. */
    public static float toxinAt(ServerLevel level, BlockPos pos) {
        if (!enabled() || Shelters.sealedRoomAt(level, pos) != null) {
            return 0.0f;
        }
        Environment.Shelter shelter = Environment.shelterAt(level, pos);
        return toxin(biomeFactor(level, pos), Wasteland.stage(level), shelter, isNight(level));
    }

    /** The calculation alone, with nothing taken from the world. */
    public static float toxin(float biome, int stage, Environment.Shelter shelter, boolean night) {
        float rate = WastelandConfig.AIR_TOXIN.get().floatValue() * biome * WastelandConfig.byStage(WastelandConfig.STAGE_BACKGROUND, stage)
                * WastelandConfig.preset().background().get().floatValue();
        if (shelter.underRoof()) {
            rate *= WastelandConfig.AIR_ROOF.get().floatValue();
        } else if (night) {
            rate *= WastelandConfig.AIR_NIGHT.get().floatValue();
        }
        if (shelter.underground()) {
            rate *= WastelandConfig.AIR_UNDERGROUND.get().floatValue();
        }
        return rate;
    }

    /** Night in a world with a sky: the fog lies by the ground. */
    public static boolean isNight(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime() && level.isNight();
    }

    /** Dose per second from the deep rock: underground, below [wasteland.air] deepRockY. */
    public static float deepRockDoseAt(ServerLevel level, BlockPos pos) {
        if (!enabled() || pos.getY() >= WastelandConfig.AIR_DEEP_ROCK_Y.get() || !Environment.shelterAt(level, pos).underground()) {
            return 0.0f;
        }
        return WastelandConfig.AIR_DEEP_ROCK_DOSE.get().floatValue();
    }

    /** The share of the air of the Wasteland that this person's gear keeps off, 0 to 1. */
    public static float protection(Player player) {
        float total = 0.0f;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        Protection.Kit headKit = Protection.kitOf(head);
        if (headKit != null && Protection.filterLeft(head) > 0.0f && WastelandConfig.kit(headKit.id()) != null) {
            total += WastelandConfig.kit(headKit.id()).breath().get().floatValue();
        }
        float skinShares = 0.0f;
        for (EquipmentSlot slot : SKIN) {
            skinShares += Protection.share(slot);
        }
        if (skinShares > 0.0f) {
            for (EquipmentSlot slot : SKIN) {
                Protection.Kit kit = Protection.kitOf(player.getItemBySlot(slot));
                if (kit != null && WastelandConfig.kit(kit.id()) != null) {
                    total += Protection.share(slot) / skinShares * WastelandConfig.kit(kit.id()).skin().get().floatValue();
                }
            }
        }
        return Math.min(1.0f, total);
    }

    /** The share of its own clearance the body keeps while it breathes air of this strength. */
    public static float clearance(float rawToxin) {
        return rawToxin > 0.0f ? WastelandConfig.AIR_EXPOSED_CLEARANCE.get().floatValue() : 1.0f;
    }

    /** Uses the filter of the head piece for one second of this air (on top of what polluted air uses). */
    public static void useFilter(ServerPlayer player, float rawToxin) {
        if (rawToxin <= 0.0f) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!Protection.breathes(head)) {
            return;
        }
        float left = Protection.filterLeft(head);
        if (left <= 0.0f) {
            return;
        }
        float after = Math.max(0.0f, left - WastelandConfig.AIR_FILTER_USE.get().floatValue());
        head.set(EnvironmentRegistry.FILTER_LIFE.get(), after);
        if (after <= 0.0f) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projectrebirth.environment.filter_spent"), true);
        }
    }

    /** Seconds of the ordinary filter life, for the HUD and tests. */
    public static float filterLife() {
        return RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue();
    }
}
