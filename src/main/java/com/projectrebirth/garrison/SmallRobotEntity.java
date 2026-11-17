package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The small robot (1.7.1): the cheap soldier of the heavy dock. A biped as tall as the patrol robot (block H) with a rotary
 * machine gun built into its right arm, on the guard mech's logic (orders, the rules of targets, energy and magazine, packing) with its own numbers
 * ([garrison.small_robot]) and integrity ([garrison.machines.robot]). It fires assault rifle rounds, loaded by hand or at a service
 * post, and charges at a service post or a charging pad, whichever is nearer. Its gun cannot be swapped.
 */
public class SmallRobotEntity extends GuardMechEntity {
    @Nullable
    private static Stats robotStats;

    public SmallRobotEntity(EntityType<? extends SmallRobotEntity> type, Level level) {
        super(type, level, MachineKind.ROBOT);
        setNode(RIGHT, Node.GUN);
        setNode(LEFT, Node.NONE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected Stats stats() {
        if (robotStats == null) {
            robotStats = new Stats(RebirthConfig.SMALL_ROBOT_ENABLED, RebirthConfig.SMALL_ROBOT_SPEED, RebirthConfig.SMALL_ROBOT_ENERGY_CAPACITY,
                    RebirthConfig.SMALL_ROBOT_ENERGY_IDLE, RebirthConfig.SMALL_ROBOT_ENERGY_MOVING, RebirthConfig.SMALL_ROBOT_ENERGY_SHOT,
                    RebirthConfig.SMALL_ROBOT_ROUNDS, RebirthConfig.SMALL_ROBOT_ROCKETS, RebirthConfig.SMALL_ROBOT_RANGE, RebirthConfig.SMALL_ROBOT_DAMAGE,
                    RebirthConfig.SMALL_ROBOT_SPREAD, RebirthConfig.SMALL_ROBOT_BURST, RebirthConfig.SMALL_ROBOT_SHOT_TICKS,
                    RebirthConfig.SMALL_ROBOT_PAUSE_TICKS, RebirthConfig.SMALL_ROBOT_ROCKET_TICKS, RebirthConfig.SMALL_ROBOT_LOW_ENERGY,
                    RebirthConfig.SMALL_ROBOT_CHARGED, RebirthConfig.SMALL_ROBOT_GUARD_RADIUS);
        }
        return robotStats;
    }

    @Override
    protected Item packedItem() {
        return GarrisonRegistry.SMALL_ROBOT_ITEM.get();
    }

    @Override
    public void readPacked(ItemStack stack) {
        super.readPacked(stack);
        // its gun is built in, whatever the item says
        setNode(RIGHT, Node.GUN);
        setNode(LEFT, Node.NONE);
    }

    @Override
    protected boolean swapsNodes() {
        return false;
    }

    @Override
    public boolean chargesOnPads() {
        return true;
    }

    @Override
    @Nullable
    protected Vec3 chargePoint(ServerLevel server) {
        Vec3 post = ServicePostBlockEntity.nearest(server, blockPosition());
        BlockPos pad = AndroidEntity.nearestPad(server, blockPosition());
        if (pad == null) {
            return post;
        }
        Vec3 onPad = Vec3.atBottomCenterOf(pad).add(0.0, 0.2, 0.0);
        return post != null && post.distanceToSqr(position()) < onPad.distanceToSqr(position()) ? post : onPad;
    }

    @Override
    protected String animationPrefix() {
        return "animation.small_robot";
    }

    /** Its gun: the end of the barrels in the right forearm, at the height of its waist (bone right_gun_muzzle). */
    @Override
    public Vec3 muzzle(int side) {
        double yaw = getYHeadRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        return position().add(0.0, 1.03, 0.0).add(right.scale(0.5)).add(forward.scale(0.96));
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.35f, 1.5f);
    }
}
