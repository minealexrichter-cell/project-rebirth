// Builds the data of the machine mind (1.9.0, block F): the base templates of its stages (structure/mind/stage_<n>.nbt and the start
// of the world generation), the stage data, block states, block models, item models, loot tables and tags.
// Usage, from the project root:  node assets_src/machine_mind/build_mind_data.js
// Templates: x east, y up, z south, 33 x 33, the computer in the middle of the bottom layer; the base looks north.
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '../../src/main/resources');
const DATA = path.join(ROOT, 'data/projectrebirth'), ASSETS = path.join(ROOT, 'assets/projectrebirth');
const {nbtFile, Build} = require('../structures/structure_lib');
const P = 'projectrebirth:';
const write = (file, value) => {
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, Buffer.isBuffer(value) ? value : JSON.stringify(value, null, 2) + '\n');
};

// ------------------------------------------------------------------ the base
const N = 33, M = 16;
const PLATE = P + 'mind_plating', CABLE = P + 'mind_cable', LAMP = P + 'mind_floodlight', GEN = P + 'mind_generator';
const FENCE = [P + 'mind_fence', {east: 'false', north: 'false', south: 'false', waterlogged: 'false', west: 'false'}];
const GATE = [P + 'mind_gate', {facing: 'north', in_wall: 'true', open: 'false', powered: 'false'}];
const unit = (id, facing) => [P + id, {facing: facing || 'north'}];
const pillar = (b, x, z, h, top) => { b.box(x, 0, z, x, h - 1, z, PLATE); b.set(x, h, z, top || LAMP); };

function stage(n) {
    const b = new Build(N, 10, N, 1900 + n);
    if (n === 1) {
        b.set(M, 0, M, unit('mind_computer'));
        b.set(M, 0, 22, unit('mind_factory_line'));
        b.box(M, 0, 18, M, 0, 20, CABLE);
        // the first corners of a fence
        for (const [x, z, dx, dz] of [[8, 8, 1, 1], [24, 8, -1, 1], [8, 24, 1, -1], [24, 24, -1, -1]]) {
            for (let i = 0; i < 3; i++) { b.set(x + dx * i, 0, z, FENCE); b.set(x, 0, z + dz * i, FENCE); }
        }
    } else if (n === 2) {
        for (let y = 0; y < 2; y++) for (let i = 7; i <= 25; i++) {
            for (const [x, z] of [[i, 7], [i, 25], [7, i], [25, i]]) {
                const gate = Math.abs(x - M) <= 1 && (z === 7 || z === 25);
                if (gate) { if (y === 0) b.set(x, 0, z, GATE); } else b.set(x, y, z, FENCE);
            }
        }
        for (const [x, z] of [[7, 7], [25, 7], [7, 25], [25, 25]]) pillar(b, x, z, 4);
        b.set(11, 0, 16, GEN); b.set(21, 0, 16, GEN);
        b.box(12, 0, 16, 14, 0, 16, CABLE); b.box(18, 0, 16, 20, 0, 16, CABLE);
    } else if (n === 3) {
        b.set(10, 0, 22, unit('mind_foundry'));
        b.set(22, 0, 22, unit('mind_factory_line'));
        b.box(12, 0, 22, 14, 0, 22, CABLE); b.box(18, 0, 22, 20, 0, 22, CABLE);
        for (let y = 0; y < 4; y++) for (let i = 3; i <= 29; i++) {
            for (const [x, z] of [[i, 3], [i, 29], [3, i], [29, i]]) {
                const gate = Math.abs(x - M) <= 1 && (z === 3 || z === 29);
                if (gate && y < 3) { if (y === 0) b.set(x, 0, z, GATE); } else b.set(x, y, z, PLATE);
            }
        }
        for (const [x, z] of [[13, 3], [19, 3], [13, 29], [19, 29]]) b.set(x, 4, z, LAMP);
    } else if (n === 4) {
        b.set(M, 0, 10, unit('mind_hangar', 'north'));
        b.set(11, 0, 12, GEN); b.set(21, 0, 12, GEN);
        b.box(11, 0, 13, 11, 0, 15, CABLE); b.box(21, 0, 13, 21, 0, 15, CABLE);
        for (const [x, z] of [[3, 3], [29, 3], [3, 29], [29, 29]]) {
            for (let y = 0; y < 6; y++) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) b.set(x + dx, y, z + dz, PLATE);
            b.set(x, 6, z, LAMP);
        }
    } else {
        for (const [x, z] of [[13, 13], [19, 13], [13, 19], [19, 19]]) { b.box(x, 0, z, x, 5, z, CABLE); b.set(x, 6, z, LAMP); }
        b.box(14, 5, 13, 18, 5, 13, CABLE); b.box(14, 5, 19, 18, 5, 19, CABLE);
        b.box(13, 5, 14, 13, 5, 18, CABLE); b.box(19, 5, 14, 19, 5, 18, CABLE);
        for (let i = 5; i <= 27; i += 2) for (const [x, z] of [[i, 3], [i, 29], [3, i], [29, i]]) b.set(x, 4, z, PLATE);
        b.set(9, 0, 13, GEN); b.set(23, 0, 13, GEN);
    }
    return b;
}
for (let n = 1; n <= 5; n++) write(path.join(DATA, `structure/mind/stage_${n}.nbt`), nbtFile(stage(n).toNbt()));

// the start in the world: stage I on a floor of plating flush with the ground, the ground over the yard taken away
const start = new Build(N, 6, N, 1900);
start.box(8, 1, 8, 24, 4, 24, 'minecraft:air');
for (const [k, b] of stage(1).cells) {
    const [x, y, z] = k.split(',').map(Number);
    start.set(x, y + 1, z, b);
    start.set(x, 0, z, PLATE);
}
// a placed template does not join its fences by itself
const cells = stage(1).cells, joins = (x, z) => { const c = cells.get(`${x},0,${z}`); return String(Array.isArray(c) && c[0] === FENCE[0]); };
for (const [k, b] of cells) if (Array.isArray(b) && b[0] === FENCE[0]) {
    const [x, y, z] = k.split(',').map(Number);
    start.set(x, y + 1, z, [FENCE[0], {east: joins(x + 1, z), north: joins(x, z - 1), south: joins(x, z + 1), waterlogged: 'false', west: joins(x - 1, z)}]);
}
start.box(M - 1, 0, 21, M + 1, 0, 23, PLATE);
write(path.join(DATA, 'structure/mind/start.nbt'), nbtFile(start.toNbt()));

// ------------------------------------------------------------------ stages (block G adds its robots)
const drone = (cap, seconds) => ({entity: P + 'mind_drone', cap, seconds, cost: 8, module: P + 'mind_factory_line'});
[[2, 60], [3, 60], [4, 50], [5, 50], [6, 40]].forEach(([cap, seconds], i) =>
    write(path.join(DATA, `projectrebirth/mind_stages/${i + 1}.json`), {stage: i + 1, template: `${P}mind/stage_${i + 1}`, robots: [drone(cap, seconds)]}));

// ------------------------------------------------------------------ world generation
write(path.join(DATA, 'worldgen/structure/mind_node.json'), {
    type: P + 'compound', biomes: '#projectrebirth:has_structure/mind_node', step: 'surface_structures', spawn_overrides: {},
    terrain_adaptation: 'beard_thin', check_radius: 14, max_slope: 5, size_x: N, size_z: N, processors: 'minecraft:empty',
    foundation: {Name: PLATE}, parts: [{template: P + 'mind/start', x: 0, z: 0, floor: 0}]});
write(path.join(DATA, 'worldgen/structure_set/mind_node.json'), {
    structures: [{structure: P + 'mind_node', weight: 1}],
    placement: {type: P + 'switchable_spread', spacing: 100, separation: 60, salt: 190619, switch: 'mind_node'}});
write(path.join(DATA, 'tags/worldgen/biome/has_structure/mind_node.json'), {values: ['plains', 'sunflower_plains', 'meadow', 'savanna', 'savanna_plateau', 'desert',
    'snowy_plains', 'taiga', 'forest', 'birch_forest', 'flower_forest', 'badlands'].map(id => 'minecraft:' + id)});

// ------------------------------------------------------------------ tags
const MIND = ['mind_plating', 'mind_cable', 'mind_floodlight', 'mind_generator', 'mind_fence', 'mind_gate'].map(id => P + id);
const ALL = ['mind_computer', 'mind_factory_line', 'mind_foundry', 'mind_hangar', 'mind_part', 'mind_computer_wreck'].map(id => P + id).concat(MIND);
write(path.join(DATA, 'tags/block/mind_blocks.json'), {values: MIND});
write(path.join(DATA, 'tags/block/mind_clearable.json'), {values: ['#minecraft:dirt', '#minecraft:leaves', '#minecraft:logs', '#minecraft:sand', '#minecraft:snow',
    '#minecraft:terracotta', '#c:gravels', '#c:sandstone/blocks', '#minecraft:base_stone_overworld', '#minecraft:flowers', 'minecraft:snow_block', 'minecraft:powder_snow',
    'minecraft:clay', 'minecraft:moss_block', 'minecraft:pumpkin', 'minecraft:melon', 'minecraft:bee_nest', 'minecraft:cactus', 'minecraft:mossy_cobblestone']});
const addTag = (file, ids) => {
    const tag = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {values: []};
    ids.forEach(id => { if (!tag.values.includes(id)) tag.values.push(id); });
    write(file, tag);
};
const MC = path.join(ROOT, 'data/minecraft/tags/block');
addTag(path.join(MC, 'mineable/pickaxe.json'), ALL);
addTag(path.join(MC, 'needs_iron_tool.json'), ALL);
addTag(path.join(MC, 'fence_gates.json'), [P + 'mind_gate']);
addTag(path.join(DATA, 'tags/entity_type/wanderer_foes.json'), [P + 'mind_drone']);

// ------------------------------------------------------------------ loot
const item = (name, min, max, chance) => ({rolls: 1, entries: [{type: 'minecraft:item', name, functions: [{function: 'minecraft:set_count',
    count: {type: 'minecraft:uniform', min, max}}]}], ...(chance === undefined ? {} : {conditions: [{condition: 'minecraft:random_chance', chance}]})});
const loot = (type, pools) => ({type, pools});
const blockLoot = (id, pools) => write(path.join(DATA, `loot_table/blocks/${id}.json`), loot('minecraft:block', pools));
const chest = (id, pools) => write(path.join(DATA, `loot_table/entities/${id}.json`), loot('minecraft:chest', pools));
blockLoot('mind_plating', [item('minecraft:iron_nugget', 2, 5)]);
blockLoot('mind_cable', [item(P + 'cable', 1, 1, 0.6), item('minecraft:redstone', 0, 1)]);
blockLoot('mind_floodlight', [item('minecraft:glowstone_dust', 1, 2), item('minecraft:iron_nugget', 1, 3)]);
blockLoot('mind_generator', [item('minecraft:iron_ingot', 1, 3), item('minecraft:copper_ingot', 1, 3), item(P + 'power_cell', 1, 1, 0.3)]);
blockLoot('mind_fence', [item('minecraft:iron_nugget', 1, 3)]);
blockLoot('mind_gate', [item('minecraft:iron_nugget', 2, 4)]);
blockLoot('mind_computer', [item('minecraft:iron_ingot', 2, 4), item(P + 'circuit_board', 1, 2)]);
blockLoot('mind_computer_wreck', [item('minecraft:iron_ingot', 2, 5), item(P + 'circuit_board', 1, 2), item(P + 'cable', 1, 3)]);
const parts = [item('minecraft:iron_ingot', 3, 6), item(P + 'circuit_board', 1, 2), item(P + 'actuator', 1, 2), item(P + 'processor', 1, 1, 0.15)];
for (const id of ['mind_factory_line', 'mind_foundry', 'mind_hangar']) { blockLoot(id, parts); chest(id, parts); }
chest('mind_drone', [item('minecraft:iron_nugget', 1, 4), item(P + 'circuit_board', 1, 1, 0.25)]);
const drone_table = JSON.parse(fs.readFileSync(path.join(DATA, 'loot_table/entities/mind_drone.json'), 'utf8'));
drone_table.type = 'minecraft:entity';
write(path.join(DATA, 'loot_table/entities/mind_drone.json'), drone_table);
for (let n = 1; n <= 5; n++) {
    chest('mind_computer_' + n, [item(P + 'mind_chip', n >= 4 ? 2 : 1, Math.max(1, n)), item(P + 'damaged_ai_core', 1, 1, 0.3 + 0.14 * n), item(P + 'processor', n, n + 2),
        item(P + 'circuit_board', 2, 4 + n), item(P + 'titanium_ingot', 1, 2 + n), item(P + 'power_cell', 1, 1 + n)]);
}

// ------------------------------------------------------------------ block states and models
const B = id => `projectrebirth:block/${id}`;
const state = (id, value) => write(path.join(ASSETS, `blockstates/${id}.json`), value);
const model = (id, value) => write(path.join(ASSETS, `models/block/${id}.json`), value);
const itemModel = (id, value) => write(path.join(ASSETS, `models/item/${id}.json`), value);
for (const id of ['mind_plating', 'mind_cable', 'mind_floodlight']) {
    state(id, {variants: {'': {model: B(id)}}});
    itemModel(id, {parent: B(id)});
}
model('mind_plating', {parent: 'minecraft:block/cube_all', textures: {all: B('mind_plating')}});
model('mind_cable', {parent: 'minecraft:block/cube_all', textures: {all: B('mind_cable')}});
model('mind_floodlight', {parent: 'minecraft:block/cube_bottom_top', textures: {side: B('mind_floodlight'), top: B('mind_plating'), bottom: B('mind_plating')}});
state('mind_generator', {variants: {'': {model: B('mind_generator')}}});
model('mind_generator', {parent: 'minecraft:block/cube_bottom_top', textures: {side: B('mind_generator'), top: B('mind_generator_top'), bottom: B('mind_plating')}});
itemModel('mind_generator', {parent: B('mind_generator')});
// the fence: the iron bars' own block state and models with our texture
const bars = ['post', 'post_ends', 'cap', 'cap_alt', 'side', 'side_alt'];
bars.forEach(part => model('mind_fence_' + part, {parent: 'minecraft:block/iron_bars_' + part, render_type: 'minecraft:cutout_mipped',
    textures: {bars: B('mind_fence'), edge: B('mind_fence'), particle: B('mind_fence')}}));
const off = {north: 'false', west: 'false', south: 'false', east: 'false'};
state('mind_fence', {multipart: [
    {apply: {model: B('mind_fence_post_ends')}}, {when: off, apply: {model: B('mind_fence_post')}},
    {when: {...off, north: 'true'}, apply: {model: B('mind_fence_cap')}}, {when: {...off, east: 'true'}, apply: {model: B('mind_fence_cap'), y: 90}},
    {when: {...off, south: 'true'}, apply: {model: B('mind_fence_cap_alt')}}, {when: {...off, west: 'true'}, apply: {model: B('mind_fence_cap_alt'), y: 90}},
    {when: {north: 'true'}, apply: {model: B('mind_fence_side')}}, {when: {east: 'true'}, apply: {model: B('mind_fence_side'), y: 90}},
    {when: {south: 'true'}, apply: {model: B('mind_fence_side_alt')}}, {when: {west: 'true'}, apply: {model: B('mind_fence_side_alt'), y: 90}}]});
itemModel('mind_fence', {parent: 'minecraft:item/generated', textures: {layer0: B('mind_fence')}});
['', '_open', '_wall', '_wall_open'].forEach(s => model('mind_gate' + s, {parent: 'minecraft:block/template_fence_gate' + s, textures: {texture: B('mind_plating')}}));
const gate = {};
for (const [facing, y] of [['south', 0], ['west', 90], ['north', 180], ['east', 270]]) for (const wall of [false, true]) for (const open of [false, true]) {
    gate[`facing=${facing},in_wall=${wall},open=${open}`] = {model: B('mind_gate' + (wall ? '_wall' : '') + (open ? '_open' : '')), uvlock: true, ...(y ? {y} : {})};
}
state('mind_gate', {variants: gate});
itemModel('mind_gate', {parent: B('mind_gate')});
const turned = id => ({variants: Object.fromEntries([['north', 0], ['east', 90], ['south', 180], ['west', 270]].map(([f, y]) => ['facing=' + f, {model: B(id), ...(y ? {y} : {})}]))});
// the animated blocks: only particles come from the block model
for (const id of ['mind_computer', 'mind_factory_line', 'mind_foundry', 'mind_hangar']) {
    state(id, {variants: {'': {model: B(id)}}});
    model(id, {textures: {particle: B('mind_plating')}});
    itemModel(id, {parent: 'minecraft:item/generated', textures: {layer0: `projectrebirth:item/${id}`}});
}
state('mind_part', {variants: {'': {model: B('mind_part')}}});
model('mind_part', {textures: {particle: B('mind_plating')}});
// the wreck: a split, leaning stump
state('mind_computer_wreck', turned('mind_computer_wreck'));
const box = (from, to, tex) => ({from, to, faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(d => [d, {texture: tex || '#0'}]))});
model('mind_computer_wreck', {textures: {0: B('mind_plating'), 1: B('mind_wreck'), particle: B('mind_plating')}, parent: 'minecraft:block/block', elements: [
    box([1, 0, 1], [15, 3, 15]), box([2.5, 3, 3], [7, 14, 13], '#1'), box([7.2, 3, 4], [13, 9, 12], '#1'), box([8, 9, 6], [10, 12, 9], '#1'), box([11, 3, 1.5], [14, 5, 3.8])]});
itemModel('mind_computer_wreck', {parent: B('mind_computer_wreck')});
itemModel('mind_chip', {parent: 'minecraft:item/generated', textures: {layer0: 'projectrebirth:item/mind_chip'}});
itemModel('mind_drone_spawn_egg', {parent: 'minecraft:item/template_spawn_egg'});
console.log('machine mind data written');
