package com.projectrebirth.client;

import java.util.EnumMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.ReturnToHumanPayload;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.client.render.ArtificialBodyRenderer;
import com.projectrebirth.client.render.BodyArmRenderer;
import com.projectrebirth.client.render.HumanShellRenderer;
import com.projectrebirth.client.render.PossessedPlayerRenderer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only entry point. NeoForge never loads this class on a dedicated server,
 * so rendering, models, key bindings and HUD are registered from here.
 */
@Mod(value = ProjectRebirth.MOD_ID, dist = Dist.CLIENT)
public class ProjectRebirthClient {
    private static final KeyMapping RETURN_KEY = new KeyMapping("key.projectrebirth.return_to_human",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.projectrebirth");

    private final Map<BodyClass, PossessedPlayerRenderer> possessedRenderers = new EnumMap<>(BodyClass.class);
    private BodyArmRenderer armRenderer;

    public ProjectRebirthClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::createPossessedRenderers);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(this::onRenderArm);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        ProjectRebirth.LOGGER.info("Project: Rebirth client setup complete");
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BodyClass bodyClass : BodyClass.values()) {
            event.registerEntityRenderer(BodyEntities.type(bodyClass), context -> new ArtificialBodyRenderer(context, bodyClass));
        }
        event.registerEntityRenderer(BodyEntities.HUMAN_SHELL.get(), HumanShellRenderer::new);
    }

    /** Fired on every resource reload, with a fresh renderer context. */
    private void createPossessedRenderers(EntityRenderersEvent.AddLayers event) {
        for (BodyClass bodyClass : BodyClass.values()) {
            possessedRenderers.put(bodyClass, new PossessedPlayerRenderer(event.getContext(), bodyClass));
        }
        armRenderer = new BodyArmRenderer(event.getContext());
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RETURN_KEY);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("body_status"), this::renderBodyStatus);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        while (RETURN_KEY.consumeClick()) {
            if (player != null && TransferService.isInArtificialBody(player)) {
                PacketDistributor.sendToServer(ReturnToHumanPayload.INSTANCE);
            }
        }
    }

    private void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        TransferService.state(player).body().ifPresent(bodyClass -> {
            PossessedPlayerRenderer renderer = possessedRenderers.get(bodyClass);
            if (renderer != null) {
                event.setCanceled(true);
                renderer.render(player, player.getViewYRot(event.getPartialTick()), event.getPartialTick(),
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            }
        });
    }

    /** First-person hand: draw the arm of the controlled body instead of the player's own arm. */
    private void onRenderArm(RenderArmEvent event) {
        if (armRenderer == null) {
            return;
        }
        TransferService.state(event.getPlayer()).body().ifPresent(bodyClass -> {
            event.setCanceled(true);
            armRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    event.getPlayer(), event.getArm(), bodyClass);
        });
    }

    /** Small line in the top-left corner: which body is controlled and how to leave it. */
    private void renderBodyStatus(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        TransferService.state(player).body().ifPresent(bodyClass -> {
            Component line = Component.translatable("hud.projectrebirth.body",
                    Component.translatable(bodyClass.translationKey()), RETURN_KEY.getTranslatedKeyMessage());
            graphics.drawString(minecraft.font, line, 4, 4, 0x7FE8FF);
        });
    }
}
