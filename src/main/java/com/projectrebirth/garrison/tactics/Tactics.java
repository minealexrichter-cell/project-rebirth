package com.projectrebirth.garrison.tactics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ToxicPuddleBlock;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineMover;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.OrderRules;
import com.projectrebirth.garrison.ServicePostBlockEntity;
import com.projectrebirth.garrison.TargetRules;
import com.projectrebirth.hostile.MechRocketEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.8.0, block I: the common tactics of the garrison's combat machines (androids, small robots, mechs, turrets), one logic for
 * all of them; the kinds differ by numbers in the config ([garrison.tactics]) and by which of these parts they use. Server only.
 * <ul>
 * <li>{@link #choose}: the target by priority — whoever hits its people (owner, faction, their machines, work androids), then
 * whoever hits the machine itself, then the one its group called it to, then the most dangerous one near (tag
 * {@code projectrebirth:dangerous_targets}), then the nearest; the rules of targets ({@link TargetRules}) decide first.</li>
 * <li>{@link #clearLine}: no shot while one of its own stands on the line.</li>
 * <li>{@link #lead}: where a moving target will be when a slow shot gets there.</li>
 * <li>{@link #retreating}, {@link #retreatPoint}: below retreatBelow of its integrity it falls back to a service post or behind
 * its own, and comes back from returnAbove.</li>
 * <li>{@link #cover}: a place near it with a solid block between it and the threat.</li>
 * <li>{@link #anchor}, {@link #leashed}: it does not chase farther than the area of its order.</li>
 * <li>{@link #callForHelp}: the machines of its group near come to the same target (focus fire, a switch of the console).</li>
 * <li>{@link #danger}: a hissing creeper, where an enemy rocket comes down, a toxic puddle — it gets out.</li>
 * </ul>
 */
public final class Tactics {
    /** Enemies that fight from afar (a machine with a bow does not step back from them). Data: a tag a datapack changes. */
    public static final TagKey<EntityType<?>> RANGED = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ProjectRebirth.MOD_ID, "ranged_attackers"));
    /** Enemies that come first after those that hit (brutes, mechs, shooters). */
    public static final TagKey<EntityType<?>> DANGEROUS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ProjectRebirth.MOD_ID, "dangerous_targets"));
    /** How long a call for help holds, in ticks. */
    public static final int CALL_TICKS = 200;

    private Tactics() {
    }

    /** Whether the tactics are on ([garrison.tactics] enabled). */
    public static boolean on() {
        return RebirthConfig.TACTICS_ENABLED.get();
    }

    // ------------------------------------------------------------------ whom

    /** The priority of a target, lower first: 0 hits its people, 1 hits it, 2 called to, 3 dangerous, 4 any other. */
    public static int priority(GarrisonMachine machine, LivingEntity target) {
        MachineCore core = machine.machineCore();
        long now = machine.asEntity().level().getGameTime();
        if (core.hitOurs(target, now)) {
            return 0;
        }
        if (core.hitMe(target, now)) {
            return 1;
        }
        if (core.calledTo(target, now)) {
            return 2;
        }
        return target.getType().is(DANGEROUS) ? 3 : 4;
    }

    /**
     * The best target in range it can see that `valid` lets it fight: by priority, then the nearest; with `current` still
     * valid, another only of a better priority.
     */
    @Nullable
    public static LivingEntity choose(Mob self, GarrisonMachine machine, double range, @Nullable LivingEntity current, Predicate<LivingEntity> valid) {
        LivingEntity best = null;
        int bestPriority = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity found : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(range),
                e -> e != self && e.isAlive())) {
            double distance = self.distanceToSqr(found);
            if (distance > range * range) {
                continue;
            }
            int priority = priority(machine, found);
            if (priority > bestPriority || priority == bestPriority && distance >= bestDistance) {
                continue;
            }
            if (!valid.test(found) || !self.hasLineOfSight(found)) {
                continue;
            }
            best = found;
            bestPriority = priority;
            bestDistance = distance;
        }
        if (current != null && current.isAlive() && valid.test(current)) {
            return best != null && bestPriority < priority(machine, current) ? best : current;
        }
        return best;
    }

    /** Whether the target fights up close (not a shooter of the tag). */
    public static boolean melee(LivingEntity target) {
        return !target.getType().is(RANGED);
    }

    /** Whether the target is big (bigWidth and wider). */
    public static boolean big(LivingEntity target) {
        return target.getBbWidth() >= RebirthConfig.TACTICS_BIG_WIDTH.get();
    }

    /** Whether the target holds an axe (it breaks shields). */
    public static boolean axe(LivingEntity target) {
        return target.getMainHandItem().is(ItemTags.AXES);
    }

    // ------------------------------------------------------------------ the line of fire

    /** Whether no one of its own (players, bodies, machines of its owner, faction and allies) stands on the line to the target. */
    public static boolean clearLine(Mob self, GarrisonMachine machine, Vec3 from, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        AABB along = new AABB(from, to).inflate(1.0);
        List<Entity> near = self.level().getEntities(self, along, e -> e != target && e instanceof LivingEntity && e.isAlive()
                && TargetRules.friendly(machine, e));
        for (Entity friend : near) {
            if (friend.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A place a couple of blocks to the side of it, square to the line to the target, from where the line is clear (when one of
     * its own stood on it), or null.
     */
    @Nullable
    public static Vec3 sideStep(Mob self, GarrisonMachine machine, MachineMover mover, LivingEntity target, double eyeUp) {
        Vec3 to = target.position().subtract(self.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) {
            return null;
        }
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        double reach = 2.0 + self.getBbWidth();
        int first = (self.getUUID().getLeastSignificantBits() & 1) == 0 ? 1 : -1;
        for (int sign : new int[] {first, -first}) {
            Vec3 point = self.position().add(side.scale(sign * reach));
            if (mover.clearWay(point) && clearLine(self, machine, point.add(0, eyeUp, 0), target)) {
                return point;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ aim

    /** How a target moves now, in blocks a tick (from where it was last tick: the server knows no speed of a player). */
    public static Vec3 velocity(Entity target) {
        return new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
    }

    /** Where to aim at a moving target so that a shot of that speed (blocks a tick) meets it; its middle when it stands. */
    public static Vec3 lead(Vec3 from, LivingEntity target, double speed) {
        Vec3 at = target.getBoundingBox().getCenter();
        Vec3 velocity = velocity(target);
        if (speed <= 0.0 || velocity.lengthSqr() < 1.0e-4) {
            return at;
        }
        // two rounds are close enough: the time to the target, then to where it will be
        double ticks = from.distanceTo(at) / speed;
        Vec3 ahead = at.add(velocity.multiply(1, 0, 1).scale(ticks));
        ticks = from.distanceTo(ahead) / speed;
        return at.add(velocity.multiply(1, 0, 1).scale(Math.min(ticks, 60.0)));
    }

    /** Where a rocket of that flight time (ticks) should come down to meet a moving target. */
    public static Vec3 leadGround(LivingEntity target, int flightTicks) {
        Vec3 velocity = velocity(target).multiply(1, 0, 1);
        if (velocity.lengthSqr() < 1.0e-4) {
            return target.position();
        }
        return target.position().add(velocity.scale(Math.min(flightTicks, 60)));
    }

    // ------------------------------------------------------------------ falling back

    /** Whether it falls back now: below retreatBelow it starts, from returnAbove it stops; between, it keeps what it did. */
    public static boolean retreating(MachineCore core, boolean wasRetreating) {
        if (!on() || core.maxStructural() <= 0.0f) {
            return false;
        }
        double share = core.structural() / core.maxStructural();
        if (share < RebirthConfig.TACTICS_RETREAT_BELOW.get()) {
            return true;
        }
        return wasRetreating && share < RebirthConfig.TACTICS_RETURN_ABOVE.get();
    }

    /**
     * Where to fall back to: the nearest service post (it repairs there), else behind the nearest of its own from the threat,
     * else straight away from the threat; null when there is nowhere to go.
     */
    @Nullable
    public static Vec3 retreatPoint(Mob self, GarrisonMachine machine, @Nullable LivingEntity threat) {
        if (self.level() instanceof ServerLevel server) {
            Vec3 post = ServicePostBlockEntity.nearest(server, self.blockPosition());
            if (post != null) {
                return post;
            }
        }
        if (threat == null) {
            return null;
        }
        Entity friend = null;
        double nearest = 24.0 * 24.0;
        for (Entity found : self.level().getEntities(self, self.getBoundingBox().inflate(24.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != threat && TargetRules.friendly(machine, e))) {
            double d = found.distanceToSqr(self);
            if (d < nearest && found.distanceToSqr(threat) > self.distanceToSqr(threat) * 0.25) {
                nearest = d;
                friend = found;
            }
        }
        Vec3 from = threat.position();
        if (friend != null) {
            Vec3 away = friend.position().subtract(from).multiply(1, 0, 1);
            return away.lengthSqr() < 1.0e-4 ? friend.position() : friend.position().add(away.normalize().scale(4.0));
        }
        Vec3 away = self.position().subtract(from).multiply(1, 0, 1);
        return away.lengthSqr() < 1.0e-4 ? null : self.position().add(away.normalize().scale(12.0));
    }

    // ------------------------------------------------------------------ cover

    /** Whether a solid block stands between that eye and the threat. */
    public static boolean covered(Mob self, Vec3 eye, LivingEntity threat) {
        return self.level().clip(new ClipContext(eye, threat.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self))
                .getType() != HitResult.Type.MISS;
    }

    /**
     * A place within coverRadius where its body stands free on the ground, it gets there on foot, and a solid block stands
     * between it and the threat; with a spot next to it (within two blocks) from where it sees the threat to fire. Null when there
     * is none. Looked for every couple of seconds at most (a few dozen rays).
     */
    @Nullable
    public static Cover cover(Mob self, MachineMover mover, LivingEntity threat) {
        int radius = RebirthConfig.TACTICS_COVER_RADIUS.get();
        double eye = self.getEyeHeight();
        Cover best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos at = self.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                Vec3 spot = Vec3.atBottomCenterOf(at.offset(dx, 0, dz));
                double d = spot.distanceToSqr(self.position());
                if (d >= bestDistance || spot.distanceToSqr(threat.position()) < self.distanceToSqr(threat) * 0.5) {
                    continue;
                }
                if (!covered(self, spot.add(0, eye, 0), threat) || !mover.clearWay(spot)) {
                    continue;
                }
                Vec3 peek = peek(self, mover, spot, eye, threat);
                if (peek != null) {
                    best = new Cover(spot, peek);
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    @Nullable
    private static Vec3 peek(Mob self, MachineMover mover, Vec3 spot, double eye, LivingEntity threat) {
        Vec3 to = threat.position().subtract(spot).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) {
            return null;
        }
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        for (double step : new double[] {1.0, -1.0, 2.0, -2.0}) {
            Vec3 point = spot.add(side.scale(step));
            if (!covered(self, point.add(0, eye, 0), threat)) {
                return point;
            }
        }
        return null;
    }

    /** A place to hide and the spot next to it to fire from. */
    public record Cover(Vec3 hide, Vec3 peek) {
    }

    // ------------------------------------------------------------------ the area of its order

    /** The middle of the area its order keeps it in (guard point, the nearest point of a patrol, the area patrol's middle, the
     * place it holds), or null for an order without one (escort, stand down). */
    @Nullable
    public static Vec3 anchor(Mob self, MachineTask task, @Nullable BlockPos holdPoint) {
        return switch (task.type()) {
            case GUARD, AREA_PATROL -> task.point().map(Vec3::atBottomCenterOf).orElse(null);
            case HOLD -> holdPoint == null ? null : Vec3.atBottomCenterOf(holdPoint);
            case PATROL -> task.route().stream().map(Vec3::atBottomCenterOf)
                    .min((a, b) -> Double.compare(a.distanceToSqr(self.position()), b.distanceToSqr(self.position()))).orElse(null);
            default -> null;
        };
    }

    /** Whether a place lies outside the area its order keeps it in (that radius round the anchor). */
    public static boolean leashed(@Nullable Vec3 anchor, double radius, Vec3 place) {
        return anchor != null && place.subtract(anchor).horizontalDistanceSqr() > radius * radius;
    }

    // ------------------------------------------------------------------ calling for help

    /**
     * Calls the machines of its group within helpRadius (the same console and group; without a console, of the same owner) to the
     * target, unless its group's rules switch focus fire off. Returns how many it called.
     */
    public static int callForHelp(Mob self, GarrisonMachine machine, LivingEntity target) {
        MachineCore core = machine.machineCore();
        int radius = RebirthConfig.TACTICS_HELP_RADIUS.get();
        if (!on() || radius <= 0 || (core.rules() & OrderRules.NO_FOCUS) != 0 || core.owner() == null) {
            return 0;
        }
        ConsoleBlockEntity console = consoleOf(self, core);
        int group = console == null ? -1 : console.groupOf(self.getUUID());
        long now = self.level().getGameTime();
        int called = 0;
        for (Entity found : self.level().getEntities(self, self.getBoundingBox().inflate(radius), e -> e instanceof GarrisonMachine && e.isAlive())) {
            MachineCore other = ((GarrisonMachine) found).machineCore();
            if (!other.active() || !core.owner().equals(other.owner()) && console == null) {
                continue;
            }
            if (console != null && (!java.util.Objects.equals(core.console(), other.console()) || console.groupOf(found.getUUID()) != group)) {
                continue;
            }
            if (TargetRules.mayAttack((GarrisonMachine) found, target)) {
                other.call(target, now, CALL_TICKS);
                called++;
            }
        }
        return called;
    }

    @Nullable
    private static ConsoleBlockEntity consoleOf(Mob self, MachineCore core) {
        if (core.console() == null || !(self.level() instanceof ServerLevel level) || !core.console().dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos pos = core.console().pos();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ConsoleBlockEntity console ? console : null;
    }

    /** The target it was called to, if that one is still there. */
    @Nullable
    public static LivingEntity calledTarget(Mob self, MachineCore core) {
        UUID id = core.called(self.level().getGameTime());
        if (id == null || !(self.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ------------------------------------------------------------------ getting out of the way

    /**
     * The middle of what it should get away from now, or null: a creeper about to blow within fleeRadius + 1, where an enemy
     * rocket comes down within fleeRadius, a toxic puddle under or next to its feet.
     */
    @Nullable
    public static Vec3 danger(Mob self, GarrisonMachine machine) {
        double radius = RebirthConfig.TACTICS_FLEE_RADIUS.get();
        if (!on() || radius <= 0.0) {
            return null;
        }
        double half = self.getBbWidth() * 0.5;
        for (Entity found : self.level().getEntities(self, self.getBoundingBox().inflate(radius + 2.0 + half),
                e -> e instanceof Creeper || e instanceof MechRocketEntity)) {
            if (found instanceof Creeper creeper && creeper.isAlive() && (creeper.getSwellDir() > 0 || creeper.isIgnited())
                    && creeper.distanceTo(self) <= radius + 1.0 + half) {
                return creeper.position();
            }
            if (found instanceof MechRocketEntity rocket && !friendlyRocket(machine, rocket)
                    && rocket.end().subtract(self.position()).horizontalDistance() <= radius + half) {
                return rocket.end();
            }
        }
        // rockets in flight are high over the ground: those coming down near it, farther out
        for (MechRocketEntity rocket : self.level().getEntitiesOfClass(MechRocketEntity.class, self.getBoundingBox().inflate(48.0),
                r -> !friendlyRocket(machine, r))) {
            if (rocket.end().subtract(self.position()).horizontalDistance() <= radius + half) {
                return rocket.end();
            }
        }
        BlockPos feet = self.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
            if (self.level().getBlockState(pos).getBlock() instanceof ToxicPuddleBlock) {
                return Vec3.atCenterOf(pos);
            }
        }
        return null;
    }

    private static boolean friendlyRocket(GarrisonMachine machine, MechRocketEntity rocket) {
        UUID owner = rocket.owner();
        if (owner == null) {
            return false;
        }
        Entity shooter = rocket.level() instanceof ServerLevel level ? level.getEntity(owner) : null;
        return shooter != null && (shooter == machine.asEntity() || TargetRules.friendly(machine, shooter));
    }

    /** A place out of the danger that it gets to on foot (straight away, or turned up to 90 degrees either way), or null. */
    @Nullable
    public static Vec3 fleePoint(Mob self, MachineMover mover, Vec3 danger) {
        Vec3 away = self.position().subtract(danger).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize();
        double distance = RebirthConfig.TACTICS_FLEE_RADIUS.get() + 2.0 + self.getBbWidth();
        for (double turn : new double[] {0.0, 45.0, -45.0, 90.0, -90.0}) {
            double rad = turn * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(away.x * Math.cos(rad) - away.z * Math.sin(rad), 0, away.x * Math.sin(rad) + away.z * Math.cos(rad));
            Vec3 point = danger.multiply(1, 0, 1).add(0, self.getY(), 0).add(dir.scale(distance));
            if (point.subtract(danger).horizontalDistance() > self.position().subtract(danger).horizontalDistance() && mover.clearWay(point)) {
                return point;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ small helpers

    /** A point on the circle of that radius round the target, at that angle (radians, round the Y axis). */
    public static Vec3 onCircle(LivingEntity target, double radius, double angle) {
        return new Vec3(target.getX() + Math.cos(angle) * radius, target.getY(), target.getZ() + Math.sin(angle) * radius);
    }

    /** The angle (radians) of the place seen from the target. */
    public static double angleFrom(LivingEntity target, Vec3 place) {
        return Math.atan2(place.z - target.getZ(), place.x - target.getX());
    }

    /** Whether one of the enemies that fight up close is nearer than that. */
    public static Optional<LivingEntity> meleeWithin(Mob self, GarrisonMachine machine, double distance) {
        return self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(distance),
                e -> e != self && e.isAlive() && melee(e) && e.distanceToSqr(self) <= distance * distance && TargetRules.mayAttack(machine, e)
                        && e instanceof Mob mob && mob.getTarget() == self).stream().findFirst();
    }

    /** Whether a projectile (an arrow, a spit) flies at it and is near. */
    public static boolean incoming(Mob self) {
        for (Projectile shot : self.level().getEntitiesOfClass(Projectile.class, self.getBoundingBox().inflate(6.0), p -> p.isAlive() && !p.onGround())) {
            Vec3 motion = shot.getDeltaMovement();
            if (motion.lengthSqr() < 0.04 || shot.getOwner() == self) {
                continue;
            }
            Vec3 to = self.getBoundingBox().getCenter().subtract(shot.position());
            if (motion.normalize().dot(to.normalize()) > 0.9) {
                return true;
            }
        }
        return false;
    }
}
