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
 * A command (without "/") written into {@code <game dir>/ui_cmd.txt} runs as if typed in the chat: a client command such as
 * {@code rbdev ui check}, or else a server command.
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
        Path command = minecraft.gameDirectory.toPath().resolve("ui_cmd.txt");
        if (Files.exists(command)) {
            try {
                String text = Files.readString(command).trim();
                Files.delete(command);
                if (text.equals("hud_editor")) {
                    minecraft.setScreen(new com.projectrebirth.client.hud.HudEditorScreen());
                    return;
                }
                if (text.equals("close")) {
                    if (minecraft.player != null) {
                        minecraft.player.closeContainer();
                    }
                    minecraft.setScreen(null);
                    return;
                }
                // 1.8.0, block K: the frame time of the HUD and the console's map (no frame limit, no vsync; the frames of the
                // last second; the HUD hidden or shown as with F1)
                if (text.equals("perf")) {
                    minecraft.options.framerateLimit().set(260);
                    minecraft.options.enableVsync().set(false);
                    minecraft.getWindow().updateVsync(false);
                    return;
                }
                if (text.startsWith("fps")) {
                    ProjectRebirth.LOGGER.info("UI fps {}: {} ({})", text.substring(3).trim(), minecraft.getFps(), minecraft.fpsString);
                    return;
                }
                if (text.startsWith("hidegui ")) {
                    minecraft.options.hideGui = Boolean.parseBoolean(text.substring(8).trim());
                    return;
                }
                if (text.startsWith("click ") && minecraft.screen != null) {
                    // a left click on the open window at GUI coordinates (x y), as the mouse would give it
                    String[] at = text.split(" ");
                    double x = Double.parseDouble(at[1]), y = Double.parseDouble(at[2]);
                    minecraft.screen.mouseClicked(x, y, 0);
                    minecraft.screen.mouseReleased(x, y, 0);
                    ProjectRebirth.LOGGER.info("UI click at {} {} (window {}x{}, scale {})", x, y, minecraft.getWindow().getGuiScaledWidth(),
                            minecraft.getWindow().getGuiScaledHeight(), minecraft.getWindow().getGuiScale());
                    return;
                }
                if (text.equals("respawn")) {
                    if (minecraft.player != null && minecraft.player.isDeadOrDying()) {
                        minecraft.player.respawn();
                        minecraft.setScreen(null);
                    }
                    return;
                }
                if (!net.neoforged.neoforge.client.ClientCommandHandler.runCommand(text) && minecraft.getConnection() != null) {
                    minecraft.getConnection().sendCommand(text);
                }
            } catch (Exception e) {
                ProjectRebirth.LOGGER.warn("UI command failed", e);
            }
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
