package com.projectrebirth.bodies;

import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Class-dependent rules of artificial bodies, the same for a body a player controls and a body standing in the
 * world: what it may eat and drink, status effects, healing and self-repair. Which class does what comes from
 * the config ({@link RebirthConfig.BodyStats}), so there is no per-class code.
 */
public final class BodyRules {
    /** What a body with {@code canConsume} may eat and drink: golden apples and potions by default. */
    public static final TagKey<Item> CONSUMABLES = TagKey.create(Registries.ITEM, ProjectRebirth.id("body_consumables"));

    /** True while this mod itself heals a body or gives it an effect, so the class limits do not block it. */
    private static boolean ownAction;

    private BodyRules() {
    }

    /** The class of the artificial body this entity is: a player controlling one, or a body standing in the world. */
    public static Optional<BodyClass> bodyClassOf(Entity entity) {
        if (entity instanceof Player player) {
            return TransferService.state(player).body();
        }
        if (entity instanceof ArtificialBodyEntity body) {
            return Optional.of(body.getBodyClass());
        }
        return Optional.empty();
    }

    /** Food, and anything that is eaten or drunk (potions, milk, honey, ominous bottles). */
    public static boolean isConsumable(ItemStack stack) {
        UseAnim animation = stack.getUseAnimation();
        return stack.has(DataComponents.FOOD) || animation == UseAnim.EAT || animation == UseAnim.DRINK;
    }

    public static boolean mayConsume(BodyClass bodyClass, ItemStack stack) {
        return RebirthConfig.bodyStats(bodyClass).canConsume().get() && stack.is(CONSUMABLES);
    }

    /** Whether a status effect from outside may apply to this entity. */
    public static boolean allowsEffect(Entity entity) {
        return ownAction || !bodyClassOf(entity).map(bodyClass -> RebirthConfig.bodyStats(bodyClass).immuneToEffects().get()).orElse(false);
    }

    /**
     * Bodies that cannot consume anything heal only through this mod (self-repair, repair kit). This also stops
     * vanilla healing that ignores food, such as the regeneration of the peaceful difficulty.
     */
    public static boolean blocksHealing(Entity entity) {
        return !ownAction && bodyClassOf(entity).map(bodyClass -> !RebirthConfig.bodyStats(bodyClass).canConsume().get()).orElse(false);
    }

    public static void repair(LivingEntity entity, float amount) {
        ownAction = true;
        try {
            entity.heal(amount);
        } finally {
            ownAction = false;
        }
    }

    /** An effect given by this mod itself (for example protection after an emergency return); class immunity does not block it. */
    public static void addOwnEffect(LivingEntity entity, MobEffectInstance effect) {
        ownAction = true;
        try {
            entity.addEffect(effect);
        } finally {
            ownAction = false;
        }
    }

    public static void onDamaged(LivingEntity entity) {
        entity.setData(BodyAttachments.LAST_DAMAGE, entity.level().getGameTime());
    }

    /** Called once per second on the server for a controlled or standing body. */
    public static void tickSelfRepair(LivingEntity entity, BodyClass bodyClass) {
        RebirthConfig.BodyStats stats = RebirthConfig.bodyStats(bodyClass);
        double perSecond = stats.selfRepairPerSecond().get();
        if (perSecond <= 0.0 || !entity.isAlive() || entity.getHealth() >= entity.getMaxHealth()) {
            return;
        }
        // no self-repair without charge; a controlled body pays for it, a standing one only needs some charge left
        if (entity instanceof Player player ? BodyPower.isEmpty(player) : entity instanceof ArtificialBodyEntity body && BodyPower.isEmpty(body)) {
            return;
        }
        double wearMultiplier = com.projectrebirth.bodies.power.BodyWear.selfRepairMultiplier(entity);
        if (wearMultiplier <= 0.0) {
            return;
        }
        long sinceDamage = entity.level().getGameTime() - entity.getData(BodyAttachments.LAST_DAMAGE);
        if (sinceDamage >= stats.selfRepairDelaySeconds().get() * 20L) {
            float before = entity.getHealth();
            repair(entity, (float) (perSecond * wearMultiplier));
            if (entity instanceof Player player) {
                BodyPower.spend(player, (entity.getHealth() - before) * RebirthConfig.BODY_ENERGY_SELF_REPAIR.get());
            }
        }
    }
}
