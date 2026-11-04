package com.projectrebirth.weapons;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The rules of the guns, all on the server: a client only reports keys. Only the pilot of a working power armor can shoot,
 * aim or reload, and this is checked at every action. Everyone else can just carry a weapon around.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Weapons {
    /** How much wider than its box an entity is for a bullet; a little forgiveness for the delay of the network. */
    private static final float HIT_MARGIN = 0.1f;
    /** Ticks before an empty weapon can click again, however fast the trigger is pulled. */
    private static final int EMPTY_CLICK_INTERVAL = 5;

    /** What one fired shot did. */
    public record Shot(int pellets, int hits, float damage) {
    }

    /** The weapon a player holds with everything known about it. */
    private record Held(ItemStack stack, WeaponDefinition definition, RebirthConfig.WeaponStats stats) {
    }

    private Weapons() {
    }

    public static Optional<WeaponDefinition> definition(RegistryAccess access, Item item) {
        return access.registryOrThrow(WeaponRegistry.WEAPONS).getOptional(ResourceKey.create(WeaponRegistry.WEAPONS, BuiltInRegistries.ITEM.getKey(item)));
    }

    /** The config section of a weapon, or null if the item has none. */
    @Nullable
    public static RebirthConfig.WeaponStats statsOf(Item item) {
        return RebirthConfig.weapon(BuiltInRegistries.ITEM.getKey(item).getPath());
    }

    /** How fast a pilot's armor walks compared to normal; lower while aiming. Runs on both sides, since the client moves the armor. */
    public static float aimSpeedMultiplier(Player pilot) {
        RebirthConfig.WeaponStats stats = statsOf(pilot.getMainHandItem().getItem());
        return stats != null && holdsWeapon(pilot) && pilot.getVehicle() instanceof PowerArmorEntity armor && armor.isAimingWeapon()
                ? stats.aimSpeedMultiplier().get().floatValue() : 1.0f;
    }

    public static int ammoIn(ItemStack stack) {
        return stack.getOrDefault(WeaponRegistry.MAGAZINE.get(), 0);
    }

    public static void setAmmo(ItemStack stack, int rounds) {
        stack.set(WeaponRegistry.MAGAZINE.get(), rounds);
    }

    /** Works on both sides; the client only uses it to know what the keys mean. */
    public static boolean isPilotOfWorkingArmor(Player player) {
        return player.isAlive() && player.getVehicle() instanceof PowerArmorEntity armor && armor.isActive() && armor.getPilot() == player;
    }

    public static boolean holdsWeapon(Player player) {
        return player.getMainHandItem().getItem() instanceof WeaponItem;
    }

    /** The weapon in the main hand if this player may use it now. */
    @Nullable
    private static Held usable(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WeaponItem) || !isPilotOfWorkingArmor(player)) {
            return null;
        }
        RebirthConfig.WeaponStats stats = statsOf(stack.getItem());
        Optional<WeaponDefinition> definition = definition(player.registryAccess(), stack.getItem());
        return stats == null || definition.isEmpty() ? null : new Held(stack, definition.get(), stats);
    }

    private static WeaponState state(ServerPlayer player) {
        return player.getData(WeaponRegistry.STATE);
    }

    /** @return the shot if this press fired one, or null */
    @Nullable
    public static Shot pressTrigger(ServerPlayer player) {
        Held held = usable(player);
        if (held == null) {
            return null;
        }
        WeaponState state = state(player);
        state.triggerHeld = true;
        state.clickedEmpty = false;
        return fire(player, held);
    }

    public static void releaseTrigger(ServerPlayer player) {
        WeaponState state = state(player);
        state.triggerHeld = false;
        state.clickedEmpty = false;
    }

    /** @return whether the player is aiming now */
    public static boolean setAiming(ServerPlayer player, boolean aiming) {
        boolean allowed = aiming && usable(player) != null;
        state(player).aiming = allowed;
        showPose(player);
        return allowed;
    }

    public static boolean isAiming(ServerPlayer player) {
        return state(player).aiming;
    }

    /** Tells everyone what the pilot does with the weapon, for the HUD, the slower walk and the poses. */
    private static void showPose(ServerPlayer player) {
        if (player.getVehicle() instanceof PowerArmorEntity armor) {
            WeaponState state = state(player);
            armor.setWeaponPose(state.aiming, state.isReloading() ? state.reloadEndTime : 0L);
        }
    }

    private static void play(ServerPlayer player, String weapon, Function<WeaponRegistry.WeaponSounds, java.util.function.Supplier<SoundEvent>> which) {
        WeaponRegistry.WeaponSounds sounds = WeaponRegistry.sounds(weapon);
        if (sounds != null) {
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), which.apply(sounds).get(), SoundSource.PLAYERS,
                    1.0f, 0.97f + player.getRandom().nextFloat() * 0.06f);
        }
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static boolean isReloading(ServerPlayer player) {
        return state(player).isReloading();
    }

    /** @return whether a reload has started */
    public static boolean startReload(ServerPlayer player) {
        Held held = usable(player);
        WeaponState state = state(player);
        if (held == null || state.isReloading() || ammoIn(held.stack()) >= held.stats().magazine().get()) {
            return false;
        }
        if (player.getInventory().countItem(held.definition().ammo().value()) <= 0) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.weapon.no_ammo"), true);
            return false;
        }
        state.reloadSlot = player.getInventory().selected;
        state.reloadEndTime = player.level().getGameTime() + held.stats().reloadTicks().get();
        showPose(player);
        play(player, idOf(held.stack()), WeaponRegistry.WeaponSounds::reload);
        return true;
    }

    @Nullable
    private static Shot fire(ServerPlayer player, Held held) {
        WeaponState state = state(player);
        long now = player.level().getGameTime();
        int rounds = ammoIn(held.stack());
        if (state.isReloading() || now < state.nextShotTime) {
            return null;
        }
        if (rounds <= 0) {
            if (!state.clickedEmpty) {
                state.clickedEmpty = true;
                state.nextShotTime = now + EMPTY_CLICK_INTERVAL;
                play(player, idOf(held.stack()), WeaponRegistry.WeaponSounds::empty);
            }
            return null;
        }
        setAmmo(held.stack(), rounds - 1);
        state.nextShotTime = now + held.stats().shotIntervalTicks().get();
        play(player, idOf(held.stack()), WeaponRegistry.WeaponSounds::shot);
        Shot shot = shoot(player, held.stats(), state);
        state.extraSpread = Math.min(state.extraSpread + held.stats().spreadPerShot().get().floatValue(), held.stats().maxExtraSpread().get().floatValue());
        return shot;
    }

    /** All pellets of a shot that hit the same target land as one hit; otherwise its moment of invulnerability would swallow the rest. */
    private static Shot shoot(ServerPlayer player, RebirthConfig.WeaponStats stats, WeaponState state) {
        double spread = (stats.spreadDegrees().get() + state.extraSpread) * (state.aiming ? stats.aimSpreadMultiplier().get() : 1.0);
        int pellets = stats.pellets().get();
        Map<Entity, Float> damageByTarget = new LinkedHashMap<>();
        List<WeaponShotPayload.Pellet> effects = new ArrayList<>(pellets);
        Vec3 start = player.getEyePosition();
        for (int i = 0; i < pellets; i++) {
            Vec3 direction = scatter(player.getLookAngle(), spread, player.getRandom());
            WeaponShotPayload.Pellet pellet = trace(player, start, direction, stats.range().get(), damageByTarget, stats);
            effects.add(pellet);
        }
        DamageSource source = player.damageSources().source(WeaponRegistry.BULLET, player);
        float total = 0.0f;
        for (Map.Entry<Entity, Float> entry : damageByTarget.entrySet()) {
            Entity target = entry.getKey();
            // The rate of fire is the limit for bullets, not the time a target stays invulnerable after a hit; and a bullet leaves none behind.
            target.invulnerableTime = 0;
            target.hurt(source, entry.getValue());
            target.invulnerableTime = 0;
            total += entry.getValue();
        }
        // Test players have no real client behind them.
        if (player.connection.hasChannel(WeaponShotPayload.TYPE)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new WeaponShotPayload(player.getId(), effects));
        }
        return new Shot(pellets, damageByTarget.size(), total);
    }

    /** Follows one bullet to the first target or block on its way and notes the damage it does. */
    private static WeaponShotPayload.Pellet trace(ServerPlayer player, Vec3 start, Vec3 direction, double range, Map<Entity, Float> damageByTarget,
                                                  RebirthConfig.WeaponStats stats) {
        Level level = player.level();
        Vec3 end = start.add(direction.scale(range));
        WeaponShotPayload.Ending ending = WeaponShotPayload.Ending.NOTHING;
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
            ending = WeaponShotPayload.Ending.BLOCK;
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0),
                target -> canBeHit(player, target), HIT_MARGIN);
        if (hit != null) {
            end = hit.getLocation();
            ending = WeaponShotPayload.Ending.ENTITY;
            damageByTarget.merge(hit.getEntity(), damageAt(stats, start.distanceTo(end)), Float::sum);
        }
        return new WeaponShotPayload.Pellet((float) end.x, (float) end.y, (float) end.z, ending);
    }

    /** Not the shooter's own armor. A pilot is hit through their armor, whose box surrounds them. */
    private static boolean canBeHit(ServerPlayer shooter, Entity target) {
        return target.isPickable() && !target.isSpectator() && target != shooter.getVehicle() && !(target.getVehicle() instanceof PowerArmorEntity);
    }

    /** A direction at most {@code degrees} away from the given one. */
    private static Vec3 scatter(Vec3 direction, double degrees, RandomSource random) {
        if (degrees <= 0.0) {
            return direction;
        }
        Vec3 side = Math.abs(direction.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : direction.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 up = side.cross(direction).normalize();
        double angle = random.nextDouble() * Mth.TWO_PI;
        // The square root spreads the pellets evenly over the circle instead of crowding them in its middle.
        double offset = Math.tan(Math.toRadians(degrees)) * Math.sqrt(random.nextDouble());
        return direction.add(side.scale(Math.cos(angle) * offset)).add(up.scale(Math.sin(angle) * offset)).normalize();
    }

    /** The damage of one bullet or pellet at a distance: full up to the start of the falloff, then less and less up to the range. */
    public static float damageAt(RebirthConfig.WeaponStats stats, double distance) {
        double start = stats.falloffStart().get();
        double range = stats.range().get();
        double progress = range <= start ? 0.0 : Mth.clamp((distance - start) / (range - start), 0.0, 1.0);
        return (float) (stats.damage().get() * Mth.lerp(progress, 1.0, stats.damageAtRange().get()));
    }

    /** Keeps a held trigger firing, runs the reload and lets go of everything that is no longer allowed. Public for tests (mock players do not tick). */
    public static void tick(ServerPlayer player) {
        if (!player.hasData(WeaponRegistry.STATE)) {
            return;
        }
        WeaponState state = state(player);
        if (state.isIdle()) {
            return;
        }
        Held held = usable(player);
        if (held == null) {
            state.triggerHeld = false;
            state.aiming = false;
            state.stopReload();
            state.extraSpread = 0.0f;
            showPose(player);
            return;
        }
        if (state.isReloading()) {
            tickReload(player, held, state);
        }
        if (state.triggerHeld && held.definition().fireMode() == WeaponDefinition.FireMode.AUTOMATIC) {
            fire(player, held);
        } else if (state.extraSpread > 0.0f) {
            state.extraSpread = Math.max(0.0f, state.extraSpread - held.stats().spreadRecoveryPerTick().get().floatValue());
        }
    }

    private static void tickReload(ServerPlayer player, Held held, WeaponState state) {
        if (state.reloadSlot != player.getInventory().selected) {
            // Rounds move only when the time is up, so an interrupted reload loses nothing.
            state.stopReload();
            showPose(player);
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.reloadEndTime) {
            return;
        }
        Item ammo = held.definition().ammo().value();
        int magazine = held.stats().magazine().get();
        int rounds = ammoIn(held.stack());
        if (held.definition().reload() == WeaponDefinition.ReloadMode.MAGAZINE) {
            setAmmo(held.stack(), rounds + takeFromInventory(player, ammo, magazine - rounds));
            state.stopReload();
            showPose(player);
            return;
        }
        rounds += takeFromInventory(player, ammo, Math.min(1, magazine - rounds));
        setAmmo(held.stack(), rounds);
        if (rounds < magazine && player.getInventory().countItem(ammo) > 0) {
            state.reloadEndTime = now + held.stats().reloadTicks().get();
            play(player, idOf(held.stack()), WeaponRegistry.WeaponSounds::reload);
        } else {
            state.stopReload();
        }
        showPose(player);
    }

    /** @return how many were really taken */
    private static int takeFromInventory(ServerPlayer player, Item item, int wanted) {
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < wanted; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int part = Math.min(wanted - taken, stack.getCount());
                stack.shrink(part);
                taken += part;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** A weapon takes both hands. */
    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.OFF_HAND && holdsWeapon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.OFF_HAND && holdsWeapon(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
