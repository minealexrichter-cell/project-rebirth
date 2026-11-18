package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rebirth showcase} (1.7.3, block G): builds, from where the operator stands, a plot with every model the owner has not
 * accepted yet, each under a sign: the enemies of the ruins and their wrecks, the mutants, the garrison's machines standing, disabled
 * and as wrecks, the bodies and their wrecks, the armors, the blocks, the two multiblocks and the items in frames. For the
 * development environment only (the owner's decision): the package {@code gametest} is left out of the released jar.
 * The plot goes towards +x and +z, rows 8 blocks apart; look at it from the south (the signs face +z).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Showcase {
    private static final String P = ProjectRebirth.MOD_ID + ":";
    private static final int WIDTH = 96;
    private static final int DEPTH = 96;

    private Showcase() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rebirth").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("showcase").executes(ctx -> build(ctx.getSource()))));
    }

    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        // a flat floor and clear air above it
        for (int x = -2; x < WIDTH; x++) {
            for (int z = -4; z < DEPTH; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 14; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        List<String> commands = new ArrayList<>();
        int z = 0;
        // the enemies of the ruins and the wrecks they leave
        int x = 0;
        x = entity(commands, x, z, "guard_turret", "", "stoit");
        x = entity(commands, x, z, "patrol_robot", "", "stoit");
        x = entity(commands, x, z, "mech", "", "stoit");
        x = remains(commands, x, z, 0, "patrol_robot");
        x = remains(commands, x, z, 1, "mech");
        x = remains(commands, x, z, 2, "brute");
        // the mutants
        z += 8;
        x = 0;
        x = entity(commands, x, z, "mutated_hound", "", "stoit");
        x = entity(commands, x, z, "glowing_ghoul", "", "stoit");
        x = entity(commands, x, z, "brute", "", "stoit");
        // the garrison: standing, disabled, wreck
        String[][] states = {{"stoit", ""}, {"otkl", "machine:{structural:0.0f,critical:10.0f,state:1}"},
                {"ostov", "machine:{structural:0.0f,critical:0.0f,state:2}"}};
        for (String[] state : states) {
            z += 8;
            x = 0;
            String machine = state[1];
            String android = (machine.isEmpty() ? "" : machine + ",");
            x = entity(commands, x, z, "guard_mech", machine, state[0]) + 2;
            x = entity(commands, x, z, "small_robot", machine, state[0]);
            commands.add(String.format("setblock ~%d ~ ~%d %sturret_mount", x, z, P));
            x = entity(commands, x, z, 1, "machine_gun_turret", machine, state[0]);
            commands.add(String.format("setblock ~%d ~ ~%d %sturret_mount", x, z, P));
            x = entity(commands, x, z, 1, "rocket_turret", machine, state[0]);
            x = entity(commands, x, z, "android_advanced_synthetic_body", android
                    + "AiCore:{id:\"" + P + "ai_core\",count:1},Module:{id:\"" + P + "combat_module\",count:1}", state[0] + " boevoi");
            x = entity(commands, x, z, "android_basic_robot_body", android
                    + "AiCore:{id:\"" + P + "ai_core\",count:1},Module:{id:\"" + P + "work_module\",count:1}", state[0] + " rabochii");
            if ("stoit".equals(state[0])) {
                // an empty crate removes itself: it carries a turret
                x = entity(commands, x, z, "turret_crate", "Item:{id:\"" + P + "machine_gun_turret\",count:1}", state[0]);
            }
        }
        // the bodies, their wrecks and the three armors
        z += 8;
        x = 0;
        String[] bodies = {"basic_robot_body", "improved_robot_body", "advanced_synthetic_body", "hightech_synthetic_body", "premium_synthetic_body"};
        for (String body : bodies) {
            x = entity(commands, x, z, body, "", "stoit");
        }
        for (int i = 0; i < bodies.length; i++) {
            x = remains(commands, x, z, 3 + i, bodies[i]);
        }
        for (String variant : new String[]{"combat", "mobile", "worker"}) {
            x = entity(commands, x, z, "power_armor", "Variant:\"" + variant + "\",Initialized:1b", variant);
        }
        // the blocks
        z += 8;
        x = 0;
        String[] blocks = {"wall_monitor", "broken_wall_monitor", "server_rack", "broken_server_rack", "specimen_tank", "broken_specimen_tank",
                "ammo_crate", "emergency_lamp", "leaking_waste_barrel", "toxic_puddle", "radioactive_debris", "irradiated_sludge", "turret",
                "wrecked_turret", "weather_station", "monitoring_station", "water_purifier", "radioisotope_generator", "uranium_ore",
                "deepslate_uranium_ore", "console", "service_post", "service_post_plate", "turret_mount", "implant_fabricator",
                "body_modification_station", "armor_upgrade_fabricator", "weapon_workshop"};
        for (String block : blocks) {
            x = block(commands, x, z, block);
        }
        z += 6;
        x = 0;
        for (String machine : new String[]{"electronics_fabricator", "assembly_station", "armor_workstation", "maintenance_station",
                "implant_fabricator", "body_modification_station", "armor_upgrade_fabricator", "weapon_workshop", "combustion_generator",
                "battery", "solar_panel", "air_filtration_unit", "shelter_control_panel", "heater", "backup_battery"}) {
            x = block(commands, x, z, "rusted_" + machine);
        }
        // the multiblocks, assembling
        z += 8;
        commands.add(String.format("rbdev workshop ~0 ~ ~%d workshop_basic_robot_body", z));
        commands.add(String.format("rbdev dock ~24 ~ ~%d dock_guard_mech_gun_rocket", z));
        // the items, in frames on a wall
        z += 22;
        String[] items = {"assault_rifle", "battle_rifle", "combat_shotgun", "assault_rifle_ammo", "battle_rifle_ammo", "shotgun_shell",
                "tactical_tablet", "ai_core", "damaged_ai_core", "neural_matrix", "combat_module", "work_module", "machine_gun_node",
                "rocket_launcher_node", "rocket", "stabilizer", "mech_armor_hull", "field_analyzer", "contamination_map",
                "long_range_forecast_module", "sensor_module", "sensor_unit", "repair_wrench", "upgrade_mounting_tool", "copper_plating",
                "iron_plating", "titanium_plating", "sealing_module", "jetpack", "compressed_air_module", "floodlight", "armor_copper_plating",
                "armor_iron_plating", "armor_titanium_plating", "extra_battery", "cooling_module", "thermal_liner", "emi_shielding", "emi_screen",
                "combat_armor_ability", "mobile_armor_ability", "worker_armor_ability", "guard_mech", "small_robot", "machine_gun_turret",
                "rocket_turret"};
        // the frames face south (+z), so they hang on the south side of a wall one block north of them
        commands.add(String.format("fill ~0 ~ ~%d ~%d ~3 ~%d minecraft:white_concrete", z - 1, items.length, z - 1));
        for (int i = 0; i < items.length; i++) {
            int row = i % 2;
            int col = i / 2 * 2 + row;
            commands.add(String.format("summon minecraft:item_frame ~%d.5 ~%d ~%d.5 {Facing:3b,Fixed:1b,Item:{id:\"%s%s\",count:1}}",
                    col, 1 + row * 2, z, P, items[i]));
        }
        CommandSourceStack quiet = source.withPosition(Vec3.atBottomCenterOf(origin)).withSuppressedOutput();
        int failed = 0;
        for (String command : commands) {
            try {
                source.getServer().getCommands().getDispatcher().execute(command, quiet);
            } catch (Exception e) {
                failed++;
                ProjectRebirth.LOGGER.warn("Showcase: {} failed: {}", command, e.getMessage());
            }
        }
        int done = commands.size() - failed;
        source.sendSuccess(() -> Component.literal("Showcase: " + done + " of " + commands.size() + " placed"), true);
        return 1;
    }

    private static int entity(List<String> commands, int x, int z, String id, String nbt, String label) {
        return entity(commands, x, z, 0, id, nbt, label);
    }

    /** An entity standing still at (x, z), facing the viewer, with its sign; returns the next free x. */
    private static int entity(List<String> commands, int x, int z, int up, String id, String nbt, String label) {
        String data = "NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Rotation:[0.0f,0.0f]" + (nbt.isEmpty() ? "" : "," + nbt);
        commands.add(String.format("summon %s%s ~%d.5 ~%d ~%d.5 {%s}", P, id, x, up, z, data));
        sign(commands, x, z + 3, "entity." + ProjectRebirth.MOD_ID + "." + id, label);
        return x + 4;
    }

    private static int remains(List<String> commands, int x, int z, int kind, String of) {
        commands.add(String.format("summon %sremains ~%d.5 ~ ~%d.5 {Kind:%d,Rotation:[0.0f,0.0f]}", P, x, z, kind));
        sign(commands, x, z + 3, "entity." + ProjectRebirth.MOD_ID + "." + of, "oblomki");
        return x + 4;
    }

    private static int block(List<String> commands, int x, int z, String id) {
        commands.add(String.format("setblock ~%d ~ ~%d %s%s", x, z, P, id));
        sign(commands, x, z + 2, "block." + ProjectRebirth.MOD_ID + "." + id, "");
        return x + 3;
    }

    /** A standing sign facing south: the translated name and a short label (Latin letters: see {@link #label}). */
    private static void sign(List<String> commands, int x, int z, String key, String label) {
        commands.add(String.format("setblock ~%d ~ ~%d minecraft:oak_sign[rotation=0]{front_text:{messages:['{\"translate\":\"%s\"}','{\"text\":\"%s\"}','\"\"','\"\"']}}",
                x, z, key, label(label)));
    }

    /** The labels are written in Latin letters in the code; this turns them into the Russian words shown on the signs. */
    private static String label(String latin) {
        return latin.replace("stoit", "стоит").replace("otkl", "отключён")
                .replace("ostov", "остов").replace("oblomki", "обломки")
                .replace("boevoi", "боевой").replace("rabochii", "рабочий")
                .replace("combat", "боевая").replace("mobile", "мобильная")
                .replace("worker", "рабочая");
    }
}
