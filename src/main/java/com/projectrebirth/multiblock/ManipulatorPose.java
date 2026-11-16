package com.projectrebirth.multiblock;

import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.powerarmor.PowerArmorItem;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Where a manipulator's arm is (1.7.0, block B): the turret turns toward the machine under assembly and the arm bends and
 * stretches its telescopic forearm so that the tool's tip touches the machine. The welder runs its torch over the side facing
 * it; the screwdriver drives screws at points one after another; the gripper fetches a part of the current stage from beside
 * itself and sets it against the machine. A pure function of the time and the controller's state, the same on every client
 * and in every frame; the renderer draws it, the block's client ticker makes the sparks and sounds at the tip.
 *
 * <p>The sizes are those of the models (build_models.js, the manipulator specs), in pixels: the shoulder at 9 above the floor of
 * the block, the upper arm 12 long, the forearm 11.5 when drawn in, with three stages sliding out up to 9 each; the tool's tip
 * {@link ManipulatorBlock.Tool#reach()} in front of the wrist.
 */
public final class ManipulatorPose {
    public static final float SHOULDER = 9.0f;
    public static final float UPPER = 12.0f;
    public static final float FOREARM = 11.5f;
    public static final float STAGE = 9.0f;
    private static final float MAX_FOREARM = FOREARM + 3 * STAGE;
    private static final double WELD_SECONDS = 1.0;
    private static final double SCREW_CYCLE = 2.5;
    private static final double GRIP_CYCLE = 6.0;
    private static final double RELEASE_AT = 0.62;

    private ManipulatorPose() {
    }

    /**
     * One pose. Angles in radians, for the bones: {@code yaw} of the turret, {@code shoulder}, {@code elbow} and {@code wrist}
     * about X (positive lifts the part forward), {@code extension} each telescopic stage slides out (pixels), {@code fingers}
     * how far the gripper's fingers open, {@code spin} the screwdriver bit. {@code tip} in the world; {@code touching} the tool
     * is on the machine; {@code carrying} a part is in the gripper; {@code released} the gripper let go this tick.
     */
    public record Pose(boolean working, float yaw, float shoulder, float elbow, float wrist, float extension, float fingers, float spin,
                       Vec3 tip, boolean touching, boolean carrying, boolean released) {
    }

    private record Machine(Vec3 center, Vec3 away, Vec3 side, double halfWidth, double height) {
        Vec3 surface(double lateral, double up) {
            return center.add(away.scale(halfWidth)).add(side.scale(lateral)).add(0.0, up, 0.0);
        }
    }

    public static Pose compute(ManipulatorBlockEntity manipulator, float partialTick) {
        Level level = manipulator.getLevel();
        BlockPos pos = manipulator.getBlockPos();
        double phase = Math.floorMod(pos.hashCode(), 997) / 997.0 * 20.0;
        double t = (level == null ? 0.0 : level.getGameTime() + partialTick) / 20.0 + phase;
        Machine machine = level == null ? null : machine(manipulator, level);
        ManipulatorBlock.Tool tool = manipulator.tool();
        if (machine == null) {
            return rest(manipulator, t);
        }
        return switch (tool) {
            case WELDER -> weld(manipulator, machine, t);
            case SCREWDRIVER -> screw(manipulator, machine, t);
            case GRIPPER -> grip(manipulator, machine, t);
        };
    }

    /** The machine this arm works on, or null when it is not working. */
    private static Machine machine(ManipulatorBlockEntity manipulator, Level level) {
        BlockPos at = manipulator.controller();
        if (at == null || !manipulator.getBlockState().getValue(TechSounds.WORKING)
                || !(level.getBlockEntity(at) instanceof MultiblockControllerBlockEntity controller)) {
            return null;
        }
        ItemStack result = controller.previewResult();
        MultiblockSchema schema = controller.schema();
        if (result.isEmpty() || controller.previewStage() < 0 || schema == null) {
            return null;
        }
        EntityDimensions size = dimensions(result);
        Vec3 center = schema.displayPoint(at, controller.facing());
        Vec3 base = Vec3.atBottomCenterOf(manipulator.getBlockPos());
        Vec3 away = new Vec3(base.x - center.x, 0.0, base.z - center.z);
        away = away.lengthSqr() < 1.0e-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
        // only the parts built so far: the higher stages bring out the upper body
        double visible = size.height() * Math.min(1.0, 0.35 + 0.17 * Math.max(1, controller.previewStage()));
        return new Machine(center, away, new Vec3(-away.z, 0.0, away.x), size.width() * 0.5, visible);
    }

    private static EntityDimensions dimensions(ItemStack result) {
        if (result.getItem() instanceof ArtificialBodyItem body) {
            return BodyEntities.type(body.bodyClass()).getDimensions();
        }
        if (result.getItem() instanceof PowerArmorItem) {
            return PowerArmorRegistry.POWER_ARMOR.get().getDimensions();
        }
        return EntityDimensions.fixed(0.8f, 1.6f);
    }

    /** A point that stays put for one cycle of the screwdriver or the gripper: its place on the machine. */
    private static double random(long cycle, int salt) {
        long h = cycle * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static Vec3 spot(Machine machine, long cycle) {
        return machine.surface((random(cycle, 1) - 0.5) * 1.3 * machine.halfWidth, machine.height * (0.2 + 0.7 * random(cycle, 2)));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double u) {
        double s = u * u * (3.0 - 2.0 * u);
        return from.add(to.subtract(from).scale(s));
    }

    private static Pose weld(ManipulatorBlockEntity manipulator, Machine machine, double t) {
        double lateral = Math.sin(t * 0.8) * 0.6 * machine.halfWidth + 0.04 * Math.sin(t * 7.0);
        double up = machine.height * (0.25 + 0.6 * (0.5 + 0.5 * Math.sin(t * 0.27 / WELD_SECONDS)));
        return solve(manipulator, machine.surface(lateral, up), true, 0.0f, 0.0f, true, false, false);
    }

    private static Pose screw(ManipulatorBlockEntity manipulator, Machine machine, double t) {
        long cycle = (long) Math.floor(t / SCREW_CYCLE);
        double u = t / SCREW_CYCLE - cycle;
        Vec3 point = spot(machine, cycle);
        Vec3 back = point.add(machine.away.scale(0.3));
        Vec3 target;
        boolean touching = false;
        float spin = 0.0f;
        if (u < 0.2) {
            target = lerp(spot(machine, cycle - 1).add(machine.away.scale(0.3)), back, u / 0.2);
        } else if (u < 0.32) {
            target = lerp(back, point, (u - 0.2) / 0.12);
        } else if (u < 0.8) {
            // the screw goes in: the bit spins and the tip sinks a little
            target = point.subtract(machine.away.scale(0.04 * (u - 0.32) / 0.48));
            touching = true;
            spin = (float) (t * 30.0);
        } else {
            target = lerp(point, back, (u - 0.8) / 0.2);
        }
        return solve(manipulator, target, true, 0.0f, spin, touching, false, false);
    }

    private static boolean carrying(double u) {
        return u > 0.08 && u < RELEASE_AT;
    }

    private static Pose grip(ManipulatorBlockEntity manipulator, Machine machine, double t) {
        long cycle = (long) Math.floor(t / GRIP_CYCLE);
        double u = t / GRIP_CYCLE - cycle;
        Vec3 base = Vec3.atBottomCenterOf(manipulator.getBlockPos());
        // the feeder: low beside the arm's own front, where the parts come up from the hall's stores
        Vec3 tray = base.subtract(machine.away.scale(0.6)).add(machine.side.scale(0.3)).add(0.0, 0.35, 0.0);
        Vec3 point = spot(machine, cycle);
        Vec3 target;
        float open;
        if (u < 0.12) {
            target = tray;
            open = (float) Mth.lerp(Math.min(1.0, u / 0.08), 0.5, 0.05);
        } else if (u < 0.4) {
            target = lerp(tray, point.add(machine.away.scale(0.05)), (u - 0.12) / 0.28);
            open = 0.05f;
        } else if (u < 0.6) {
            target = point;
            open = 0.05f;
        } else if (u < 0.66) {
            target = point.add(machine.away.scale(0.05 * (u - 0.6) / 0.06));
            open = (float) Mth.lerp((u - 0.6) / 0.06, 0.05, 0.5);
        } else if (u < 0.92) {
            target = lerp(point.add(machine.away.scale(0.05)), tray, (u - 0.66) / 0.26);
            open = 0.5f;
        } else {
            target = tray;
            open = 0.5f;
        }
        double before = u - 1.0 / 20.0 / GRIP_CYCLE;
        boolean released = carrying(before < 0 ? before + 1.0 : before) && !carrying(u);
        return solve(manipulator, target, true, open, 0.0f, u >= 0.4 && u < 0.6, carrying(u), released);
    }

    /** At rest: the arm folded up, the turret slowly looking about. */
    private static Pose rest(ManipulatorBlockEntity manipulator, double t) {
        float yaw = (float) (Math.sin(t * 0.3) * 0.25);
        float a1 = (float) Math.toRadians(78.0), a2 = (float) Math.toRadians(-20.0);
        float shoulder = a1 - Mth.HALF_PI;
        Vec3 tip = tipOf(manipulator, yaw, a1, a2, FOREARM, manipulator.tool().reach());
        return new Pose(false, yaw, shoulder, a2 - shoulder, -a2, 0.0f, 0.3f, 0.0f, tip, false, false, false);
    }

    /** The turret's angle for a direction in the world, in the model's frame (GeckoLib turns the model by the block's facing). */
    private static double[] local(BlockPos pos, Direction facing, double dx, double dz) {
        double degrees = switch (facing) {
            case WEST -> 90.0;
            case SOUTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
        double r = Math.toRadians(-degrees);
        return new double[]{dx * Math.cos(r) + dz * Math.sin(r), -dx * Math.sin(r) + dz * Math.cos(r)};
    }

    private static Vec3 tipOf(ManipulatorBlockEntity manipulator, float yaw, float a1, float a2, float forearm, float tool) {
        BlockPos pos = manipulator.getBlockPos();
        Direction facing = manipulator.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        // the turret's forward in the world: the facing turned by the yaw toward the model's -X
        double degrees = switch (facing) {
            case WEST -> 90.0;
            case SOUTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
        double r = Math.toRadians(degrees) + yaw;
        double fx = -Math.sin(r), fz = -Math.cos(r);
        double reach = (UPPER * Math.cos(a1) + forearm * Math.cos(a2) + tool) / 16.0;
        double rise = (SHOULDER + UPPER * Math.sin(a1) + forearm * Math.sin(a2)) / 16.0;
        return new Vec3(pos.getX() + 0.5 + fx * reach, pos.getY() + rise, pos.getZ() + 0.5 + fz * reach);
    }

    /** Bends the arm so that the tool's tip, held level, reaches the target (as far as the arm stretches). */
    private static Pose solve(ManipulatorBlockEntity manipulator, Vec3 target, boolean working, float fingers, float spin, boolean touching,
                              boolean carrying, boolean released) {
        BlockPos pos = manipulator.getBlockPos();
        Direction facing = manipulator.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        float tool = manipulator.tool().reach();
        double[] l = local(pos, facing, target.x - (pos.getX() + 0.5), target.z - (pos.getZ() + 0.5));
        float yaw = (float) Math.atan2(-l[0], -l[1]);
        double wx = Math.hypot(l[0], l[1]) * 16.0 - tool;
        double wy = (target.y - pos.getY()) * 16.0 - SHOULDER;
        double d = Math.max(0.01, Math.hypot(wx, wy));
        double forearm = Mth.clamp(Math.sqrt(Math.max(d * d - UPPER * UPPER, 0.0)), FOREARM, MAX_FOREARM);
        double alpha = d >= UPPER + forearm ? 0.0
                : Math.acos(Mth.clamp((UPPER * UPPER + d * d - forearm * forearm) / (2.0 * UPPER * d), -1.0, 1.0));
        double a1 = Math.atan2(wy, wx) + alpha;
        double ex = UPPER * Math.cos(a1), ey = UPPER * Math.sin(a1);
        double a2 = Math.atan2(wy - ey, wx - ex);
        float shoulder = (float) (a1 - Math.PI / 2.0);
        Vec3 tip = tipOf(manipulator, yaw, (float) a1, (float) a2, (float) forearm, tool);
        return new Pose(working, yaw, shoulder, (float) (a2 - shoulder), (float) -a2, (float) ((forearm - FOREARM) / 3.0), fingers, spin,
                tip, touching, carrying, released);
    }
}
