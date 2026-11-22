package com.projectrebirth.garrison;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * 1.8.0 (owner's wish): a machine that walks to its place while it keeps its body turned to what it faces — a small robot backs
 * away from its target, or circles it, its gun on it, instead of turning its back and twisting its head round. With nothing to
 * face it walks as any mob does (it turns to where it goes).
 */
public class FacingMoveControl extends MoveControl {
    private final Supplier<Entity> facing;

    /** @param facing what it keeps turned to now, or null (asked every tick) */
    public FacingMoveControl(Mob mob, Supplier<Entity> facing) {
        super(mob);
        this.facing = facing;
    }

    @Override
    public void tick() {
        @Nullable
        Entity face = facing.get();
        if (face == null || operation != Operation.MOVE_TO) {
            super.tick();
            return;
        }
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
        double flat = dx * dx + dz * dz;
        if (flat + dy * dy < 2.5e-7) {
            mob.setZza(0.0f);
            mob.setXxa(0.0f);
            return;
        }
        // the body to what it faces
        float yaw = (float) (Mth.atan2(face.getZ() - mob.getZ(), face.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90.0f;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 30.0f));
        mob.yBodyRot = mob.getYRot();
        float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        mob.setSpeed(speed);
        if (flat > 1.0e-6) {
            // the way to the place in its own frame: forward and to the side (the inverse of how a mob's input is turned)
            double length = Math.sqrt(flat);
            double ux = dx / length, uz = dz / length;
            double sin = Mth.sin(mob.getYRot() * Mth.DEG_TO_RAD), cos = Mth.cos(mob.getYRot() * Mth.DEG_TO_RAD);
            mob.setXxa((float) ((ux * cos + uz * sin) * speed));
            mob.setZza((float) ((uz * cos - ux * sin) * speed));
        } else {
            mob.setXxa(0.0f);
            mob.setZza(0.0f);
        }
        if (dy > mob.maxUpStep() && flat < Math.max(1.0f, mob.getBbWidth())) {
            mob.getJumpControl().jump();
            operation = Operation.JUMPING;
        }
    }
}
