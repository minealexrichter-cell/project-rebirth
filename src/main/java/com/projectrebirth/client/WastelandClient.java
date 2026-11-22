package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.environment.ContaminatedFood;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 1.9.0, block B, the client side of the Wasteland: the mark of contaminated food in its tooltip, and the toxic fog that lies
 * by the ground at night in the open (a picture only, switched off in the client config [wasteland] nightFog).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID, value = Dist.CLIENT)
public final class WastelandClient {
    /** How far one sees through the fog at its thickest, in blocks. */
    private static final float FOG_END = 56.0f;
    private static float fog;

    private WastelandClient() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        float load = ContaminatedFood.toxin(event.getItemStack());
        if (load > 0.0f) {
            event.getToolTip().add(1, Component.translatable("tooltip.projectrebirth.contaminated_food", Math.round(load)).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Whether the fog lies where the player is now. */
    private static boolean fogHere(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || !RebirthClientConfig.WASTELAND_NIGHT_FOG.get() || !RebirthConfig.SERVER_SPEC.isLoaded()
                || !RebirthConfig.ENVIRONMENT_ENABLED.get() || !WastelandConfig.AIR_ENABLED.get()
                || !level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) {
            return false;
        }
        float angle = level.getTimeOfDay(1.0f);
        boolean night = angle > 0.27f && angle < 0.73f;
        return night && level.canSeeSky(BlockPos.containing(player.getEyePosition())) && !EnvironmentClient.inSealedRoom();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        float target = fogHere(Minecraft.getInstance()) ? 1.0f : 0.0f;
        // eased over a few seconds, so that it does not jump at dusk or at a door
        fog = Mth.approach(fog, target, 0.01f);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (fog > 0.0f) {
            float a = 0.35f * fog;
            event.setRed(Mth.lerp(a, event.getRed(), 0.22f));
            event.setGreen(Mth.lerp(a, event.getGreen(), 0.26f));
            event.setBlue(Mth.lerp(a, event.getBlue(), 0.12f));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (fog <= 0.0f || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.isCanceled()) {
            return;
        }
        float far = Mth.lerp(fog, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), FOG_END));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.2f));
        event.setCanceled(true);
    }
}
