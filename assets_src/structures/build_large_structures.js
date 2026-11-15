// Builds the large structures of version 1.6.0 (after the concept art of the owner): a military base, a science complex,
// an abandoned factory, a communication station, a defensive outpost and a contamination zone.
// Usage, from the project root:  node assets_src/structures/build_large_structures.js
// Writes src/main/resources/data/projectrebirth/structure/ruins/<name>.nbt. As with the small ruins, the decay (cracks, broken
// glass, holes) is added by the processor list ruins_decay each time a structure is placed.
// Coordinates: x east, y up, z south; y 0-1 is the foundation, y 2 the ground floor, which the jigsaw puts on the surface.
// Wrecked power armors are real armor entities in the wreck state: a wrench (sneaking) takes them apart for metal and plates.
const fs = require('fs');
const path = require('path');
const {nbtFile, Build, T, str, byte, float, list, compound} = require('./structure_lib');

const OUT = path.join(__dirname, '../../src/main/resources/data/projectrebirth/structure/ruins');
const P = 'projectrebirth:';
const AIR = 'minecraft:air';
const DEBRIS = P + 'radioactive_debris';
const BARREL = P + 'leaking_waste_barrel';
const SLUDGE = P + 'irradiated_sludge';
const TANK = P + 'specimen_tank';

// ------------------------------------------------------------------ block helpers

const facing = (id, f) => [P + id, {facing: f}];
const monitor = f => facing('wall_monitor', f);
const lamp = f => facing('emergency_lamp', f);
const rack = f => facing('server_rack', f);
const turret = f => facing('wrecked_turret', f);
const crate = f => facing('ammo_crate', f);
const chest = (f, table) => ['minecraft:chest', {facing: f, type: 'single', waterlogged: 'false'}, {LootTable: P + 'chests/' + table}];
const lootBarrel = table => ['minecraft:barrel', {facing: 'up', open: 'false'}, {LootTable: P + 'chests/' + table}];
const pane = (axis, glass) => [glass || 'minecraft:glass_pane', axis === 'x'
    ? {east: 'true', west: 'true', north: 'false', south: 'false', waterlogged: 'false'}
    : {north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false'}];
const bars = axis => pane(axis, 'minecraft:iron_bars');
const slab = (block, type) => [block, {type: type || 'bottom', waterlogged: 'false'}];
const stairs = (block, f, half) => [block, {facing: f, half: half || 'bottom', shape: 'straight', waterlogged: 'false'}];
const post = block => [block, {up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false'}];
const ladder = f => ['minecraft:ladder', {facing: f, waterlogged: 'false'}];
const banner = (color, f) => ['minecraft:' + color + '_wall_banner', {facing: f}];
const chain = ['minecraft:chain', {axis: 'y', waterlogged: 'false'}];
const log = (block, axis) => [block, {axis: axis || 'y'}];
const STEP = {north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0]};

function bed(b, color, x, y, z, f) {
    const [dx, dz] = STEP[f];
    b.set(x, y, z, ['minecraft:' + color + '_bed', {facing: f, part: 'foot', occupied: 'false'}]);
    b.set(x + dx, y, z + dz, ['minecraft:' + color + '_bed', {facing: f, part: 'head', occupied: 'false'}]);
}

/** A wrecked power armor (variant combat, mobile or worker) standing on the block; yaw 0 looks south, 90 west, 180 north. */
function wreck(b, x, y, z, variant, yaw) {
    b.entity(x, y, z, compound({
        id: str(P + 'power_armor'),
        Variant: str(variant),
        ArmorState: byte(2),
        Structural: float(0),
        Critical: float(0),
        Initialized: byte(1),
        Rotation: list(T.FLOAT, [float(yaw), float(0)])
    }));
}

/** A box room: floor at y, walls from y + 1 to y + h, a ceiling above (unless ceil is null); the inside is cleared. */
function room(b, x1, z1, x2, z2, y, h, wall, floor, ceil) {
    b.box(x1, y, z1, x2, y, z2, floor);
    b.box(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h, z2 - 1, AIR);
    b.walls(x1, y + 1, z1, x2, y + h, z2, wall);
    if (ceil) b.box(x1, y + h + 1, z1, x2, y + h + 1, z2, ceil);
}

/** Windows along a wall line: on the x axis (z fixed) or the z axis (x fixed), skipping every `every`-th column for a pillar. */
function windows(b, axis, fixed, from, to, y1, y2, glass, every) {
    for (let i = from; i <= to; i++) {
        if (every && (i - from) % every === 0) continue;
        for (let y = y1; y <= y2; y++) {
            if (axis === 'x') b.set(i, y, fixed, pane('x', glass)); else b.set(fixed, y, i, pane('z', glass));
        }
    }
}

/** Battlements: every other block along the top of a wall rectangle. */
function battlements(b, x1, z1, x2, z2, y, block) {
    for (let x = x1; x <= x2; x += 2) { b.set(x, y, z1, block); b.set(x, y, z2, block); }
    for (let z = z1; z <= z2; z += 2) { b.set(x1, y, z, block); b.set(x2, y, z, block); }
}

/** A square tower with a ladder up the inside of its north wall and a platform with battlements at the top. */
function tower(b, x1, z1, size, top, wall, floor) {
    const x2 = x1 + size - 1, z2 = z1 + size - 1;
    b.box(x1, 2, z1, x2, 2, z2, floor);
    b.box(x1 + 1, 3, z1 + 1, x2 - 1, top, z2 - 1, AIR);
    b.walls(x1, 3, z1, x2, top, z2, wall);
    b.box(x1, top, z1, x2, top, z2, floor);
    const lx = x1 + Math.floor(size / 2);
    for (let y = 3; y <= top; y++) b.set(lx, y, z1 + 1, ladder('south'));
    battlements(b, x1, z1, x2, z2, top + 1, wall);
    // arrow slits on each side
    for (let y = 6; y < top - 1; y += 4) {
        b.set(x1, y, z1 + Math.floor(size / 2), AIR); b.set(x2, y, z1 + Math.floor(size / 2), AIR);
        b.set(x1 + Math.floor(size / 2), y, z2, AIR);
    }
    return {x1, z1, x2, z2, cx: lx, cz: z1 + Math.floor(size / 2)};
}

/**
 * A dish of radius r centred at (cx, cy, cz), facing the horizontal side `f` and tipped up by 45 degrees: every block whose middle
 * lies within half a block of a shallow bowl around that axis, so the surface has no holes; a lighter rim, a feed arm and a receiver.
 */
function dish(b, cx, cy, cz, r, f, block) {
    const [dx, dz] = STEP[f];
    const n = [dx * Math.SQRT1_2, Math.SQRT1_2, dz * Math.SQRT1_2]; // the axis of the dish, outward and up
    for (let x = cx - r - 2; x <= cx + r + 2; x++) for (let y = cy - r - 2; y <= cy + r + 2; y++) for (let z = cz - r - 2; z <= cz + r + 2; z++) {
        const d = [x - cx, y - cy, z - cz];
        const t = d[0] * n[0] + d[1] * n[1] + d[2] * n[2];
        const rho = Math.sqrt(Math.max(0, d[0] * d[0] + d[1] * d[1] + d[2] * d[2] - t * t));
        if (rho > r + 0.4) continue;
        const bowl = rho * rho / (r * 3.5); // the rim stands forward of the middle
        if (Math.abs(t - bowl) > 0.62) continue;
        b.set(x, y, z, rho > r - 0.7 ? 'minecraft:light_gray_concrete' : block);
    }
    // the feed arm along the axis and the receiver at its end
    for (let k = 1; k <= 2; k++) b.set(cx + Math.round(n[0] * k * 1.4), cy + Math.round(n[1] * k * 1.4), cz + Math.round(n[2] * k * 1.4), 'minecraft:iron_bars');
    b.set(cx + Math.round(n[0] * 4.2), cy + Math.round(n[1] * 4.2), cz + Math.round(n[2] * 4.2), 'minecraft:iron_block');
}

/** Rough ground: the floor at y 2 with a share of other blocks, for yards and outside areas. */
function yard(b, x1, z1, x2, z2, base, others, share) {
    for (let x = x1; x <= x2; x++) for (let z = z1; z <= z2; z++) {
        b.set(x, 2, z, b.chance(share) ? b.pick(others) : base);
    }
}

// ------------------------------------------------------------------ military base: a fortified compound, garage, headquarters

function militaryBase() {
    const b = new Build(48, 34, 45, 160611);
    const WALL = 'minecraft:deepslate_bricks', POL = 'minecraft:polished_deepslate', CON = 'minecraft:gray_concrete',
        TILE = 'minecraft:deepslate_tiles';
    b.box(0, 0, 0, 47, 1, 43, 'minecraft:cobbled_deepslate');
    yard(b, 0, 0, 47, 43, 'minecraft:polished_andesite', ['minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:andesite'], 0.2);
    b.box(1, 3, 1, 46, 30, 42, AIR); // trees and hills out of the compound
    // the perimeter wall, its battlements and the gate on the south
    b.walls(0, 3, 0, 47, 7, 43, WALL);
    battlements(b, 0, 0, 47, 43, 8, WALL);
    b.box(20, 3, 43, 27, 6, 43, AIR);
    b.box(19, 3, 43, 19, 9, 43, POL); b.box(28, 3, 43, 28, 9, 43, POL);
    b.set(19, 8, 44, lamp('south')); b.set(28, 8, 44, lamp('south'));
    for (let x = 20; x <= 27; x++) b.set(x, 7, 43, WALL);
    for (let x = 20; x <= 27; x += 2) b.set(x, 6, 43, bars('x')); // the shutter, stuck half way
    b.set(19, 10, 43, turret('south')); b.set(28, 10, 43, turret('south'));
    // corner towers; the north-west one is the tall watch tower with an antenna
    const watch = tower(b, 0, 0, 7, 24, POL, TILE);
    for (let y = 25; y <= 31; y++) b.set(watch.cx, y, watch.cz, ['minecraft:iron_bars', {east: 'false', west: 'false', north: 'false', south: 'false', waterlogged: 'false'}]);
    b.set(watch.cx, 32, watch.cz, 'minecraft:lightning_rod');
    b.set(watch.cx - 1, 31, watch.cz, lamp('west')); b.set(watch.cx + 1, 31, watch.cz, lamp('east'));
    b.set(watch.cx + 1, 25, watch.cz + 1, turret('south'));
    b.set(7, 22, 3, lamp('east')); b.set(3, 22, 7, lamp('south'));
    for (const [x, z] of [[42, 0], [0, 38], [42, 38]]) {
        const t = tower(b, x, z, 6, 12, POL, TILE);
        b.set(t.cx, 13, t.cz, turret(z === 0 ? 'north' : 'south'));
    }
    // headquarters: three floors and a roof block with an antenna
    const hx1 = 8, hz1 = 6, hx2 = 30, hz2 = 24;
    for (const [y, top] of [[2, 7], [8, 13], [14, 19]]) {
        b.box(hx1, y, hz1, hx2, y, hz2, TILE);
        b.box(hx1 + 1, y + 1, hz1 + 1, hx2 - 1, top, hz2 - 1, AIR);
        b.walls(hx1, y + 1, hz1, hx2, top, hz2, CON);
        windows(b, 'x', hz1, hx1 + 1, hx2 - 1, y + 2, y + 3, 'minecraft:black_stained_glass_pane', 5);
        windows(b, 'x', hz2, hx1 + 1, hx2 - 1, y + 2, y + 3, 'minecraft:black_stained_glass_pane', 5);
        windows(b, 'z', hx1, hz1 + 1, hz2 - 1, y + 2, y + 3, 'minecraft:black_stained_glass_pane', 5);
    }
    b.box(hx1, 20, hz1, hx2, 20, hz2, TILE);
    battlements(b, hx1, hz1, hx2, hz2, 21, CON);
    for (let x = hx1; x <= hx2; x += 5) for (const z of [hz1, hz2]) b.box(x, 3, z, x, 21, z, POL);
    room(b, 13, 10, 25, 20, 20, 4, CON, TILE, TILE);
    for (let y = 26; y <= 31; y++) b.set(19, y, 15, ['minecraft:iron_bars', {east: 'false', west: 'false', north: 'false', south: 'false', waterlogged: 'false'}]);
    b.set(19, 32, 15, 'minecraft:lightning_rod');
    b.set(18, 30, 15, lamp('west')); b.set(20, 30, 15, lamp('east'));
    // the front: door, banners, lamps
    b.box(18, 3, hz2, 20, 5, hz2, AIR);
    b.box(17, 3, hz2, 17, 6, hz2, POL); b.box(21, 3, hz2, 21, 6, hz2, POL); b.box(17, 6, hz2, 21, 6, hz2, POL);
    for (const x of [11, 14, 24, 27]) b.set(x, 12, hz2 + 1, banner('red', 'south'));
    b.set(16, 6, hz2 + 1, lamp('south')); b.set(22, 6, hz2 + 1, lamp('south'));
    // a ladder up through every floor in the south-east corner
    for (let y = 3; y <= 20; y++) b.set(29, y, 23, ladder('west'));
    // ground floor: the command room
    for (let x = 12; x <= 26; x++) { b.set(x, 5, 7, monitor('south')); b.set(x, 6, 7, monitor('south')); }
    for (const x of [9, 10, 11, 27, 28]) { b.set(x, 3, 7, rack('south')); b.set(x, 4, 7, rack('south')); }
    for (let x = 15; x <= 23; x++) for (const z of [13, 14, 15]) b.set(x, 3, z, slab('minecraft:polished_blackstone_slab', 'top'));
    for (const x of [16, 18, 20, 22]) { b.set(x, 3, 12, stairs('minecraft:polished_blackstone_stairs', 'south')); b.set(x, 3, 16, stairs('minecraft:polished_blackstone_stairs', 'north')); }
    b.set(19, 4, 14, monitor('north'));
    b.set(9, 3, 22, chest('east', 'military_base'));
    b.set(29, 3, 9, chest('west', 'military_base'));
    for (const [x, z, f] of [[9, 12, 'east'], [29, 16, 'west'], [14, 23, 'north']]) b.set(x, 6, z, lamp(f));
    // second floor: barracks to the west, the armory to the east behind a partition
    for (const x of [10, 12, 14, 16]) bed(b, 'gray', x, 9, 22, 'north');
    for (let x = 10; x <= 17; x++) b.set(x, 9, 7, x === 11 || x === 16 ? lootBarrel('military_base') : ['minecraft:barrel', {facing: 'south', open: 'false'}]);
    for (let z = 7; z <= 23; z++) for (let y = 9; y <= 13; y++) b.set(20, y, z, CON);
    b.box(20, 9, 15, 20, 10, 15, AIR);
    for (let x = 22; x <= 28; x++) { b.set(x, 9, 7, crate('south')); if (x % 2 === 0) b.set(x, 10, 7, crate('south')); }
    for (let x = 22; x <= 26; x += 2) b.set(x, 9, 12, crate('north'));
    b.set(28, 9, 18, chest('west', 'military_base'));
    wreck(b, 25, 9, 17, 'combat', 160);
    b.set(21, 12, 11, lamp('east')); b.set(19, 12, 11, lamp('west'));
    // third floor: communications
    for (let x = 10; x <= 18; x++) b.set(x, 17, 7, monitor('south'));
    for (const x of [10, 12, 14, 16]) { b.set(x, 15, 11, rack('south')); b.set(x, 16, 11, rack('south')); }
    for (let x = 21; x <= 27; x++) b.set(x, 15, 9, slab('minecraft:polished_blackstone_slab', 'top'));
    for (const x of [22, 24, 26]) b.set(x, 16, 9, monitor('south'));
    b.set(9, 15, 22, chest('east', 'military_base'));
    b.set(19, 18, 23, lamp('north'));
    // the garage: wrecked armors waiting for repairs that never came
    room(b, 33, 8, 45, 26, 2, 8, CON, TILE, TILE);
    b.box(36, 11, 13, 41, 11, 18, AIR); // a hole in the roof
    b.box(35, 3, 26, 43, 8, 26, AIR);
    for (let x = 35; x <= 43; x++) b.set(x, 9, 26, bars('x'));
    wreck(b, 37, 3, 13, 'mobile', 20);
    wreck(b, 41, 3, 15, 'combat', -30);
    wreck(b, 38, 3, 20, 'worker', 10);
    for (const [x, z] of [[36, 15], [37, 16], [40, 19], [42, 21], [39, 12]]) b.set(x, 2, z, 'minecraft:black_concrete');
    for (const x of [36, 42]) for (let y = 8; y <= 10; y++) b.set(x, y, 10, chain);
    b.set(44, 3, 9, ['minecraft:damaged_anvil', {facing: 'north'}]);
    b.set(44, 3, 11, chest('west', 'military_base'));
    b.set(34, 3, 9, crate('east')); b.set(34, 4, 9, crate('east')); b.set(34, 3, 10, crate('east'));
    b.set(44, 3, 24, lootBarrel('military_base'));
    b.set(34, 7, 18, lamp('east')); b.set(44, 7, 18, lamp('west'));
    // the yard: sandbags at the gate, crates, a floodlight pole, and one more wreck that never made it inside
    for (const [x1, x2] of [[13, 17], [30, 34]]) for (let x = x1; x <= x2; x++) { b.set(x, 3, 35, 'minecraft:packed_mud'); b.set(x, 4, 35, slab('minecraft:mud_brick_slab')); }
    for (const [x, z] of [[4, 30], [5, 30], [4, 31], [40, 32], [41, 32]]) b.set(x, 3, z, crate('north'));
    b.box(38, 3, 36, 38, 8, 36, post('minecraft:polished_deepslate_wall'));
    b.set(38, 9, 36, 'minecraft:sea_lantern');
    wreck(b, 23, 3, 38, 'combat', 180);
    b.set(10, 3, 32, BARREL);
    return b;
}

// ------------------------------------------------------------------ science complex: laboratories, a server room and an observatory

function scienceComplex() {
    const b = new Build(46, 28, 40, 160612);
    const W = 'minecraft:light_gray_concrete', G = 'minecraft:gray_concrete', S = 'minecraft:smooth_stone', BLUE = 'minecraft:blue_stained_glass_pane';
    b.box(0, 0, 0, 45, 1, 39, 'minecraft:stone_bricks');
    yard(b, 0, 0, 45, 39, S, ['minecraft:polished_andesite', 'minecraft:gravel'], 0.12);
    // the main building: two floors with long blue windows
    const x1 = 2, z1 = 10, x2 = 28, z2 = 34;
    for (const [y, top] of [[2, 7], [8, 13]]) {
        b.box(x1, y, z1, x2, y, z2, S);
        b.box(x1 + 1, y + 1, z1 + 1, x2 - 1, top, z2 - 1, AIR);
        b.walls(x1, y + 1, z1, x2, top, z2, W);
        windows(b, 'x', z1, x1 + 1, x2 - 1, y + 2, y + 4, BLUE, 4);
        windows(b, 'x', z2, x1 + 1, x2 - 1, y + 2, y + 4, BLUE, 4);
        windows(b, 'z', x1, z1 + 1, z2 - 1, y + 2, y + 4, BLUE, 4);
    }
    b.box(x1, 14, z1, x2, 14, z2, G);
    for (let x = x1; x <= x2; x += 4) for (const z of [z1, z2]) b.box(x, 3, z, x, 15, z, G);
    battlements(b, x1, z1, x2, z2, 15, W);
    // the entrance with a canopy
    b.box(13, 3, z2, 17, 6, z2, AIR);
    for (let x = 12; x <= 18; x++) for (const z of [35, 36]) b.set(x, 7, z, slab('minecraft:smooth_stone_slab', 'top'));
    b.box(12, 3, 36, 12, 6, 36, post('minecraft:andesite_wall')); b.box(18, 3, 36, 18, 6, 36, post('minecraft:andesite_wall'));
    b.set(15, 6, 35, lamp('south'));
    // ground floor: the specimen hall
    for (const x of [4, 6, 8, 10, 12]) { b.set(x, 3, 12, TANK); b.set(x, 4, 12, TANK); }
    for (const x of [4, 6, 8, 10]) { b.set(x, 3, 22, TANK); b.set(x, 4, 22, TANK); }
    b.set(6, 3, 22, 'minecraft:glass'); b.set(6, 4, 22, DEBRIS); // a broken tank with what was in it
    for (const z of [17, 27]) for (let x = 13; x <= 22; x++) b.set(x, 3, z, slab('minecraft:smooth_quartz_slab', 'top'));
    b.set(14, 4, 17, ['minecraft:brewing_stand', {has_bottle_0: 'true', has_bottle_1: 'false', has_bottle_2: 'true'}]);
    b.set(19, 4, 27, ['minecraft:brewing_stand', {has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false'}]);
    b.set(17, 4, 17, monitor('north')); b.set(21, 4, 27, monitor('south'));
    for (let x = 14; x <= 20; x++) b.set(x, 5, 11, monitor('south'));
    wreck(b, 18, 3, 23, 'worker', 45);
    b.set(3, 3, 32, chest('east', 'science_complex'));
    b.set(21, 3, 12, chest('south', 'science_complex'));
    // the office behind a glass partition, with the ladder up
    for (let z = 11; z <= 33; z++) for (let y = 3; y <= 7; y++) b.set(23, y, z, y >= 5 ? pane('z', BLUE) : W);
    b.box(23, 3, 25, 23, 4, 25, AIR);
    for (let y = 3; y <= 13; y++) b.set(27, y, 33, ladder('west'));
    b.set(27, 8, 33, AIR);
    for (let z = 13; z <= 19; z += 3) { b.set(25, 3, z, slab('minecraft:smooth_quartz_slab', 'top')); b.set(25, 4, z, monitor('west')); }
    b.set(27, 3, 12, lootBarrel('science_complex'));
    b.set(24, 6, 30, lamp('east'));
    // second floor: the server room
    for (const z of [14, 18, 22, 26]) for (let x = 5; x <= 19; x++) if (x !== 12) { b.set(x, 9, z, rack(z % 8 === 6 ? 'north' : 'south')); b.set(x, 10, z, rack(z % 8 === 6 ? 'north' : 'south')); }
    for (let x = 21; x <= 26; x++) b.set(x, 11, 11, monitor('south'));
    for (let x = 21; x <= 26; x++) b.set(x, 9, 13, slab('minecraft:smooth_quartz_slab', 'top'));
    b.set(26, 9, 30, chest('west', 'science_complex'));
    b.set(12, 12, 11, lamp('south')); b.set(12, 12, 33, lamp('north'));
    // the roof: a dish on a pedestal and air vents
    b.box(10, 15, 18, 10, 17, 18, 'minecraft:iron_block');
    dish(b, 10, 20, 18, 4, 'south', 'minecraft:white_concrete');
    for (const [x, z] of [[20, 14], [24, 14], [20, 28], [24, 28]]) { b.set(x, 15, z, 'minecraft:iron_block'); b.set(x, 16, z, ['minecraft:iron_trapdoor', {facing: 'north', half: 'bottom', open: 'false', powered: 'false', waterlogged: 'false'}]); }
    // the observatory: a round hall under a dome with a slit and a telescope
    const cx = 37, cz = 22;
    for (let x = 29; x <= 45; x++) for (let z = 14; z <= 30; z++) {
        const d = Math.hypot(x - cx, z - cz);
        if (d <= 7.5) b.set(x, 2, z, S);
        if (d < 6.5) b.box(x, 3, z, x, 16, z, AIR);
        else if (d <= 7.5) { b.box(x, 3, z, x, 8, z, W); b.set(x, 9, z, G); } // the drum, with a dark band under the dome
        for (let y = 10; y <= 17; y++) {
            const r = Math.hypot(x - cx, y - 9, z - cz);
            // the shell: eight dark ribs running up from the band, light panels between them
            if (r >= 6.5 && r <= 7.5) b.set(x, y, z, Math.abs(Math.sin(4 * Math.atan2(z - cz, x - cx))) < 0.2 && y < 16 ? G : 'minecraft:light_gray_concrete');
            else if (r < 6.5) b.set(x, y, z, AIR);
        }
    }
    for (let y = 12; y <= 17; y++) for (let z = cz - 7; z <= cz; z++) if (Math.hypot(0, y - 9, z - cz) >= 6) b.set(cx, y, z, AIR); // the slit
    b.box(29, 3, 22, 31, 4, 22, AIR);
    b.box(29, 5, 21, 31, 5, 23, W); b.walls(29, 3, 21, 31, 4, 23, W); b.box(28, 3, 22, 31, 4, 22, AIR);
    b.set(cx, 3, cz, 'minecraft:iron_block'); b.set(cx, 4, cz, post('minecraft:andesite_wall'));
    for (const [y, z] of [[5, 22], [6, 21], [7, 20], [8, 19]]) b.set(cx, y, z, 'minecraft:iron_block');
    b.set(cx, 9, 18, 'minecraft:tinted_glass');
    for (const [x, z, f] of [[31, 19, 'east'], [31, 25, 'east'], [43, 19, 'west'], [43, 25, 'west']]) b.set(x, 5, z, monitor(f));
    b.set(42, 3, 27, chest('north', 'science_complex'));
    b.set(32, 8, 22, lamp('east'));
    return b;
}

// ------------------------------------------------------------------ abandoned factory: a smelting hall, presses, two chimneys

function abandonedFactory() {
    const b = new Build(54, 38, 40, 160613);
    const LOW = 'minecraft:stone_bricks', UP = 'minecraft:gray_concrete', PIL = 'minecraft:polished_deepslate', ROOF = 'minecraft:weathered_cut_copper';
    b.box(0, 0, 0, 53, 1, 39, 'minecraft:cobblestone');
    yard(b, 0, 0, 53, 39, 'minecraft:gravel', ['minecraft:coarse_dirt', 'minecraft:cobblestone', 'minecraft:andesite'], 0.3);
    // the hall
    b.box(2, 2, 4, 36, 2, 32, 'minecraft:polished_andesite');
    b.box(3, 3, 5, 35, 15, 31, AIR);
    b.walls(2, 3, 4, 36, 6, 32, LOW);
    b.walls(2, 7, 4, 36, 15, 32, UP);
    windows(b, 'x', 4, 3, 35, 10, 12, null, 6);
    windows(b, 'x', 32, 3, 35, 10, 12, null, 6);
    windows(b, 'z', 2, 5, 31, 10, 12, null, 6);
    for (let x = 2; x <= 36; x += 6) for (const z of [4, 32]) b.box(x, 3, z, x, 16, z, PIL);
    // the roof, with great holes torn in it
    for (let x = 2; x <= 36; x++) for (let z = 4; z <= 32; z++) {
        const hole = Math.hypot(x - 14, z - 12) < 5 || Math.hypot(x - 27, z - 25) < 4 || (b.chance(0.06));
        if (!hole) b.set(x, 16, z, ROOF);
    }
    for (let x = 8; x <= 32; x += 6) for (let z = 5; z <= 31; z++) b.set(x, 15, z, bars('z'));
    for (const [x, z] of [[8, 10], [14, 20], [20, 9], [26, 18], [32, 27]]) b.box(x, 11, z, x, 14, z, chain);
    b.box(3, 3, 14, 3, 6, 18, AIR); // a side door
    b.box(36, 3, 14, 36, 6, 16, AIR); // to the boiler house
    // the furnace line along the north wall, glowing pits in front of it
    for (let x = 5; x <= 23; x += 2) b.set(x, 3, 6, ['minecraft:blast_furnace', {facing: 'south', lit: 'true'}]);
    for (let x = 5; x <= 23; x += 2) b.set(x, 2, 8, 'minecraft:magma_block');
    b.set(9, 3, 7, ['minecraft:campfire', {facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false'}]);
    // the crucible: an iron vat full of glowing slag
    b.walls(26, 3, 6, 32, 6, 12, 'minecraft:iron_block');
    b.box(27, 3, 7, 31, 5, 11, 'minecraft:magma_block');
    b.box(28, 5, 8, 30, 5, 10, 'minecraft:shroomlight');
    b.set(29, 7, 9, chain); b.set(29, 8, 9, chain);
    // the conveyor and the presses
    for (let x = 4; x <= 34; x++) b.set(x, 3, 18, ['minecraft:rail', {shape: 'east_west', waterlogged: 'false'}]);
    for (const x of [12, 13, 25]) b.set(x, 3, 18, AIR);
    for (const x of [9, 15, 21]) {
        b.box(x - 1, 3, 22, x - 1, 7, 22, 'minecraft:iron_block'); b.box(x + 1, 3, 22, x + 1, 7, 22, 'minecraft:iron_block');
        b.box(x - 1, 8, 22, x + 1, 8, 22, 'minecraft:iron_block');
        b.set(x, 7, 22, ['minecraft:piston', {facing: 'down', extended: 'false'}]);
        b.set(x, 3, 22, ['minecraft:damaged_anvil', {facing: 'east'}]);
        b.set(x, 3, 20, ['minecraft:hopper', {enabled: 'true', facing: 'down'}]);
    }
    // a catwalk along the north wall
    for (let x = 3; x <= 35; x++) { b.set(x, 9, 5, slab('minecraft:smooth_stone_slab', 'top')); b.set(x, 9, 6, slab('minecraft:smooth_stone_slab', 'top')); b.set(x, 10, 7, bars('x')); }
    for (let y = 3; y <= 9; y++) b.set(4, y, 5, ladder('south'));
    b.set(34, 10, 5, chest('south', 'abandoned_factory'));
    // the store in the south-east: waste, barrels of loot, the control desk
    for (const [x, z] of [[29, 27], [31, 28], [33, 26], [30, 30], [34, 30]]) b.set(x, 3, z, BARREL);
    b.set(32, 3, 24, lootBarrel('abandoned_factory')); b.set(33, 3, 24, lootBarrel('abandoned_factory'));
    b.set(26, 3, 30, chest('north', 'abandoned_factory'));
    for (let z = 26; z <= 30; z++) b.set(4, 3, z, slab('minecraft:polished_blackstone_slab', 'top'));
    for (let z = 26; z <= 30; z++) b.set(3, 5, z, monitor('east'));
    b.set(5, 3, 28, chest('east', 'abandoned_factory'));
    wreck(b, 19, 3, 26, 'worker', 30);
    wreck(b, 31, 3, 16, 'worker', -90);
    for (const [x, z, f] of [[3, 12, 'east'], [35, 22, 'west'], [20, 31, 'north'], [20, 5, 'south']]) b.set(x, 8, z, lamp(f));
    // the boiler house and its two chimneys, smoke still rising
    room(b, 37, 6, 47, 24, 2, 7, LOW, 'minecraft:polished_andesite', 'minecraft:deepslate_tiles');
    for (const z1 of [8, 16]) {
        b.walls(40, 3, z1, 44, 35, z1 + 4, 'minecraft:bricks');
        for (let y = 9; y <= 33; y += 6) b.walls(40, y, z1, 44, y, z1 + 4, 'minecraft:deepslate_bricks');
        b.box(41, 3, z1 + 1, 43, 33, z1 + 3, AIR);
        b.set(42, 34, z1 + 2, 'minecraft:hay_block');
        b.set(42, 35, z1 + 2, ['minecraft:campfire', {facing: 'north', lit: 'true', signal_fire: 'true', waterlogged: 'false'}]);
        b.set(42, 3, z1, ['minecraft:blast_furnace', {facing: 'north', lit: 'true'}]);
    }
    b.set(39, 3, 22, chest('east', 'abandoned_factory'));
    b.set(46, 6, 15, lamp('west'));
    // the transformer yard: copper, lightning rods, a fence
    for (const z of [8, 14, 20, 26]) { b.box(49, 3, z, 50, 4, z + 1, 'minecraft:oxidized_copper'); b.set(49, 5, z, 'minecraft:lightning_rod'); b.set(50, 5, z + 1, 'minecraft:lightning_rod'); }
    for (let z = 6; z <= 30; z++) b.set(52, 3, z, bars('z'));
    b.set(48, 3, 30, BARREL);
    return b;
}

// ------------------------------------------------------------------ communication station: a lattice mast, dishes, the control building

function commStation() {
    const b = new Build(36, 48, 36, 160614);
    const CON = 'minecraft:gray_concrete', POL = 'minecraft:polished_deepslate', POST = post('minecraft:polished_blackstone_wall');
    b.box(0, 0, 0, 35, 1, 35, 'minecraft:stone_bricks');
    yard(b, 0, 0, 35, 35, 'minecraft:gravel', ['minecraft:polished_andesite', 'minecraft:coarse_dirt'], 0.25);
    b.box(1, 3, 1, 34, 20, 34, AIR);
    // the mast: three sections, each narrower, with rings of bars, two platforms and lamps
    b.box(19, 2, 7, 29, 3, 17, 'minecraft:smooth_stone');
    const sections = [[21, 27, 9, 15, 4, 19], [22, 26, 10, 14, 20, 33], [23, 25, 11, 13, 34, 43]];
    for (const [x1, x2, z1, z2, y1, y2] of sections) {
        for (const [x, z] of [[x1, z1], [x2, z1], [x1, z2], [x2, z2]]) b.box(x, y1, z, x, y2, z, POST);
        for (let y = y1 + 3; y <= y2; y += 4) {
            for (let x = x1 + 1; x < x2; x++) { b.set(x, y, z1, bars('x')); b.set(x, y, z2, bars('x')); }
            for (let z = z1 + 1; z < z2; z++) { b.set(x1, y, z, bars('z')); b.set(x2, y, z, bars('z')); }
        }
        b.set(x1 - 1, y2, z1, lamp('west')); b.set(x2 + 1, y2, z1, lamp('east')); b.set(x1 - 1, y2, z2, lamp('west')); b.set(x2 + 1, y2, z2, lamp('east'));
    }
    for (const [x1, x2, z1, z2, y] of [[21, 27, 9, 15, 19], [22, 26, 10, 14, 33]]) {
        for (let x = x1 + 1; x < x2; x++) for (let z = z1 + 1; z < z2; z++) b.set(x, y, z, slab('minecraft:polished_blackstone_slab', 'top'));
    }
    for (let y = 4; y <= 33; y++) b.set(24, y, 13, ladder('north'));
    b.box(24, 4, 14, 24, 33, 14, 'minecraft:polished_deepslate'); // the mast's spine, which the ladder hangs on
    b.box(24, 44, 12, 24, 46, 12, POST);
    b.set(24, 47, 12, 'minecraft:lightning_rod');
    b.set(23, 46, 12, lamp('west')); b.set(25, 46, 12, lamp('east')); b.set(24, 46, 11, lamp('north')); b.set(24, 46, 13, lamp('south'));
    dish(b, 24, 26, 16, 3, 'south', 'minecraft:white_concrete');
    dish(b, 28, 38, 12, 2, 'east', 'minecraft:white_concrete');
    b.set(23, 20, 11, turret('north'));
    // two dishes on the ground
    for (const [x, z, f] of [[6, 7, 'north'], [12, 5, 'east']]) { b.box(x, 3, z, x, 5, z, 'minecraft:iron_block'); dish(b, x, 8, z, 3, f, 'minecraft:white_concrete'); }
    // the control building
    room(b, 3, 19, 19, 33, 2, 6, CON, 'minecraft:polished_andesite', 'minecraft:deepslate_tiles');
    for (const [x, z] of [[3, 19], [19, 19], [3, 33], [19, 33]]) b.box(x, 3, z, x, 9, z, POL);
    windows(b, 'x', 33, 4, 18, 5, 6, 'minecraft:black_stained_glass_pane', 4);
    windows(b, 'z', 3, 20, 32, 5, 6, 'minecraft:black_stained_glass_pane', 4);
    b.box(10, 3, 33, 11, 5, 33, AIR);
    b.set(9, 6, 34, lamp('south')); b.set(12, 6, 34, lamp('south'));
    room(b, 12, 19, 19, 25, 9, 4, CON, 'minecraft:deepslate_tiles', 'minecraft:deepslate_tiles');
    b.box(13, 3, 25, 13, 8, 25, CON);
    for (let y = 3; y <= 9; y++) b.set(13, y, 24, ladder('north'));
    for (let x = 13; x <= 18; x++) b.set(x, 12, 20, monitor('south'));
    b.set(18, 10, 24, chest('west', 'comm_station'));
    b.box(16, 14, 22, 16, 18, 22, POST); b.set(16, 19, 22, 'minecraft:lightning_rod');
    // the screen wall: three rows of monitors, desks with screens in front of it
    for (let x = 4; x <= 11; x++) for (let y = 4; y <= 6; y++) b.set(x, y, 20, monitor('south'));
    for (let x = 4; x <= 11; x++) b.set(x, 3, 23, slab('minecraft:polished_blackstone_slab', 'top'));
    for (const x of [5, 8, 11]) { b.set(x, 4, 23, monitor('south')); b.set(x, 3, 24, stairs('minecraft:polished_blackstone_stairs', 'south')); }
    // the server room and the generator room
    for (let z = 27; z <= 32; z++) if (z !== 29) { b.set(4, 3, z, rack('east')); b.set(4, 4, z, rack('east')); b.set(8, 3, z, rack('west')); b.set(8, 4, z, rack('west')); }
    for (let z = 26; z <= 32; z++) for (let y = 3; y <= 7; y++) b.set(10, y, z, CON);
    for (let x = 4; x <= 18; x++) for (let y = 3; y <= 7; y++) if (x !== 6 && x !== 14) b.set(x, y, 26, CON);
    b.box(10, 3, 30, 10, 4, 30, AIR);
    b.box(13, 3, 28, 16, 5, 31, 'minecraft:iron_block');
    b.box(14, 6, 29, 15, 6, 30, 'minecraft:copper_block');
    b.set(13, 6, 28, 'minecraft:lightning_rod'); b.set(16, 6, 31, 'minecraft:lightning_rod');
    b.set(18, 3, 32, lootBarrel('comm_station')); b.set(18, 3, 27, lootBarrel('comm_station'));
    b.set(5, 3, 32, chest('north', 'comm_station'));
    b.set(16, 3, 21, chest('south', 'comm_station'));
    b.set(12, 6, 27, lamp('south'));
    // the cable trench to the mast and one wreck at its foot
    for (let z = 18; z <= 19; z++) for (let x = 15; x <= 24; x++) b.set(x, 2, z, 'minecraft:black_concrete');
    wreck(b, 21, 3, 20, 'mobile', 200);
    return b;
}

// ------------------------------------------------------------------ defensive outpost: walls, two towers, barracks and armory

function defensiveOutpost() {
    const b = new Build(40, 22, 32, 160615);
    const WALL = 'minecraft:stone_bricks', TOW = 'minecraft:deepslate_bricks', CON = 'minecraft:gray_concrete';
    b.box(0, 0, 0, 39, 1, 30, 'minecraft:cobbled_deepslate');
    yard(b, 0, 0, 39, 30, 'minecraft:coarse_dirt', ['minecraft:gravel', 'minecraft:packed_mud', 'minecraft:cobblestone'], 0.3);
    b.box(1, 3, 1, 38, 20, 29, AIR);
    b.walls(0, 3, 0, 39, 7, 30, WALL);
    battlements(b, 0, 0, 39, 30, 8, WALL);
    for (let x = 1; x <= 38; x++) { b.set(x, 7, 1, slab('minecraft:stone_brick_slab', 'top')); b.set(x, 7, 29, slab('minecraft:stone_brick_slab', 'top')); }
    for (let z = 2; z <= 28; z++) { b.set(1, 7, z, slab('minecraft:stone_brick_slab', 'top')); b.set(38, 7, z, slab('minecraft:stone_brick_slab', 'top')); }
    // the gate with its portcullis half up
    b.box(17, 3, 30, 22, 6, 30, AIR);
    for (let x = 17; x <= 22; x++) b.set(x, 6, 30, bars('x'));
    b.box(16, 3, 30, 16, 9, 30, TOW); b.box(23, 3, 30, 23, 9, 30, TOW);
    b.set(16, 7, 31, lamp('south')); b.set(23, 7, 31, lamp('south'));
    // the two towers with searchlights and turrets
    for (const [x, z, f] of [[0, 24, 'south'], [33, 0, 'north']]) {
        const t = tower(b, x, z, 7, 15, TOW, 'minecraft:deepslate_tiles');
        for (const [px, pz] of [[t.x1, t.z1], [t.x2, t.z1], [t.x1, t.z2], [t.x2, t.z2]]) b.box(px, 16, pz, px, 18, pz, post('minecraft:deepslate_brick_wall'));
        b.box(t.x1, 19, t.z1, t.x2, 19, t.z2, slab('minecraft:deepslate_tile_slab'));
        b.set(t.cx, 16, t.cz, turret(f));
        const [dx, dz] = STEP[f];
        b.set(t.cx + 2 * dx + 1, 16, t.cz + 2 * dz, 'minecraft:sea_lantern');
    }
    b.set(7, 14, 27, lamp('east')); b.set(32, 14, 3, lamp('west'));
    // barracks
    room(b, 3, 3, 17, 12, 2, 4, CON, 'minecraft:spruce_planks', 'minecraft:deepslate_tiles');
    b.box(9, 3, 12, 10, 4, 12, AIR);
    windows(b, 'x', 12, 4, 16, 5, 5, 'minecraft:glass_pane', 3);
    for (const x of [4, 6, 12, 14]) bed(b, 'gray', x, 3, 4, 'south');
    for (let x = 4; x <= 16; x += 4) b.set(x, 3, 11, lootBarrel('defensive_outpost'));
    b.set(16, 3, 4, chest('west', 'defensive_outpost'));
    b.set(10, 6, 4, lamp('south'));
    // the armory and the command post
    room(b, 22, 3, 32, 12, 2, 4, CON, 'minecraft:polished_andesite', 'minecraft:deepslate_tiles');
    b.box(26, 3, 12, 27, 4, 12, AIR);
    for (let x = 23; x <= 31; x++) { b.set(x, 3, 4, crate('south')); if (x % 2) b.set(x, 4, 4, crate('south')); }
    for (let z = 6; z <= 10; z++) b.set(31, 5, z, monitor('west'));
    for (let z = 6; z <= 10; z++) b.set(30, 3, z, slab('minecraft:polished_blackstone_slab', 'top'));
    b.set(23, 3, 10, chest('east', 'defensive_outpost'));
    b.set(23, 6, 7, lamp('east'));
    // the yard: sandbags, crates, a floodlight, and the wreck that held the gate
    for (const [x1, x2, z] of [[10, 15, 24], [24, 29, 24], [17, 22, 19]]) for (let x = x1; x <= x2; x++) { b.set(x, 3, z, 'minecraft:packed_mud'); b.set(x, 4, z, slab('minecraft:mud_brick_slab')); }
    for (const [x, z] of [[34, 22], [35, 22], [34, 23], [5, 17], [6, 17]]) b.set(x, 3, z, crate('north'));
    b.box(30, 3, 18, 30, 7, 18, post('minecraft:polished_deepslate_wall')); b.set(30, 8, 18, 'minecraft:sea_lantern');
    wreck(b, 20, 3, 26, 'mobile', 10);
    b.set(36, 3, 27, chest('north', 'defensive_outpost'));
    return b;
}

// ------------------------------------------------------------------ contamination zone: an ancient arch over a glowing pool

function contaminationZone() {
    const b = new Build(38, 28, 38, 160616);
    const C = 19;
    b.box(0, 0, 0, 37, 1, 37, 'minecraft:mossy_cobblestone');
    for (let x = 0; x < 38; x++) for (let z = 0; z < 38; z++) {
        const d = Math.hypot(x - C, z - C);
        if (d > 18.5) continue;
        b.set(x, 2, z, b.chance(0.35) ? b.pick(['minecraft:moss_block', 'minecraft:coarse_dirt', 'minecraft:mud']) : b.pick(['minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:stone_bricks']));
        if (d < 17.5) b.box(x, 3, z, x, 22, z, AIR);
    }
    // the arch: a broken ring of dark stone with green lights set in its inner face
    for (let x = 6; x <= 32; x++) for (let y = 3; y <= 20; y++) {
        const r = y <= 6 ? Math.abs(x - C) : Math.hypot(x - C, y - 6);
        if (r < 8.5 || r > 11.5) continue;
        const angle = Math.atan2(y - 6, x - C);
        if (angle > 1.75 && angle < 2.25 && y > 12) continue; // the fallen keystones
        for (let z = 18; z <= 20; z++) {
            const block = r < 9.5 && z === 19 && (y % 4 === 1) ? 'minecraft:verdant_froglight'
                : b.chance(0.25) ? 'minecraft:cracked_deepslate_bricks' : b.pick(['minecraft:deepslate_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles']);
            b.set(x, y, z, block);
        }
    }
    for (const [x, y, z] of [[13, 3, 22], [14, 3, 23], [12, 3, 15], [26, 3, 23], [27, 4, 23], [27, 3, 22]]) b.set(x, y, z, 'minecraft:cracked_deepslate_bricks');
    // pools of irradiated sludge, sunk into the floor; the big one under the arch
    const pool = (px, pz, rx, rz) => {
        for (let x = px - rx; x <= px + rx; x++) for (let z = pz - rz; z <= pz + rz; z++) {
            if (((x - px) / (rx + 0.5)) ** 2 + ((z - pz) / (rz + 0.5)) ** 2 > 1) continue;
            b.set(x, 1, z, 'minecraft:mud');
            b.set(x, 2, z, SLUDGE);
        }
    };
    pool(C, 19, 5, 3);
    pool(8, 28, 3, 2);
    pool(29, 9, 2, 3);
    pool(10, 8, 2, 2);
    // broken columns around, with green lights inside
    for (const [x, z] of [[7, 7], [31, 7], [7, 31], [31, 31], [19, 4]]) {
        const h = 4 + Math.floor(b.rand() * 6);
        b.box(x, 3, z, x, 2 + h, z, 'minecraft:deepslate_tiles');
        b.set(x, 5, z, 'minecraft:verdant_froglight');
    }
    // remains of an outer wall
    for (let a = 0; a < Math.PI * 2; a += 0.03) {
        const x = Math.round(C + Math.cos(a) * 16.5), z = Math.round(C + Math.sin(a) * 16.5);
        if (Math.sin(a * 3) > 0.6) continue;
        const h = Math.floor(b.rand() * 4);
        for (let y = 3; y <= 2 + h; y++) b.set(x, y, z, b.pick(['minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:mossy_cobblestone']));
    }
    // debris, barrels, dead trees and moss
    for (let i = 0; i < 30; i++) {
        const a = b.rand() * Math.PI * 2, r = 3 + b.rand() * 12;
        const x = Math.round(C + Math.cos(a) * r), z = Math.round(C + Math.sin(a) * r);
        if (b.get(x, 2, z) !== SLUDGE && b.get(x, 3, z) === AIR) b.set(x, 3, z, DEBRIS);
    }
    for (const [x, z] of [[12, 27], [13, 28], [27, 13], [24, 28]]) b.set(x, 3, z, BARREL);
    for (const [x, z, h] of [[5, 19, 6], [33, 22, 5], [15, 33, 4], [23, 5, 5]]) {
        b.box(x, 3, z, x, 2 + h, z, log('minecraft:stripped_dark_oak_log'));
        b.set(x + 1, 1 + h, z, log('minecraft:stripped_dark_oak_log', 'x'));
        b.set(x, h, z - 1, log('minecraft:stripped_dark_oak_log', 'z'));
    }
    for (let i = 0; i < 40; i++) {
        const x = 2 + Math.floor(b.rand() * 34), z = 2 + Math.floor(b.rand() * 34);
        if (Math.hypot(x - C, z - C) < 17 && b.get(x, 3, z) === AIR && b.get(x, 2, z) !== SLUDGE) b.set(x, 3, z, b.chance(0.6) ? 'minecraft:moss_carpet' : 'minecraft:dead_bush');
    }
    // a sunken bunker by the east wall, flooded with sludge
    room(b, 27, 25, 34, 33, 2, 4, 'minecraft:cracked_stone_bricks', 'minecraft:mud', null);
    b.box(27, 7, 25, 34, 7, 29, 'minecraft:deepslate_tiles');
    b.box(28, 2, 30, 31, 2, 32, SLUDGE);
    b.box(27, 3, 28, 27, 4, 29, AIR);
    b.set(30, 7, 27, 'minecraft:verdant_froglight');
    b.set(33, 3, 26, chest('west', 'contamination_zone'));
    b.set(33, 3, 32, BARREL); b.set(28, 3, 26, TANK); b.set(29, 3, 26, 'minecraft:glass');
    // the expedition that did not come back: a wrecked armor by the pool and what it carried
    wreck(b, 24, 3, 24, 'combat', 160);
    b.set(26, 3, 25, chest('west', 'contamination_zone'));
    b.set(12, 3, 14, chest('south', 'contamination_zone'));
    return b;
}

fs.mkdirSync(OUT, {recursive: true});
const ALL = {military_base: militaryBase, science_complex: scienceComplex, abandoned_factory: abandonedFactory,
    comm_station: commStation, defensive_outpost: defensiveOutpost, contamination_zone: contaminationZone};
for (const [name, make] of Object.entries(ALL)) {
    const b = make();
    fs.writeFileSync(path.join(OUT, name + '.nbt'), nbtFile(b.toNbt()));
    const counts = {};
    for (const block of b.cells.values()) {
        const n = Array.isArray(block) ? block[0] : block;
        if (n.startsWith(P) || n === 'minecraft:chest' || n === 'minecraft:barrel') counts[n.replace(P, '')] = (counts[n.replace(P, '')] || 0) + 1;
    }
    console.log(name.padEnd(19), b.size.join('x'), b.cells.size + ' cells', b.entities.length + ' wrecks', JSON.stringify(counts));
}
