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
 * show: green tinted air and green rain in every biome in acid rain with hissing, a thick yellow-green fog in a toxic storm,
 * a brownish filter and film grain in a radiation burst, a brown dust fog, a smooth violet-green glow in an EMI storm, and the
 * clicks of a carried dosimeter.
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
    private static final RandomSource RANDOM = RandomSource.create();
    private static final net.minecraft.core.particles.ParticleOptions ACID_PUFF =
            net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0xFF9CCC65);
    private static final net.minecraft.core.particles.ParticleOptions STORM_HAZE =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.78f, 0.8f, 0.36f), 1.6f);
    private static final net.minecraft.core.particles.ParticleOptions DUST_GRAIN =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.78f, 0.64f, 0.42f), 1.4f);
    /** Fog end in a dust storm at full strength, in blocks: about 8 blocks of view. */
    private static final float DUST_FOG_END = 9.0f;
    private CatastropheClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(CatastropheClient::registerGuiLayers);
        // acid rain is drawn by the mod in every biome of the Overworld (vanilla shows snow or nothing in some)
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) ->
                event.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS, new AcidRainEffects()));
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
            zoneShown = 0.0f;
            return;
        }
        shown += Mth.clamp(target() - shown, -0.01f, 0.01f);
        BlockPos eyes = BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition());
        // in a sealed room the storm stays outside: no haze, fog or tinted air inside
        float openTarget = EnvironmentClient.inSealedRoom() ? 0.0f : level.canSeeSky(eyes) ? 1.0f : 0.35f;
        open += Mth.clamp(openTarget - open, -0.05f, 0.05f);
        if (minecraft.isPaused()) {
            return;
        }
        tickHiss(level, player);
        tickWind(minecraft);
        tickCrackle(level, player);
        tickClicks(level, player);
        tickParticles(level, player, minecraft);
        tickZone(level, player, minecraft);
    }

    /** Shown strength of the contaminated zone around the player (1.6.0), eased so that the tint does not jump at the edge of a spot. */
    private static float zoneShown;
    private static final net.minecraft.core.particles.ParticleOptions ZONE_RADIATION =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.95f, 0.55f, 0.25f), 1.0f);
    private static final net.minecraft.core.particles.ParticleOptions ZONE_TOXIC =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.7f, 0.8f, 0.3f), 1.2f);

    private static boolean zonesShown() {
        return com.projectrebirth.config.RebirthClientConfig.ZONES_VISUALS.get();
    }

    /** Rare drifting particles inside a contaminated spot, fewer with the "decreased" particle setting and none with "minimal"; none in a sealed room. */
    private static void tickZone(ClientLevel level, LocalPlayer player, Minecraft minecraft) {
        float target = zonesShown() && !EnvironmentClient.inSealedRoom() ? EnvironmentClient.zoneStrength() : 0.0f;
        zoneShown += Mth.clamp(target - zoneShown, -0.02f, 0.02f);
        if (zoneShown < 0.05f || minecraft.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) {
            return;
        }
        float many = minecraft.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 0.4f : 1.0f;
        float perTick = com.projectrebirth.config.RebirthClientConfig.ZONES_PARTICLES.get().floatValue() * zoneShown * many / TICKS_PER_SECOND;
        int count = (int) perTick + (RANDOM.nextFloat() < perTick - (int) perTick ? 1 : 0);
        int zone = EnvironmentClient.zone();
        for (int i = 0; i < count; i++) {
            boolean radiation = (zone & com.projectrebirth.environment.ContaminationZones.RADIATION_BIT) != 0
                    && ((zone & com.projectrebirth.environment.ContaminationZones.TOXIC_BIT) == 0 || RANDOM.nextBoolean());
            level.addParticle(radiation ? ZONE_RADIATION : ZONE_TOXIC, player.getX() + (RANDOM.nextDouble() - 0.5) * 14.0,
                    player.getY() + RANDOM.nextDouble() * 3.0, player.getZ() + (RANDOM.nextDouble() - 0.5) * 14.0, 0.0, 0.02, 0.0);
        }
    }

    /**
     * The air of a catastrophe: green puffs where acid rain hits the ground, a drifting yellow-green haze in a toxic storm,
     * green sparks in a radiation burst. Fewer with the "decreased" or "minimal" particle setting.
     */
    private static void tickParticles(ClientLevel level, LocalPlayer player, Minecraft minecraft) {
        if (shown < 0.1f || EnvironmentClient.inSealedRoom() || minecraft.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) {
            return;
        }
        float many = minecraft.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 0.4f : 1.0f;
        if (is(Environment.ACID_RAIN)) {
            for (int i = 0; i < Math.round(4 * shown * many); i++) {
                double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 16.0;
                double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 16.0;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, player.getY(), z));
                if (Math.abs(top.getY() - player.getY()) < 10.0 && level.isRaining()) {
                    level.addParticle(ACID_PUFF, x, top.getY() + 0.05, z, 0.0, 0.03, 0.0);
                }
            }
        } else if (is(Environment.TOXIC_STORM)) {
            for (int i = 0; i < Math.round(6 * shown * many); i++) {
                level.addParticle(STORM_HAZE, player.getX() + (RANDOM.nextDouble() - 0.5) * 20.0, player.getEyeY() + (RANDOM.nextDouble() - 0.4) * 8.0,
                        player.getZ() + (RANDOM.nextDouble() - 0.5) * 20.0, 0.0, 0.0, 0.0);
            }
        } else if (is(Environment.DUST_STORM)) {
            // grains driven by the wind, outdoors many, under a roof few
            for (int i = 0; i < Math.round(8 * shown * many * open); i++) {
                level.addParticle(DUST_GRAIN, player.getX() + (RANDOM.nextDouble() - 0.5) * 18.0, player.getEyeY() + (RANDOM.nextDouble() - 0.5) * 6.0,
                        player.getZ() + (RANDOM.nextDouble() - 0.5) * 18.0, 0.25 + RANDOM.nextDouble() * 0.2, -0.02, 0.1);
            }
        }
    }

    /** The loop of the dust storm wind, while it plays. */
    private static WindLoop wind;

    /** The wind of a dust storm: one loop, as loud as the storm looks, quieter under a roof, silent in a sealed room. */
    private static void tickWind(Minecraft minecraft) {
        if (is(Environment.DUST_STORM) && shown > 0.05f && (wind == null || wind.isStopped())) {
            wind = new WindLoop();
            minecraft.getSoundManager().play(wind);
        }
    }

    private static float windVolume() {
        return is(Environment.DUST_STORM) ? shown * (0.25f + 0.75f * open) : 0.0f;
    }

    private static final class WindLoop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        WindLoop() {
            super(ModSounds.DUST_WIND.get(), SoundSource.WEATHER, RANDOM);
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0f;
            this.relative = true;
            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = windVolume();
            if (volume <= 0.0f) {
                stop();
            }
        }
    }

    /**
     * The crackle of an EMI storm: now and then near the player, more often when the player's own devices are disturbed
     * (a body or an armor); not in a sealed room.
     */
    private static void tickCrackle(ClientLevel level, LocalPlayer player) {
        if (!is(Environment.EMI_STORM) || shown < 0.15f || EnvironmentClient.inSealedRoom()) {
            return;
        }
        float chance = (Interference.active() ? 0.05f : 0.015f) * shown;
        if (RANDOM.nextFloat() < chance) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.EMI_CRACKLE.get(), SoundSource.WEATHER,
                    0.3f + 0.4f * shown, 0.8f + 0.4f * RANDOM.nextFloat(), false);
        }
    }

    /** Acid hisses where it falls on something open to the sky, in every biome: the mod draws the rain everywhere. */
    private static void tickHiss(ClientLevel level, LocalPlayer player) {
        if (!is(Environment.ACID_RAIN) || shown < 0.15f || RANDOM.nextFloat() >= 0.06f * shown) {
            return;
        }
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 10.0;
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 10.0;
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, player.getY(), z));
        if (Math.abs(top.getY() - player.getY()) > 12.0 || !level.isRaining()) {
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
        if (zoneShown > 0.0f && zonesShown()) {
            // a light tint of the air inside a contaminated spot: dirty brown for radiation, yellow-green for toxins
            boolean radiation = (EnvironmentClient.zone() & com.projectrebirth.environment.ContaminationZones.RADIATION_BIT) != 0;
            float tint = com.projectrebirth.config.RebirthClientConfig.ZONES_TINT.get().floatValue() * zoneShown;
            if (radiation) {
                blend(event, 0.55f, 0.45f, 0.30f, tint);
            } else {
                blend(event, 0.62f, 0.66f, 0.30f, tint);
            }
        }
        if (shown <= 0.0f) {
            return;
        }
        if (is(Environment.ACID_RAIN)) {
            blend(event, 0.50f, 0.68f, 0.30f, 0.4f * shown * open);
        } else if (is(Environment.TOXIC_STORM)) {
            blend(event, 0.62f, 0.66f, 0.30f, 0.85f * shown);
        } else if (is(Environment.DUST_STORM)) {
            blend(event, 0.74f, 0.60f, 0.38f, 0.9f * shown * (0.4f + 0.6f * open));
        } else if (is(Environment.EMI_STORM)) {
            // a slow, smooth swing between violet and green
            float swing = emiSwing();
            blend(event, Mth.lerp(swing, 0.62f, 0.35f), Mth.lerp(swing, 0.40f, 0.85f), Mth.lerp(swing, 0.90f, 0.55f), 0.3f * shown * open);
        } else if (is(Environment.RADIATION_BURST)) {
            // the air turns a dirty brown, as through a filter
            blend(event, 0.55f, 0.45f, 0.30f, 0.3f * shown);
        }
    }

    /** The toxic storm closes the view in, indoors less. */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        boolean dust = is(Environment.DUST_STORM);
        if (!is(Environment.TOXIC_STORM) && !dust || shown <= 0.0f || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float strength = EnvironmentClient.inSealedRoom() ? 0.0f : shown * (0.4f + 0.6f * open);
        float end = dust ? DUST_FOG_END : STORM_FOG_END;
        float far = Mth.lerp(strength, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), end / Math.max(0.2f, open)));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.05f));
        event.setCanceled(true);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ProjectRebirth.id("catastrophe_flash"), CatastropheClient::renderFlash);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ProjectRebirth.id("catastrophe_warning"), CatastropheClient::renderWarning);
    }

    /** Whether acid rain is falling now, as the client sees it: then the mod draws the rain in every biome. */
    static boolean acidRainFalling() {
        return is(Environment.ACID_RAIN) && (state.phase() == EventPhase.ACTIVE || state.phase() == EventPhase.AFTERMATH);
    }

    /** Where the EMI glow is between violet (0) and green (1): a slow eased swing, one full cycle in EMI_CYCLE_MS. */
    private static float emiSwing() {
        float phase = (System.currentTimeMillis() % EMI_CYCLE_MS) / (float) EMI_CYCLE_MS;
        float wave = 0.5f - 0.5f * Mth.cos(phase * Mth.TWO_PI);
        // smoothstep: it rests a little at violet and at green and glides in between
        return wave * wave * (3.0f - 2.0f * wave);
    }

    private static final long EMI_CYCLE_MS = 24000L;

    /**
     * Overlays of the catastrophes. An EMI storm: a glow over the upper half of the view outdoors, gliding between violet and
     * green. A radiation burst: a brownish tint and a constant film grain over the whole view, stronger with the burst (indoors
     * too, since a roof does not stop the radiation; half as strong in a sealed room).
     */
    private static void renderFlash(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        // 1.6.0: the same grain from the radiation reaching the player (a spot, a source, uranium carried), by its level;
        // with a burst going on the stronger of the two is drawn, never both
        float level = levelGrain();
        if (shown <= 0.0f) {
            RadiationGrain.render(graphics, level);
            return;
        }
        if (is(Environment.EMI_STORM) && open > 0.0f) {
            float swing = emiSwing();
            int r = (int) Mth.lerp(swing, 0xB0, 0x40);
            int g = (int) Mth.lerp(swing, 0x50, 0xF0);
            int b = (int) Mth.lerp(swing, 0xFF, 0x90);
            int alpha = (int) (130 * shown * open);
            int rgb = (r << 16) | (g << 8) | b;
            graphics.fillGradient(0, 0, graphics.guiWidth(), graphics.guiHeight() / 2, (alpha << 24) | rgb, rgb);
            RadiationGrain.render(graphics, level);
        } else if (is(Environment.RADIATION_BURST)) {
            float strength = shown * (EnvironmentClient.inSealedRoom() ? 0.5f : 1.0f);
            RadiationGrain.render(graphics, Math.max(strength, level));
        } else {
            RadiationGrain.render(graphics, level);
        }
    }

    /** The grain from the radiation level, eased toward its target so that the once-a-second reading does not jump. */
    private static float levelGrainShown;
    private static long levelGrainTime;

    private static float levelGrain() {
        float target = 0.0f;
        if (com.projectrebirth.config.RebirthClientConfig.RADIATION_GRAIN.get()) {
            float from = com.projectrebirth.config.RebirthClientConfig.RADIATION_GRAIN_FROM.get().floatValue();
            float full = Math.max(from + 0.1f, com.projectrebirth.config.RebirthClientConfig.RADIATION_GRAIN_FULL.get().floatValue());
            target = Mth.clamp((EnvironmentClient.radiation() - from) / (full - from), 0.0f, 1.0f);
        }
        long now = net.minecraft.Util.getMillis();
        float step = Mth.clamp((now - levelGrainTime) / 1500.0f, 0.0f, 1.0f);
        levelGrainTime = now;
        levelGrainShown += (target - levelGrainShown) * step;
        return levelGrainShown < 0.01f ? 0.0f : levelGrainShown;
    }

    /** The hazard a catastrophe brings, for its mark; null for an unknown one (from a newer config). */
    static com.projectrebirth.environment.Hazard hazardOf(String event) {
        return switch (event) {
            case Environment.ACID_RAIN -> com.projectrebirth.environment.Hazard.ACID;
            case Environment.TOXIC_STORM -> com.projectrebirth.environment.Hazard.POLLUTION;
            case Environment.RADIATION_BURST -> com.projectrebirth.environment.Hazard.RADIATION;
            case Environment.DUST_STORM -> com.projectrebirth.environment.Hazard.DUST;
            case Environment.EMI_STORM -> com.projectrebirth.environment.Hazard.EMI;
            default -> null;
        };
    }

    private static void renderWarning(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !HudLayout.enabled()) {
            return;
        }
        // 1.6.0, block D: a weather station's forecast, before the catastrophe and (long range) in place of the rough warning
        com.projectrebirth.weather.ForecastPayload forecast = ForecastClient.current();
        boolean calm = state.phase() == EventPhase.CALM || state.event().isEmpty();
        if (forecast != null && (calm || state.phase() == EventPhase.WARNING && forecast.exact() && forecast.event().equals(state.event()))) {
            renderLine(graphics, minecraft, ForecastClient.line(), forecast.event(), calm ? 0xFF8FD3F5 : (ticks() / 10) % 2 == 0 ? 0xFFFFFF55 : 0xFFFF8A24);
            return;
        }
        if (calm) {
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
        renderLine(graphics, minecraft, text, state.event(), color);
    }

    private static void renderLine(GuiGraphics graphics, Minecraft minecraft, Component text, String event, int color) {
        com.projectrebirth.environment.Hazard hazard = hazardOf(event);
        int textX = hazard == null ? 0 : 11;
        int width = textX + minecraft.font.width(text);
        // The last line of the status panel, the same for a human, a body and a pilot; the mark tells the kind of catastrophe.
        HudLayout.pushPanel(graphics);
        int y = HudLayout.CATASTROPHE_ROW;
        graphics.fill(-2, y - 2, width + 2, y + minecraft.font.lineHeight, 0x90000000);
        if (hazard != null) {
            GuiAtlas.hazardMark(graphics, 0, y - 1, hazard);
        }
        graphics.drawString(minecraft.font, text, textX, y, color);
        graphics.pose().popPose();
    }

}
