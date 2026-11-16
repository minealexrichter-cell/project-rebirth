package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A mutant of the contaminated lands (1.6.0, part B3c): the mutated hound, the glowing ghoul and the brute. One shared body:
 * it wanders, goes for players and fights in melee; each kind sets its numbers from the config ([hostile.mutants]) and its
 * own touches. They do not spawn by the vanilla rules: {@link MutantSpawner} brings them to contaminated places.
 */
public abstract class MutantEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle, walk, attack;
    private final String name;
    private boolean configured;

    protected MutantEntity(EntityType<? extends Monster> type, Level level, String name) {
        super(type, level);
        this.name = name;
        this.idle = RawAnimation.begin().thenLoop("animation." + name + ".idle");
        this.walk = RawAnimation.begin().thenLoop("animation." + name + ".walk");
        this.attack = RawAnimation.begin().thenPlay("animation." + name + ".attack");
    }

    /** The kind's own one-shot animations besides "attack", played with triggerAnim("action", name). */
    protected java.util.List<String> extraAnimations() {
        return java.util.List.of();
    }

    /** Health, damage and speed from the config. */
    protected abstract void applyConfig();

    /** How fast it runs at its target, times its speed. */
    protected double chaseSpeed() {
        return 1.0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, chaseSpeed(), false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static boolean enabled() {
        return !RebirthConfig.SERVER_SPEC.isLoaded() || (RebirthConfig.HOSTILE_ENABLED.get() && RebirthConfig.MUTANTS_ENABLED.get());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!configured) {
            configured = true;
            boolean fresh = getHealth() >= getMaxHealth();
            if (RebirthConfig.SERVER_SPEC.isLoaded()) {
                applyConfig();
            }
            if (fresh) {
                setHealth(getMaxHealth());
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "attack");
        return super.doHurtTarget(target);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(state.isMoving() ? walk : idle)));
        AnimationController<MutantEntity> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        action.triggerableAnim("attack", attack);
        for (String extra : extraAnimations()) {
            String id = "animation." + name + "." + extra;
            action.triggerableAnim(extra, extra.equals("charge") ? RawAnimation.begin().thenPlayXTimes(id, 4) : RawAnimation.begin().thenPlay(id));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
