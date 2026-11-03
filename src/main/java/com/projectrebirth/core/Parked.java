package com.projectrebirth.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared rules for an artificial body or a power armor that nobody controls: it stands like a solid object.
 * Players and mobs collide with it and can stand on it; nothing pushes, throws or turns it, but it still falls.
 * The entities call these from their overrides while they are parked.
 */
public final class Parked {
    private Parked() {
    }

    /** Only its own movement (falling) is allowed: pistons, shulkers and shulker boxes do not move it. */
    public static boolean allowsMove(MoverType type) {
        return type == MoverType.SELF;
    }

    /**
     * Drops every push it got since the last tick (knockback, explosions, fishing rods, bubble columns) and keeps
     * only falling. Called right before the entity moves.
     */
    public static void keepOnlyFalling(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
    }

    /** Keeps the body and head facing where the entity itself faces. */
    public static void holdRotation(LivingEntity entity) {
        entity.yBodyRot = entity.getYRot();
        entity.yBodyRotO = entity.getYRot();
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
    }
}
