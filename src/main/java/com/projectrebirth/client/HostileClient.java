package com.projectrebirth.client;

import com.projectrebirth.client.render.TracerParticle;
import com.projectrebirth.hostile.TurretShotPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** The client side of the hostile mechanisms (1.6.0, part B3): the flash, tracer and sparks of a turret's shot. */
public final class HostileClient {
    private static final int TRACER_COLOR = 0xFFC48A;
    private static final float TRACER_WIDTH = 0.04f;

    private HostileClient() {
    }

    public static void init() {
        TurretShotPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> onShot(payload));
    }

    private static void onShot(TurretShotPayload shot) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        com.projectrebirth.client.render.MuzzleFlash.onShot(shot.shooter(), level.getGameTime());
        Vec3 from = new Vec3(shot.sx(), shot.sy(), shot.sz()), to = new Vec3(shot.ex(), shot.ey(), shot.ez());
        Vec3 dir = to.subtract(from).normalize();
        level.addParticle(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, dir.x * 0.08, dir.y * 0.08, dir.z * 0.08);
        level.addParticle(ParticleTypes.SMOKE, from.x, from.y, from.z, dir.x * 0.05, dir.y * 0.05 + 0.02, dir.z * 0.05);
        minecraft.particleEngine.add(new TracerParticle(level, from, to, TRACER_WIDTH, 3, TRACER_COLOR));
        if (shot.ending() == 1) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, (level.random.nextDouble() - 0.5) * 0.3, level.random.nextDouble() * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.3);
            }
            BlockPos hit = BlockPos.containing(to);
            if (!level.getBlockState(hit).isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit)), to.x, to.y, to.z, 0.0, 0.05, 0.0);
            }
        } else if (shot.ending() == 2) {
            level.addParticle(ParticleTypes.CRIT, to.x, to.y, to.z, 0.0, 0.1, 0.0);
        }
    }
}
