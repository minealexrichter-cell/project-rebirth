package com.projectrebirth.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.render.ArmorArmRenderer;
import com.projectrebirth.client.render.PowerArmorRenderer;
import com.projectrebirth.powerarmor.ArmorSwingPayload;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import net.minecraft.world.item.ItemStack;
import com.projectrebirth.client.render.UpgradeVisuals;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.Upgrades;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.Floodlight;
import com.projectrebirth.powerarmor.HatchReleasePayload;
import com.projectrebirth.powerarmor.MiningModePayload;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of the power armor: renderer, hidden pilot, integrity HUD and the emergency hatch key. */
public final class PowerArmorClient {
    private static final KeyMapping HATCH_KEY = new KeyMapping("key.projectrebirth.emergency_hatch",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.projectrebirth");
    /** Worker armor: switch between 3x3 and single-block mining. */
    private static final KeyMapping MINING_KEY = new KeyMapping("key.projectrebirth.mining_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.projectrebirth");
    private static final KeyMapping FLOODLIGHT_KEY = new KeyMapping("key.projectrebirth.floodlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.projectrebirth");
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
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::blockAbilityClick);
        NeoForge.EVENT_BUS.addListener(PowerArmorClient::blockAbilityKey);
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
                || !ArmorAbility.isBareHand(event.getItemStack())) {
            return;
        }
        if (armorArmRenderer == null) {
            armorArmRenderer = new ArmorArmRenderer();
        }
        HumanoidArm side = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        // The player's swing (of either hand), not only this hand's: both armor arms punch at once.
        float swing = player.getAttackAnim(event.getPartialTick());
        armorArmRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getEquipProgress(), swing, side, armor.getVariant(), armorPlateColor(armor, side == HumanoidArm.RIGHT));
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
        event.register(MINING_KEY);
        event.register(FLOODLIGHT_KEY);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("power_armor_status"), PowerArmorClient::renderArmorStatus);
    }

    private static void hidePilot(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof PowerArmorEntity armor && !armor.isWrecked()) {
            event.setCanceled(true);
        }
    }

    /**
     * The ability icon in hotbar slot 9 cannot be taken, moved or swapped in any window. The server would put it back
     * anyway; stopping the click here keeps it from even flickering.
     */
    private static void blockAbilityClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen && touchesAbility(screen)) {
            event.setCanceled(true);
        }
    }

    /** Number keys and the off-hand key over a slot swap items; the 9 key would swap something into slot 9. */
    private static void blockAbilityKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || screen.getSlotUnderMouse() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean intoAbilitySlot = minecraft.player != null && minecraft.player.getVehicle() instanceof PowerArmorEntity
                && minecraft.options.keyHotbarSlots[ArmorAbility.SLOT].matches(event.getKeyCode(), event.getScanCode());
        if (intoAbilitySlot || touchesAbility(screen)) {
            event.setCanceled(true);
        }
    }

    private static boolean touchesAbility(AbstractContainerScreen<?> screen) {
        Slot slot = screen.getSlotUnderMouse();
        return slot != null && ArmorAbility.isAbility(slot.getItem()) || ArmorAbility.isAbility(screen.getMenu().getCarried());
    }

    /** Tells the server when the hatch key starts or stops being held; the server counts the time. */
    /** The colour of the plate on the pilot's arm, 0 for none. */
    private static int armorPlateColor(PowerArmorEntity armor, boolean rightArm) {
        InstalledUpgrades upgrades = armor.upgrades();
        int index = upgrades.indexOf(rightArm ? ArmorSlot.RIGHT_ARM : ArmorSlot.LEFT_ARM);
        if (index < 0) {
            return 0;
        }
        ItemStack stack = upgrades.entries().get(index).stack();
        boolean plate = Upgrades.definition(armor.registryAccess(), stack).filter(definition -> definition.kind().equals(ArmorUpgradeEffects.PLATING)).isPresent();
        return plate ? UpgradeVisuals.colorOf(armor.registryAccess(), stack) : 0;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        while (FLOODLIGHT_KEY.consumeClick()) {
            if (player != null && player.getVehicle() instanceof PowerArmorEntity) {
                PacketDistributor.sendToServer(Floodlight.TogglePayload.INSTANCE);
            }
        }
        while (MINING_KEY.consumeClick()) {
            if (player != null && player.getVehicle() instanceof PowerArmorEntity armor && armor.getVariant() == ArmorVariant.WORKER) {
                PacketDistributor.sendToServer(MiningModePayload.INSTANCE);
            }
        }
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
        if (player == null || !HudLayout.enabled() || minecraft.options.hideGui || !(player.getVehicle() instanceof PowerArmorEntity armor)) {
            return;
        }
        Font font = minecraft.font;
        HudLayout.pushPanel(graphics);
        int x = 0;
        Component state = Component.translatable(armor.isDisabled() ? "hud.projectrebirth.armor.state_disabled" : "hud.projectrebirth.armor.state_ok");
        graphics.drawString(font, Component.translatable("hud.projectrebirth.armor.title", Component.translatable(armor.getVariant().translationKey()), state),
                x, 12, armor.isDisabled() ? 0xFF5555 : 0x7FE8FF);
        int y = 24;
        bar(graphics, font, x, y, Component.translatable("hud.projectrebirth.armor.structural"),
                armor.getStructural(), armor.getMaxStructural(), 0xFF4FC3F7);
        bar(graphics, font, x, y + 12, Component.translatable("hud.projectrebirth.armor.critical"),
                armor.getCritical(), armor.getMaxCritical(), 0xFFFFA726);
        if (armor.isDisabled()) {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.armor.disabled", HATCH_KEY.getTranslatedKeyMessage()),
                    x, y + 26, 0xFF5555);
            float progress = armor.getHatchProgress();
            if (progress > 0) {
                GuiAtlas.bar(graphics, x, y + 37, BAR_WIDTH, 4, progress, 0xFFFF5555);
            }
        } else {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.armor.exit_hint",
                    minecraft.options.keyShift.getTranslatedKeyMessage()), x, y + 26, 0xAAAAAA);
            if (armor.getVariant() == ArmorVariant.WORKER) {
                graphics.drawString(font, Component.translatable(armor.isWideMining() ? "hud.projectrebirth.armor.mining_wide"
                        : "hud.projectrebirth.armor.mining_single", MINING_KEY.getTranslatedKeyMessage()), x, y + 38, 0xFFD54F);
            }
        }
        if (ArmorUpgradeEffects.hasFloodlight(armor)) {
            int lineY = armor.isDisabled() ? y + 44 : armor.getVariant() == ArmorVariant.WORKER ? y + 50 : y + 38;
            boolean on = armor.isFloodlightOn() && armor.isActive();
            graphics.drawString(font, Component.translatable(on ? "hud.projectrebirth.armor.floodlight_on" : "hud.projectrebirth.armor.floodlight_off",
                    FLOODLIGHT_KEY.getTranslatedKeyMessage()), x, lineY, on ? 0xFFF2B0 : 0xAAAAAA);
        }
        graphics.pose().popPose();
    }

    private static void bar(GuiGraphics graphics, Font font, int x, int y, Component label, float value, float max, int color) {
        int labelWidth = 70;
        graphics.drawString(font, label, x, y, 0xFFFFFF);
        int barX = x + labelWidth;
        float fraction = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        GuiAtlas.bar(graphics, barX, y, BAR_WIDTH, 9, fraction, color);
        graphics.drawString(font, Mth.ceil(value) + " / " + Mth.ceil(max), barX + BAR_WIDTH + 4, y, 0xFFFFFF);
    }
}
