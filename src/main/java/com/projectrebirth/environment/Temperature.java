package com.projectrebirth.environment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temperature (version 1.5.0): the temperature of a place in degrees Celsius, and the heat or cold stress of a human body.
 * <p>
 * The place: the biome's temperature, colder at night, high up and in rain; a roof or deep ground evens it out; fire, lava,
 * magma, lit campfires and furnaces and working heaters warm it; the Nether is hot; a sealed room of a shelter has its own
 * comfortable climate. Once a second per person, never every tick.
 * <p>
 * The person: outside the comfortable range the stress grows (heat above, cold below), the less the more the suits and thermal
 * liners keep off; in comfort it goes away. Its stages are like those of the dose and the toxic load: weakness, slower healing,
 * damage. Heat also makes hungry faster, cold slower. Artificial bodies do not feel cold (heat is their own overheating), and
 * the pilot of a power armor is protected: their stress only goes away.
 */
public final class Temperature {
    public static final ResourceKey<DamageType> HEATSTROKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("heatstroke"));
    public static final ResourceKey<DamageType> HYPOTHERMIA_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("hypothermia"));
    public static final float MAX_STRESS = 100.0f;

    private static final ResourceLocation WEAK_SPEED = ProjectRebirth.id("thermal_weak_speed");
    private static final ResourceLocation WEAK_ATTACK = ProjectRebirth.id("thermal_weak_attack");
    private static final int SYMPTOM_TICKS = 100;
    private static final int RENEW_BELOW_TICKS = 60;

    /** Working heaters of each dimension, kept up to date by the heaters themselves; a handful, not a scan of the world. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> HEATERS = new HashMap<>();

    private Temperature() {
    }

    // ---------------------------------------------------------------- the place

    static void heaterWorking(Level level, BlockPos pos, boolean working) {
        Set<BlockPos> heaters = HEATERS.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        if (working) {
            heaters.add(pos.immutable());
        } else {
            heaters.remove(pos);
        }
    }

    /** The temperature of a place now, with everything that warms it. */
    public static float at(ServerLevel level, BlockPos pos) {
        if (Shelters.sealedRoomAt(level, pos) != null) {
            return RebirthConfig.TEMP_SHELTER.get().floatValue();
        }
        return withoutHeaters(level, pos) + heaters(level, pos);
    }

    /** The temperature of a place without heaters: what a heater's thermostat looks at. */
    public static float withoutHeaters(ServerLevel level, BlockPos pos) {
        float temperature;
        if (level.dimensionType().ultraWarm()) {
            temperature = RebirthConfig.TEMP_HOT_DIMENSION.get().floatValue();
        } else {
            Holder<Biome> biome = level.getBiome(pos);
            temperature = (float) (biome.value().getBaseTemperature() * RebirthConfig.TEMP_BIOME_SCALE.get() + RebirthConfig.TEMP_BIOME_OFFSET.get())
                    + biomeOffset(level, biome);
            boolean sky = level.canSeeSky(pos);
            if (level.dimensionType().hasSkyLight() && level.isNight()) {
                temperature -= (float) (RebirthConfig.TEMP_NIGHT.get() * (sky ? 1.0 : 0.5));
            }
            int from = RebirthConfig.TEMP_ALTITUDE_FROM.get();
            if (pos.getY() > from) {
                temperature -= (float) ((pos.getY() - from) / RebirthConfig.TEMP_ALTITUDE_PER_DEGREE.get());
            }
            if (sky && level.isRaining() && biome.value().getPrecipitationAt(pos) != Biome.Precipitation.NONE) {
                temperature -= RebirthConfig.TEMP_RAIN.get().floatValue();
            }
            Environment.Shelter shelter = Environment.shelterAt(level, pos);
            if (shelter.underground()) {
                temperature = Mth.lerp(RebirthConfig.TEMP_UNDERGROUND_BLEND.get().floatValue(), temperature, RebirthConfig.TEMP_UNDERGROUND.get().floatValue());
            } else if (shelter.underRoof()) {
                temperature = Mth.lerp(RebirthConfig.TEMP_INDOOR_BLEND.get().floatValue(), temperature, RebirthConfig.TEMP_INDOOR.get().floatValue());
            }
        }
        return temperature + sources(level, pos);
    }

    /** The degrees the biome's environment profile adds (1.5.0: savannas are cooler than deserts). */
    public static float biomeOffset(Level level, Holder<Biome> biome) {
        return Environment.profileFor(level.registryAccess(), biome).map(EnvironmentProfile::temperatureOffset).orElse(0.0f);
    }

    /** Fire, lava, magma, lit campfires and furnaces around; a few hundred blocks once a second. */
    public static float sources(Level level, BlockPos center) {
        int radius = RebirthConfig.TEMP_SOURCE_RADIUS.get();
        if (radius <= 0) {
            return 0.0f;
        }
        double warmth = 0.0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                warmth += RebirthConfig.TEMP_FIRE.get();
            } else if (state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)
                    || state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
                warmth += RebirthConfig.TEMP_WARM_BLOCK.get();
            }
        }
        return (float) Math.min(warmth, RebirthConfig.TEMP_SOURCES_MAX.get());
    }

    private static float heaters(ServerLevel level, BlockPos pos) {
        Set<BlockPos> heaters = HEATERS.get(level.dimension());
        if (heaters == null || heaters.isEmpty()) {
            return 0.0f;
        }
        double radius = RebirthConfig.HEATER_RADIUS.get();
        for (BlockPos heater : heaters) {
            if (heater.distSqr(pos) <= radius * radius) {
                return RebirthConfig.HEATER_WARMTH.get().floatValue(); // heaters do not add up: one warms a camp
            }
        }
        return 0.0f;
    }

    // ---------------------------------------------------------------- the person

    public static float stress(Player player) {
        return player.getData(TemperatureRegistry.THERMAL_STRESS);
    }

    public static void setStress(Player player, float stress) {
        player.setData(TemperatureRegistry.THERMAL_STRESS, Mth.clamp(stress, -MAX_STRESS, MAX_STRESS));
    }

    /** 0 none, 1 weakness, 2 slower healing, 3 damage; for heat and cold alike. */
    public static int stage(float stress) {
        float amount = Math.abs(stress);
        if (amount >= RebirthConfig.TEMP_DAMAGE.get()) {
            return 3;
        }
        if (amount >= RebirthConfig.TEMP_SLOW_HEALING.get()) {
            return 2;
        }
        return amount >= RebirthConfig.TEMP_WEAKNESS.get() ? 1 : 0;
    }

    /** The stress of one second at this temperature, with this much of heat and cold kept off. Nothing taken from the world. */
    public static float afterSecond(float stress, float temperature, float heatKeptOff, float coldKeptOff) {
        float gain = 0.0f;
        double min = RebirthConfig.TEMP_COMFORT_MIN.get();
        double max = RebirthConfig.TEMP_COMFORT_MAX.get();
        if (temperature > max) {
            gain = (float) ((temperature - max) * RebirthConfig.TEMP_HEAT_RATE.get() * (1.0f - Mth.clamp(heatKeptOff, 0.0f, 1.0f)));
        } else if (temperature < min) {
            gain = (float) (-(min - temperature) * RebirthConfig.TEMP_COLD_RATE.get() * (1.0f - Mth.clamp(coldKeptOff, 0.0f, 1.0f)));
        }
        float after = stress + gain;
        // in comfort, or when the place pulls the other way, the body also gets back to normal by itself
        if (gain == 0.0f || stress != 0.0f && Math.signum(gain) != Math.signum(stress)) {
            after = recover(after);
        }
        return Mth.clamp(after, -MAX_STRESS, MAX_STRESS);
    }

    private static float recover(float stress) {
        float recovery = RebirthConfig.TEMP_RECOVERY.get().floatValue();
        return stress > 0.0f ? Math.max(0.0f, stress - recovery) : Math.min(0.0f, stress + recovery);
    }

    /** One second of a human on foot. Public for tests. */
    public static void second(ServerPlayer player) {
        if (!TemperatureRegistry.enabled()) {
            clearSymptoms(player);
            return;
        }
        float temperature = at(player.serverLevel(), BlockPos.containing(player.getEyePosition()));
        setStress(player, afterSecond(stress(player), temperature, Protection.thermal(player, true), Protection.thermal(player, false)));
        applySymptoms(player);
    }

    /** One second of someone the temperature cannot reach: an artificial body, a power armor pilot, creative. */
    public static void recoverSecond(ServerPlayer player) {
        setStress(player, recover(stress(player)));
        clearSymptoms(player);
    }

    private static void applySymptoms(ServerPlayer player) {
        float stress = stress(player);
        int stage = stage(stress);
        boolean hot = stress > 0.0f;
        showSymptom(player, TemperatureRegistry.HEATSTROKE, hot ? stage : 0);
        showSymptom(player, TemperatureRegistry.HYPOTHERMIA, hot ? 0 : stage);
        setModifier(player, Attributes.MOVEMENT_SPEED, WEAK_SPEED, stage >= 1 ? -RebirthConfig.HUMAN_WEAKNESS_SPEED_PENALTY.get() : 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, WEAK_ATTACK, stage >= 1 ? -RebirthConfig.HUMAN_WEAKNESS_ATTACK_PENALTY.get() : 0.0);
        if (stage >= 1) {
            if (hot) {
                player.causeFoodExhaustion(RebirthConfig.TEMP_HEAT_EXHAUSTION.get().floatValue());
            } else {
                player.getFoodData().setExhaustion(Math.max(0.0f,
                        player.getFoodData().getExhaustionLevel() - RebirthConfig.TEMP_COLD_EXHAUSTION_RELIEF.get().floatValue()));
            }
        }
        if (stage >= 3 && (player.level().getGameTime() / 20) % RebirthConfig.HUMAN_SICKNESS_DAMAGE_INTERVAL.get() == 0) {
            player.hurt(player.serverLevel().damageSources().source(hot ? HEATSTROKE_DAMAGE : HYPOTHERMIA_DAMAGE),
                    RebirthConfig.HUMAN_SICKNESS_DAMAGE.get().floatValue());
        }
    }

    /** Healing is multiplied by this from the slow-healing stage. */
    public static float healingMultiplier(Player player) {
        if (!TemperatureRegistry.enabled()) {
            return 1.0f;
        }
        int stage = stage(stress(player));
        // at the last stage, as with the dose and toxins: the damage is not healed away while it lasts
        return stage >= 3 ? RebirthConfig.HUMAN_LAST_STAGE_HEALING_MULTIPLIER.get().floatValue()
                : stage >= 2 ? RebirthConfig.TEMP_HEALING_MULTIPLIER.get().floatValue() : 1.0f;
    }

    public static void clearSymptoms(ServerPlayer player) {
        showSymptom(player, TemperatureRegistry.HEATSTROKE, 0);
        showSymptom(player, TemperatureRegistry.HYPOTHERMIA, 0);
        setModifier(player, Attributes.MOVEMENT_SPEED, WEAK_SPEED, 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, WEAK_ATTACK, 0.0);
    }

    private static void showSymptom(ServerPlayer player, Holder<MobEffect> effect, int stage) {
        MobEffectInstance current = player.getEffect(effect);
        if (stage <= 0) {
            if (current != null) {
                player.removeEffect(effect);
            }
            return;
        }
        if (current == null || current.getAmplifier() != stage - 1 || current.getDuration() < RENEW_BELOW_TICKS) {
            player.removeEffect(effect);
            player.addEffect(new MobEffectInstance(effect, SYMPTOM_TICKS, stage - 1, true, false, true));
        }
    }

    private static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0.0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
