package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only entry point. NeoForge never loads this class on a dedicated server,
 * so rendering, models and screens are registered from here.
 */
@Mod(value = ProjectRebirth.MOD_ID, dist = Dist.CLIENT)
public class ProjectRebirthClient {
    public ProjectRebirthClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        ProjectRebirth.LOGGER.info("Project: Rebirth client setup complete");
    }
}
