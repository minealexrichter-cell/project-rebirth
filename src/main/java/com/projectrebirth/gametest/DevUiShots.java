package com.projectrebirth.gametest;

import java.nio.file.Files;
import java.nio.file.Path;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development only (the gametest package is not in the jar), 1.8.0: screenshots of windows on request, without touching
 * the game window. Write a name into {@code <game dir>/ui_shot.txt}; on the next tick the client saves the frame as
 * {@code screenshots/<name>.png} and deletes the request. Used for the window gallery (docs/screenshots/ui/).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID, value = Dist.CLIENT)
public final class DevUiShots {
    private DevUiShots() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getGameTime() % 10 != 0) {
            return;
        }
        Path request = minecraft.gameDirectory.toPath().resolve("ui_shot.txt");
        if (!Files.exists(request)) {
            return;
        }
        try {
            String name = Files.readString(request).trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
            Files.delete(request);
            minecraft.execute(() -> Screenshot.grab(minecraft.gameDirectory, name.isEmpty() ? "ui" : name + ".png",
                    minecraft.getMainRenderTarget(), message -> ProjectRebirth.LOGGER.info("UI shot: {}", message.getString())));
        } catch (Exception e) {
            ProjectRebirth.LOGGER.warn("UI shot failed", e);
        }
    }
}
