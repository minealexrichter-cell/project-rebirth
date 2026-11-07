package com.projectrebirth.core;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.projectrebirth"))
            .icon(() -> BodyItems.item(BodyClass.BASIC_ROBOT).getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (Metal metal : Metal.values()) {
                    output.accept(TechItems.ORES.get(metal).get());
                    output.accept(TechItems.DEEPSLATE_ORES.get(metal).get());
                }
                for (Metal metal : Metal.values()) {
                    output.accept(TechItems.RAW.get(metal).get());
                    output.accept(TechItems.INGOTS.get(metal).get());
                }
                output.accept(TechItems.BRONZE_INGOT.get());
                for (Station station : Station.values()) {
                    output.accept(TechItems.STATIONS.get(station).get());
                }
                output.accept(TechItems.MAINTENANCE_STATION.get());
                output.accept(TechItems.BODY_MODIFICATION_STATION.get());
                output.accept(TechItems.COMBUSTION_GENERATOR.get());
                output.accept(TechItems.SOLAR_PANEL.get());
                output.accept(TechItems.BATTERY.get());
                output.accept(TechItems.CABLE.get());
                output.accept(FactionBlocks.FACTION_CORE_ITEM.get());
                output.accept(TechItems.CIRCUIT_BOARD.get());
                output.accept(TechItems.PROCESSOR.get());
                output.accept(TechItems.POWER_CELL.get());
                output.accept(TechItems.ACTUATOR.get());
                output.accept(TechItems.SYNTHETIC_FIBER.get());
                output.accept(BodyItems.TRANSFER_CAPSULE.get());
                output.accept(BodyItems.REPAIR_KIT.get());
                output.accept(EnvironmentRegistry.ANTIRADIN.get());
                output.accept(EnvironmentRegistry.SORBENT.get());
                output.accept(EnvironmentRegistry.RESPIRATOR.get());
                output.accept(EnvironmentRegistry.FILTER.get());
                output.accept(EnvironmentRegistry.DOSIMETER.get());
                output.accept(EnvironmentRegistry.HAZMAT_HELMET.get());
                output.accept(EnvironmentRegistry.HAZMAT_SUIT.get());
                output.accept(EnvironmentRegistry.HAZMAT_TROUSERS.get());
                output.accept(EnvironmentRegistry.HAZMAT_BOOTS.get());
                output.accept(EnvironmentRegistry.HEAVY_SUIT_HELMET.get());
                output.accept(EnvironmentRegistry.HEAVY_SUIT.get());
                output.accept(EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get());
                output.accept(EnvironmentRegistry.HEAVY_SUIT_BOOTS.get());
                output.accept(UpgradeRegistry.COPPER_PLATING.get());
                output.accept(UpgradeRegistry.IRON_PLATING.get());
                output.accept(UpgradeRegistry.TITANIUM_PLATING.get());
                output.accept(UpgradeRegistry.SEALING_MODULE.get());
                output.accept(UpgradeRegistry.JETPACK.get());
                for (BodyClass bodyClass : BodyClass.values()) {
                    output.accept(BodyItems.item(bodyClass));
                }
                for (ArmorVariant variant : ArmorVariant.values()) {
                    output.accept(PowerArmorRegistry.item(variant));
                }
                output.accept(PowerArmorRegistry.ARMOR_PLATE.get());
                output.accept(PowerArmorRegistry.REPAIR_WRENCH.get());
                output.accept(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get());
                output.accept(UpgradeRegistry.ARMOR_COPPER_PLATING.get());
                output.accept(UpgradeRegistry.ARMOR_IRON_PLATING.get());
                output.accept(UpgradeRegistry.ARMOR_TITANIUM_PLATING.get());
                output.accept(UpgradeRegistry.COMPRESSED_AIR_MODULE.get());
                output.accept(UpgradeRegistry.FLOODLIGHT.get());
                output.accept(WeaponRegistry.ASSAULT_RIFLE.get());
                output.accept(WeaponRegistry.BATTLE_RIFLE.get());
                output.accept(WeaponRegistry.COMBAT_SHOTGUN.get());
                output.accept(WeaponRegistry.ASSAULT_RIFLE_AMMO.get());
                output.accept(WeaponRegistry.BATTLE_RIFLE_AMMO.get());
                output.accept(WeaponRegistry.SHOTGUN_SHELL.get());
                output.accept(PowerArmorRegistry.RESCUE_TOOL.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
