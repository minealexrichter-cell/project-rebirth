package com.projectrebirth.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Development only (1.8.0): the blocks with windows that /rbdev ui check opens, in order, and where /rbdev ui bench puts
 * them (the radiation purge station has no window). Shared by the server command and the client check, so both agree on the places.
 */
public final class DevUiBench {
    public static final List<String> BLOCKS = List.of(
            "electronics_fabricator", "assembly_station", "armor_workstation", "implant_fabricator", "armor_upgrade_fabricator",
            "weapon_workshop", "maintenance_station", "body_modification_station", "combustion_generator", "solar_panel", "radioisotope_generator", "battery", "backup_battery", "air_filtration_unit", "shelter_control_panel", "water_purifier", "weather_station", "heater", "decontamination_chamber", "radiation_purge_station",
            "monitoring_station", "multiblock_controller", "body_workshop_controller",
            "heavy_dock_controller", "console", "transfer_capsule", "turret_mount", "service_post");

    private DevUiBench() {
    }

    public static BlockPos place(BlockPos origin, int index) {
        return origin.offset(index * 3, 0, 0);
    }
}
