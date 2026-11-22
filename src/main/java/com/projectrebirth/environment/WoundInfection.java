package com.projectrebirth.environment;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.hostile.MutantEntity;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 1.9.0, block B: a blow of a mutant carries the agent into the wound. While the infection lasts the person gathers toxins;
 * a new blow starts it again. The sorbent and the antiseptic stop it. An artificial body and the pilot of a power armor are
 * not infected.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WoundInfection {
    private WoundInfection() {
    }

    public static boolean enabled() {
        return Environment.isEnabled() && WastelandConfig.WOUND_ENABLED.get();
    }

    /** Public for tests. */
    public static void infect(ServerPlayer player) {
        if (!enabled() || TransferService.isInArtificialBody(player) || player.getVehicle() instanceof PowerArmorEntity) {
            return;
        }
        player.removeEffect(EnvironmentRegistry.WOUND_INFECTION);
        player.addEffect(new MobEffectInstance(EnvironmentRegistry.WOUND_INFECTION, WastelandConfig.WOUND_SECONDS.get() * 20, 0, false, true, true));
    }

    public static boolean infected(ServerPlayer player) {
        return player.hasEffect(EnvironmentRegistry.WOUND_INFECTION);
    }

    /** Toxic load per second the infection brings now. */
    public static float toxinPerSecond(ServerPlayer player) {
        return enabled() && infected(player) ? WastelandConfig.WOUND_TOXIN.get().floatValue() : 0.0f;
    }

    public static void cure(net.minecraft.world.entity.player.Player player) {
        if (player.hasEffect(EnvironmentRegistry.WOUND_INFECTION)) {
            player.removeEffect(EnvironmentRegistry.WOUND_INFECTION);
        }
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0f
                && event.getSource().getDirectEntity() instanceof MutantEntity && player.gameMode.isSurvival()) {
            infect(player);
        }
    }
}
