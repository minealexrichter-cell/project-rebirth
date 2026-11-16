package com.projectrebirth.powerarmor;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeDefinition;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.RegistryAccess;

/** What the upgrades of a power armor do; the numbers come from the config. */
public final class ArmorUpgradeEffects {
    public static final String PLATING = "armor_plating";
    public static final String COMPRESSED_AIR = "compressed_air";
    public static final String FLOODLIGHT = "floodlight";

    private ArmorUpgradeEffects() {
    }

    private static int count(RegistryAccess access, InstalledUpgrades upgrades, String kind) {
        int count = 0;
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            if (Upgrades.definition(access, entry.stack()).filter(definition -> definition.kind().equals(kind)).isPresent()) {
                count++;
            }
        }
        return count;
    }

    /** Added structural integrity: the plates of each part count on their own, whatever their materials. */
    /** How many platings the armor carries: each also keeps some radiation off the pilot. */
    public static int platingCount(PowerArmorEntity armor) {
        return count(armor.registryAccess(), armor.upgrades(), PLATING);
    }

    public static float structuralBonus(RegistryAccess access, InstalledUpgrades upgrades) {
        return platingBonus(access, upgrades, false);
    }

    public static float criticalBonus(RegistryAccess access, InstalledUpgrades upgrades) {
        return platingBonus(access, upgrades, true);
    }

    private static float platingBonus(RegistryAccess access, InstalledUpgrades upgrades, boolean critical) {
        double bonus = 0.0;
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            UpgradeDefinition definition = Upgrades.definition(access, entry.stack()).filter(found -> found.kind().equals(PLATING)).orElse(null);
            if (definition != null && definition.material().isPresent()) {
                RebirthConfig.ArmorPlatingStats stats = RebirthConfig.armorPlating(definition.material().get());
                if (stats != null) {
                    bonus += critical ? stats.critical().get() : stats.structural().get();
                }
            }
        }
        return (float) bonus;
    }

    /** Jump of the variant plus the modules, up to the common limit; a variant above the limit keeps its own jump. */
    public static double jumpStrength(PowerArmorEntity armor) {
        double base = armor.stats().jumpStrength().get();
        int modules = count(armor.registryAccess(), armor.upgrades(), COMPRESSED_AIR);
        return modules == 0 ? base : Math.max(base, Math.min(RebirthConfig.ARMOR_MAX_JUMP_STRENGTH.get(),
                base + modules * RebirthConfig.ARMOR_JUMP_PER_AIR_MODULE.get()));
    }

    /** A higher jump must be able to land: every module adds to the safe fall. */
    public static double safeFallDistance(PowerArmorEntity armor) {
        return armor.stats().safeFallDistance().get()
                + count(armor.registryAccess(), armor.upgrades(), COMPRESSED_AIR) * RebirthConfig.ARMOR_SAFE_FALL_PER_AIR_MODULE.get();
    }

    /** 1.6.0, block F: the sensor unit on the helmet. */
    public static boolean hasSensor(PowerArmorEntity armor) {
        return count(armor.registryAccess(), armor.upgrades(), com.projectrebirth.scouting.Scouting.SENSOR) > 0;
    }

    public static boolean hasFloodlight(PowerArmorEntity armor) {
        return count(armor.registryAccess(), armor.upgrades(), FLOODLIGHT) > 0;
    }
}
