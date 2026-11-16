package com.projectrebirth.hostile;

import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The guns of the hostile mechanisms (1.6.0, part B3): one bullet is a ray with spread from the muzzle toward a target, to the
 * first block or body in its way; it hits like the mod's weapons (damage type bullet; a pilot is hit through the armor). The
 * mechanisms do not hurt each other. Everyone who sees the shooter gets the tracer, flash and sparks.
 */
public final class Gunnery {
    private Gunnery() {
    }

    /** Whether a player is fair game: alive, not in creative and not spectating. */
    public static boolean fair(Entity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** The nearest fair player within range that the mob can see, or null. */
    public static Player nearestVisiblePlayer(Mob mob, double range) {
        return mob.level().getNearestPlayer(mob.getX(), mob.getEyeY(), mob.getZ(), range, e -> fair(e) && mob.hasLineOfSight(e));
    }

    /** Fires one bullet from {@code start} at the target with the given spread; returns whether it hit a body. */
    public static boolean fire(Mob shooter, Vec3 start, LivingEntity target, double spreadDegrees, float damage, double range, float pitch) {
        Level level = shooter.level();
        Vec3 aim = target.getEyePosition().subtract(0.0, 0.3, 0.0).subtract(start).normalize();
        Vec3 direction = scatter(aim, spreadDegrees, shooter.getRandom());
        Vec3 end = start.add(direction.scale(range));
        int ending = 0;
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
            ending = 1;
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, start, end, shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0),
                e -> e != shooter && e.isPickable() && !e.isSpectator() && !(e.getVehicle() instanceof PowerArmorEntity) && !isMechanism(e), 0.1f);
        if (hit != null) {
            end = com.projectrebirth.weapons.Weapons.entryPoint(hit.getEntity(), start, end);
            ending = 2;
            Entity victim = hit.getEntity();
            DamageSource source = shooter.damageSources().source(WeaponRegistry.BULLET, shooter);
            victim.invulnerableTime = 0;
            victim.hurt(source, damage);
            victim.invulnerableTime = 0;
        }
        level.playSound(null, start.x, start.y, start.z, WeaponRegistry.sounds("assault_rifle").shot().get(), SoundSource.HOSTILE, 1.4f,
                pitch + shooter.getRandom().nextFloat() * 0.1f);
        if (level instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(shooter, new TurretShotPayload(shooter.getId(), (float) start.x, (float) start.y, (float) start.z,
                    (float) end.x, (float) end.y, (float) end.z, ending));
        }
        return ending == 2;
    }

    /** The hostile mechanisms, which do not shoot each other. */
    public static boolean isMechanism(Entity entity) {
        return entity instanceof GuardTurretEntity || entity instanceof PatrolRobotEntity || entity instanceof MechEntity;
    }

    public static Vec3 scatter(Vec3 direction, double degrees, RandomSource random) {
        if (degrees <= 0.0) {
            return direction;
        }
        double spread = Math.tan(degrees * Mth.DEG_TO_RAD);
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(direction).normalize();
        double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * spread;
        return direction.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).normalize();
    }
}
