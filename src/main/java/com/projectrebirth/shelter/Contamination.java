package com.projectrebirth.shelter;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * External contamination: dust, acid and fallout on a person, the gear they wear, an artificial body or a power armor. It
 * is not the toxic load inside a body and hardly harms the carrier, but whoever carries it into a sealed room sheds it into
 * the room's air, which the filtration then has to clean. It comes off by itself very slowly; a decontamination chamber
 * takes it off in seconds. Items in chests and inventories carry none.
 */
public final class Contamination {
    private Contamination() {
    }

    public static float of(Entity carrier) {
        return carrier.getData(ShelterRegistry.CONTAMINATION);
    }

    public static void set(Entity carrier, float amount) {
        float clamped = Mth.clamp(amount, 0.0f, RebirthConfig.CONTAMINATION_MAX.get().floatValue());
        if (clamped <= 0.0f) {
            if (carrier.hasData(ShelterRegistry.CONTAMINATION)) {
                carrier.removeData(ShelterRegistry.CONTAMINATION);
            }
        } else {
            carrier.setData(ShelterRegistry.CONTAMINATION, clamped);
        }
    }

    /** What one second outside adds: polluted air above the background, radiation above the background, acid. Public for tests. */
    public static float gainPerSecond(Exposure around) {
        float pollution = Math.max(0.0f, around.get(Hazard.POLLUTION) - RebirthConfig.PROTECTION_FILTER_SAFE_POLLUTION.get().floatValue());
        float radiation = Math.max(0.0f, around.get(Hazard.RADIATION) - RebirthConfig.HUD_DANGER_RADIATION.get().floatValue());
        return pollution * RebirthConfig.CONTAMINATION_PER_POLLUTION.get().floatValue()
                + radiation * RebirthConfig.CONTAMINATION_PER_RADIATION.get().floatValue()
                + around.get(Hazard.ACID) * RebirthConfig.CONTAMINATION_PER_ACID.get().floatValue();
    }

    /**
     * One second of a carrier. Outside a sealed room it gathers contamination from the air around; inside one it sheds a
     * share of it into the room's air. Either way a little comes off by itself.
     */
    public static void second(Entity carrier) {
        if (!Environment.isEnabled() || !(carrier.level() instanceof ServerLevel level) || !carrier.isAlive()) {
            return;
        }
        BlockPos pos = BlockPos.containing(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.5, carrier.getZ());
        float amount = of(carrier);
        Room room = Shelters.sealedRoomAt(level, pos);
        if (room == null) {
            amount += gainPerSecond(Environment.ordinaryExposureAt(level, pos));
        } else if (amount > 0.0f) {
            float shed = amount * RebirthConfig.CONTAMINATION_SHED_SHARE.get().floatValue();
            room.setPollution(room.pollution() + shed * RebirthConfig.CONTAMINATION_AIR_PER_UNIT.get().floatValue() / Math.max(1, room.volume()));
            amount -= shed;
        }
        amount -= RebirthConfig.CONTAMINATION_DECAY.get().floatValue();
        set(carrier, amount);
    }
}
