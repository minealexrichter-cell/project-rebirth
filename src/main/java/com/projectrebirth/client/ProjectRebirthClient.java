package com.projectrebirth.client;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.client.render.ArtificialBodyRenderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only entry point. NeoForge never loads this class on a dedicated server,
 * so rendering, models and screens are registered from here.
 */
@Mod(value = ProjectRebirth.MOD_ID, dist = Dist.CLIENT)
public class ProjectRebirthClient {
    public ProjectRebirthClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerRenderers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        ProjectRebirth.LOGGER.info("Project: Rebirth client setup complete");
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BodyClass bodyClass : BodyClass.values()) {
            event.registerEntityRenderer(BodyEntities.type(bodyClass), context -> new ArtificialBodyRenderer(context, bodyClass));
        }
    }
}
