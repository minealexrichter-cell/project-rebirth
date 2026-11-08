package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.power.BodyPower;
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
    private static final int THRUST_HEIGHT = 5;
    private static final int THRUST_COLOR = 0xFF7FE8FF;
    private static final int CHARGE_HEIGHT = 4;
    private static final int HEAT_HEIGHT = 3;
    private static final int WEAR_HEIGHT = 3;

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
        boolean jetpack = Jetpack.equipped(player);
        boolean charge = BodyPower.enabled();
        boolean heat = com.projectrebirth.bodies.power.BodyHeat.enabled();
        boolean wear = com.projectrebirth.bodies.power.BodyWear.enabled() && com.projectrebirth.bodies.power.BodyWear.wear(player) > 0.0f;
        minecraft.gui.leftHeight += height + 1 + (jetpack ? Math.round((THRUST_HEIGHT + 1) * scale) : 0)
                + (charge ? Math.round((CHARGE_HEIGHT + 1) * scale) : 0) + (heat ? Math.round((HEAT_HEIGHT + 1) * scale) : 0)
                + (wear ? Math.round((WEAR_HEIGHT + 1) * scale) : 0);
        graphics.pose().pushPose();
        graphics.pose().translate(HudLayout.barX(graphics.guiWidth() / 2 - 91, graphics.guiWidth()),
                HudLayout.barBottom(bottom, graphics.guiHeight()), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int x = 0;
        int y = -HEIGHT;

        float max = player.getMaxHealth();
        float fraction = max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
        int color = fraction > 0.5f ? 0xFF4FC3F7 : fraction > 0.25f ? 0xFFFFC107 : 0xFFE53935;
        GuiAtlas.bar(graphics, x, y, WIDTH, HEIGHT, Interference.jitter(fraction), color);

        int above = y;
        if (wear) {
            // wear: a thin brown bar right above integrity, red at critical wear (temporary, block G)
            float worn = com.projectrebirth.bodies.power.BodyWear.wear(player);
            above -= WEAR_HEIGHT + 1;
            GuiAtlas.bar(graphics, x, above, WIDTH, WEAR_HEIGHT, worn / com.projectrebirth.bodies.power.BodyWear.MAX, worn >= com.projectrebirth.bodies.power.BodyWear.MAX ? 0xFFE53935 : 0xFFA1887F);
        }
        if (charge) {
            // charge: yellow, orange when low, red and blinking in the emergency mode (1.5.0; the full HUD comes in block G)
            BodyPower.State state = BodyPower.state(player);
            int chargeColor = state == BodyPower.State.NORMAL ? 0xFFFFD54F : state == BodyPower.State.LOW ? 0xFFFF8F00
                    : (player.tickCount / 10 % 2 == 0 ? 0xFFE53935 : 0xFF7F1D1D);
            above -= CHARGE_HEIGHT + 1;
            GuiAtlas.bar(graphics, x, above, WIDTH, CHARGE_HEIGHT, state == BodyPower.State.EMPTY ? 1.0f : BodyPower.fraction(player), chargeColor);
        }
        if (heat) {
            // heat: grey-blue when cool, orange from the warning level, red and blinking while throttled (temporary, block G)
            com.projectrebirth.bodies.power.BodyHeat.State heatState = com.projectrebirth.bodies.power.BodyHeat.state(player);
            int heatColor = switch (heatState) {
                case NORMAL -> 0xFF90A4AE;
                case WARM -> 0xFFFF9800;
                default -> player.tickCount / 10 % 2 == 0 ? 0xFFFF3D00 : 0xFF8D1C00;
            };
            above -= HEAT_HEIGHT + 1;
            GuiAtlas.bar(graphics, x, above, WIDTH, HEAT_HEIGHT, com.projectrebirth.bodies.power.BodyHeat.heat(player) / com.projectrebirth.bodies.power.BodyHeat.MAX, heatColor);
        }
        if (jetpack) {
            GuiAtlas.bar(graphics, x, above - THRUST_HEIGHT - 1, WIDTH, THRUST_HEIGHT, Jetpack.reserve(player), THRUST_COLOR);
        }
        if (BodyGadgets.has(player.level().registryAccess(), BodyGadgets.of(player), BodyGadgets.SEALING)) {
            GuiAtlas.sealIcon(graphics, x + WIDTH + 1, y);
        }

        Font font = minecraft.font;
        String text = Mth.ceil(player.getHealth()) + " / " + Mth.ceil(max);
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            text += " +" + Mth.ceil(absorption);
        }
        text = Interference.scramble(text);
        graphics.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y + 1, 0xFFFFFF, true);
        graphics.pose().popPose();
    }
}
