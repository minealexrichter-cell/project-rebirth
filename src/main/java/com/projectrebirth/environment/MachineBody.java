package com.projectrebirth.environment;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.upgrades.InstalledUpgrades;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The environment and the machines: artificial bodies and power armor. They have no biology, so dose and toxins mean
 * nothing to them; acid rain wears their open mechanisms instead, unless a sealing module keeps it out. Better bodies wear
 * more slowly. The damage counts as damage, so it also holds back self-repair.
 */
public final class MachineBody {
    private static final int TICKS_PER_SECOND = 20;

    private MachineBody() {
    }

    /** One second of a player in an artificial body. */
    static void onSecond(ServerPlayer player, Exposure exposure) {
        if (player.getVehicle() instanceof PowerArmorEntity armor && !armor.isWrecked()) {
            return; // The cockpit is sealed.
        }
        BodyRules.bodyClassOf(player).ifPresent(bodyClass ->
                acidSecond(player, bodyClass, BodyGadgets.of(player), exposure.get(Hazard.ACID), player.level().getGameTime()));
    }

    /** One second of a body standing in the world, called by the body itself. */
    public static void tickStanding(ArtificialBodyEntity body) {
        if (!Environment.isEnabled() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        Exposure exposure = Environment.exposureAt(level, body.blockPosition());
        acidSecond(body, body.getBodyClass(), body.upgrades(), exposure.get(Hazard.ACID), level.getGameTime());
    }

    /** Whether the upgrades of a body keep a hazard out (from the data of the upgrades, see protects_from). */
    public static boolean sealedAgainst(LivingEntity body, InstalledUpgrades upgrades, String hazard) {
        return !upgrades.isEmpty() && BodyGadgets.protects(body.level().registryAccess(), upgrades, hazard);
    }

    /** The wear of a body per second per unit of acid. */
    public static float acidWear(BodyClass bodyClass) {
        return RebirthConfig.bodyStats(bodyClass).acidWearPerSecond().get().floatValue();
    }

    /**
     * One second of acid on a body; the wear of several seconds comes at once, so that the body is not hurt every second.
     * Public for tests.
     *
     * @return the damage done this second
     */
    public static float acidSecond(LivingEntity body, BodyClass bodyClass, InstalledUpgrades upgrades, float acid, long gameTime) {
        int interval = RebirthConfig.MACHINE_ACID_WEAR_INTERVAL.get();
        if (acid <= 0.0f || (gameTime / TICKS_PER_SECOND) % interval != 0 || sealedAgainst(body, upgrades, BodyGadgets.ACID)
                || !(body.level() instanceof ServerLevel level)) {
            return 0.0f;
        }
        float damage = acid * acidWear(bodyClass) * interval;
        if (damage > 0.0f) {
            body.hurt(level.damageSources().source(HumanBody.ACID_DAMAGE), damage);
        }
        return damage;
    }

    /** One second of a power armor, if acid wears armor at all (off by default). Called by the armor itself. */
    public static void tickArmor(PowerArmorEntity armor) {
        if (!Environment.isEnabled() || !RebirthConfig.MACHINE_ARMOR_ACID_WEAR.get() || armor.isWrecked()
                || !(armor.level() instanceof ServerLevel level)) {
            return;
        }
        float acid = Environment.exposureAt(level, armor.blockPosition()).get(Hazard.ACID);
        if (acid > 0.0f) {
            armor.corrode(acid * RebirthConfig.MACHINE_ARMOR_ACID_WEAR_PER_SECOND.get().floatValue());
        }
    }
}
