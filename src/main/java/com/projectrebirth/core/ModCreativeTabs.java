package com.projectrebirth.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.codex.CodexRegistry;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.SourceRegistry;
import com.projectrebirth.environment.TemperatureRegistry;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.monitoring.MonitoringRegistry;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.scouting.Scouting;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.structures.StructureBlocks;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.water.WaterRegistry;
import com.projectrebirth.weapons.WeaponRegistry;
import com.projectrebirth.weather.WeatherRegistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tabs (1.7.1): one for each kind of thing, in the order of the tech tree. Each tab's contents are a plain list
 * ({@link #contents}), so a test can check that every item of the mod is in one of them.
 */
public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectRebirth.MOD_ID);

    /** The tabs, in their order: id, icon, contents. */
    public enum Kind {
        RESOURCES("main", () -> TechItems.INGOTS.get(Metal.TITANIUM).get(), ModCreativeTabs::resources),
        MACHINES("machines", () -> TechItems.STATIONS.get(Station.values()[0]).get(), ModCreativeTabs::machines),
        BODIES("bodies", () -> BodyItems.item(BodyClass.BASIC_ROBOT), ModCreativeTabs::bodies),
        POWER_ARMOR("power_armor", () -> PowerArmorRegistry.item(ArmorVariant.values()[0]), ModCreativeTabs::powerArmor),
        WEAPONS("weapons", () -> WeaponRegistry.ASSAULT_RIFLE.get(), ModCreativeTabs::weapons),
        GARRISON("garrison", () -> GarrisonRegistry.AI_CORE.get(), ModCreativeTabs::garrison),
        SURVIVAL("survival", () -> EnvironmentRegistry.HAZMAT_HELMET.get(), ModCreativeTabs::survival),
        WORLD("world", () -> HostileRegistry.PATROL_ROBOT_SPAWN_EGG.get(), ModCreativeTabs::world);

        private final String id;
        private final Supplier<ItemLike> icon;
        private final Consumer<Consumer<ItemLike>> fill;

        Kind(String id, Supplier<ItemLike> icon, Consumer<Consumer<ItemLike>> fill) {
            this.id = id;
            this.icon = icon;
            this.fill = fill;
        }

        public String id() {
            return id;
        }
    }

    private static final List<DeferredHolder<CreativeModeTab, CreativeModeTab>> REGISTERED = new ArrayList<>();

    static {
        Kind previous = null;
        for (Kind kind : Kind.values()) {
            Kind before = previous;
            REGISTERED.add(TABS.register(kind.id, () -> {
                CreativeModeTab.Builder builder = CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.projectrebirth." + kind.id))
                        .icon(() -> new ItemStack(kind.icon.get()))
                        .displayItems((parameters, output) -> contents(kind).forEach(output::accept));
                if (before != null) {
                    builder.withTabsBefore(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ProjectRebirth.id(before.id)));
                }
                return builder.build();
            }));
            previous = kind;
        }
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    /** What a tab shows, in order. */
    public static List<ItemStack> contents(Kind kind) {
        List<ItemStack> out = new ArrayList<>();
        kind.fill.accept(item -> out.add(new ItemStack(item)));
        return out;
    }

    // ------------------------------------------------------------------ the tabs

    private static void resources(Consumer<ItemLike> out) {
        out.accept(CodexRegistry.REBIRTH_CODEX.get());
        for (Metal metal : Metal.values()) {
            out.accept(TechItems.ORES.get(metal).get());
            out.accept(TechItems.DEEPSLATE_ORES.get(metal).get());
        }
        for (Metal metal : Metal.values()) {
            out.accept(TechItems.RAW.get(metal).get());
            out.accept(TechItems.INGOTS.get(metal).get());
        }
        out.accept(TechItems.BRONZE_INGOT.get());
        out.accept(TechItems.CIRCUIT_BOARD.get());
        out.accept(TechItems.PROCESSOR.get());
        out.accept(TechItems.POWER_CELL.get());
        out.accept(TechItems.ACTUATOR.get());
        out.accept(TechItems.SYNTHETIC_FIBER.get());
        out.accept(MultiblockRegistry.HEAVY_ACTUATOR.get());
        out.accept(MultiblockRegistry.POWER_FRAME.get());
        out.accept(GarrisonRegistry.NEURAL_MATRIX.get());
    }

    private static void machines(Consumer<ItemLike> out) {
        for (Station station : Station.values()) {
            out.accept(TechItems.STATIONS.get(station).get());
        }
        out.accept(TechItems.MAINTENANCE_STATION.get());
        out.accept(TechItems.BODY_MODIFICATION_STATION.get());
        out.accept(TechItems.COMBUSTION_GENERATOR.get());
        out.accept(TechItems.SOLAR_PANEL.get());
        out.accept(TechItems.RADIOISOTOPE_GENERATOR.get());
        out.accept(TechItems.BATTERY.get());
        out.accept(TechItems.CABLE.get());
        out.accept(MultiblockRegistry.WORKSHOP_CONTROLLER_ITEM.get());
        out.accept(MultiblockRegistry.DOCK_CONTROLLER_ITEM.get());
        out.accept(MultiblockRegistry.ASSEMBLY_PLATE_ITEM.get());
        out.accept(MultiblockRegistry.ASSEMBLY_FRAME_ITEM.get());
        out.accept(MultiblockRegistry.HEAVY_FRAME_ITEM.get());
        out.accept(MultiblockRegistry.CRANE_BEAM_ITEM.get());
        out.accept(MultiblockRegistry.WELDING_MANIPULATOR_ITEM.get());
        out.accept(MultiblockRegistry.GRIPPER_MANIPULATOR_ITEM.get());
        out.accept(MultiblockRegistry.SCREWDRIVER_MANIPULATOR_ITEM.get());
        out.accept(MultiblockRegistry.ENERGY_PORT_ITEM.get());
        out.accept(MultiblockRegistry.CARGO_PORT_ITEM.get());
        out.accept(WeatherRegistry.WEATHER_STATION_ITEM.get());
        out.accept(WeatherRegistry.LONG_RANGE_MODULE.get());
        out.accept(MonitoringRegistry.MONITORING_STATION_ITEM.get());
        out.accept(MonitoringRegistry.CONTAMINATION_MAP.get());
        out.accept(FactionBlocks.FACTION_CORE_ITEM.get());
    }

    private static void bodies(Consumer<ItemLike> out) {
        out.accept(BodyItems.TRANSFER_CAPSULE.get());
        for (BodyClass bodyClass : BodyClass.values()) {
            out.accept(BodyItems.item(bodyClass));
        }
        out.accept(BodyItems.REPAIR_KIT.get());
        out.accept(BodyPowerRegistry.CHARGING_PAD_ITEM.get());
        out.accept(BodyPowerRegistry.BODY_BATTERY.get());
        out.accept(UpgradeRegistry.COPPER_PLATING.get());
        out.accept(UpgradeRegistry.IRON_PLATING.get());
        out.accept(UpgradeRegistry.TITANIUM_PLATING.get());
        out.accept(UpgradeRegistry.CHITIN_PLATING.get());
        out.accept(UpgradeRegistry.SEALING_MODULE.get());
        out.accept(UpgradeRegistry.JETPACK.get());
        out.accept(UpgradeRegistry.EXTRA_BATTERY.get());
        out.accept(UpgradeRegistry.COOLING_MODULE.get());
        out.accept(UpgradeRegistry.EMI_SHIELDING.get());
        out.accept(UpgradeRegistry.SENSOR_UNIT.get());
    }

    private static void powerArmor(Consumer<ItemLike> out) {
        for (ArmorVariant variant : ArmorVariant.values()) {
            out.accept(PowerArmorRegistry.item(variant));
        }
        out.accept(MultiblockRegistry.COMBAT_ARMOR_HULL.get());
        out.accept(MultiblockRegistry.MOBILE_ARMOR_HULL.get());
        out.accept(MultiblockRegistry.WORKER_ARMOR_HULL.get());
        out.accept(PowerArmorRegistry.ARMOR_PLATE.get());
        out.accept(PowerArmorRegistry.REPAIR_WRENCH.get());
        out.accept(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get());
        out.accept(PowerArmorRegistry.RESCUE_TOOL.get());
        out.accept(UpgradeRegistry.ARMOR_COPPER_PLATING.get());
        out.accept(UpgradeRegistry.ARMOR_IRON_PLATING.get());
        out.accept(UpgradeRegistry.ARMOR_TITANIUM_PLATING.get());
        out.accept(UpgradeRegistry.COMPRESSED_AIR_MODULE.get());
        out.accept(UpgradeRegistry.FLOODLIGHT.get());
        out.accept(UpgradeRegistry.EMI_SCREEN.get());
        out.accept(UpgradeRegistry.SENSOR_MODULE.get());
    }

    private static void weapons(Consumer<ItemLike> out) {
        out.accept(WeaponRegistry.ASSAULT_RIFLE.get());
        out.accept(WeaponRegistry.BATTLE_RIFLE.get());
        out.accept(WeaponRegistry.COMBAT_SHOTGUN.get());
        out.accept(WeaponRegistry.ASSAULT_RIFLE_AMMO.get());
        out.accept(WeaponRegistry.BATTLE_RIFLE_AMMO.get());
        out.accept(WeaponRegistry.SHOTGUN_SHELL.get());
        out.accept(GarrisonRegistry.ROCKET.get());
    }

    private static void garrison(Consumer<ItemLike> out) {
        out.accept(GarrisonRegistry.AI_CORE.get());
        out.accept(GarrisonRegistry.DAMAGED_AI_CORE.get());
        out.accept(GarrisonRegistry.COMBAT_MODULE.get());
        out.accept(GarrisonRegistry.WORK_MODULE.get());
        out.accept(GarrisonRegistry.CONSOLE_ITEM.get());
        out.accept(GarrisonRegistry.TABLET.get());
        out.accept(GarrisonRegistry.SERVICE_POST_ITEM.get());
        // 1.8.0, block J: the storage
        out.accept(com.projectrebirth.logistics.LogisticsRegistry.STORAGE_CONTROLLER_ITEM.get());
        out.accept(com.projectrebirth.logistics.LogisticsRegistry.STORAGE_RACK_ITEM.get());
        out.accept(com.projectrebirth.logistics.LogisticsRegistry.STORAGE_ROOF_ITEM.get());
        out.accept(GarrisonRegistry.SERVICE_POST_PLATE_ITEM.get());
        out.accept(GarrisonRegistry.TURRET_MOUNT_ITEM.get());
        out.accept(GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get());
        out.accept(GarrisonRegistry.ROCKET_TURRET_ITEM.get());
        out.accept(GarrisonRegistry.SMALL_ROBOT_ITEM.get());
        out.accept(GarrisonRegistry.GUARD_MECH_ITEM.get());
        out.accept(GarrisonRegistry.MECH_ARMOR_HULL.get());
        out.accept(GarrisonRegistry.MACHINE_GUN_NODE.get());
        out.accept(GarrisonRegistry.ROCKET_LAUNCHER_NODE.get());
        out.accept(GarrisonRegistry.STABILIZER.get());
    }

    private static void survival(Consumer<ItemLike> out) {
        out.accept(EnvironmentRegistry.DOSIMETER.get());
        out.accept(Scouting.FIELD_ANALYZER.get());
        out.accept(EnvironmentRegistry.ANTIRADIN.get());
        out.accept(EnvironmentRegistry.SORBENT.get());
        out.accept(EnvironmentRegistry.ANTITOXIN.get());
        out.accept(EnvironmentRegistry.ANTISEPTIC.get());
        out.accept(EnvironmentRegistry.RESPIRATOR.get());
        out.accept(EnvironmentRegistry.FILTER.get());
        out.accept(EnvironmentRegistry.BIOFILTER.get());
        out.accept(EnvironmentRegistry.HAZMAT_HELMET.get());
        out.accept(EnvironmentRegistry.HAZMAT_SUIT.get());
        out.accept(EnvironmentRegistry.HAZMAT_TROUSERS.get());
        out.accept(EnvironmentRegistry.HAZMAT_BOOTS.get());
        out.accept(EnvironmentRegistry.HEAVY_SUIT_HELMET.get());
        out.accept(EnvironmentRegistry.HEAVY_SUIT.get());
        out.accept(EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get());
        out.accept(EnvironmentRegistry.HEAVY_SUIT_BOOTS.get());
        out.accept(TemperatureRegistry.THERMAL_LINER.get());
        out.accept(TemperatureRegistry.HEATER_ITEM.get());
        out.accept(ShelterRegistry.SHELTER_CONTROL_PANEL_ITEM.get());
        out.accept(ShelterRegistry.BACKUP_BATTERY_ITEM.get());
        out.accept(ShelterRegistry.AIR_FILTRATION_UNIT_ITEM.get());
        out.accept(ShelterRegistry.FILTER_CARTRIDGE.get());
        out.accept(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get());
        out.accept(ShelterRegistry.AIRLOCK_DOOR_ITEM.get());
        out.accept(ShelterRegistry.SEALED_HATCH_ITEM.get());
        out.accept(ShelterRegistry.DECONTAMINATION_CHAMBER_ITEM.get());
        out.accept(ShelterRegistry.DECONTAMINATION_SOLUTION.get());
        out.accept(ShelterRegistry.RADIATION_PURGE_STATION_ITEM.get());
        out.accept(WaterRegistry.WATER_PURIFIER_ITEM.get());
        out.accept(WaterRegistry.POLLUTED_WATER_BOTTLE.get());
        out.accept(WaterRegistry.POLLUTED_WATER_BUCKET.get());
    }

    private static void world(Consumer<ItemLike> out) {
        out.accept(HostileRegistry.GUARD_TURRET_SPAWN_EGG.get());
        out.accept(HostileRegistry.PATROL_ROBOT_SPAWN_EGG.get());
        out.accept(HostileRegistry.MECH_SPAWN_EGG.get());
        out.accept(HostileRegistry.MUTAGEN.get());
        out.accept(HostileRegistry.TOXIC_GLAND.get());
        out.accept(HostileRegistry.CHITIN_PLATE.get());
        out.accept(HostileRegistry.RADIOACTIVE_BONE.get());
        out.accept(HostileRegistry.MUTATED_HOUND_SPAWN_EGG.get());
        out.accept(HostileRegistry.CRAWLER_SPAWN_EGG.get());
        out.accept(HostileRegistry.SCREAMER_SPAWN_EGG.get());
        out.accept(HostileRegistry.RAT_SPAWN_EGG.get());
        out.accept(HostileRegistry.HUNTER_SPAWN_EGG.get());
        out.accept(HostileRegistry.DROWNER_SPAWN_EGG.get());
        out.accept(HostileRegistry.GLOWING_GHOUL_SPAWN_EGG.get());
        out.accept(HostileRegistry.BRUTE_SPAWN_EGG.get());
        out.accept(HostileRegistry.MUTATED_COW_SPAWN_EGG.get());
        out.accept(HostileRegistry.MUTATED_PIG_SPAWN_EGG.get());
        out.accept(HostileRegistry.MUTATED_CHICKEN_SPAWN_EGG.get());
        out.accept(SourceRegistry.RADIOACTIVE_DEBRIS_ITEM.get());
        out.accept(SourceRegistry.LEAKING_WASTE_BARREL_ITEM.get());
        out.accept(SourceRegistry.IRRADIATED_SLUDGE_ITEM.get());
        out.accept(StructureBlocks.WALL_MONITOR_ITEM.get());
        out.accept(StructureBlocks.SERVER_RACK_ITEM.get());
        out.accept(StructureBlocks.SPECIMEN_TANK_ITEM.get());
        out.accept(StructureBlocks.EMERGENCY_LAMP_ITEM.get());
        out.accept(StructureBlocks.TURRET_ITEM.get());
        out.accept(StructureBlocks.WRECKED_TURRET_ITEM.get());
        out.accept(StructureBlocks.AMMO_CRATE_ITEM.get());
        out.accept(StructureBlocks.BROKEN_WALL_MONITOR_ITEM.get());
        out.accept(StructureBlocks.BROKEN_SERVER_RACK_ITEM.get());
        out.accept(StructureBlocks.BROKEN_SPECIMEN_TANK_ITEM.get());
        StructureBlocks.RUSTED_ITEMS.values().forEach(item -> out.accept(item.get()));
    }
}
