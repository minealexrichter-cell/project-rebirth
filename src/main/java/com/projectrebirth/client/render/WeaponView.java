package com.projectrebirth.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * How one weapon looks in the hands of one entity: its animations and the moments of its shots. It only exists on the client
 * and decides nothing: the server sends the shots, and the armor shows aiming and reloading.
 */
public final class WeaponView implements GeoAnimatable {
    /** Length of the reload animations in the model files, in seconds: the speed is set so that they fit the server's time. */
    private static final double MAGAZINE_RELOAD_SECONDS = 2.5;
    private static final double SHELL_RELOAD_SECONDS = 0.8;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.weapon.idle");
    private static final RawAnimation AIM = RawAnimation.begin().thenPlayAndHold("animation.weapon.aim");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.weapon.shot");
    private static final RawAnimation EMPTY = RawAnimation.begin().thenPlay("animation.weapon.empty");
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlayAndHold("animation.weapon.reload");
    private static final RawAnimation RELOAD_SHELLS = RawAnimation.begin().thenLoop("animation.weapon.reload");
    private static final int FLASH_TICKS = 2;
    private static final int KICK_TICKS = 4;
    private static final int TRACKED_POINT_LIFE = 3;

    private static final Map<LivingEntity, Map<Item, WeaponView>> VIEWS = new WeakHashMap<>();
    /** For drawings without anyone holding the weapon. */
    private static final Map<Item, WeaponView> LOOSE = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Item item;
    private final String id;
    private boolean aiming;
    private boolean reloading;
    private float reloadSpeed = 1.0f;
    private float aimAmount;
    private long lastShotAt = -100L;
    private float partialNow;
    private long now;
    private Vec3 muzzle;
    private Vec3 ejection;
    private long pointsAt;

    private WeaponView(Item item) {
        this.item = item;
        this.id = BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static WeaponView of(@Nullable LivingEntity holder, Item item) {
        Map<Item, WeaponView> views = holder == null ? LOOSE : VIEWS.computeIfAbsent(holder, entity -> new HashMap<>());
        return views.computeIfAbsent(item, WeaponView::new);
    }

    public String id() {
        return id;
    }

    /** Reads what the holder does, once per drawing. */
    public void update(@Nullable LivingEntity holder, float partialTick) {
        partialNow = partialTick;
        if (holder == null) {
            return;
        }
        now = holder.level().getGameTime();
        boolean armored = holder.getVehicle() instanceof PowerArmorEntity;
        aiming = armored && ((PowerArmorEntity) holder.getVehicle()).isAimingWeapon();
        reloading = armored && ((PowerArmorEntity) holder.getVehicle()).weaponReloadEnd() > 0L;
        RebirthConfig.WeaponStats stats = Weapons.statsOf(item);
        if (stats != null) {
            double seconds = id.equals("combat_shotgun") ? SHELL_RELOAD_SECONDS : MAGAZINE_RELOAD_SECONDS;
            reloadSpeed = (float) (seconds * 20.0 / stats.reloadTicks().get());
        }
        aimAmount = Mth.approach(aimAmount, aiming ? 1.0f : 0.0f, 0.12f);
    }

    public void onShot(long gameTime) {
        lastShotAt = gameTime;
        trigger("shot");
    }

    public void onEmptyClick() {
        trigger("empty");
    }

    private void trigger(String name) {
        cache.getManagerForId(hashCode()).tryTriggerAnimation("action", name);
    }

    public boolean isFlashing() {
        return now - lastShotAt < FLASH_TICKS;
    }

    /** 0 at the moment of the shot, 1 when the flash is over; above 1 when there is none. */
    public float flashAge() {
        return (now + partialNow - lastShotAt) / FLASH_TICKS;
    }

    /** A number of its own for each shot, so that every flash is turned differently. */
    public long flashSeed() {
        return lastShotAt * 31L + hashCode();
    }

    /** 1 right after a shot, down to 0 over a few ticks. */
    public float kick() {
        float age = now + partialNow - lastShotAt;
        return age >= KICK_TICKS || age < 0.0f ? 0.0f : 1.0f - age / KICK_TICKS;
    }

    /** 0 at the hip, 1 at the shoulder; moves smoothly. */
    public float aimAmount() {
        return aimAmount;
    }

    public boolean isReloading() {
        return reloading;
    }

    public void setTrackedPoints(Vec3 muzzle, Vec3 ejection, long gameTime) {
        this.muzzle = muzzle != null ? muzzle : this.muzzle;
        this.ejection = ejection != null ? ejection : this.ejection;
        this.pointsAt = gameTime;
    }

    @Nullable
    public Vec3 muzzle() {
        return now - pointsAt <= TRACKED_POINT_LIFE ? muzzle : null;
    }

    @Nullable
    public Vec3 ejection() {
        return now - pointsAt <= TRACKED_POINT_LIFE ? ejection : null;
    }

    public long now() {
        return now;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pose", 3, state -> state.setAndContinue(aiming ? AIM : IDLE)));
        controllers.add(new AnimationController<>(this, "reload", 2, state -> {
            state.getController().setAnimationSpeed(reloadSpeed);
            if (!reloading) {
                state.getController().forceAnimationReset();
                return PlayState.STOP;
            }
            return state.setAndContinue(id.equals("combat_shotgun") ? RELOAD_SHELLS : RELOAD);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shot", SHOT).triggerableAnim("empty", EMPTY));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return software.bernie.geckolib.util.RenderUtil.getCurrentTick();
    }
}
