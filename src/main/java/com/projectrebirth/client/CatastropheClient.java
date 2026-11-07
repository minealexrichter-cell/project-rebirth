package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.environment.CatastrophePayload;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EventPhase;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * How a catastrophe looks and sounds. The server says which catastrophe it is and in which phase; everything here is only
 * show: green tinted air in acid rain with hissing, a thick yellow-green fog in a toxic storm, a flickering sky in a
 * radiation burst, and the clicks of a carried dosimeter.
 */
public final class CatastropheClient {
    private static final int TICKS_PER_SECOND = 20;
    /** Fog end in a toxic storm at full strength, in blocks. */
    private static final float STORM_FOG_END = 10.0f;
    private static final float CLICKS_PER_RADIATION = 0.6f;
    private static final float MAX_CLICKS_PER_SECOND = 40.0f;

    private static CatastrophePayload state = CatastrophePayload.CALM;
    private static long receivedAt;
    /** Shown strength, eased toward the target so that nothing jumps. */
    private static float shown;
    /** Outdoors 1, under a roof less. */
    private static float open = 1.0f;
    private static float flash;
    private static int flashTicks;
    private static final RandomSource RANDOM = RandomSource.create();

    private CatastropheClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(CatastropheClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(CatastropheClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CatastropheClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(CatastropheClient::onRenderFog);
        CatastrophePayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> {
            state = payload;
            receivedAt = ticks();
        });
    }

    private static long ticks() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    /** Seconds left in the phase, counted on from the last message of the server. */
    private static int secondsLeft() {
        return Math.max(0, state.secondsLeft() - (int) ((ticks() - receivedAt) / TICKS_PER_SECOND));
    }

    /** How strong the catastrophe should look now, 0 to 1. */
    private static float target() {
        float phase = switch (state.phase()) {
            case ACTIVE -> 1.0f;
            case AFTERMATH -> state.phaseSeconds() <= 0 ? 0.0f : Mth.clamp((float) secondsLeft() / state.phaseSeconds(), 0.0f, 1.0f);
            default -> 0.0f;
        };
        return phase * (0.5f + 0.5f * Mth.clamp(state.intensity() / 1.5f, 0.0f, 1.0f));
    }

    private static boolean is(String event) {
        return event.equals(state.event());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            state = CatastrophePayload.CALM;
            shown = 0.0f;
            return;
        }
        shown += Mth.clamp(target() - shown, -0.01f, 0.01f);
        BlockPos eyes = BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition());
        float openTarget = level.canSeeSky(eyes) ? 1.0f : 0.35f;
        open += Mth.clamp(openTarget - open, -0.05f, 0.05f);
        if (minecraft.isPaused()) {
            return;
        }
        tickFlash();
        tickHiss(level, player);
        tickClicks(level, player);
    }

    /** The sky of a radiation burst flickers: short flashes at random. */
    private static void tickFlash() {
        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                flash = 0.0f;
            }
        } else if (is(Environment.RADIATION_BURST) && shown > 0.0f && RANDOM.nextFloat() < 0.06f * shown) {
            flashTicks = 2 + RANDOM.nextInt(4);
            flash = 0.4f + 0.6f * RANDOM.nextFloat();
        }
    }

    /** Acid hisses where it falls on something: only where it rains and the sky is open. */
    private static void tickHiss(ClientLevel level, LocalPlayer player) {
        if (!is(Environment.ACID_RAIN) || shown < 0.15f || RANDOM.nextFloat() >= 0.06f * shown) {
            return;
        }
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 10.0;
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 10.0;
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, player.getY(), z));
        if (Math.abs(top.getY() - player.getY()) > 12.0 || !level.isRainingAt(top)) {
            return;
        }
        level.playLocalSound(x, top.getY(), z, ModSounds.ENVIRONMENT_ACID_HISS.get(), SoundSource.WEATHER,
                0.25f + 0.35f * shown, 0.85f + 0.3f * RANDOM.nextFloat(), false);
    }

    /** A carried dosimeter clicks the faster, the more radiation reaches its bearer. */
    private static void tickClicks(ClientLevel level, LocalPlayer player) {
        if (!EnvironmentClient.carriesDosimeter(player)) {
            return;
        }
        float perTick = Math.min(MAX_CLICKS_PER_SECOND, EnvironmentClient.radiation() * CLICKS_PER_RADIATION) / TICKS_PER_SECOND;
        int clicks = (int) perTick + (RANDOM.nextFloat() < perTick - (int) perTick ? 1 : 0);
        for (int i = 0; i < clicks; i++) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), ModSounds.DOSIMETER_CLICK.get(), SoundSource.PLAYERS,
                    0.45f, 0.9f + 0.2f * RANDOM.nextFloat(), false);
        }
    }

    private static void blend(ViewportEvent.ComputeFogColor event, float r, float g, float b, float amount) {
        float a = Mth.clamp(amount, 0.0f, 1.0f);
        event.setRed(Mth.lerp(a, event.getRed(), r));
        event.setGreen(Mth.lerp(a, event.getGreen(), g));
        event.setBlue(Mth.lerp(a, event.getBlue(), b));
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (shown <= 0.0f && flash <= 0.0f) {
            return;
        }
        if (is(Environment.ACID_RAIN)) {
            blend(event, 0.50f, 0.68f, 0.30f, 0.4f * shown * open);
        } else if (is(Environment.TOXIC_STORM)) {
            blend(event, 0.62f, 0.66f, 0.30f, 0.85f * shown);
        } else if (is(Environment.RADIATION_BURST)) {
            blend(event, 0.62f, 0.80f, 0.55f, 0.2f * shown * open);
            blend(event, 0.85f, 1.0f, 0.85f, 0.7f * flash * open);
        }
    }

    /** The toxic storm closes the view in, indoors less. */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!is(Environment.TOXIC_STORM) || shown <= 0.0f || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float strength = shown * (0.4f + 0.6f * open);
        float far = Mth.lerp(strength, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), STORM_FOG_END / Math.max(0.2f, open)));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.05f));
        event.setCanceled(true);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ProjectRebirth.id("catastrophe_flash"), CatastropheClient::renderFlash);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ProjectRebirth.id("catastrophe_warning"), CatastropheClient::renderWarning);
    }

    /** The flash of the sky also lights the screen a little, outdoors. */
    private static void renderFlash(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (flash <= 0.0f || open <= 0.5f) {
            return;
        }
        int alpha = (int) (60 * flash * shown * open);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xD8FFD0);
    }

    /** The type of the catastrophe and the rough time to it; exact time and intensity come with the weather station (1.6.0). */
    private static void renderWarning(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !HudLayout.enabled() || state.phase() == EventPhase.CALM || state.event().isEmpty()) {
            return;
        }
        Component name = Component.translatable("environment.projectrebirth.event." + state.event());
        int left = secondsLeft();
        Component text = switch (state.phase()) {
            case WARNING -> left < 60 ? Component.translatable("hud.projectrebirth.catastrophe.warning_soon", name)
                    : Component.translatable("hud.projectrebirth.catastrophe.warning", name, Mth.ceil(left / 60.0f));
            case ACTIVE -> left < 60 ? Component.translatable("hud.projectrebirth.catastrophe.active", name)
                    : Component.translatable("hud.projectrebirth.catastrophe.active_for", name, Mth.ceil(left / 60.0f));
            default -> Component.translatable("hud.projectrebirth.catastrophe.aftermath", name);
        };
        boolean blink = state.phase() == EventPhase.WARNING && (ticks() / 10) % 2 == 0;
        int color = state.phase() == EventPhase.AFTERMATH ? 0xFFCCCCCC : blink ? 0xFFFFFF55 : 0xFFFF8A24;
        int width = minecraft.font.width(text);
        // The last line of the status panel, the same for a human, a body and a pilot.
        HudLayout.pushPanel(graphics);
        int y = HudLayout.CATASTROPHE_ROW;
        graphics.fill(-2, y - 2, width + 2, y + minecraft.font.lineHeight, 0x90000000);
        graphics.drawString(minecraft.font, text, 0, y, color);
        graphics.pose().popPose();
    }

}
