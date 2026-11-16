package com.projectrebirth;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.CapsuleNetwork;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.ReturnToHumanPayload;
import com.projectrebirth.bodies.TransferAnimationPayload;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModCreativeTabs;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionSync;
import com.projectrebirth.powerarmor.ArmorSwingPayload;
import com.projectrebirth.powerarmor.HatchReleasePayload;
import com.projectrebirth.powerarmor.MiningModePayload;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.TechBlockEntities;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechMenus;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

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
        TechBlockEntities.register(modEventBus);
        TechMenus.register(modEventBus);
        TechRecipes.register(modEventBus);
        BodyBlocks.register(modEventBus);
        BodyComponents.register(modEventBus);
        BodyAttachments.register(modEventBus);
        BodyEntities.register(modEventBus);
        BodyItems.register(modEventBus);
        ReturnToHumanPayload.register(modEventBus);
        TransferAnimationPayload.register(modEventBus);
        CapsuleNetwork.register(modEventBus);
        PowerArmorRegistry.register(modEventBus);
        HatchReleasePayload.register(modEventBus);
        ArmorSwingPayload.register(modEventBus);
        MiningModePayload.register(modEventBus);
        FactionBlocks.register(modEventBus);
        FactionSync.register(modEventBus);
        UpgradeRegistry.register(modEventBus);
        com.projectrebirth.bodies.power.BodyPowerRegistry.register(modEventBus);
        WeaponRegistry.register(modEventBus);
        EnvironmentRegistry.register(modEventBus);
        com.projectrebirth.environment.TemperatureRegistry.register(modEventBus);
        com.projectrebirth.environment.SourceRegistry.register(modEventBus);
        com.projectrebirth.structures.StructureRegistry.register(modEventBus);
        com.projectrebirth.structures.StructureBlocks.register(modEventBus);
        com.projectrebirth.hostile.HostileRegistry.register(modEventBus);
        com.projectrebirth.water.WaterRegistry.register(modEventBus);
        com.projectrebirth.weather.WeatherRegistry.register(modEventBus);
        com.projectrebirth.monitoring.MonitoringRegistry.register(modEventBus);
        com.projectrebirth.shelter.ShelterRegistry.register(modEventBus);
        com.projectrebirth.codex.CodexRegistry.register(modEventBus);
        Jetpack.register(modEventBus);
        ModSounds.register(modEventBus);
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
