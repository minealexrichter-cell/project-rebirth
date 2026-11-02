package com.projectrebirth;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.ReturnToHumanPayload;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModCreativeTabs;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Common entry point of Project: Rebirth. Loaded on both the client and the dedicated server,
 * so it must never reference client-only classes.
 */
@Mod(ProjectRebirth.MOD_ID)
public class ProjectRebirth {
    public static final String MOD_ID = "projectrebirth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProjectRebirth(IEventBus modEventBus, ModContainer modContainer) {
        TechBlocks.register(modEventBus);
        TechItems.register(modEventBus);
        BodyBlocks.register(modEventBus);
        BodyComponents.register(modEventBus);
        BodyAttachments.register(modEventBus);
        BodyEntities.register(modEventBus);
        BodyItems.register(modEventBus);
        ReturnToHumanPayload.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, RebirthConfig.SERVER_SPEC);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Project: Rebirth common setup complete");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
