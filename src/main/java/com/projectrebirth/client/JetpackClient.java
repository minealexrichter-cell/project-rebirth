package com.projectrebirth.client;

import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client's side of the jetpack: lifts the body while the jump key is held in the air, up to the height limit. */
public final class JetpackClient {
    private static final double ACCELERATION = 0.12;
    private static boolean sentWanted;
    /** An empty reserve stays off until the key is released, so that holding it on the ground is plain jumping. */
    private static boolean waitingForRelease;

    private JetpackClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(JetpackClient::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean wanted = player != null && Jetpack.equipped(player) && player.input.jumping && !waitingForRelease && Jetpack.inFlightConditions(player)
                && !com.projectrebirth.bodies.power.BodyPower.isEmpty(player) && !com.projectrebirth.bodies.power.BodyHeat.isThrottled(player);
        if (wanted != sentWanted) {
            sentWanted = wanted;
            PacketDistributor.sendToServer(new Jetpack.ThrustPayload(wanted));
        }
        if (player == null || !player.input.jumping) {
            waitingForRelease = false;
        } else if (Jetpack.reserve(player) <= 0.0f) {
            waitingForRelease = true;
        }
        if (wanted && !waitingForRelease) {
            lift(player);
        }
    }

    private static void lift(LocalPlayer player) {
        int limit = RebirthConfig.JETPACK_MAX_HEIGHT.get();
        double height = Jetpack.heightAboveSurface(player.level(), player.position(), limit);
        double rise = RebirthConfig.JETPACK_RISE_SPEED.get();
        Vec3 motion = player.getDeltaMovement();
        // At the limit it hovers; above it, it only slows the fall, so the body comes back down.
        double vertical = height < limit ? Math.min(rise, motion.y + ACCELERATION) : Math.min(0.0, Math.max(motion.y, -0.08));
        player.setDeltaMovement(motion.x, vertical, motion.z);
        player.fallDistance = 0.0f;
    }
}
