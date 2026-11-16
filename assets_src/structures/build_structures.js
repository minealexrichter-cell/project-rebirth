// Builds the structure templates of version 1.6.0 (block B2) as Minecraft structure files (gzipped NBT):
// an abandoned laboratory (small), industrial ruins (medium) and a ruined reactor (large, rare).
// Usage, from the project root:  node assets_src/structures/build_structures.js
// Writes src/main/resources/data/projectrebirth/structure/ruins/<name>.nbt. The decay (cracks, broken glass, holes) is not baked
// in here: the processor list worldgen/processor_list/ruins_decay.json adds it each time a structure is placed, so no two look alike.
// Coordinates: x east, y up, z south; y 0 is the ground floor, which the jigsaw puts on the surface.
const fs = require('fs');
const path = require('path');

const OUT = path.join(__dirname, '../../src/main/resources/data/projectrebirth/structure/ruins');

const {nbtFile, Build, T, str, byte, float, list, compound} = require('./structure_lib');

/** A hostile creature or machine of part B3 standing at (x, y, z), looking toward yaw; it stays (PersistenceRequired). */
const mob = (b, x, y, z, id, yaw) => b.entity(x, y, z, compound({id: str('projectrebirth:' + id), Rotation: list(T.FLOAT, [float(yaw || 0), float(0)]), PersistenceRequired: byte(1)}));

const AIR = 'minecraft:air';
const DEBRIS = 'projectrebirth:radioactive_debris';
const BARREL = 'projectrebirth:leaking_waste_barrel';
const chest = (facing, table) => ['minecraft:chest', {facing, type: 'single', waterlogged: 'false'}, {LootTable: 'projectrebirth:chests/' + table}];
const pane = (axis) => ['minecraft:glass_pane', axis === 'x' ? {east: 'true', west: 'true'} : {north: 'true', south: 'true'}];

/** Clears the inside volume (so terrain and trees do not fill a building) and leaves the rest of the box to the terrain. */
function clear(b, x1, y1, z1, x2, y2, z2) {
    b.box(x1, y1, z1, x2, y2, z2, AIR);
}

/** A foundation two blocks deep under a floor, so a building on a slope stands on something. */
function foundation(b, x1, z1, x2, z2, block) {
    b.box(x1, 0, z1, x2, 1, z2, block);
}

// ------------------------------------------------------------------ abandoned laboratory (small): toxic barrels, electronics, medicine

function lab() {
    const b = new Build(15, 9, 13, 160601);
    const W = 'minecraft:white_concrete', G = 'minecraft:light_gray_concrete', F = 'minecraft:smooth_stone';
    foundation(b, 0, 0, 14, 12, 'minecraft:stone_bricks');
    b.box(0, 2, 0, 14, 2, 12, F); // floor
    clear(b, 1, 3, 1, 13, 7, 11);
    // walls with a grey band, window rows on the long sides
    b.walls(0, 3, 0, 14, 7, 12, W);
    b.walls(0, 3, 0, 14, 3, 12, G);
    for (const x of [2, 3, 5, 6, 8, 9, 11, 12]) { b.set(x, 5, 0, pane('x')); b.set(x, 5, 12, pane('x')); }
    for (const z of [3, 4, 8, 9]) b.set(14, 5, z, pane('z'));
    // door on the west side
    b.box(0, 3, 5, 0, 4, 6, AIR);
    // roof, partly fallen in
    b.box(0, 8, 0, 14, 8, 12, G);
    b.box(8, 8, 6, 12, 8, 10, AIR);
    b.box(8, 3, 7, 11, 3, 9, 'minecraft:gravel');
    b.set(9, 4, 8, 'minecraft:gravel');
    // a partition: the storeroom with the barrels in the north-east
    b.box(10, 3, 1, 10, 7, 4, W);
    b.set(10, 3, 3, AIR); b.set(10, 4, 3, AIR);
    b.set(11, 3, 1, BARREL); b.set(13, 3, 1, BARREL); b.set(12, 3, 3, BARREL);
    b.set(13, 3, 4, 'minecraft:cauldron');
    // benches along the south wall: quartz slabs on iron trapdoor legs, with brewing stands and a lamp
    for (let x = 2; x <= 8; x++) b.set(x, 3, 11, ['minecraft:smooth_quartz_slab', {type: 'top', waterlogged: 'false'}]);
    b.set(3, 4, 11, ['minecraft:brewing_stand', {has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false'}]);
    b.set(6, 4, 11, ['minecraft:brewing_stand', {has_bottle_0: 'true', has_bottle_1: 'false', has_bottle_2: 'false'}]);
    b.set(8, 4, 11, ['minecraft:flower_pot', null]);
    // a bench in the middle and the lab's chests
    for (let x = 3; x <= 6; x++) b.set(x, 3, 6, ['minecraft:smooth_quartz_slab', {type: 'top', waterlogged: 'false'}]);
    b.set(2, 3, 1, chest('south', 'abandoned_lab'));
    b.set(12, 3, 11, chest('north', 'abandoned_lab'));
    b.set(1, 3, 10, ['minecraft:iron_trapdoor', {facing: 'north', half: 'bottom', open: 'false', powered: 'false', waterlogged: 'false'}]);
    // cobwebs in the corners
    for (const [x, y, z] of [[1, 7, 1], [13, 7, 11], [1, 7, 11], [6, 7, 1], [13, 6, 6]]) b.set(x, y, z, 'minecraft:cobweb');
    b.set(7, 7, 6, ['minecraft:redstone_lamp', {lit: 'false'}]);
    // its dwellers: a pack of hounds among the benches and a ghoul by the storeroom
    for (const [x, z] of [[2, 8], [4, 9], [6, 8]]) mob(b, x, 3, z, 'mutated_hound', 90);
    mob(b, 12, 3, 7, 'glowing_ghoul', 90);
    return b;
}

// ------------------------------------------------------------------ industrial ruins (medium): broken machines, metals, parts

function ruins() {
    const b = new Build(25, 14, 21, 160602);
    const P = 'minecraft:light_gray_concrete', I = 'minecraft:iron_block', C = 'minecraft:weathered_cut_copper', O = 'minecraft:oxidized_cut_copper';
    foundation(b, 0, 0, 24, 20, 'minecraft:cobblestone');
    b.box(0, 2, 0, 24, 2, 20, 'minecraft:polished_andesite');
    clear(b, 1, 3, 1, 23, 12, 19);
    // the frame: pillars every 6 blocks, a few still holding beams
    for (const x of [0, 6, 12, 18, 24]) for (const z of [0, 10, 20]) {
        const h = 3 + Math.floor(b.rand() * 8);
        b.box(x, 3, z, x, 3 + h, z, b.chance(0.5) ? I : O);
    }
    b.box(0, 11, 0, 24, 11, 0, C);
    b.box(0, 11, 0, 0, 11, 20, C);
    b.box(6, 11, 0, 6, 11, 14, C);
    // remains of the walls: low concrete with ragged tops
    for (let x = 1; x <= 23; x++) { b.box(x, 3, 0, x, 3 + Math.floor(b.rand() * 5), 0, P); b.box(x, 3, 20, x, 3 + Math.floor(b.rand() * 3), 20, P); }
    for (let z = 1; z <= 19; z++) { b.box(0, 3, z, 0, 3 + Math.floor(b.rand() * 6), z, P); b.box(24, 3, z, 24, 3 + Math.floor(b.rand() * 2), z, P); }
    b.box(24, 3, 8, 24, 6, 12, AIR); // the big gate, open
    // broken machines: blast furnaces in a row, an anvil, hoppers, a press of iron and copper
    for (const x of [3, 5, 7]) b.set(x, 3, 3, ['minecraft:blast_furnace', {facing: 'south', lit: 'false'}]);
    b.set(9, 3, 3, ['minecraft:damaged_anvil', {facing: 'east'}]);
    b.box(14, 3, 3, 17, 5, 5, I);
    b.box(15, 6, 4, 16, 7, 4, O);
    b.set(15, 3, 6, ['minecraft:hopper', {enabled: 'true', facing: 'down'}]);
    b.set(16, 3, 6, ['minecraft:hopper', {enabled: 'true', facing: 'down'}]);
    for (let x = 2; x <= 22; x++) b.set(x, 3, 15, ['minecraft:rail', {shape: 'east_west', waterlogged: 'false'}]);
    b.set(19, 3, 15, AIR); b.set(20, 3, 15, AIR);
    // chains hanging from the beams
    for (const [x, z] of [[6, 4], [6, 9], [3, 0]]) b.box(x, 8, z, x, 10, z, ['minecraft:chain', {axis: 'y', waterlogged: 'false'}]);
    // scrap heaps and a little radioactive debris by a broken machine
    for (const [x, z] of [[10, 12], [11, 12], [10, 13], [20, 4], [21, 4]]) b.set(x, 3, z, b.pick(['minecraft:gravel', 'minecraft:cobblestone', 'minecraft:iron_bars']));
    b.set(18, 3, 4, DEBRIS); b.set(19, 3, 5, DEBRIS); b.set(18, 4, 4, DEBRIS);
    b.set(3, 3, 18, BARREL);
    // the loot
    b.set(2, 3, 9, chest('east', 'industrial_ruins'));
    b.set(22, 3, 18, chest('west', 'industrial_ruins'));
    b.set(13, 3, 11, chest('north', 'industrial_ruins'));
    b.set(4, 3, 12, ['minecraft:barrel', {facing: 'up', open: 'false'}, {LootTable: 'projectrebirth:chests/industrial_ruins'}]);
    // still on guard: a patrol robot in the hall
    mob(b, 12, 3, 8, 'patrol_robot', 90);
    return b;
}

// ------------------------------------------------------------------ ruined reactor (large, rare): strong radiation at the core, rare loot

function reactor() {
    const S = 33, H = 26, C = 16;
    const b = new Build(S, H, S, 160603);
    const T1 = 'minecraft:deepslate_tiles', T2 = 'minecraft:cracked_deepslate_tiles', R = 'minecraft:polished_blackstone';
    // a round platform and the containment: a ring wall with a great breach on the south-east
    for (let x = 0; x < S; x++) for (let z = 0; z < S; z++) {
        const d = Math.hypot(x - C, z - C);
        if (d <= 15.5) b.box(x, 0, z, x, 2, z, d > 14.5 ? R : 'minecraft:smooth_stone');
        if (d <= 13.5) clear(b, x, 3, z, x, H - 1, z);
        if (d > 12.5 && d <= 13.5) {
            const angle = Math.atan2(z - C, x - C);
            const breach = angle > 0.2 && angle < 1.1;
            const top = breach ? 3 + Math.floor(b.rand() * 3) : 14 + Math.floor(b.rand() * 9);
            b.box(x, 3, z, x, top, z, b.chance(0.3) ? T2 : T1);
        }
    }
    // the core: a pit lined with iron, its lid broken open; radioactive debris heaped in and around it
    for (let x = C - 4; x <= C + 4; x++) for (let z = C - 4; z <= C + 4; z++) {
        const d = Math.hypot(x - C, z - C);
        if (d <= 4.2) b.set(x, 2, z, 'minecraft:iron_block');
        if (d <= 3.2) {
            const heap = d < 1.5 ? 4 : d < 2.5 ? 2 : 1;
            b.box(x, 3, z, x, 2 + heap, z, DEBRIS);
        } else if (d <= 4.2 && b.chance(0.5)) {
            b.set(x, 3, z, DEBRIS);
        }
    }
    // scattered debris out to the breach, and broken lid plates
    for (let i = 0; i < 18; i++) {
        const r = 5 + b.rand() * 8, a = 0.2 + b.rand() * 0.9;
        b.set(Math.round(C + Math.cos(a) * r), 3, Math.round(C + Math.sin(a) * r), DEBRIS);
    }
    for (const [x, z] of [[C + 6, C - 3], [C - 6, C + 2], [C + 2, C + 7]]) b.box(x, 3, z, x + 1, 3, z, ['minecraft:heavy_weighted_pressure_plate', {power: '0'}]);
    // coolant pipes along the inside of the wall, rusted
    for (let i = 0; i < 4; i++) {
        const a = i * Math.PI / 2 + 0.8;
        const x = Math.round(C + Math.cos(a) * 11), z = Math.round(C + Math.sin(a) * 11);
        b.box(x, 3, z, x, 12, z, 'minecraft:oxidized_copper');
    }
    // leaking waste barrels in a corner of the hall
    for (const [x, z] of [[C - 9, C - 6], [C - 10, C - 4], [C - 8, C - 8], [C - 10, C - 7]]) b.set(x, 3, z, BARREL);
    // the control room on the north-west, outside the ring: concrete box, consoles, the loot
    b.box(1, 0, 1, 9, 2, 9, 'minecraft:gray_concrete');
    clear(b, 2, 3, 2, 8, 7, 8);
    b.walls(1, 3, 1, 9, 7, 9, 'minecraft:gray_concrete');
    b.box(1, 8, 1, 9, 8, 9, 'minecraft:gray_concrete');
    for (const x of [3, 4, 6, 7]) b.set(x, 5, 1, pane('x'));
    b.box(9, 3, 6, 9, 4, 7, AIR); // door toward the ring
    for (let x = 3; x <= 7; x++) b.set(x, 3, 2, ['minecraft:polished_blackstone_slab', {type: 'top', waterlogged: 'false'}]);
    b.set(4, 4, 2, ['minecraft:redstone_lamp', {lit: 'false'}]);
    b.set(6, 4, 2, ['minecraft:lever', {face: 'floor', facing: 'south', powered: 'false'}]);
    b.set(2, 3, 8, chest('east', 'ruined_reactor'));
    b.set(8, 3, 8, chest('west', 'ruined_reactor'));
    // one more chest by the core: the richest place is the most dangerous
    b.set(C + 6, 3, C, chest('west', 'ruined_reactor'));
    // ghouls glow among the debris; a brute keeps the breach
    for (const [x, z] of [[C + 7, C + 1], [C - 5, C + 6], [C + 1, C - 7]]) mob(b, x, 3, z, 'glowing_ghoul', 0);
    mob(b, C + 8, 3, C + 6, 'brute', -45);
    return b;
}

fs.mkdirSync(OUT, {recursive: true});
for (const [name, make] of Object.entries({abandoned_lab: lab, industrial_ruins: ruins, ruined_reactor: reactor})) {
    const b = make();
    fs.writeFileSync(path.join(OUT, name + '.nbt'), nbtFile(b.toNbt()));
    const counts = {};
    for (const block of b.cells.values()) {
        const n = Array.isArray(block) ? block[0] : block;
        if (n.startsWith('projectrebirth:') || n === 'minecraft:chest' || n === 'minecraft:barrel') counts[n] = (counts[n] || 0) + 1;
    }
    console.log(name.padEnd(18), b.size.join('x'), b.cells.size + ' cells', JSON.stringify(counts));
}
