package com.projectrebirth.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projectrebirth.weapons.WeaponInputPayload;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Turns the keys of a pilot holding a weapon into reports to the server, which decides what they do. */
public final class WeaponClient {
    /** Not R: that returns the player to their own body. */
    private static final KeyMapping RELOAD_KEY = new KeyMapping("key.projectrebirth.weapon_reload",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.projectrebirth");

    private static boolean triggerDown;
    private static boolean aimDown;

    private WeaponClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(WeaponClient::registerKeys);
        // Before PowerArmorClient hears the attack key: a pilot with a weapon shoots instead of punching.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WeaponClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(WeaponClient::onClientTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RELOAD_KEY);
    }

    private static boolean readyToShoot(Player player) {
        return player != null && Weapons.holdsWeapon(player) && Weapons.isPilotOfWorkingArmor(player);
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !Weapons.holdsWeapon(player)) {
            return;
        }
        boolean takenByWeapon = readyToShoot(player) && (event.isAttack() || event.isUseItem());
        boolean secondHand = event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND;
        if (takenByWeapon || secondHand) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean ready = readyToShoot(player) && minecraft.screen == null;
        boolean trigger = ready && minecraft.options.keyAttack.isDown();
        boolean aim = ready && minecraft.options.keyUse.isDown();
        if (player != null && trigger != triggerDown) {
            send(trigger ? WeaponInputPayload.Input.TRIGGER_DOWN : WeaponInputPayload.Input.TRIGGER_UP);
        }
        if (player != null && aim != aimDown) {
            send(aim ? WeaponInputPayload.Input.AIM_DOWN : WeaponInputPayload.Input.AIM_UP);
        }
        triggerDown = player != null && trigger;
        aimDown = player != null && aim;
        while (RELOAD_KEY.consumeClick()) {
            if (ready) {
                send(WeaponInputPayload.Input.RELOAD);
            }
        }
    }

    private static void send(WeaponInputPayload.Input input) {
        PacketDistributor.sendToServer(new WeaponInputPayload(input));
    }
}
