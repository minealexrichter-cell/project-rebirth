package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * HUD of a player in an artificial body: an integrity bar in place of the hearts, and no hunger bar (bodies have
 * no hunger). Only the vanilla health and food layers are replaced; other mods' layers stay. A simple bar for now,
 * the final look comes with the custom HUD.
 */
public final class BodyHud {
    private static final int WIDTH = 81;
    private static final int HEIGHT = 9;

    private BodyHud() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(BodyHud::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BodyHud::hideVanillaBars);
    }

    private static boolean inBody(Player player) {
        return player != null && TransferService.isInArtificialBody(player);
    }

    private static void hideVanillaBars(RenderGuiLayerEvent.Pre event) {
        if ((event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) || event.getName().equals(VanillaGuiLayers.FOOD_LEVEL))
                && inBody(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ProjectRebirth.id("body_integrity"), BodyHud::renderIntegrity);
    }

    /** Drawn where the hearts would be, and takes their space so the armor icons and air bubbles stay above it. */
    private static void renderIntegrity(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!inBody(player) || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight;
        minecraft.gui.leftHeight += HEIGHT + 1;

        float max = player.getMaxHealth();
        float fraction = max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
        int color = fraction > 0.5f ? 0xFF4FC3F7 : fraction > 0.25f ? 0xFFFFC107 : 0xFFE53935;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF1B1F24);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF3A414A);
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((WIDTH - 2) * fraction), y + HEIGHT - 1, color);

        Font font = minecraft.font;
        String text = Mth.ceil(player.getHealth()) + " / " + Mth.ceil(max);
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            text += " +" + Mth.ceil(absorption);
        }
        graphics.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y + 1, 0xFFFFFF, true);
    }
}
