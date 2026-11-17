package com.projectrebirth.garrison;

import java.util.Locale;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.world.item.Item;

/**
 * The two defence turrets (1.7.0, block G; the concept's section 5.4): the machine gun (bursts of assault rifle rounds, 24
 * blocks, 80 / 40) and the rocket turret (a rocket along an arc, 6 to 32 blocks, 100 / 50; it holds fire while one of its own
 * is near the target). Numbers in the config ([garrison.turrets]).
 */
public enum TurretType {
    MACHINE_GUN,
    ROCKET;

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_turret";
    }

    public boolean rocket() {
        return this == ROCKET;
    }

    public int range() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_RANGE.get() : RebirthConfig.GUN_TURRET_RANGE.get();
    }

    public int minRange() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_MIN_RANGE.get() : 0;
    }

    public float maxStructural() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_STRUCTURAL.get().floatValue() : MachineKind.TURRET.maxStructural();
    }

    public float maxCritical() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_CRITICAL.get().floatValue() : MachineKind.TURRET.maxCritical();
    }

    public Item ammo() {
        return rocket() ? GarrisonRegistry.ROCKET.get() : WeaponRegistry.ASSAULT_RIFLE_AMMO.get();
    }

    public int energyPerShot() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_ENERGY.get() : RebirthConfig.GUN_TURRET_ENERGY.get();
    }

    public int lockTicks() {
        return rocket() ? RebirthConfig.ROCKET_TURRET_LOCK_TICKS.get() : RebirthConfig.GUN_TURRET_LOCK_TICKS.get();
    }
}
