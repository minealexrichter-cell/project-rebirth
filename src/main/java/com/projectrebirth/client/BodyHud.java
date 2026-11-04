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
 * no hunger). Only the vanilla health and food layers are replaced; other mods' layers stay. With the HUD switched
 * off in the settings the hearts come back; the hunger bar stays hidden, since a body has no hunger.
 */
public final class BodyHud {
    private static final int WIDTH = HudLayout.BAR_WIDTH;
    private static final int HEIGHT = HudLayout.BAR_HEIGHT;

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
        boolean hidden = event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                || event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) && HudLayout.enabled();
        if (hidden && inBody(Minecraft.getInstance().player)) {
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
        if (!HudLayout.enabled() || !inBody(player) || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        float scale = HudLayout.scale();
        int height = Math.round(HEIGHT * scale);
        int bottom = graphics.guiHeight() - minecraft.gui.leftHeight + HEIGHT;
        minecraft.gui.leftHeight += height + 1;
        graphics.pose().pushPose();
        graphics.pose().translate(HudLayout.barX(graphics.guiWidth() / 2 - 91, graphics.guiWidth()),
                HudLayout.barBottom(bottom, graphics.guiHeight()), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int x = 0;
        int y = -HEIGHT;

        float max = player.getMaxHealth();
        float fraction = max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
        int color = fraction > 0.5f ? 0xFF4FC3F7 : fraction > 0.25f ? 0xFFFFC107 : 0xFFE53935;
        GuiAtlas.bar(graphics, x, y, WIDTH, HEIGHT, fraction, color);

        Font font = minecraft.font;
        String text = Mth.ceil(player.getHealth()) + " / " + Mth.ceil(max);
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            text += " +" + Mth.ceil(absorption);
        }
        graphics.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y + 1, 0xFFFFFF, true);
        graphics.pose().popPose();
    }
}
