package com.projectrebirth.environment;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.HumanShellEntity;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * What the environment does to a human body: the radiation dose and the toxic load, which grow from the exposure once per
 * second, and the symptoms they cause. All on the server. An artificial body has no biology and takes none of this.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class HumanBody {
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("radiation_sickness"));
    public static final ResourceKey<DamageType> TOXIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("toxic_poisoning"));
    public static final ResourceKey<DamageType> ACID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectRebirth.id("acid_rain"));

    private static final ResourceLocation RADIATION_WEAK_SPEED = ProjectRebirth.id("radiation_weak_speed");
    private static final ResourceLocation RADIATION_WEAK_ATTACK = ProjectRebirth.id("radiation_weak_attack");
    private static final ResourceLocation TOXIN_WEAK_SPEED = ProjectRebirth.id("toxin_weak_speed");
    private static final ResourceLocation TOXIN_WEAK_ATTACK = ProjectRebirth.id("toxin_weak_attack");
    private static final ResourceLocation RADIATION_MAX_HEALTH = ProjectRebirth.id("radiation_max_health");
    private static final int NAUSEA_TICKS = 140;
    /** A symptom icon lasts this long and is renewed before it ends; a player who gets better loses it at the next check. */
    private static final int SYMPTOM_TICKS = 100;
    private static final int RENEW_BELOW_TICKS = 60;
    private static final int TICKS_PER_SECOND = 20;

    private HumanBody() {
    }

    public static Biology biology(Player player) {
        return player.getData(EnvironmentRegistry.BIOLOGY);
    }

    public static void setBiology(Player player, Biology biology) {
        player.setData(EnvironmentRegistry.BIOLOGY, biology.clamped());
    }

    /** Which share of a hazard does not reach this human. The protective gear of the next block adds to this. */
    public static float protection(Player player, Hazard hazard) {
        if (player.getVehicle() instanceof PowerArmorEntity armor && !armor.isWrecked()) {
            // The cockpit is sealed; radiation is only weakened.
            return hazard == Hazard.RADIATION ? Math.min(1.0f, RebirthConfig.HUMAN_PILOT_RADIATION_PROTECTION.get().floatValue()
                    + ArmorUpgradeEffects.platingCount(armor) * RebirthConfig.MACHINE_PILOT_RADIATION_PER_PLATE.get().floatValue()) : 1.0f;
        }
        return Protection.ofGear(player, hazard);
    }

    private static float reaching(Player player, Exposure exposure, Hazard hazard) {
        return exposure.get(hazard) * (1.0f - Mth.clamp(protection(player, hazard), 0.0f, 1.0f));
    }

    /** One second in this environment. Public for tests (mock players do not tick). */
    public static void onSecond(ServerPlayer player, Exposure exposure) {
        // The game mode as the server keeps it: creative and spectator players are left alone.
        if (!Environment.isEnabled() || !player.gameMode.isSurvival() || !player.isAlive()) {
            clearSymptoms(player);
            return;
        }
        if (TransferService.isInArtificialBody(player)) {
            // The own body stands in its capsule, which looks after it: it only recovers.
            setBiology(player, afterSecond(biology(player), 0.0f, 0.0f, 0.0f));
            clearSymptoms(player);
            MachineBody.onSecond(player, exposure);
            return;
        }
        float radiation = reaching(player, exposure, Hazard.RADIATION);
        float pollution = reaching(player, exposure, Hazard.POLLUTION);
        float acid = reaching(player, exposure, Hazard.ACID);
        if (!(player.getVehicle() instanceof PowerArmorEntity)) {
            Protection.useFilter(player, exposure.get(Hazard.POLLUTION));
            if (exposure.get(Hazard.ACID) > 0.0f && everySeconds(player, RebirthConfig.PROTECTION_ACID_WEAR_INTERVAL.get())) {
                Protection.wearByAcid(player);
            }
        }
        setBiology(player, afterSecond(biology(player), radiation, pollution, acid));
        applySymptoms(player);
        hurtBySymptoms(player, acid);
        tellClient(player, radiation);
    }

    /** What this person's own HUD may show. Test players have no real client behind them. */
    private static void tellClient(ServerPlayer player, float radiation) {
        if (player.connection.hasChannel(EnvironmentStatusPayload.TYPE)) {
            Biology biology = biology(player);
            PacketDistributor.sendToPlayer(player, new EnvironmentStatusPayload(radiation, biology.dose(), biology.toxin()));
        }
    }

    /** The numbers of one second. */
    public static Biology afterSecond(Biology biology, float radiation, float pollution, float acid) {
        float doseGain = radiation * RebirthConfig.HUMAN_DOSE_PER_RADIATION.get().floatValue();
        float toxinGain = pollution * RebirthConfig.HUMAN_TOXIN_PER_POLLUTION.get().floatValue() + acid * RebirthConfig.HUMAN_TOXIN_PER_ACID.get().floatValue();
        return new Biology(biology.dose() + doseGain - RebirthConfig.HUMAN_DOSE_CLEARANCE.get().floatValue(),
                biology.toxin() + toxinGain - RebirthConfig.HUMAN_TOXIN_CLEARANCE.get().floatValue()).clamped();
    }

    /** How far the dose has gone: 0 none, 1 weakness, 2 slow healing, 3 lower maximum health, 4 damage. */
    public static int doseStage(float dose) {
        if (dose >= RebirthConfig.HUMAN_DOSE_DAMAGE.get()) {
            return 4;
        }
        if (dose >= RebirthConfig.HUMAN_DOSE_MAX_HEALTH.get()) {
            return 3;
        }
        if (dose >= RebirthConfig.HUMAN_DOSE_SLOW_HEALING.get()) {
            return 2;
        }
        return dose >= RebirthConfig.HUMAN_DOSE_WEAKNESS.get() ? 1 : 0;
    }

    /** How far the toxic load has gone: 0 none, 1 nausea, 2 weakness, 3 damage. */
    public static int toxinStage(float toxin) {
        if (toxin >= RebirthConfig.HUMAN_TOXIN_DAMAGE.get()) {
            return 3;
        }
        if (toxin >= RebirthConfig.HUMAN_TOXIN_WEAKNESS.get()) {
            return 2;
        }
        return toxin >= RebirthConfig.HUMAN_TOXIN_NAUSEA.get() ? 1 : 0;
    }

    private static void applySymptoms(ServerPlayer player) {
        Biology biology = biology(player);
        int dose = doseStage(biology.dose());
        int toxin = toxinStage(biology.toxin());
        showSymptom(player, EnvironmentRegistry.RADIATION_SICKNESS, dose);
        showSymptom(player, EnvironmentRegistry.TOXIC_POISONING, toxin);
        double speed = RebirthConfig.HUMAN_WEAKNESS_SPEED_PENALTY.get();
        double attack = RebirthConfig.HUMAN_WEAKNESS_ATTACK_PENALTY.get();
        setModifier(player, Attributes.MOVEMENT_SPEED, RADIATION_WEAK_SPEED, dose >= 1 ? -speed : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ATTACK_DAMAGE, RADIATION_WEAK_ATTACK, dose >= 1 ? -attack : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.MOVEMENT_SPEED, TOXIN_WEAK_SPEED, toxin >= 2 ? -speed : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ATTACK_DAMAGE, TOXIN_WEAK_ATTACK, toxin >= 2 ? -attack : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.MAX_HEALTH, RADIATION_MAX_HEALTH, dose >= 3 ? -RebirthConfig.HUMAN_DOSE_HEALTH_PENALTY.get() : 0.0, AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (toxin >= 1 && player.getRandom().nextFloat() < RebirthConfig.HUMAN_NAUSEA_CHANCE.get().floatValue()) {
            MobEffectInstance nausea = new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0, true, false, false);
            nausea.getCures().clear();
            player.addEffect(nausea);
        }
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

    /** Without symptoms, as an artificial body or with the system switched off. The numbers stay. */
    public static void clearSymptoms(ServerPlayer player) {
        if (player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS)) {
            player.removeEffect(EnvironmentRegistry.RADIATION_SICKNESS);
        }
        if (player.hasEffect(EnvironmentRegistry.TOXIC_POISONING)) {
            player.removeEffect(EnvironmentRegistry.TOXIC_POISONING);
        }
        setModifier(player, Attributes.MOVEMENT_SPEED, RADIATION_WEAK_SPEED, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ATTACK_DAMAGE, RADIATION_WEAK_ATTACK, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.MOVEMENT_SPEED, TOXIN_WEAK_SPEED, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ATTACK_DAMAGE, TOXIN_WEAK_ATTACK, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.MAX_HEALTH, RADIATION_MAX_HEALTH, 0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
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
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static boolean everySeconds(ServerPlayer player, int seconds) {
        return (player.level().getGameTime() / TICKS_PER_SECOND) % seconds == 0;
    }

    private static DamageSource source(ServerLevel level, ResourceKey<DamageType> type) {
        return level.damageSources().source(type);
    }

    private static void hurtBySymptoms(ServerPlayer player, float acid) {
        Biology biology = biology(player);
        ServerLevel level = player.serverLevel();
        if (everySeconds(player, RebirthConfig.HUMAN_SICKNESS_DAMAGE_INTERVAL.get())) {
            float damage = RebirthConfig.HUMAN_SICKNESS_DAMAGE.get().floatValue();
            if (doseStage(biology.dose()) >= 4) {
                player.hurt(source(level, RADIATION_DAMAGE), damage);
            }
            if (toxinStage(biology.toxin()) >= 3) {
                player.hurt(source(level, TOXIN_DAMAGE), damage);
            }
        }
        if (acid > 0.0f && everySeconds(player, RebirthConfig.HUMAN_ACID_DAMAGE_INTERVAL.get())) {
            player.hurt(source(level, ACID_DAMAGE), acid * RebirthConfig.HUMAN_ACID_DAMAGE.get().floatValue());
        }
    }

    /** Whether the body stands in a capsule that still exists: it is a life support system. */
    public static boolean inIntactCapsule(ServerLevel level, HumanShellEntity shell) {
        return level.getBlockState(shell.blockPosition()).is(BodyBlocks.TRANSFER_CAPSULE.get());
    }

    /**
     * The own body waiting in the world while its owner is in an artificial body. Once the capsule is gone the air reaches it.
     * Its owner carries the numbers, so without its owner online nothing changes. Public for tests.
     */
    public static void tickShell(HumanShellEntity shell, ServerLevel level, ServerPlayer owner) {
        if (!Environment.isEnabled() || inIntactCapsule(level, shell)) {
            return;
        }
        Exposure exposure = Environment.exposureAt(level, shell.blockPosition());
        // One more second on top of what the capsule-free recovery of onSecond takes back.
        Biology after = afterSecond(biology(owner), exposure.get(Hazard.RADIATION), exposure.get(Hazard.POLLUTION), exposure.get(Hazard.ACID));
        setBiology(owner, after);
        if ((level.getGameTime() / TICKS_PER_SECOND) % RebirthConfig.HUMAN_SICKNESS_DAMAGE_INTERVAL.get() == 0
                && (doseStage(after.dose()) >= 4 || toxinStage(after.toxin()) >= 3)) {
            shell.hurt(source(level, doseStage(after.dose()) >= 4 ? RADIATION_DAMAGE : TOXIN_DAMAGE), RebirthConfig.HUMAN_SICKNESS_DAMAGE.get().floatValue());
        }
    }

    /**
     * A high dose slows healing. At the last stage of either number the body does not heal at all, or eating well would
     * outweigh the damage: only medicine helps then.
     */
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Environment.isEnabled() || TransferService.isInArtificialBody(player)) {
            return;
        }
        Biology biology = biology(player);
        if (doseStage(biology.dose()) >= 4 || toxinStage(biology.toxin()) >= 3) {
            event.setAmount(event.getAmount() * RebirthConfig.HUMAN_LAST_STAGE_HEALING_MULTIPLIER.get().floatValue());
        } else if (doseStage(biology.dose()) >= 2) {
            event.setAmount(event.getAmount() * RebirthConfig.HUMAN_DOSE_HEALING_MULTIPLIER.get().floatValue());
        }
    }

    /** By default a new life starts clean; the config can keep a share of what the body had. */
    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            float kept = RebirthConfig.HUMAN_KEPT_ON_DEATH.get().floatValue();
            Biology old = biology(event.getOriginal());
            setBiology(event.getEntity(), new Biology(old.dose() * kept, old.toxin() * kept));
        } else {
            setBiology(event.getEntity(), biology(event.getOriginal()));
        }
    }
}
