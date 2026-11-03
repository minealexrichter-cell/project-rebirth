package com.projectrebirth.client;

import com.projectrebirth.bodies.TransferService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A player in a robot body does not sound like a person: the vanilla footsteps, hurt and death sounds of that
 * player are muted here, and the server plays the robot's own sounds instead (see BodyEvents). Synthetic bodies keep
 * the ordinary sounds.
 */
final class BodySounds {
    /** Sounds of a player are played exactly at the player's position. */
    private static final double SAME_PLACE_SQR = 0.01;

    private BodySounds() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(BodySounds::onPlaySound);
    }

    private static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft minecraft = Minecraft.getInstance();
        if (sound == null || minecraft.level == null || sound.getSource() != SoundSource.PLAYERS || !isHumanSound(sound.getLocation())) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            boolean robot = TransferService.state(player).body().map(bodyClass -> !bodyClass.synthetic()).orElse(false);
            if (robot && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < SAME_PLACE_SQR) {
                event.setSound(null);
                return;
            }
        }
    }

    private static boolean isHumanSound(ResourceLocation location) {
        if (!location.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            return false;
        }
        String path = location.getPath();
        return path.endsWith(".step") || path.startsWith("entity.player.hurt") || path.equals("entity.player.death");
    }
}
