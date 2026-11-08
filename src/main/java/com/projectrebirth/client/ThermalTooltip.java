package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.environment.TemperatureRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A piece of armor or a suit with a thermal liner sewn in says so in its tooltip (1.5.0). */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID, value = Dist.CLIENT)
public final class ThermalTooltip {
    private ThermalTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().has(TemperatureRegistry.THERMAL_LINER_INSIDE.get())) {
            event.getToolTip().add(1, Component.translatable("tooltip.projectrebirth.thermal_liner.inside").withStyle(ChatFormatting.GOLD));
        }
    }
}
