package com.projectrebirth.client;

import java.util.Locale;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.EnvironmentStatusPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The client side of the environment: it only shows what the server sends. For now a dosimeter line; the full panel of the
 * HUD comes with block F.
 */
public final class EnvironmentClient {
    private static final int MARGIN = 6;
    private static EnvironmentStatusPayload last;

    private EnvironmentClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EnvironmentClient::registerColors);
        modEventBus.addListener(EnvironmentClient::registerGuiLayers);
        EnvironmentStatusPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> last = payload);
    }

    /** The suits are tinted like leather armor: their colour is part of the item. */
    private static void registerColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -1)) : -1,
                EnvironmentRegistry.TINTED_GEAR.stream().map(item -> item.get()).toArray(net.minecraft.world.level.ItemLike[]::new));
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("dosimeter"), EnvironmentClient::renderDosimeter);
    }

    private static boolean carriesDosimeter(Player player) {
        return player.getInventory().contains(stack -> stack.is(EnvironmentRegistry.DOSIMETER.get()));
    }

    /** Left of the hotbar: the radiation now and the dose. Without a dosimeter nobody knows them. */
    private static void renderDosimeter(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || last == null || minecraft.options.hideGui || !HudLayout.enabled() || !carriesDosimeter(player)) {
            return;
        }
        Font font = minecraft.font;
        Component radiation = Component.translatable("hud.projectrebirth.dosimeter.radiation", String.format(Locale.ROOT, "%.2f", last.radiation()));
        Component dose = Component.translatable("hud.projectrebirth.dosimeter.dose", String.format(Locale.ROOT, "%.1f", last.dose()));
        int right = graphics.guiWidth() / 2 - 91 - MARGIN;
        int y = graphics.guiHeight() - 2 * font.lineHeight - 2;
        graphics.drawString(font, radiation, right - font.width(radiation), y, last.radiation() > 5.0f ? 0xFFFF5555 : 0xFFB8F27A);
        graphics.drawString(font, dose, right - font.width(dose), y + font.lineHeight + 1, 0xFFDDDDDD);
    }
}
