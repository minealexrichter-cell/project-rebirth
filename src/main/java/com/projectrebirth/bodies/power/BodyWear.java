package com.projectrebirth.bodies.power;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Wear of artificial bodies: a second scale beside integrity, 0 to 100 percent, the tiredness of the mechanisms. Integrity
 * is lost to physical damage and mends (self-repair, repair kit, maintenance station); wear grows with work, overheating,
 * acid (and from later blocks dust and electromagnetic faults), never mends by itself and goes away only at the maintenance
 * station. It never destroys a body and never takes control away; it makes the body worse in steps:
 * 25% more charge used, 50% slower with half the self-repair and faster heating, 75% weaker blows (and more faults in an
 * electromagnetic storm), 100% critical: half the maximum integrity, much slower, no self-repair, "needs maintenance".
 * Kept like the charge: in the standing body, in the player's data while controlled, on the packed item.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyWear {
    public static final float MAX = 100.0f;

    private static final ResourceLocation SPEED_MODIFIER = ProjectRebirth.id("body_wear_speed");
    private static final ResourceLocation ATTACK_MODIFIER = ProjectRebirth.id("body_wear_attack");
    private static final ResourceLocation HEALTH_MODIFIER = ProjectRebirth.id("body_wear_health");

    private static final Map<UUID, Integer> LAST_STAGE = new HashMap<>();

    private BodyWear() {
    }

    public static boolean enabled() {
        return RebirthConfig.BODY_WEAR_ENABLED.get();
    }

    // ---------------------------------------------------------------- the scale

    public static float wear(Player player) {
        return TransferService.isInArtificialBody(player) ? Mth.clamp(player.getData(BodyPowerRegistry.CONTROLLED_WEAR), 0.0f, MAX) : 0.0f;
    }

    public static void setWear(Player player, float wear) {
        player.setData(BodyPowerRegistry.CONTROLLED_WEAR, Mth.clamp(wear, 0.0f, MAX));
    }

    /** Wear of a controlled body or a standing one; 0 for anything else. */
    public static float wearOf(LivingEntity entity) {
        if (entity instanceof Player player) {
            return wear(player);
        }
        return entity instanceof ArtificialBodyEntity body ? body.wear() : 0.0f;
    }

    /**
     * Adds wear (percent, already per class) to a controlled or standing body. Nothing with the system off, for a human,
     * in creative or as a spectator. Returns what was added.
     */
    public static float add(LivingEntity entity, float amount) {
        if (!enabled() || amount <= 0.0f || entity instanceof Player player && (player.getAbilities().instabuild || player.isSpectator())) {
            return 0.0f;
        }
        float before = wearOf(entity);
        if (entity instanceof Player player && TransferService.isInArtificialBody(player)) {
            setWear(player, before + amount);
            return wear(player) - before;
        }
        if (entity instanceof ArtificialBodyEntity body) {
            body.setWear(before + amount);
            return body.wear() - before;
        }
        return 0.0f;
    }

    public static double classMultiplier(BodyClass bodyClass) {
        return RebirthConfig.bodyStats(bodyClass).wearMultiplier().get();
    }

    /** 0 below 25%, 1 from 25%, 2 from 50%, 3 from 75%, 4 at 100% (critical). */
    public static int stage(float wear) {
        if (!enabled()) {
            return 0;
        }
        return wear >= MAX ? 4 : wear >= 75.0f ? 3 : wear >= 50.0f ? 2 : wear >= 25.0f ? 1 : 0;
    }

    public static int stage(LivingEntity entity) {
        return stage(wearOf(entity));
    }

    public static boolean isCritical(LivingEntity entity) {
        return stage(entity) == 4;
    }

    // ---------------------------------------------------------------- what wear does to the other systems

    /** Charge use is multiplied by this. */
    public static double energyMultiplier(Player player) {
        return stage(player) >= 1 ? 1.0 + RebirthConfig.WEAR_ENERGY_AT_25.get() : 1.0;
    }

    /** Self-repair is multiplied by this (0 = none). */
    public static double selfRepairMultiplier(LivingEntity entity) {
        int stage = stage(entity);
        return stage >= 4 ? 0.0 : stage >= 2 ? RebirthConfig.WEAR_SELF_REPAIR_AT_50.get() : 1.0;
    }

    /** Heating is multiplied by this. */
    public static double heatMultiplier(LivingEntity entity) {
        return stage(entity) >= 2 ? RebirthConfig.WEAR_HEAT_AT_50.get() : 1.0;
    }

    /** Electromagnetic faults are this many times as frequent (used by the storm of block E). */
    public static double faultMultiplier(LivingEntity entity) {
        return stage(entity) >= 3 ? RebirthConfig.WEAR_FAULTS_AT_75.get() : 1.0;
    }

    // ---------------------------------------------------------------- once a second

    /** Work and overheating of one second of a controlled body, then the limits. Called after the heat. Public for tests. */
    public static void tickSecond(ServerPlayer player) {
        TransferService.state(player).body().ifPresent(bodyClass -> {
            double perSecond = RebirthConfig.WEAR_WORK.get();
            if (BodyHeat.isOverheated(player)) {
                perSecond += RebirthConfig.WEAR_OVERHEAT.get();
            }
            add(player, (float) (perSecond * classMultiplier(bodyClass)));
        });
        applyLimits(player);
    }

    /** Slowdown, weaker blows and the lower maximum integrity of the current stage; a message when the stage goes up. */
    public static void applyLimits(Player player) {
        int stage = stage(player);
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER,
                stage >= 4 ? -RebirthConfig.WEAR_SLOWDOWN_AT_100.get() : stage >= 2 ? -RebirthConfig.WEAR_SLOWDOWN_AT_50.get() : 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, stage >= 3 ? -RebirthConfig.WEAR_ATTACK_AT_75.get() : 0.0);
        setModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER, stage >= 4 ? -RebirthConfig.WEAR_MAX_HEALTH_AT_100.get() : 0.0);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Integer last = LAST_STAGE.put(player.getUUID(), stage);
            if (last != null && stage > last) {
                serverPlayer.displayClientMessage(stage == 4
                        ? Component.translatable("message.projectrebirth.body_wear.critical").withStyle(ChatFormatting.RED)
                        : Component.translatable("message.projectrebirth.body_wear.stage", stage * 25).withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /** A standing body: only its lower maximum integrity at critical wear. */
    public static void applyLimits(ArtificialBodyEntity body) {
        setModifier(body, Attributes.MAX_HEALTH, HEALTH_MODIFIER, isCritical(body) ? -RebirthConfig.WEAR_MAX_HEALTH_AT_100.get() : 0.0);
        if (body.getHealth() > body.getMaxHealth()) {
            body.setHealth(body.getMaxHealth());
        }
    }

    public static void removeLimits(Player player) {
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, 0.0);
        setModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, 0.0);
        setModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER, 0.0);
        LAST_STAGE.remove(player.getUUID());
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0.0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** For {@link BodyRules}: whether self-repair runs at all, given the wear. */
    public static boolean blocksSelfRepair(LivingEntity entity) {
        return selfRepairMultiplier(entity) <= 0.0;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_STAGE.remove(event.getEntity().getUUID());
    }
}
