package com.projectrebirth.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.render.ArmorArmRenderer;
import com.projectrebirth.client.render.PowerArmorRenderer;
import com.projectrebirth.powerarmor.ArmorSwingPayload;
import com.projectrebirth.powerarmor.HatchReleasePayload;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of the power armor: renderer, hidden pilot, integrity HUD and the emergency hatch key. */
public final class PowerArmorClient {
    private static final KeyMapping HATCH_KEY = new KeyMapping("key.projectrebirth.emergency_hatch",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.projectrebirth");
    private static final int BAR_WIDTH = 100;

    private static boolean hatchKeyWasDown;
    private static ArmorArmRenderer armorArmRenderer;

    private PowerArmorClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(PowerArmorClient::registerRenderers);
        modEventBus.addListener(PowerArmorClient::registerKeys);
        modEventBus.addListener(PowerArmorClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::onClientTick);
        // Runs before the possessed-body renderer so that a pilot is hidden in any body.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PowerArmorClient::hidePilot);
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::hideVehicleHearts);
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::renderArmorHands);
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::onAttackKey);
        PowerArmorEntity.isLocalPlayer = entity -> entity == Minecraft.getInstance().player;
    }

    /**
     * In first person the pilot sees the armor's arms instead of their own. A hand holding an item shows only
     * the item (vanilla view). Both empty armor arms strike together whenever the pilot swings.
     */
    private static void renderArmorHands(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getVehicle() instanceof PowerArmorEntity armor) || armor.isWrecked()
                || !event.getItemStack().isEmpty()) {
            return;
        }
        if (armorArmRenderer == null) {
            armorArmRenderer = new ArmorArmRenderer();
        }
        HumanoidArm side = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        // The player's swing (of either hand), not only this hand's: both armor arms punch at once.
        float swing = player.getAttackAnim(event.getPartialTick());
        armorArmRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getEquipProgress(), swing, side);
        event.setCanceled(true);
    }

    /** Every attack of the pilot, even into the air, makes the armor punch for everyone to see. */
    private static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && player.getVehicle() instanceof PowerArmorEntity armor && armor.isActive()) {
            PacketDistributor.sendToServer(ArmorSwingPayload.INSTANCE);
        }
    }

    /** The armor's own integrity bars replace the vanilla "mount health" hearts. */
    private static void hideVehicleHearts(RenderGuiLayerEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (event.getName().equals(VanillaGuiLayers.VEHICLE_HEALTH) && player != null && player.getVehicle() instanceof PowerArmorEntity) {
            event.setCanceled(true);
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PowerArmorRegistry.POWER_ARMOR.get(), PowerArmorRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(HATCH_KEY);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("power_armor_status"), PowerArmorClient::renderArmorStatus);
    }

    private static void hidePilot(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof PowerArmorEntity armor && !armor.isWrecked()) {
            event.setCanceled(true);
        }
    }

    /** Tells the server when the hatch key starts or stops being held; the server counts the time. */
    private static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        boolean down = player != null && player.getVehicle() instanceof PowerArmorEntity && HATCH_KEY.isDown();
        if (down != hatchKeyWasDown) {
            hatchKeyWasDown = down;
            if (player != null && player.getVehicle() instanceof PowerArmorEntity) {
                PacketDistributor.sendToServer(new HatchReleasePayload(down));
            }
        }
    }

    private static void renderArmorStatus(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !(player.getVehicle() instanceof PowerArmorEntity armor)) {
            return;
        }
        Font font = minecraft.font;
        int x = 4;
        int y = 16;
        bar(graphics, font, x, y, Component.translatable("hud.projectrebirth.armor.structural"),
                armor.getStructural(), armor.getMaxStructural(), 0xFF4FC3F7);
        bar(graphics, font, x, y + 12, Component.translatable("hud.projectrebirth.armor.critical"),
                armor.getCritical(), armor.getMaxCritical(), 0xFFFFA726);
        if (armor.isDisabled()) {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.armor.disabled", HATCH_KEY.getTranslatedKeyMessage()),
                    x, y + 26, 0xFF5555);
            float progress = armor.getHatchProgress();
            if (progress > 0) {
                graphics.fill(x, y + 37, x + BAR_WIDTH, y + 41, 0xAA000000);
                graphics.fill(x, y + 37, x + (int) (BAR_WIDTH * progress), y + 41, 0xFFFF5555);
            }
        } else {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.armor.exit_hint",
                    minecraft.options.keyShift.getTranslatedKeyMessage()), x, y + 26, 0xAAAAAA);
        }
    }

    private static void bar(GuiGraphics graphics, Font font, int x, int y, Component label, float value, float max, int color) {
        int labelWidth = 70;
        graphics.drawString(font, label, x, y, 0xFFFFFF);
        int barX = x + labelWidth;
        float fraction = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        graphics.fill(barX, y + 1, barX + BAR_WIDTH, y + 8, 0xAA000000);
        graphics.fill(barX, y + 1, barX + (int) (BAR_WIDTH * fraction), y + 8, color);
        graphics.drawString(font, Mth.ceil(value) + " / " + Mth.ceil(max), barX + BAR_WIDTH + 4, y, 0xFFFFFF);
    }
}
