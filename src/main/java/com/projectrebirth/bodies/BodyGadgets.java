package com.projectrebirth.bodies;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.RegistryAccess;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/** What the gadgets of a body do, whether the body stands in the world or a player controls it. See also {@link Jetpack}. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyGadgets {
    public static final String PLATING = "plating";
    public static final String SEALING = "sealing";
    public static final String JETPACK = "jetpack";
    /** What an upgrade may keep out of a body (protects_from in the upgrade data). */
    public static final String DROWNING = "drowning";
    public static final String ACID = "acid";
    public static final String DUST = "dust";

    private BodyGadgets() {
    }

    public static boolean has(RegistryAccess access, InstalledUpgrades upgrades, String kind) {
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            if (Upgrades.definition(access, entry.stack()).filter(definition -> definition.kind().equals(kind)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Whether one of the upgrades keeps this out of the body. */
    public static boolean protects(RegistryAccess access, InstalledUpgrades upgrades, String hazard) {
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            if (Upgrades.definition(access, entry.stack()).filter(definition -> definition.protectsFrom().contains(hazard)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The upgrades of a player's controlled body, or none for a human. */
    public static InstalledUpgrades of(Player player) {
        return TransferService.isInArtificialBody(player) ? player.getData(UpgradeRegistry.CONTROLLED_BODY) : InstalledUpgrades.EMPTY;
    }

    /** Armor points the plates add on top of the body class. */
    public static double armorBonus(RegistryAccess access, InstalledUpgrades upgrades) {
        return platingBonus(access, upgrades, false);
    }

    /** Armor toughness the plates add. */
    public static double toughnessBonus(RegistryAccess access, InstalledUpgrades upgrades) {
        return platingBonus(access, upgrades, true);
    }

    private static double platingBonus(RegistryAccess access, InstalledUpgrades upgrades, boolean toughness) {
        double bonus = 0.0;
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            var definition = Upgrades.definition(access, entry.stack()).filter(found -> found.kind().equals(PLATING));
            if (definition.isPresent() && definition.get().material().isPresent()) {
                RebirthConfig.PlatingStats stats = RebirthConfig.plating(definition.get().material().get());
                if (stats != null) {
                    bonus += toughness ? stats.toughness().get() : stats.armor().get();
                }
            }
        }
        return bonus;
    }

    /** A body sealed against drowning does not use up air under water. A rule of the body, not an effect: most bodies ignore effects. */
    @SubscribeEvent
    static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        InstalledUpgrades upgrades;
        if (entity instanceof ArtificialBodyEntity body) {
            upgrades = body.upgrades();
        } else if (entity instanceof Player player) {
            upgrades = of(player);
        } else {
            return;
        }
        if (!upgrades.isEmpty() && entity.isEyeInFluid(FluidTags.WATER) && protects(entity.level().registryAccess(), upgrades, DROWNING)) {
            event.setCanBreathe(true);
        }
    }
}
