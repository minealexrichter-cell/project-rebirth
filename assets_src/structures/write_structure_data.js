// Writes the data of the 1.6.0 structures (block B2): worldgen structures, template pools, the decay processor list, structure
// sets with the switchable placement, biome tags and chest loot tables. Usage from the project root:
//   node assets_src/structures/write_structure_data.js
// Frequency is in the structure sets (spacing and separation in chunks), biomes in the tags, loot in loot_table/chests/.
const fs = require('fs');
const path = require('path');

const DATA = path.join(__dirname, '../../src/main/resources/data/projectrebirth');
const write = (rel, obj) => {
    const file = path.join(DATA, rel);
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
};

const STRUCTURES = {
    abandoned_lab: {checkRadius: 8, spacing: 28, separation: 9, salt: 160601,
        biomes: ['#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow', 'minecraft:savanna', 'minecraft:swamp']},
    industrial_ruins: {checkRadius: 12, spacing: 40, separation: 14, salt: 160602,
        biomes: ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:desert', '#minecraft:is_badlands', 'minecraft:taiga', 'minecraft:snowy_plains']},
    ruined_reactor: {checkRadius: 16, spacing: 80, separation: 30, salt: 160603,
        biomes: ['minecraft:plains', 'minecraft:savanna', 'minecraft:desert', '#minecraft:is_badlands', 'minecraft:snowy_plains']},
    // the large structures (after the owner's concept art): rarer than the small ruins, on flatter ground
    military_base: {checkRadius: 22, maxSlope: 8, spacing: 64, separation: 24, salt: 160611,
        biomes: ['minecraft:plains', 'minecraft:snowy_plains', 'minecraft:savanna', 'minecraft:taiga', 'minecraft:snowy_taiga', 'minecraft:forest']},
    science_complex: {checkRadius: 20, maxSlope: 8, spacing: 60, separation: 22, salt: 160612,
        biomes: ['#minecraft:is_forest', 'minecraft:taiga', 'minecraft:plains', 'minecraft:meadow']},
    abandoned_factory: {checkRadius: 24, maxSlope: 8, spacing: 60, separation: 22, salt: 160613,
        biomes: ['minecraft:plains', 'minecraft:savanna', 'minecraft:desert', '#minecraft:is_badlands', 'minecraft:snowy_plains']},
    comm_station: {checkRadius: 16, maxSlope: 8, spacing: 56, separation: 20, salt: 160614,
        biomes: ['minecraft:plains', 'minecraft:meadow', 'minecraft:taiga', 'minecraft:snowy_plains', 'minecraft:savanna']},
    defensive_outpost: {checkRadius: 18, maxSlope: 7, spacing: 44, separation: 16, salt: 160615,
        biomes: ['#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains', 'minecraft:savanna', 'minecraft:snowy_plains']},
    contamination_zone: {checkRadius: 17, maxSlope: 8, spacing: 64, separation: 24, salt: 160616,
        biomes: ['#minecraft:is_forest', 'minecraft:swamp', 'minecraft:taiga', 'minecraft:plains', 'minecraft:dark_forest']}
};

for (const [name, s] of Object.entries(STRUCTURES)) {
    write(`worldgen/structure/${name}.json`, {
        // a jigsaw that keeps off water and steep slopes (structures/DryJigsawStructure.java)
        type: 'projectrebirth:dry_jigsaw',
        check_radius: s.checkRadius,
        max_slope: s.maxSlope || 6,
        biomes: `#projectrebirth:has_structure/${name}`,
        // the large ones go in after the trees (top_layer_modification), so that their clearing keeps trees out of yards and halls
        step: s.maxSlope ? 'top_layer_modification' : 'surface_structures',
        spawn_overrides: {},
        terrain_adaptation: 'beard_thin',
        start_pool: `projectrebirth:ruins/${name}`,
        size: 1,
        // two blocks down: the foundation goes into the ground and the floor lies at the surface
        start_height: {absolute: -2},
        project_start_to_heightmap: 'WORLD_SURFACE_WG',
        max_distance_from_center: 80,
        use_expansion_hack: false
    });
    write(`worldgen/template_pool/ruins/${name}.json`, {
        fallback: 'minecraft:empty',
        elements: [{weight: 1, element: {element_type: 'minecraft:single_pool_element', location: `projectrebirth:ruins/${name}`,
            processors: 'projectrebirth:ruins_decay', projection: 'rigid'}}]
    });
    write(`worldgen/structure_set/${name}.json`, {
        structures: [{structure: `projectrebirth:${name}`, weight: 1}],
        placement: {type: 'projectrebirth:switchable_spread', spacing: s.spacing, separation: s.separation, salt: s.salt, switch: name}
    });
    write(`tags/worldgen/biome/has_structure/${name}.json`, {values: s.biomes});
}

// Decay: each placement cracks, rusts and breaks a share of the blocks, so no two ruins look alike.
const rule = (block, probability, output, props) => ({
    input_predicate: {predicate_type: 'minecraft:random_block_match', block, probability},
    location_predicate: {predicate_type: 'minecraft:always_true'},
    output_state: props ? {Name: output, Properties: props} : {Name: output}
});
write('worldgen/processor_list/ruins_decay.json', {processors: [{processor_type: 'minecraft:rule', rules: [
    rule('minecraft:white_concrete', 0.12, 'minecraft:light_gray_concrete'),
    rule('minecraft:white_concrete', 0.06, 'minecraft:air'),
    rule('minecraft:light_gray_concrete', 0.1, 'minecraft:gray_concrete'),
    rule('minecraft:light_gray_concrete', 0.08, 'minecraft:air'),
    rule('minecraft:gray_concrete', 0.06, 'minecraft:air'),
    rule('minecraft:glass_pane', 0.6, 'minecraft:air'),
    rule('minecraft:stone_bricks', 0.3, 'minecraft:cracked_stone_bricks'),
    rule('minecraft:stone_bricks', 0.1, 'minecraft:mossy_stone_bricks'),
    rule('minecraft:smooth_stone', 0.08, 'minecraft:gravel'),
    rule('minecraft:polished_andesite', 0.1, 'minecraft:andesite'),
    rule('minecraft:polished_andesite', 0.05, 'minecraft:gravel'),
    rule('minecraft:iron_block', 0.15, 'minecraft:raw_iron_block'),
    rule('minecraft:weathered_cut_copper', 0.3, 'minecraft:oxidized_cut_copper'),
    rule('minecraft:weathered_cut_copper', 0.1, 'minecraft:air'),
    rule('minecraft:deepslate_tiles', 0.25, 'minecraft:cracked_deepslate_tiles'),
    rule('minecraft:deepslate_tiles', 0.04, 'minecraft:air'),
    rule('minecraft:cobweb', 0.4, 'minecraft:air'),
    // the large structures
    rule('minecraft:deepslate_bricks', 0.25, 'minecraft:cracked_deepslate_bricks'),
    rule('minecraft:deepslate_bricks', 0.03, 'minecraft:air'),
    rule('minecraft:black_stained_glass_pane', 0.45, 'minecraft:air'),
    rule('minecraft:blue_stained_glass_pane', 0.35, 'minecraft:air'),
    rule('minecraft:bricks', 0.04, 'minecraft:air'),
    rule('minecraft:iron_bars', 0.12, 'minecraft:air'),
    rule('minecraft:mossy_stone_bricks', 0.08, 'minecraft:air')
]}]});

// ------------------------------------------------------------------ loot: the value grows with the danger
const item = (name, weight, min, max, extra) => {
    const entry = {type: 'minecraft:item', name, weight};
    const functions = [];
    if (min !== undefined && (min !== 1 || max !== 1)) functions.push({function: 'minecraft:set_count', count: {type: 'minecraft:uniform', min, max}});
    if (extra) functions.push(...extra);
    if (functions.length) entry.functions = functions;
    return entry;
};
const components = c => ({function: 'minecraft:set_components', components: c});
// a packed body that has been lying here: low integrity and much wear; repair and service it before use
const wornBody = (name, weight, integrity, wear) => item(name, weight, 1, 1, [components({'projectrebirth:stored_integrity': integrity, 'projectrebirth:stored_wear': wear})]);
// a packed armor wrecked to the edge: both integrities low
const damagedArmor = (name, weight, structural, critical) => item(name, weight, 1, 1, [components({'projectrebirth:stored_structural': structural, 'projectrebirth:stored_critical': critical})]);
const pool = (min, max, entries) => ({rolls: {type: 'minecraft:uniform', min, max}, entries});
const table = pools => ({type: 'minecraft:chest', pools, random_sequence: undefined});

const P = 'projectrebirth:';
write('loot_table/chests/abandoned_lab.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'circuit_board', 10, 1, 3), item(P + 'processor', 5, 1, 2), item(P + 'synthetic_fiber', 8, 2, 5),
        item(P + 'power_cell', 6, 1, 2), item(P + 'cable', 8, 2, 6), item(P + 'sorbent', 6, 1, 3),
        item('minecraft:glass_bottle', 6, 1, 3), item('minecraft:redstone', 6, 2, 6), item('minecraft:paper', 5, 1, 4)]),
    pool(1, 2, [
        item(P + 'antiradin', 8, 1, 2), item(P + 'decontamination_solution', 6, 1, 2), item(P + 'filter_cartridge', 6, 1, 1),
        item(P + 'filter', 5, 1, 2), item(P + 'respirator', 3, 1, 1), item(P + 'dosimeter', 2, 1, 1), item(P + 'repair_kit', 2, 1, 1)])
]});
write('loot_table/chests/industrial_ruins.json', {type: 'minecraft:chest', pools: [
    pool(3, 6, [
        item(P + 'tin_ingot', 8, 2, 6), item(P + 'aluminum_ingot', 8, 2, 6), item(P + 'bronze_ingot', 6, 2, 5),
        item(P + 'silver_ingot', 4, 1, 4), item(P + 'lithium_ingot', 4, 1, 3), item(P + 'titanium_ingot', 3, 1, 3),
        item('minecraft:iron_ingot', 8, 2, 8), item('minecraft:copper_ingot', 6, 3, 9), item(P + 'cable', 6, 2, 8)]),
    pool(1, 3, [
        item(P + 'actuator', 8, 1, 2), item(P + 'circuit_board', 6, 1, 3), item(P + 'iron_plating', 6, 1, 2),
        item(P + 'armor_plate', 5, 1, 2), item(P + 'battery', 5, 1, 1), item(P + 'repair_kit', 4, 1, 1),
        item(P + 'cooling_module', 2, 1, 1), item(P + 'extra_battery', 2, 1, 1)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 6},
        wornBody(P + 'basic_robot_body', 3, 8.0, 65.0), wornBody(P + 'improved_robot_body', 1, 10.0, 70.0)])
]});
write('loot_table/chests/ruined_reactor.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'processor', 8, 2, 4), item(P + 'titanium_ingot', 8, 2, 6), item(P + 'uranium_ingot', 5, 1, 3),
        item(P + 'titanium_plating', 5, 1, 2), item(P + 'power_cell', 6, 2, 4), item(P + 'circuit_board', 6, 2, 5),
        item(P + 'armor_titanium_plating', 3, 1, 2)]),
    pool(1, 2, [
        item(P + 'emi_shielding', 5, 1, 1), item(P + 'sealing_module', 5, 1, 1), item(P + 'compressed_air_module', 4, 1, 1),
        item(P + 'cooling_module', 4, 1, 1), item(P + 'extra_battery', 4, 1, 1), item(P + 'heavy_suit_helmet', 2, 1, 1),
        item(P + 'jetpack', 1, 1, 1), item(P + 'antiradin', 6, 1, 3)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 4},
        wornBody(P + 'advanced_synthetic_body', 2, 12.0, 60.0), wornBody(P + 'improved_robot_body', 3, 12.0, 55.0),
        damagedArmor(P + 'worker_power_armor', 2, 12.0, 8.0), damagedArmor(P + 'mobile_power_armor', 2, 14.0, 9.0),
        damagedArmor(P + 'power_armor', 1, 18.0, 10.0)])
]});

// ------------------------------------------------------------------ loot of the large structures
const weapon = (name, weight) => item(P + name, weight, 1, 1);
write('loot_table/chests/military_base.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'assault_rifle_ammo', 10, 8, 24), item(P + 'battle_rifle_ammo', 7, 5, 15), item(P + 'shotgun_shell', 8, 4, 12),
        item(P + 'armor_plate', 6, 1, 3), item(P + 'iron_plating', 5, 1, 2), item(P + 'power_cell', 5, 1, 3),
        item(P + 'repair_kit', 4, 1, 2), item('minecraft:iron_ingot', 6, 3, 9), item(P + 'circuit_board', 4, 1, 3)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 5},
        weapon('assault_rifle', 3), weapon('combat_shotgun', 3), weapon('battle_rifle', 2),
        item(P + 'armor_iron_plating', 3, 1, 1), item(P + 'armor_titanium_plating', 1, 1, 1), item(P + 'extra_battery', 2, 1, 1)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 8},
        damagedArmor(P + 'power_armor', 1, 16.0, 9.0), wornBody(P + 'improved_robot_body', 2, 10.0, 65.0)])
]});
write('loot_table/chests/science_complex.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'processor', 10, 1, 3), item(P + 'circuit_board', 10, 2, 4), item(P + 'synthetic_fiber', 8, 2, 6),
        item(P + 'power_cell', 6, 1, 3), item(P + 'cable', 6, 2, 6), item('minecraft:glass_bottle', 4, 1, 3), item('minecraft:redstone', 6, 3, 8)]),
    pool(1, 2, [
        item(P + 'antiradin', 8, 1, 3), item(P + 'decontamination_solution', 6, 1, 2), item(P + 'dosimeter', 4, 1, 1),
        item(P + 'emi_shielding', 3, 1, 1), item(P + 'sealing_module', 2, 1, 1), item(P + 'filter_cartridge', 5, 1, 2), item(P + 'hazmat_helmet', 2, 1, 1)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 6},
        wornBody(P + 'advanced_synthetic_body', 2, 10.0, 60.0), wornBody(P + 'hightech_synthetic_body', 1, 8.0, 70.0)])
]});
write('loot_table/chests/abandoned_factory.json', {type: 'minecraft:chest', pools: [
    pool(4, 7, [
        item(P + 'tin_ingot', 8, 2, 8), item(P + 'aluminum_ingot', 8, 2, 8), item(P + 'bronze_ingot', 6, 2, 6),
        item(P + 'titanium_ingot', 4, 1, 4), item(P + 'lithium_ingot', 3, 1, 3), item('minecraft:iron_ingot', 8, 3, 10),
        item('minecraft:copper_ingot', 6, 4, 12), item('minecraft:coal', 6, 4, 12), item(P + 'cable', 5, 3, 8)]),
    pool(1, 3, [
        item(P + 'actuator', 8, 1, 3), item(P + 'iron_plating', 6, 1, 3), item(P + 'copper_plating', 5, 1, 3),
        item(P + 'armor_plate', 5, 1, 2), item(P + 'repair_kit', 4, 1, 1), item(P + 'armor_copper_plating', 2, 1, 1)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 6},
        damagedArmor(P + 'worker_power_armor', 2, 10.0, 7.0), wornBody(P + 'basic_robot_body', 3, 8.0, 70.0)])
]});
write('loot_table/chests/comm_station.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'circuit_board', 10, 2, 5), item(P + 'processor', 8, 1, 3), item(P + 'cable', 8, 4, 10),
        item(P + 'power_cell', 6, 1, 3), item(P + 'silver_ingot', 5, 1, 4), item('minecraft:copper_ingot', 6, 3, 8), item('minecraft:redstone', 6, 4, 10)]),
    pool(1, 2, [
        item(P + 'emi_shielding', 5, 1, 1), item(P + 'extra_battery', 4, 1, 1), item(P + 'battery', 4, 1, 1),
        item(P + 'floodlight', 3, 1, 1), item(P + 'dosimeter', 3, 1, 1), item(P + 'repair_kit', 3, 1, 1)])
]});
write('loot_table/chests/defensive_outpost.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'assault_rifle_ammo', 8, 6, 18), item(P + 'shotgun_shell', 8, 4, 10), item(P + 'battle_rifle_ammo', 4, 3, 10),
        item(P + 'armor_plate', 5, 1, 2), item('minecraft:iron_ingot', 8, 2, 6), item(P + 'filter', 5, 1, 2), item('minecraft:bread', 5, 2, 5)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 6},
        weapon('combat_shotgun', 3), weapon('assault_rifle', 2), item(P + 'repair_kit', 4, 1, 1), item(P + 'respirator', 3, 1, 1)])
]});
write('loot_table/chests/contamination_zone.json', {type: 'minecraft:chest', pools: [
    pool(3, 5, [
        item(P + 'uranium_ingot', 6, 1, 3), item(P + 'raw_uranium', 6, 2, 5), item(P + 'titanium_ingot', 6, 1, 4),
        item(P + 'processor', 5, 1, 2), item(P + 'power_cell', 5, 1, 3), item(P + 'antiradin', 8, 1, 3), item(P + 'decontamination_solution', 6, 1, 2)]),
    pool(1, 2, [
        item(P + 'hazmat_suit', 3, 1, 1), item(P + 'hazmat_helmet', 3, 1, 1), item(P + 'sealing_module', 4, 1, 1),
        item(P + 'compressed_air_module', 3, 1, 1), item(P + 'emi_shielding', 3, 1, 1), item(P + 'jetpack', 1, 1, 1), item(P + 'dosimeter', 3, 1, 1)]),
    pool(0, 1, [
        {type: 'minecraft:empty', weight: 4},
        damagedArmor(P + 'power_armor', 2, 14.0, 8.0), damagedArmor(P + 'mobile_power_armor', 2, 12.0, 8.0),
        wornBody(P + 'premium_synthetic_body', 1, 10.0, 65.0)])
]});
console.log('structure data written');
