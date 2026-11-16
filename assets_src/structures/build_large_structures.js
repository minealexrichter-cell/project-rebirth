// Builds the large structures of version 1.6.0 (after the owner's concept art): a military base, a science complex, an abandoned
// factory, a communication station, a defensive outpost and a contamination zone.
// Usage, from the project root:  node assets_src/structures/build_large_structures.js
//
// Each structure is a layout of pieces, the way a village is (structures/CompoundStructure.java): every building is its own
// template that stands at the height of the ground under it ("rigid"), while walls, fences, paths and yard props lie on the
// ground block by block ("follow"). So a structure grows into the land instead of sitting on one flat plate.
// Writes src/main/resources/data/projectrebirth/structure/ruins/<structure>/<piece>.nbt and assets_src/structures/layouts.json,
// which write_structure_data.js turns into the worldgen data. Decay (cracks, broken glass, moss) is added at placement by the
// processor list ruins_decay; the weathering here (vines, cobwebs, moss, a few leaves, rubble) is part of the templates.
//
// Coordinates in a piece: x east, y up, z south; y 0-1 foundation, y 2 the ground floor, which is put flush with the ground.
// Building methods (from building guides): pillars stand one block out of the wall, windows sit in the recess between them with
// a sill under them, a cornice marks each floor and a parapet the roof; a dark frame with a lighter trim.
const fs = require('fs');
const path = require('path');
const {nbtFile, Build, T, str, byte, float, list, compound} = require('./structure_lib');

const OUT = path.join(__dirname, '../../src/main/resources/data/projectrebirth/structure/ruins');
const P = 'projectrebirth:';
const AIR = 'minecraft:air';
const DEBRIS = P + 'radioactive_debris';
const BARREL = P + 'leaking_waste_barrel';
const SLUDGE = P + 'irradiated_sludge';
const CABLE = P + 'cable';
const F = 2; // the floor row of a rigid piece

// ------------------------------------------------------------------ block helpers

const facing = (id, f, extra) => [P + id, Object.assign({facing: f}, extra || {})];
// One random for the choices of the helpers below, so each run writes the same files.
let seed = 1606;
const roll = () => { seed = (seed * 1103515245 + 12345) >>> 0; return (seed >>> 8) / 16777216; };
const BROKEN_SHARE = 0.65, RUSTED_SHARE = 0.8;
// monitors, racks and tanks: mostly broken; machines: about four rusted to one that still works
const monitor = f => facing(roll() < BROKEN_SHARE ? 'broken_wall_monitor' : 'wall_monitor', f);
const rack = f => facing(roll() < BROKEN_SHARE ? 'broken_server_rack' : 'server_rack', f);
const tank = () => P + (roll() < BROKEN_SHARE ? 'broken_specimen_tank' : 'specimen_tank');
const machine = (id, f, props) => roll() < RUSTED_SHARE ? facing('rusted_' + id, f || 'north') : [P + id, Object.assign(f ? {facing: f} : {}, props)];
const station = (id, f) => machine(id, f, {working: 'false'});
const generator = f => machine('combustion_generator', f, {lit: 'false'});
const battery = f => machine('battery', f, {working: 'false'});
const solar = f => machine('solar_panel', f, {working: 'false'});
const backupBattery = () => machine('backup_battery', null, {working: 'false'});
const heater = () => machine('heater', null, {working: 'false'});
// the emergency lamp on a wall (looking toward f), standing on a block or a fence post, or hanging from a ceiling
const lamp = f => [P + 'emergency_lamp', {face: 'wall', facing: f, post: 'false'}];
const lampUp = [P + 'emergency_lamp', {face: 'floor', facing: 'north', post: 'false'}];
const lampHang = [P + 'emergency_lamp', {face: 'ceiling', facing: 'north', post: 'false'}];

/**
 * A turret looking toward f at (x, y, z). About two in five still work: a guard turret (an entity, part B3 of 1.6.0) that turns and
 * shoots, with the room it needs left free; the rest are the broken turret's three blocks (base, tower over it, barrel in front).
 */
const YAW = {south: 0, west: 90, north: 180, east: -90};
function turretAt(b, x, y, z, f, active) {
    const [dx, dz] = STEP[f];
    if (active === undefined ? roll() < 0.4 : active) {
        b.set(x, y, z, AIR); b.set(x, y + 1, z, AIR); b.set(x + dx, y + 1, z + dz, AIR);
        b.entity(x, y, z, compound({id: str(P + 'guard_turret'), Rotation: list(T.FLOAT, [float(YAW[f]), float(0)]), PersistenceRequired: byte(1)}));
        return;
    }
    const id = P + 'wrecked_turret';
    b.set(x, y, z, [id, {facing: f, part: 'base'}]);
    b.set(x, y + 1, z, [id, {facing: f, part: 'tower'}]);
    b.set(x + dx, y + 1, z + dz, [id, {facing: f, part: 'barrel'}]);
}

/**
 * A guard post of the yard (part B3 of 1.6.0): a patch of trodden ground and a walking machine on it looking toward f, 'patrol_robot'
 * or 'mech'. It keeps to this place and walks round it (see WalkingGunner).
 */
function guardPost(kind, f, seed, count) {
    const n = kind === 'mech' || count > 1 ? 3 : 1;
    const b = new Build(n, 2, n, seed);
    for (let x = 0; x < n; x++) for (let z = 0; z < n; z++) b.set(x, 0, z, b.pick(['minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:packed_mud']));
    const c = (n - 1) / 2;
    const spots = [[c, c], [0, 2], [2, 0], [2, 2], [0, 0]];
    for (let i = 0; i < (count || 1); i++) {
        b.entity(spots[i][0], 1, spots[i][1], compound({id: str(P + kind), Rotation: list(T.FLOAT, [float(YAW[f]), float(0)]), PersistenceRequired: byte(1)}));
    }
    return b;
}

/** What a lamp can hold on to in the template: 'post' (a fence or a stone wall), 'solid', or null (nothing, a slab or a thin block). */
const NO_HOLD = /air|vine|leaves|carpet|cobweb|lamp|monitor|ladder|pane|bars|door|trapdoor|banner|chain|torch|lantern|rod|sludge|rail|lever|plate|bed|cable|fern|grass|azalea|bush|campfire|turret|slab|stairs|glass|solar|chest|barrel|crate|rack|tank/;
function holdOf(b, x, y, z) {
    const c = b.get(x, y, z);
    if (!c) return null; // outside what the template places: the land there is unknown
    const n = name(c);
    if (/_wall$|_fence$/.test(n)) return 'post';
    return NO_HOLD.test(n) ? null : 'solid';
}

/**
 * Every lamp of a piece holds on to something: the block it is fixed to must be solid, a fence or a stone wall, inside the piece.
 * A lamp that would hang on nothing is moved to a floor, a ceiling or a wall next to it, or taken away if there is none.
 */
function fixLamps(b) {
    const sides = {north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0]};
    let moved = 0, removed = 0;
    for (const [k, cell] of [...b.cells]) {
        if (name(cell) !== P + 'emergency_lamp') continue;
        const [x, y, z] = k.split(',').map(Number);
        const props = cell[1];
        const support = props.face === 'floor' ? [0, -1, 0] : props.face === 'ceiling' ? [0, 1, 0] : sides[OPPOSITE[props.facing]];
        let hold = holdOf(b, x + support[0], y + support[1], z + support[2]);
        let next = Object.assign({}, props);
        if (!hold) {
            // look for something to hold on to: under a ceiling one or two blocks up, a wall beside, the floor
            let target = null;
            for (let dy = 1; dy <= 2 && !target; dy++) {
                const free = [...Array(dy).keys()].every(i => b.get(x, y + 1 + i - 1 + (i === 0 ? 0 : 0), z) === AIR || i === 0);
                if (b.get(x, y + dy, z) === AIR && holdOf(b, x, y + dy + 1, z) && free) target = [x, y + dy, z, {face: 'ceiling', facing: props.facing}];
            }
            if (!target) {
                const wall = Object.entries(sides).find(([, [dx, , dz]]) => holdOf(b, x + dx, y, z + dz));
                if (wall) target = [x, y, z, {face: 'wall', facing: OPPOSITE[wall[0]]}];
            }
            if (!target && holdOf(b, x, y - 1, z)) target = [x, y, z, {face: 'floor', facing: props.facing}];
            if (!target) {
                removed++;
                if (process.env.LAMP_DEBUG) console.log('    lamp at', x, y, z, JSON.stringify(props), 'support', JSON.stringify(b.get(x + support[0], y + support[1], z + support[2])));
                b.cells.set(k, AIR);
                continue;
            }
            moved++;
            if (target[1] !== y) b.cells.set(k, AIR);
            next = Object.assign({post: 'false'}, target[3]);
            const [tx, ty, tz] = target;
            if (next.face === 'wall') {
                const [dx, , dz] = sides[OPPOSITE[next.facing]];
                next.post = String(holdOf(b, tx + dx, ty, tz + dz) === 'post');
            }
            b.set(tx, ty, tz, [P + 'emergency_lamp', next]);
            continue;
        }
        if (next.face === 'wall') {
            const [dx, , dz] = sides[OPPOSITE[next.facing]];
            next.post = String(holdOf(b, x + dx, y, z + dz) === 'post');
        } else next.post = 'false';
        b.cells.set(k, [P + 'emergency_lamp', next]);
    }
    return {moved, removed};
}
const crate = f => facing('ammo_crate', f);
const chest = (f, table) => ['minecraft:chest', {facing: f, type: 'single', waterlogged: 'false'}, {LootTable: P + 'chests/' + table}];
const lootBarrel = (table, f) => ['minecraft:barrel', {facing: f || 'up', open: 'false'}, {LootTable: P + 'chests/' + table}];
const plainBarrel = f => ['minecraft:barrel', {facing: f || 'up', open: 'false'}];
const pane = (axis, glass) => [glass || 'minecraft:glass_pane', axis === 'x'
    ? {east: 'true', west: 'true', north: 'false', south: 'false', waterlogged: 'false'}
    : {north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false'}];
const bars = axis => pane(axis, 'minecraft:iron_bars');
const pole = ['minecraft:polished_deepslate_wall', {up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false'}];
const slab = (block, type) => [block, {type: type || 'bottom', waterlogged: 'false'}];
const stair = (block, f, half) => [block, {facing: f, half: half || 'bottom', shape: 'straight', waterlogged: 'false'}];
const post = block => [block, {up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false'}];
const ladder = f => ['minecraft:ladder', {facing: f, waterlogged: 'false'}];
const banner = (color, f) => ['minecraft:' + color + '_wall_banner', {facing: f}];
const chain = ['minecraft:chain', {axis: 'y', waterlogged: 'false'}];
const hangingLantern = ['minecraft:lantern', {hanging: 'true', waterlogged: 'false'}];
const log = (block, axis) => [block, {axis: axis || 'y'}];
const trapdoor = (f, half, open) => ['minecraft:iron_trapdoor', {facing: f, half: half || 'top', open: open ? 'true' : 'false', powered: 'false', waterlogged: 'false'}];
const leaves = kind => ['minecraft:' + (kind || 'oak') + '_leaves', {distance: '7', persistent: 'true', waterlogged: 'false'}];
const STEP = {north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0]};
const OPPOSITE = {north: 'south', south: 'north', west: 'east', east: 'west'};
const name = cell => Array.isArray(cell) ? cell[0] : cell;

function bed(b, color, x, y, z, f) {
    const [dx, dz] = STEP[f];
    b.set(x, y, z, ['minecraft:' + color + '_bed', {facing: f, part: 'foot', occupied: 'false'}]);
    b.set(x + dx, y, z + dz, ['minecraft:' + color + '_bed', {facing: f, part: 'head', occupied: 'false'}]);
}

/** A door of the mod's airlock (two blocks high); width 2 makes a double door. `f` is the side the door looks to. */
function airlock(b, x, y, z, f, width) {
    const [dx] = STEP[f];
    const across = dx === 0 ? [1, 0] : [0, 1];
    for (let i = 0; i < (width || 1); i++) {
        const hinge = i === 0 ? 'left' : 'right';
        const cx = x + across[0] * i, cz = z + across[1] * i;
        b.set(cx, y, cz, [P + 'airlock_door', {facing: f, half: 'lower', hinge, open: 'false', powered: 'false'}]);
        b.set(cx, y + 1, cz, [P + 'airlock_door', {facing: f, half: 'upper', hinge, open: 'false', powered: 'false'}]);
    }
}

function ironDoor(b, x, y, z, f, open) {
    b.set(x, y, z, ['minecraft:iron_door', {facing: f, half: 'lower', hinge: 'left', open: open ? 'true' : 'false', powered: 'false'}]);
    b.set(x, y + 1, z, ['minecraft:iron_door', {facing: f, half: 'upper', hinge: 'left', open: open ? 'true' : 'false', powered: 'false'}]);
}

/** A wrecked power armor (variant combat, mobile or worker) standing on the block; yaw 0 looks south, 90 west, 180 north. */
function wreck(b, x, y, z, variant, yaw) {
    b.entity(x, y, z, compound({
        id: str(P + 'power_armor'), Variant: str(variant), ArmorState: byte(2), Structural: float(0), Critical: float(0),
        Initialized: byte(1), Rotation: list(T.FLOAT, [float(yaw), float(0)])
    }));
}

/** Gives every cable cell its connections: to a neighbouring cable or a machine of the mod. */
const MACHINES = /^projectrebirth:(rusted_.*|cable|combustion_generator|battery|solar_panel|backup_battery|radioisotope_generator|.*_station|.*fabricator|.*workstation|weapon_workshop|air_filtration_unit|heater|charging_pad|shelter_control_panel|decontamination_chamber)$/;
function wireCables(b) {
    for (const [k, cell] of b.cells) {
        if (name(cell) !== CABLE) continue;
        const [x, y, z] = k.split(',').map(Number);
        const props = {};
        for (const [dir, [dx, dy, dz]] of Object.entries({north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]})) {
            const n = b.get(x + dx, y + dy, z + dz);
            props[dir] = n && MACHINES.test(name(n)) ? 'true' : 'false';
        }
        b.cells.set(k, [CABLE, props]);
    }
}
function cableLine(b, points, y) {
    for (let i = 0; i + 1 < points.length; i++) {
        const [ax, az] = points[i], [bx, bz] = points[i + 1];
        for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) b.set(x, y, z, CABLE);
    }
}

/** Battlements: every other block along the top of a wall rectangle. */
function battlements(b, x1, z1, x2, z2, y, block) {
    for (let x = x1; x <= x2; x += 2) { b.set(x, y, z1, block); b.set(x, y, z2, block); }
    for (let z = z1; z <= z2; z += 2) { b.set(x1, y, z, block); b.set(x2, y, z, block); }
}

// ------------------------------------------------------------------ the building kit

/**
 * The shell of a building at (x0, z0) of size w x d with storeys of the given inner heights: foundation, a plinth ring, walls set one
 * block back from a ring of pillars, recessed windows with sills between the pillars, a cornice at each floor, a flat roof with a
 * parapet. Returns the levels ({y, top}), the roof height and the bounds.
 */
function shell(b, o) {
    const x1 = o.x0, z1 = o.z0, x2 = o.x0 + o.w - 1, z2 = o.z0 + o.d - 1, bay = o.bay || 5;
    const isPillar = (i, from, to) => i === from || i === to || (i - from) % bay === 0;
    const nearPillar = (i, from, to) => isPillar(i, from, to) || isPillar(i - 1, from, to) || isPillar(i + 1, from, to);
    b.box(x1, 0, z1, x2, 1, z2, o.trim);
    for (let x = x1; x <= x2; x++) for (let z = z1; z <= z2; z++) if (x === x1 || x === x2 || z === z1 || z === z2) b.set(x, F, z, o.trim);
    let y = F;
    const levels = [];
    o.storeys.forEach((h, i) => {
        b.box(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, i === 0 ? o.floor : (o.upperFloor || o.floor));
        b.box(x1 + 2, y + 1, z1 + 2, x2 - 2, y + h, z2 - 2, AIR);
        b.walls(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h, z2 - 1, o.wall);
        if (o.glass && h >= 4) {
            const top = y + Math.min(h - 1, o.tallWindows ? 4 : 3);
            for (let x = x1 + 1; x < x2; x++) {
                if (nearPillar(x, x1, x2)) continue;
                for (let yy = y + 2; yy <= top; yy++) { b.set(x, yy, z1 + 1, pane('x', o.glass)); b.set(x, yy, z2 - 1, pane('x', o.glass)); }
                b.set(x, y + 1, z1, slab(o.trimSlab, 'top')); b.set(x, y + 1, z2, slab(o.trimSlab, 'top'));
            }
            for (let z = z1 + 1; z < z2; z++) {
                if (nearPillar(z, z1, z2)) continue;
                for (let yy = y + 2; yy <= top; yy++) { b.set(x1 + 1, yy, z, pane('z', o.glass)); b.set(x2 - 1, yy, z, pane('z', o.glass)); }
                b.set(x1, y + 1, z, slab(o.trimSlab, 'top')); b.set(x2, y + 1, z, slab(o.trimSlab, 'top'));
            }
        }
        levels.push({y, top: y + h});
        y += h + 1;
        for (let x = x1; x <= x2; x++) { b.set(x, y, z1, slab(o.trimSlab, 'top')); b.set(x, y, z2, slab(o.trimSlab, 'top')); }
        for (let z = z1; z <= z2; z++) { b.set(x1, y, z, slab(o.trimSlab, 'top')); b.set(x2, y, z, slab(o.trimSlab, 'top')); }
    });
    const roofY = y;
    b.box(x1 + 1, roofY, z1 + 1, x2 - 1, roofY, z2 - 1, o.roof);
    for (let x = x1; x <= x2; x++) for (const z of [z1, z2]) if (isPillar(x, x1, x2)) b.box(x, F, z, x, roofY + 1, z, o.pillar);
    for (let z = z1; z <= z2; z++) for (const x of [x1, x2]) if (isPillar(z, z1, z2)) b.box(x, F, z, x, roofY + 1, z, o.pillar);
    for (let x = x1 + 1; x < x2; x++) { b.set(x, roofY + 1, z1 + 1, o.wall); b.set(x, roofY + 1, z2 - 1, o.wall); }
    for (let z = z1 + 1; z < z2; z++) { b.set(x1 + 1, roofY + 1, z, o.wall); b.set(x2 - 1, roofY + 1, z, o.wall); }
    return {levels, roofY, x1, z1, x2, z2};
}

/** An entrance in the wall of a shell on side f at offset `at` along the wall: airlock doors, a lintel, a canopy, lamps, a step. */
function entrance(b, s, f, at, level, width, o) {
    const w = width || 2, y = s.levels[level || 0].y;
    const horizontal = f === 'north' || f === 'south';
    const wallLine = {north: s.z1 + 1, south: s.z2 - 1, west: s.x1 + 1, east: s.x2 - 1}[f];
    const ring = {north: s.z1, south: s.z2, west: s.x1, east: s.x2}[f];
    const [dx, dz] = STEP[f];
    const out = horizontal ? dz : dx;
    const cell = (along, k, yy, blk) => horizontal ? b.set(along, yy, k, blk) : b.set(k, yy, along, blk);
    for (let i = -1; i <= w; i++) {
        const along = at + i;
        if (i >= 0 && i < w) {
            cell(along, wallLine, y + 1, AIR); cell(along, wallLine, y + 2, AIR); cell(along, wallLine, y + 3, o.lintel || o.trim);
            cell(along, ring, y + 1, AIR); cell(along, ring, y + 2, AIR); cell(along, ring, y + 3, AIR);
            cell(along, ring + out, y, slab(o.trimSlab));
        } else {
            for (let yy = y + 1; yy <= y + 3; yy++) cell(along, ring, yy, o.pillar);
        }
        cell(along, ring, y + 4, slab(o.trimSlab, 'top'));
        cell(along, ring + out, y + 4, slab(o.trimSlab, 'top'));
    }
    if (horizontal) airlock(b, at, y + 1, wallLine, OPPOSITE[f], w); else airlock(b, wallLine, y + 1, at, OPPOSITE[f], w);
    cell(at - 1, ring + out, y + 3, lamp(f));
    cell(at + w, ring + out, y + 3, lamp(f));
}

/** A straight stair run of `rise` steps going toward f from (x, z) on the floor y, `width` wide, solid under, headroom cut above. */
function stairRun(b, x, z, f, y, rise, width, block, under) {
    const [dx, dz] = STEP[f];
    const across = dx === 0 ? [1, 0] : [0, 1];
    for (let i = 0; i < rise; i++) for (let j = 0; j < width; j++) {
        const cx = x + dx * i + across[0] * j, cz = z + dz * i + across[1] * j;
        for (let k = y + 1; k < y + 1 + i; k++) b.set(cx, k, cz, under);
        b.set(cx, y + 1 + i, cz, stair(block, f));
        for (let k = 1; k <= 3; k++) b.set(cx, y + 1 + i + k, cz, AIR);
    }
}

/** A partition wall along x (axis 'x', z fixed) or along z, with door openings (2 high) at the given positions. */
function partition(b, axis, fixed, from, to, y, h, block, doors) {
    for (let i = from; i <= to; i++) for (let yy = y + 1; yy <= y + h; yy++) {
        const door = (doors || []).includes(i) && yy <= y + 2;
        if (axis === 'x') b.set(i, yy, fixed, door ? AIR : block); else b.set(fixed, yy, i, door ? AIR : block);
    }
}

/** A small room with walls, floor and roof: floor at y, walls from y + 1 to y + h, roof above. */
function room(b, x1, z1, x2, z2, y, h, wall, roof) {
    b.box(x1, y, z1, x2, y, z2, roof);
    b.box(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h, z2 - 1, AIR);
    b.walls(x1, y + 1, z1, x2, y + h, z2, wall);
    b.box(x1, y + h + 1, z1, x2, y + h + 1, z2, roof);
}

/**
 * A dish of radius r centred at (cx, cy, cz), facing the horizontal side `f` and tipped up by 45 degrees: every block whose middle
 * lies within half a block of a shallow bowl around that axis, so the surface has no holes; a lighter rim, a feed arm and a receiver.
 */
function dish(b, cx, cy, cz, r, f, block) {
    const [dx, dz] = STEP[f];
    const n = [dx * Math.SQRT1_2, Math.SQRT1_2, dz * Math.SQRT1_2];
    for (let x = cx - r - 2; x <= cx + r + 2; x++) for (let y = cy - r - 2; y <= cy + r + 2; y++) for (let z = cz - r - 2; z <= cz + r + 2; z++) {
        const d = [x - cx, y - cy, z - cz];
        const t = d[0] * n[0] + d[1] * n[1] + d[2] * n[2];
        const rho = Math.sqrt(Math.max(0, d[0] * d[0] + d[1] * d[1] + d[2] * d[2] - t * t));
        if (rho > r + 0.4) continue;
        if (Math.abs(t - rho * rho / (r * 3.5)) > 0.62) continue;
        b.set(x, y, z, rho > r - 0.7 ? 'minecraft:light_gray_concrete' : block);
    }
    for (let k = 1; k <= 2; k++) b.set(cx + Math.round(n[0] * k * 1.4), cy + Math.round(n[1] * k * 1.4), cz + Math.round(n[2] * k * 1.4), 'minecraft:iron_bars');
    b.set(cx + Math.round(n[0] * 4.2), cy + Math.round(n[1] * 4.2), cz + Math.round(n[2] * 4.2), 'minecraft:iron_block');
}

// ------------------------------------------------------------------ weathering: moss, vines, cobwebs, a few leaves, rubble

const SOFT = /air|vine|leaves|carpet|cobweb|lamp|monitor|ladder|pane|bars|door|trapdoor|banner|chain|torch|lantern|rod|slab|stairs|_wall$|sludge|rail|lever|plate|bed|chest|barrel|crate|turret|rack|tank|cable|fern|grass|azalea|bush|campfire|panel/;
function solidAt(b, x, y, z) {
    const c = b.get(x, y, z);
    return !!c && !SOFT.test(name(c));
}

/**
 * Signs of abandonment, kept sparing: vines hang from the outside of walls (only in open air around a building, never into it),
 * cobwebs in upper corners of rooms, moss carpet on some floors, a few clumps of leaves on the roof, rubble at the foot of walls.
 */
function weather(b, o) {
    const opt = Object.assign({vines: 0.07, webs: 0.22, moss: 0.012, leafClumps: 3, rubble: 6}, o || {});
    const [sx, sy, sz] = b.size;
    const sides = {north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0]};
    for (let x = 0; x < sx; x++) for (let z = 0; z < sz; z++) {
        for (const [dir, [dx, dz]] of Object.entries(sides)) {
            if (!b.chance(opt.vines)) continue;
            let top = -1;
            for (let y = sy - 1; y >= F + 1; y--) if (b.get(x, y, z) === undefined && solidAt(b, x + dx, y, z + dz)) { top = y; break; }
            if (top < 0) continue;
            const len = 2 + Math.floor(b.rand() * 5);
            for (let y = top; y > top - len && y >= F + 1; y--) {
                if (b.get(x, y, z) !== undefined || !solidAt(b, x + dx, y, z + dz)) break;
                b.set(x, y, z, ['minecraft:vine', {north: String(dir === 'north'), south: String(dir === 'south'), west: String(dir === 'west'), east: String(dir === 'east'), up: 'false'}]);
            }
        }
    }
    for (const [k, cell] of [...b.cells]) {
        if (cell !== AIR) continue;
        const [x, y, z] = k.split(',').map(Number);
        const walls = Object.values(sides).filter(([dx, dz]) => solidAt(b, x + dx, y, z + dz)).length;
        if (solidAt(b, x, y + 1, z) && walls >= 2 && b.chance(opt.webs)) b.set(x, y, z, 'minecraft:cobweb');
        else if (solidAt(b, x, y - 1, z) && b.get(x, y + 1, z) === AIR && b.chance(opt.moss)) b.set(x, y, z, 'minecraft:moss_carpet');
    }
    const roofTop = [];
    for (let x = 1; x < sx - 1; x++) for (let z = 1; z < sz - 1; z++) {
        for (let y = sy - 2; y > F; y--) {
            if (solidAt(b, x, y, z) && b.get(x, y + 1, z) === undefined) { roofTop.push([x, y, z]); break; }
            if (b.get(x, y, z) !== undefined) break;
        }
    }
    for (let i = 0; i < opt.leafClumps && roofTop.length; i++) {
        const [x, y, z] = roofTop[Math.floor(b.rand() * roofTop.length)];
        const kind = b.pick(['oak', 'azalea', 'dark_oak']);
        for (const [dx, dy, dz] of [[0, 1, 0], [1, 1, 0], [0, 1, 1], [-1, 1, 0], [0, 2, 0], [1, 1, 1]]) {
            if (b.get(x + dx, y + dy, z + dz) === undefined && b.chance(0.8)) b.set(x + dx, y + dy, z + dz, leaves(kind));
        }
    }
    let placed = 0;
    for (let tries = 0; tries < 600 && placed < opt.rubble; tries++) {
        const x = Math.floor(b.rand() * sx), z = Math.floor(b.rand() * sz);
        if (b.get(x, F + 1, z) !== undefined || b.get(x, F, z) !== undefined) continue;
        if (!Object.values(sides).some(([dx, dz]) => solidAt(b, x + dx, F + 1, z + dz))) continue;
        b.set(x, F + 1, z, b.pick([slab('minecraft:cobblestone_slab'), slab('minecraft:mossy_cobblestone_slab'), slab('minecraft:andesite_slab'), 'minecraft:mossy_cobblestone']));
        placed++;
    }
}

// ------------------------------------------------------------------ small pieces shared by the layouts

/** A wall that lies on the ground: base in the ground, `height` blocks, crenels on top, some stretches broken lower, a ledge inside. */
function wallLine(len, axis, height, block, ledge, seed) {
    const b = new Build(axis === 'x' ? len : 2, height + 2, axis === 'x' ? 2 : len, seed);
    const set = (i, y, k, blk) => axis === 'x' ? b.set(i, y, k, blk) : b.set(k, y, i, blk);
    for (let i = 0; i < len; i++) {
        const broken = b.chance(0.12) ? 1 + Math.floor(b.rand() * 2) : 0;
        const top = height - broken;
        for (let y = 0; y <= top; y++) set(i, y, 0, block);
        if (!broken && i % 2 === 0) set(i, top + 1, 0, block);
        set(i, top - 1, 1, slab(ledge, 'top'));
    }
    return b;
}

/** A fence of iron bars on a low curb, posts every four blocks, a gap here and there where it has fallen. */
function fenceLine(len, axis, seed) {
    const b = new Build(axis === 'x' ? len : 1, 4, axis === 'x' ? 1 : len, seed);
    for (let i = 0; i < len; i++) {
        const set = (y, blk) => axis === 'x' ? b.set(i, y, 0, blk) : b.set(0, y, i, blk);
        set(0, 'minecraft:polished_andesite');
        if (i % 4 === 0) { for (let y = 1; y <= 3; y++) set(y, post('minecraft:andesite_wall')); continue; }
        if (b.chance(0.15)) continue;
        set(1, bars(axis)); set(2, bars(axis));
    }
    return b;
}

/** A path on the ground, `w` wide, of mixed materials, with ragged edges. */
function pathStrip(len, w, axis, seed, mats) {
    const b = new Build(axis === 'x' ? len : w, 1, axis === 'x' ? w : len, seed);
    const m = mats || ['minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:packed_mud', 'minecraft:gravel', 'minecraft:dirt_path'];
    for (let i = 0; i < len; i++) for (let j = 0; j < w; j++) {
        if ((j === 0 || j === w - 1) && b.chance(0.3)) continue;
        axis === 'x' ? b.set(i, 0, j, b.pick(m)) : b.set(j, 0, i, b.pick(m));
    }
    return b;
}

/** A sandbag nest open toward `f`: a U of packed mud with a slab top, a crate and maybe a chest inside. */
function sandbags(f, table, seed) {
    const b = new Build(5, 3, 5, seed);
    const open = (x, z) => (f === 'south' && z === 4 || f === 'north' && z === 0 || f === 'east' && x === 4 || f === 'west' && x === 0) && !(x % 4 === 0 && z % 4 === 0);
    for (let x = 0; x < 5; x++) for (let z = 0; z < 5; z++) {
        b.set(x, 0, z, b.pick(['minecraft:coarse_dirt', 'minecraft:gravel']));
        if ((x === 0 || x === 4 || z === 0 || z === 4) && !open(x, z)) { b.set(x, 1, z, 'minecraft:packed_mud'); b.set(x, 2, z, slab('minecraft:mud_brick_slab')); }
    }
    b.set(2, 1, 2, crate(f));
    if (table) b.set(1, 1, 1, chest(f, table));
    return b;
}

/** A floodlight on a post with a hood, rigid on a small pad. */
function floodlight(seed) {
    const b = new Build(3, 11, 3, seed);
    b.box(0, 0, 0, 2, 1, 2, 'minecraft:cobblestone');
    b.box(0, F, 0, 2, F, 2, 'minecraft:polished_andesite');
    b.box(1, F + 1, 1, 1, F + 6, 1, post('minecraft:polished_deepslate_wall'));
    b.set(1, F + 7, 1, 'minecraft:sea_lantern');
    b.set(1, F + 8, 1, slab('minecraft:polished_deepslate_slab'));
    b.set(0, F + 7, 1, trapdoor('west', 'top', true)); b.set(2, F + 7, 1, trapdoor('east', 'top', true));
    return b;
}

/** A spot where a wrecked armor fell: scorched ground, scattered plates, the armor, maybe what it carried. */
function wreckSpot(variant, yaw, table, seed) {
    const b = new Build(7, 4, 7, seed);
    b.box(0, 0, 0, 6, 1, 6, 'minecraft:dirt');
    for (let x = 0; x < 7; x++) for (let z = 0; z < 7; z++) {
        const d = Math.hypot(x - 3, z - 3);
        if (d < 3.3) b.set(x, F, z, d < 1.6 ? b.pick(['minecraft:coal_block', 'minecraft:black_concrete_powder', 'minecraft:coarse_dirt']) : b.pick(['minecraft:coarse_dirt', 'minecraft:gravel', 'minecraft:grass_block']));
    }
    wreck(b, 3, F + 1, 3, variant, yaw);
    for (const [x, z] of [[1, 4], [5, 2], [4, 5]]) b.set(x, F + 1, z, trapdoor(b.pick(['north', 'east']), 'bottom', false));
    if (table) b.set(5, F + 1, 4, chest('west', table));
    b.set(1, F + 1, 1, slab('minecraft:cobblestone_slab'));
    return b;
}

/** A row of solar panels on a levelled strip with a cable behind them, ending in a battery; rigid, so the cable stays joined. */
function solarRow(len, seed) {
    const b = new Build(len, 4, 2, seed);
    b.box(0, 0, 0, len - 1, 1, 1, 'minecraft:cobblestone');
    for (let x = 0; x < len; x++) {
        b.set(x, F, 0, 'minecraft:polished_andesite'); b.set(x, F, 1, 'minecraft:gravel');
        if (!b.chance(0.15)) b.set(x, F + 1, 0, solar('south'));
        b.set(x, F + 1, 1, x === len - 1 ? battery('west') : CABLE);
    }
    wireCables(b);
    return b;
}

/** A lamp post on the ground: a dark fence post with the emergency lamp standing on top. */
function lampPost(seed) {
    const b = new Build(1, 4, 1, seed);
    b.set(0, 0, 0, 'minecraft:gravel');
    b.set(0, 1, 0, 'minecraft:dark_oak_fence'); b.set(0, 2, 0, 'minecraft:dark_oak_fence');
    b.set(0, 3, 0, lampUp);
    return b;
}

/** A heap of scrap on the ground. */
function scrapHeap(seed) {
    const b = new Build(5, 4, 5, seed);
    for (let x = 0; x < 5; x++) for (let z = 0; z < 5; z++) {
        const h = Math.max(0, 3 - Math.round(Math.hypot(x - 2, z - 2) + b.rand()));
        for (let y = 0; y <= h; y++) b.set(x, y, z, b.pick(['minecraft:gravel', 'minecraft:cobblestone', 'minecraft:andesite', 'minecraft:raw_iron_block', 'minecraft:coarse_dirt']));
        if (h >= 1 && b.chance(0.35)) b.set(x, h + 1, z, b.pick([trapdoor('north', 'bottom', false), 'minecraft:iron_bars', ['minecraft:rail', {shape: 'east_west', waterlogged: 'false'}]]));
    }
    return b;
}

/** A dead tree standing on the ground, bark falling off, two stubs of branches. */
function deadTree(h, seed) {
    const b = new Build(3, h + 2, 3, seed);
    b.set(1, 0, 1, 'minecraft:coarse_dirt');
    for (let y = 1; y <= h; y++) b.set(1, y, 1, log(b.chance(0.5) ? 'minecraft:stripped_dark_oak_log' : 'minecraft:dark_oak_log'));
    b.set(2, h - 1, 1, log('minecraft:stripped_dark_oak_log', 'x'));
    b.set(1, h - 2, 0, log('minecraft:stripped_dark_oak_log', 'z'));
    return b;
}

/** A square tower: a door at the foot on side `door`, a ladder up, a platform with battlements, options for a roof, turret, light, antenna. */
function towerPiece(size, top, wall, floor, door, opts, seed) {
    const M = 1, S = size + 2 * M;
    const b = new Build(S, top + 9, S, seed);
    const x1 = M, z1 = M, x2 = M + size - 1, z2 = M + size - 1, c = M + Math.floor(size / 2);
    b.box(x1, 0, z1, x2, 1, z2, 'minecraft:cobbled_deepslate');
    b.box(x1, F, z1, x2, F, z2, floor);
    b.box(x1 + 1, F + 1, z1 + 1, x2 - 1, top, z2 - 1, AIR);
    b.walls(x1, F + 1, z1, x2, top, z2, wall);
    for (const [x, z] of [[x1, z1], [x2, z1], [x1, z2], [x2, z2]]) b.box(x, F, z, x, top + 1, z, 'minecraft:polished_deepslate');
    for (let y = F + 5; y < top; y += 5) b.walls(x1, y, z1, x2, y, z2, 'minecraft:polished_deepslate');
    for (let y = F + 4; y < top - 1; y += 4) { b.set(x1, y, c, bars('z')); b.set(x2, y, c, bars('z')); b.set(c, y, z1, bars('x')); }
    b.box(x1, top, z1, x2, top, z2, floor);
    for (let y = F + 1; y <= top; y++) b.set(c, y, z2 - 1, ladder('north'));
    const [dx, dz] = STEP[door];
    const px = door === 'west' ? x1 : door === 'east' ? x2 : c - 1, pz = door === 'north' ? z1 : door === 'south' ? z2 : c - 1;
    ironDoor(b, px, F + 1, pz, OPPOSITE[door], false);
    b.set(px + dx, F + 3, pz + dz, lamp(door));
    battlements(b, x1, z1, x2, z2, top + 1, wall);
    if (opts.roof) {
        for (const [x, z] of [[x1, z1], [x2, z1], [x1, z2], [x2, z2]]) b.box(x, top + 1, z, x, top + 3, z, post('minecraft:deepslate_brick_wall'));
        b.box(x1, top + 4, z1, x2, top + 4, z2, slab('minecraft:deepslate_tile_slab'));
    }
    if (opts.turret) turretAt(b, c, top + 1, c, opts.turret);
    if (opts.light) { b.set(x1 + 1, top + 1, z1 + 1, 'minecraft:sea_lantern'); b.set(x1 + 1, top + 2, z1 + 1, trapdoor('north', 'bottom', false)); }
    if (opts.antenna) {
        for (let y = top + 1; y <= top + 7; y++) b.set(c + 1, y, c, pole);
        b.set(c + 1, top + 8, c, 'minecraft:lightning_rod');
        b.set(c, top + 6, c, lamp('west')); b.set(c + 2, top + 6, c, lamp('east'));
    }
    weather(b, {leafClumps: 1, rubble: 3});
    return b;
}

// ------------------------------------------------------------------ the layouts

/** One structure: its size, the foundation block, and its pieces. */
class Layout {
    constructor(id, sizeX, sizeZ, foundation) { Object.assign(this, {id, sizeX, sizeZ, foundation, parts: []}); }
    rigid(piece, b, x, z, floor) { this.parts.push({piece, b, x, z, floor: floor === undefined ? F : floor, follow: false}); }
    follow(piece, b, x, z) { this.parts.push({piece, b, x, z, floor: 0, follow: true}); }
}

// ------------------------------------------------------------------ military base

function militaryBase() {
    const L = new Layout('military_base', 66, 62, 'minecraft:cobbled_deepslate');
    const WALL = 'minecraft:deepslate_bricks', CON = 'minecraft:gray_concrete', TILE = 'minecraft:deepslate_tiles', BLACK = 'minecraft:polished_blackstone_bricks';
    const BASE = {wall: CON, pillar: 'minecraft:polished_deepslate', trim: WALL, trimSlab: 'minecraft:deepslate_brick_slab',
        floor: 'minecraft:polished_andesite', upperFloor: TILE, roof: TILE, glass: 'minecraft:black_stained_glass_pane', bay: 5, lintel: 'minecraft:polished_deepslate'};

    L.rigid('tower_nw', towerPiece(7, 22, BLACK, TILE, 'south', {antenna: true, turret: 'north'}, 611), 0, 0);
    L.rigid('tower_ne', towerPiece(6, 12, BLACK, TILE, 'south', {turret: 'north', roof: true}, 612), 58, 0);
    L.rigid('tower_sw', towerPiece(6, 12, BLACK, TILE, 'north', {turret: 'south', light: true}, 613), 0, 54);
    L.rigid('tower_se', towerPiece(6, 12, BLACK, TILE, 'north', {turret: 'south', light: true}, 614), 58, 54);
    L.follow('wall_n', wallLine(49, 'x', 5, WALL, 'minecraft:deepslate_brick_slab', 615), 9, 2);
    L.follow('wall_w', wallLine(45, 'z', 5, WALL, 'minecraft:deepslate_brick_slab', 616), 2, 9);
    L.follow('wall_e', wallLine(45, 'z', 5, WALL, 'minecraft:deepslate_brick_slab', 617), 62, 9);
    L.follow('wall_sw', wallLine(16, 'x', 5, WALL, 'minecraft:deepslate_brick_slab', 618), 8, 58);
    L.follow('wall_se', wallLine(16, 'x', 5, WALL, 'minecraft:deepslate_brick_slab', 619), 42, 58);

    // the gatehouse: two blocks with a lintel and the base's banners, turrets, a guard booth, a road through
    (() => {
        const b = new Build(18, 14, 8, 620);
        for (const x0 of [0, 13]) {
            b.box(x0, 0, 1, x0 + 4, 1, 6, 'minecraft:cobbled_deepslate');
            b.box(x0, F, 1, x0 + 4, F, 6, TILE);
            b.box(x0 + 1, F + 1, 2, x0 + 3, F + 7, 5, AIR);
            b.walls(x0, F + 1, 1, x0 + 4, F + 7, 6, BLACK);
            b.box(x0, F + 8, 1, x0 + 4, F + 8, 6, TILE);
            battlements(b, x0, 1, x0 + 4, 6, F + 9, BLACK);
            turretAt(b, x0 + 2, F + 9, 3, 'south');
            b.set(x0 + 2, F + 4, 6, bars('x')); b.set(x0 + 2, F + 5, 6, bars('x'));
        }
        ironDoor(b, 4, F + 1, 3, 'east', false);
        b.set(1, F + 1, 3, slab('minecraft:polished_blackstone_slab', 'top')); b.set(1, F + 2, 3, monitor('east'));
        b.set(3, F + 1, 5, chest('north', 'military_base'));
        b.set(2, F + 3, 2, lamp('south'));
        b.box(5, F + 6, 2, 12, F + 8, 5, BLACK);
        b.box(5, F + 9, 2, 12, F + 9, 5, TILE);
        for (const x of [6, 11]) b.set(x, F + 7, 6, banner('red', 'south'));
        b.set(8, F + 6, 6, lamp('south')); b.set(9, F + 6, 6, lamp('south'));
        b.box(5, 0, 0, 12, 1, 7, 'minecraft:cobblestone');
        for (let x = 5; x <= 12; x++) for (let z = 0; z <= 7; z++) b.set(x, F, z, b.pick(['minecraft:gravel', 'minecraft:polished_andesite', 'minecraft:coarse_dirt']));
        b.set(5, F + 1, 7, post('minecraft:polished_blackstone_wall')); b.set(5, F + 2, 7, post('minecraft:polished_blackstone_wall'));
        b.set(12, F + 1, 7, crate('south'));
        weather(b, {leafClumps: 1});
        L.rigid('gatehouse', b, 24, 54);
    })();

    // the headquarters: three storeys, entrance to the south, a stair room in the south-east
    (() => {
        const M = 2, W = 27, D = 21;
        const b = new Build(W + 2 * M, 30, D + 2 * M, 621);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [4, 4, 4]}, BASE));
        const [g, f2, f3] = s.levels;
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..22, z 0..16
        entrance(b, s, 'south', X(10), 0, 2, BASE);
        for (const x of [X(3), X(8), X(13), X(18)]) b.set(x, f2.y + 3, s.z2 + 1, banner('red', 'south'));
        // ground floor: corridor, the command room to the north, guard room, lobby and stair room to the south
        partition(b, 'x', Z(9), X(0), X(22), g.y, 4, CON, [X(4), X(5), X(10), X(11), X(18)]);
        partition(b, 'z', X(7), Z(10), Z(16), g.y, 4, CON, [Z(13)]);
        partition(b, 'z', X(15), Z(10), Z(16), g.y, 4, CON, [Z(13)]);
        for (let x = X(2); x <= X(20); x++) { b.set(x, g.y + 2, Z(0), monitor('south')); b.set(x, g.y + 3, Z(0), monitor('south')); }
        for (const x of [X(0), X(1), X(21), X(22)]) { b.set(x, g.y + 1, Z(0), rack('south')); b.set(x, g.y + 2, Z(0), rack('south')); }
        for (let x = X(2); x <= X(20); x++) b.set(x, g.y + 1, Z(0), slab('minecraft:polished_blackstone_slab', 'top')); // the console under the screens
        for (const x of [X(5), X(11), X(17)]) b.set(x, g.y + 4, Z(5), lampHang);
        for (let x = X(7); x <= X(15); x++) for (const z of [Z(4), Z(5)]) b.set(x, g.y + 1, z, slab('minecraft:polished_blackstone_slab', 'top'));
        for (const x of [X(8), X(10), X(12), X(14)]) { b.set(x, g.y + 1, Z(3), stair('minecraft:polished_blackstone_stairs', 'south')); b.set(x, g.y + 1, Z(6), stair('minecraft:polished_blackstone_stairs', 'north')); }
        b.set(X(11), g.y + 2, Z(4), monitor('south'));
        b.set(X(3), g.y + 1, Z(8), station('shelter_control_panel', 'north'));
        b.set(X(0), g.y + 1, Z(6), chest('east', 'military_base'));
        for (let x = X(9); x <= X(12); x++) b.set(x, g.y + 1, Z(12), slab('minecraft:polished_blackstone_slab', 'top'));
        b.set(X(10), g.y + 2, Z(12), monitor('south'));
        for (let z = Z(10); z <= Z(15); z++) b.set(X(0), g.y + 1, z, z % 2 ? plainBarrel('east') : lootBarrel('military_base', 'east'));
        b.set(X(5), g.y + 1, Z(16), crate('north')); b.set(X(5), g.y + 2, Z(16), crate('north')); b.set(X(4), g.y + 1, Z(16), crate('north'));
        stairRun(b, X(17), Z(16), 'north', g.y, 5, 2, 'minecraft:polished_deepslate_stairs', 'minecraft:polished_deepslate');
        b.set(X(22), g.y + 1, Z(16), battery('west')); b.set(X(22), g.y + 1, Z(15), CABLE); b.set(X(22), g.y + 1, Z(14), station('shelter_control_panel', 'west'));
        for (const [x, z, f] of [[X(0), Z(3), 'east'], [X(22), Z(3), 'west'], [X(6), Z(11), 'west'], [X(16), Z(11), 'east']]) b.set(x, g.y + 3, z, lamp(f));
        // second floor: dormitory, mess, armory, medical bay
        stairRun(b, X(20), Z(11), 'south', f2.y, 5, 2, 'minecraft:polished_deepslate_stairs', 'minecraft:polished_deepslate');
        partition(b, 'x', Z(9), X(0), X(22), f2.y, 4, CON, [X(4), X(5), X(10), X(11), X(18)]);
        partition(b, 'z', X(11), Z(0), Z(8), f2.y, 4, CON, [Z(5)]);
        partition(b, 'z', X(8), Z(10), Z(16), f2.y, 4, CON, [Z(12)]);
        partition(b, 'z', X(15), Z(10), Z(16), f2.y, 4, CON, [Z(12)]);
        for (const x of [X(1), X(4), X(7), X(10)]) bed(b, 'gray', x, f2.y + 1, Z(1), 'north');
        for (const x of [X(0), X(3), X(6), X(9)]) b.set(x, f2.y + 1, Z(8), plainBarrel('north'));
        b.set(X(0), f2.y + 1, Z(4), lootBarrel('military_base', 'east'));
        for (let x = X(13); x <= X(21); x++) b.set(x, f2.y + 1, Z(4), slab('minecraft:spruce_slab', 'top'));
        for (let x = X(13); x <= X(21); x += 2) { b.set(x, f2.y + 1, Z(3), stair('minecraft:spruce_stairs', 'south')); b.set(x, f2.y + 1, Z(5), stair('minecraft:spruce_stairs', 'north')); }
        b.set(X(22), f2.y + 1, Z(0), 'minecraft:cauldron'); b.set(X(21), f2.y + 1, Z(0), ['minecraft:smoker', {facing: 'south', lit: 'false'}]);
        b.set(X(12), f2.y + 1, Z(0), heater());
        for (let x = X(0); x <= X(7); x++) { b.set(x, f2.y + 1, Z(16), crate('north')); if (x % 2) b.set(x, f2.y + 2, Z(16), crate('north')); }
        b.set(X(0), f2.y + 1, Z(11), station('weapon_workshop', 'east'));
        b.set(X(6), f2.y + 1, Z(11), chest('west', 'military_base'));
        wreck(b, X(4), f2.y + 1, Z(13), 'combat', 160);
        for (const z of [Z(11), Z(14)]) bed(b, 'white', X(10), f2.y + 1, z, 'east');
        b.set(X(14), f2.y + 1, Z(16), ['minecraft:brewing_stand', {has_bottle_0: 'true', has_bottle_1: 'false', has_bottle_2: 'false'}]);
        b.set(X(13), f2.y + 1, Z(16), chest('north', 'military_base'));
        for (const [x, z, f] of [[X(0), Z(6), 'east'], [X(22), Z(6), 'west'], [X(1), Z(14), 'east'], [X(13), Z(10), 'south']]) b.set(x, f2.y + 3, z, lamp(f));
        // third floor: communications, the officer's office, the ladder to the roof
        partition(b, 'x', Z(9), X(0), X(22), f3.y, 4, CON, [X(4), X(5), X(10), X(11)]);
        for (let x = X(0); x <= X(10); x++) b.set(x, f3.y + 3, Z(0), monitor('south'));
        for (const x of [X(1), X(3), X(5), X(7), X(9)]) { b.set(x, f3.y + 1, Z(4), rack('south')); b.set(x, f3.y + 2, Z(4), rack('south')); }
        for (let x = X(13); x <= X(21); x++) b.set(x, f3.y + 1, Z(2), slab('minecraft:polished_blackstone_slab', 'top'));
        for (const x of [X(14), X(17), X(20)]) b.set(x, f3.y + 2, Z(2), monitor('south'));
        b.set(X(0), f3.y + 1, Z(15), slab('minecraft:dark_oak_slab', 'top')); b.set(X(1), f3.y + 1, Z(15), slab('minecraft:dark_oak_slab', 'top'));
        b.set(X(1), f3.y + 2, Z(15), monitor('north'));
        b.set(X(0), f3.y + 1, Z(12), chest('east', 'military_base'));
        b.set(X(8), f3.y + 3, Z(16), banner('red', 'north'));
        for (let y = f3.y + 1; y <= s.roofY; y++) b.set(X(22), y, Z(16), ladder('west'));
        b.set(X(22), s.roofY + 1, Z(16), trapdoor('west', 'bottom', true));
        b.set(X(16), f3.y + 3, Z(10), lamp('south')); b.set(X(5), f3.y + 3, Z(8), lamp('north'));
        // the roof: solar panels and their battery, an antenna, an air unit
        for (let x = X(1); x <= X(12); x++) { b.set(x, s.roofY + 1, Z(2), solar('south')); b.set(x, s.roofY + 1, Z(3), CABLE); }
        b.set(X(13), s.roofY + 1, Z(3), battery('west'));
        b.set(X(18), s.roofY + 1, Z(5), station('air_filtration_unit', 'south'));
        for (let y = s.roofY + 1; y <= s.roofY + 7; y++) b.set(X(16), y, Z(12), pole);
        b.set(X(16), s.roofY + 8, Z(12), 'minecraft:lightning_rod');
        b.set(X(15), s.roofY + 6, Z(12), lamp('west')); b.set(X(17), s.roofY + 6, Z(12), lamp('east'));
        wireCables(b);
        weather(b);
        L.rigid('headquarters', b, 14, 11);
    })();

    // the garage: a roller door to the south, wrecks on the floor and in the repair pit, the workbench wall
    (() => {
        const M = 2, W = 13, D = 19;
        const b = new Build(W + 2 * M, 16, D + 2 * M, 622);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [7]}, BASE, {glass: null, bay: 6}));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..8, z 0..14
        for (let x = X(1); x <= X(7); x++) {
            for (let y = g.y + 1; y <= g.y + 4; y++) { b.set(x, y, s.z2 - 1, AIR); b.set(x, y, s.z2, AIR); }
            b.set(x, g.y + 5, s.z2 - 1, bars('x')); b.set(x, g.y + 5, s.z2, AIR); b.set(x, g.y + 6, s.z2 - 1, 'minecraft:iron_block');
            b.set(x, F, s.z2 + 1, b.pick(['minecraft:gravel', 'minecraft:polished_andesite']));
        }
        b.set(s.x1, g.y + 4, s.z2 + 1, lamp('south')); b.set(s.x2, g.y + 4, s.z2 + 1, lamp('south'));
        b.set(s.x1, g.y + 1, Z(3), AIR); b.set(s.x1, g.y + 2, Z(3), AIR);
        ironDoor(b, s.x1 + 1, g.y + 1, Z(3), 'east', true);
        for (let x = X(3); x <= X(5); x++) for (let z = Z(4); z <= Z(9); z++) { b.set(x, g.y, z, AIR); b.set(x, g.y - 1, z, 'minecraft:black_concrete'); }
        b.set(X(4), g.y, Z(9), ladder('north'));
        wreck(b, X(4), g.y, Z(6), 'mobile', 20);
        wreck(b, X(1), g.y + 1, Z(12), 'combat', -30);
        wreck(b, X(7), g.y + 1, Z(12), 'worker', 10);
        b.set(X(8), g.y + 1, Z(1), station('maintenance_station', 'west'));
        b.set(X(8), g.y + 1, Z(3), station('armor_workstation', 'west'));
        b.set(X(8), g.y + 1, Z(5), station('weapon_workshop', 'west'));
        for (const z of [Z(0), Z(2), Z(4), Z(6)]) b.set(X(8), g.y + 1, z, CABLE);
        b.set(X(8), g.y + 1, Z(7), battery('west')); b.set(X(8), g.y + 2, Z(7), battery('west'));
        for (const z of [Z(1), Z(3), Z(5)]) b.set(X(8), g.y + 3, z, trapdoor('west', 'top', true));
        b.set(X(0), g.y + 1, Z(0), ['minecraft:damaged_anvil', {facing: 'north'}]);
        b.set(X(0), g.y + 1, Z(1), chest('east', 'military_base'));
        b.set(X(0), g.y + 1, Z(7), crate('east')); b.set(X(0), g.y + 2, Z(7), crate('east')); b.set(X(0), g.y + 1, Z(8), crate('east'));
        b.set(X(0), g.y + 1, Z(14), lootBarrel('military_base'));
        for (const [x, z] of [[X(2), Z(2)], [X(6), Z(1)], [X(2), Z(13)]]) b.set(x, g.y, z, 'minecraft:black_concrete');
        for (const z of [Z(2), Z(11)]) { for (let y = g.y + 5; y <= g.y + 7; y++) b.set(X(4), y, z, chain); b.set(X(4), g.y + 4, z, hangingLantern); }
        for (const [x, z, f] of [[X(0), Z(4), 'east'], [X(8), Z(10), 'west']]) b.set(x, g.y + 4, z, lamp(f));
        b.box(X(1), s.roofY, Z(9), X(3), s.roofY, Z(12), AIR);
        b.set(X(2), g.y + 1, Z(10), 'minecraft:moss_block'); b.set(X(3), g.y + 1, Z(10), 'minecraft:moss_carpet');
        wireCables(b);
        weather(b, {moss: 0.02});
        L.rigid('garage', b, 45, 8);
    })();

    // the barracks: beds in two rows, lockers, the heater and the filtration unit
    (() => {
        const M = 2, W = 17, D = 10;
        const b = new Build(W + 2 * M, 12, D + 2 * M, 623);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [4]}, BASE, {bay: 4, glass: 'minecraft:glass_pane'}));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..12, z 0..5
        entrance(b, s, 'east', Z(2), 0, 2, BASE);
        for (const x of [X(0), X(3), X(6), X(9)]) { bed(b, 'gray', x, g.y + 1, Z(0), 'south'); b.set(x + 1, g.y + 1, Z(0), plainBarrel('south')); }
        for (const x of [X(0), X(3), X(6)]) bed(b, 'gray', x, g.y + 1, Z(5), 'north');
        b.set(X(1), g.y + 1, Z(5), lootBarrel('military_base', 'north'));
        b.set(X(12), g.y + 1, Z(0), station('air_filtration_unit', 'west'));
        b.set(X(12), g.y + 1, Z(5), heater());
        b.set(X(9), g.y + 1, Z(5), chest('north', 'military_base'));
        b.set(X(6), g.y + 3, Z(0), lamp('south')); b.set(X(6), g.y + 3, Z(5), lamp('north'));
        weather(b);
        L.rigid('barracks', b, 4, 37);
    })();

    // the generator shed: two generators, batteries, a cable through the wall
    (() => {
        const M = 2, W = 9, D = 7;
        const b = new Build(W + 2 * M, 10, D + 2 * M, 624);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [4]}, BASE, {glass: null, bay: 8}));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..4, z 0..2
        entrance(b, s, 'west', Z(1), 0, 1, BASE);
        b.set(X(1), g.y + 1, Z(0), generator('south')); b.set(X(3), g.y + 1, Z(0), generator('south'));
        b.set(X(1), g.y + 1, Z(2), battery('north')); b.set(X(3), g.y + 1, Z(2), backupBattery());
        cableLine(b, [[X(2), Z(0)], [X(2), Z(2)]], g.y + 1);
        b.set(X(4), g.y + 1, Z(1), station('shelter_control_panel', 'west')); b.set(X(3), g.y + 1, Z(1), CABLE);
        b.set(X(0), g.y + 1, Z(0), plainBarrel('east')); b.set(X(0), g.y + 2, Z(0), lootBarrel('military_base'));
        b.set(X(2), g.y + 3, Z(2), lamp('north'));
        wireCables(b);
        weather(b, {leafClumps: 1});
        L.rigid('generator_shed', b, 46, 38);
    })();

    // the yard
    L.follow('path_gate', pathStrip(19, 4, 'z', 625), 28, 36);
    L.follow('path_cross', pathStrip(24, 3, 'x', 626), 32, 37);
    L.follow('path_garage', pathStrip(8, 3, 'z', 627), 51, 30);
    L.follow('path_barracks', pathStrip(6, 3, 'x', 628), 23, 42);
    L.follow('nest_w', sandbags('south', 'military_base', 629), 19, 51);
    L.follow('nest_e', sandbags('south', null, 630), 34, 49);
    L.rigid('solar', solarRow(10, 631), 44, 50);
    L.rigid('floodlight', floodlight(632), 10, 30);
    L.rigid('wreck_gate', wreckSpot('combat', 180, null, 633), 31, 42);
    L.follow('scrap', scrapHeap(634), 56, 49);
    [[27, 40], [33, 46], [27, 50], [44, 36], [52, 36]].forEach(([x, z], i) => L.follow('lamp_post_' + i, lampPost(635 + i), x, z));
    // the guards of the yard: three patrol robots and the mech
    L.follow('mech_post', guardPost('mech', 'south', 640), 38, 42);
    [[26, 46, 'east'], [41, 47, 'south'], [36, 40, 'west']].forEach(([x, z, f], i) => L.follow('robot_post_' + i, guardPost('patrol_robot', f, 641 + i), x, z));
    return L;
}

// ------------------------------------------------------------------ science complex

function scienceComplex() {
    const L = new Layout('science_complex', 62, 54, 'minecraft:stone_bricks');
    const LAB = {wall: 'minecraft:white_concrete', pillar: 'minecraft:gray_concrete', trim: 'minecraft:light_gray_concrete', trimSlab: 'minecraft:smooth_stone_slab',
        floor: 'minecraft:smooth_stone', upperFloor: 'minecraft:light_gray_concrete', roof: 'minecraft:gray_concrete', glass: 'minecraft:light_blue_stained_glass_pane',
        bay: 4, tallWindows: true, lintel: 'minecraft:gray_concrete'};

    // the laboratory: a lobby with a decontamination chamber, a corridor through, labs, an office; upstairs servers and research
    (() => {
        const M = 2, W = 31, D = 19;
        const b = new Build(W + 2 * M, 22, D + 2 * M, 641);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [5, 5]}, LAB));
        const [g, f2] = s.levels;
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..26, z 0..14
        entrance(b, s, 'south', X(12), 0, 2, LAB);
        entrance(b, s, 'east', Z(7), 0, 2, LAB);
        partition(b, 'x', Z(6), X(0), X(26), g.y, 5, LAB.wall, [X(6), X(7), X(19), X(20)]);
        partition(b, 'x', Z(9), X(0), X(26), g.y, 5, LAB.wall, [X(5), X(12), X(13), X(21)]);
        partition(b, 'z', X(13), Z(0), Z(5), g.y, 5, LAB.wall, []);
        partition(b, 'z', X(9), Z(10), Z(14), g.y, 5, 'minecraft:light_blue_stained_glass', [Z(12)]);
        partition(b, 'z', X(17), Z(10), Z(14), g.y, 5, LAB.wall, [Z(12)]);
        // the specimen lab
        for (const x of [X(1), X(3), X(5), X(7), X(9), X(11)]) { b.set(x, g.y + 1, Z(0), tank()); b.set(x, g.y + 2, Z(0), 'minecraft:iron_block'); }
        for (const x of [X(1), X(4), X(7), X(10)]) b.set(x, g.y + 1, Z(3), tank());
        b.set(X(7), g.y + 1, Z(3), 'minecraft:glass'); b.set(X(7), g.y + 2, Z(3), slab('minecraft:smooth_stone_slab'));
        b.set(X(8), g.y + 1, Z(4), DEBRIS);
        b.set(X(12), g.y + 1, Z(5), station('implant_fabricator', 'west'));
        b.set(X(0), g.y + 1, Z(5), chest('east', 'science_complex'));
        wreck(b, X(4), g.y + 1, Z(5), 'worker', 45);
        // chemistry
        for (let x = X(15); x <= X(25); x++) b.set(x, g.y + 1, Z(2), slab('minecraft:smooth_quartz_slab', 'top'));
        b.set(X(16), g.y + 2, Z(2), ['minecraft:brewing_stand', {has_bottle_0: 'true', has_bottle_1: 'false', has_bottle_2: 'true'}]);
        b.set(X(20), g.y + 2, Z(2), ['minecraft:brewing_stand', {has_bottle_0: 'false', has_bottle_1: 'true', has_bottle_2: 'false'}]);
        b.set(X(23), g.y + 2, Z(2), monitor('south'));
        b.set(X(26), g.y + 1, Z(0), 'minecraft:cauldron'); b.set(X(26), g.y + 2, Z(0), trapdoor('west', 'top', true)); b.set(X(18), g.y + 1, Z(0), 'minecraft:cauldron');
        b.set(X(15), g.y + 1, Z(5), station('electronics_fabricator', 'north'));
        b.set(X(25), g.y + 1, Z(5), lootBarrel('science_complex', 'north'));
        // the lobby: the decontamination chamber by the door, the shelter panel, a desk
        b.set(X(10), g.y + 1, Z(13), facing('decontamination_chamber', 'east', {half: 'lower', working: 'false'}));
        b.set(X(10), g.y + 2, Z(13), facing('decontamination_chamber', 'east', {half: 'upper', working: 'false'}));
        b.set(X(16), g.y + 1, Z(14), station('shelter_control_panel', 'west'));
        for (let x = X(11); x <= X(15); x++) b.set(x, g.y + 1, Z(10), slab('minecraft:smooth_quartz_slab', 'top'));
        b.set(X(13), g.y + 2, Z(10), monitor('south'));
        // the office
        for (let x = X(1); x <= X(4); x++) b.set(x, g.y + 1, Z(13), slab('minecraft:birch_slab', 'top'));
        b.set(X(2), g.y + 2, Z(13), monitor('north'));
        b.set(X(0), g.y + 1, Z(10), 'minecraft:bookshelf'); b.set(X(1), g.y + 1, Z(10), 'minecraft:bookshelf');
        b.set(X(8), g.y + 1, Z(14), chest('west', 'science_complex'));
        // the stair room
        stairRun(b, X(19), Z(13), 'east', g.y, 6, 2, 'minecraft:stone_brick_stairs', 'minecraft:stone_bricks');
        for (const x of [X(3), X(10), X(17), X(24)]) b.set(x, g.y + 5, Z(7), 'minecraft:sea_lantern');
        // upstairs: the server room to the north, the research hall to the south
        partition(b, 'x', Z(7), X(0), X(26), f2.y, 5, LAB.wall, [X(4), X(5), X(14), X(15)]);
        for (const z of [Z(1), Z(4)]) for (let x = X(1); x <= X(22); x++) if (x !== X(12)) { b.set(x, f2.y + 1, z, rack(z === Z(1) ? 'south' : 'north')); b.set(x, f2.y + 2, z, rack(z === Z(1) ? 'south' : 'north')); }
        b.set(X(24), f2.y + 1, Z(0), backupBattery()); b.set(X(25), f2.y + 1, Z(0), backupBattery());
        b.set(X(26), f2.y + 1, Z(5), chest('west', 'science_complex'));
        b.set(X(1), f2.y + 1, Z(10), station('body_modification_station', 'east'));
        b.set(X(1), f2.y + 1, Z(13), station('implant_fabricator', 'east'));
        for (const z of [Z(10), Z(13)]) b.set(X(0), f2.y + 3, z, monitor('east'));
        for (let x = X(5); x <= X(13); x++) b.set(x, f2.y + 1, Z(10), slab('minecraft:smooth_quartz_slab', 'top'));
        for (const x of [X(6), X(9), X(12)]) b.set(x, f2.y + 2, Z(10), monitor('south'));
        for (const x of [X(6), X(8), X(10)]) b.set(x, f2.y + 1, Z(14), tank());
        b.set(X(16), f2.y + 1, Z(14), lootBarrel('science_complex', 'north'));
        for (const [x, z, f] of [[X(0), Z(3), 'east'], [X(26), Z(10), 'west'], [X(13), Z(8), 'south']]) b.set(x, f2.y + 4, z, lamp(f));
        // the roof: air units, a dish, the hole where it fell in
        for (const [x, z] of [[X(3), Z(3)], [X(8), Z(3)], [X(3), Z(11)]]) b.set(x, s.roofY + 1, z, station('air_filtration_unit', 'south'));
        b.box(X(20), s.roofY + 1, Z(7), X(20), s.roofY + 3, Z(7), 'minecraft:iron_block');
        dish(b, X(20), s.roofY + 6, Z(7), 3, 'south', 'minecraft:white_concrete');
        b.box(X(13), s.roofY, Z(10), X(15), s.roofY, Z(12), AIR);
        b.set(X(14), f2.y + 1, Z(11), 'minecraft:moss_block'); b.set(X(15), f2.y + 1, Z(12), slab('minecraft:smooth_stone_slab'));
        wireCables(b);
        weather(b);
        L.rigid('laboratory', b, 4, 6);
    })();

    // the observatory: a drum and a dome with a slit, the telescope, a door toward the laboratory
    (() => {
        const b = new Build(19, 21, 19, 642);
        const cx = 9, cz = 9;
        for (let x = 0; x < 19; x++) for (let z = 0; z < 19; z++) {
            const d = Math.hypot(x - cx, z - cz);
            if (d <= 8.5) { b.box(x, 0, z, x, 1, z, 'minecraft:stone_bricks'); b.set(x, F, z, d > 7.5 ? 'minecraft:stone_bricks' : 'minecraft:smooth_stone'); }
            if (d < 6.5) b.box(x, F + 1, z, x, 18, z, AIR);
            else if (d <= 7.5) { b.box(x, F + 1, z, x, F + 6, z, 'minecraft:white_concrete'); b.set(x, F + 7, z, 'minecraft:gray_concrete'); }
            for (let y = F + 8; y <= F + 16; y++) {
                const r = Math.hypot(x - cx, y - (F + 7), z - cz);
                if (r >= 6.5 && r <= 7.5) b.set(x, y, z, Math.abs(Math.sin(4 * Math.atan2(z - cz, x - cx))) < 0.2 && y < F + 14 ? 'minecraft:gray_concrete' : 'minecraft:light_gray_concrete');
                else if (r < 6.5) b.set(x, y, z, AIR);
            }
        }
        for (let y = F + 10; y <= F + 16; y++) for (let z = 0; z <= cz; z++) if (Math.hypot(0, y - (F + 7), z - cz) >= 5.5) b.set(cx, y, z, AIR);
        b.set(1, F + 1, cz, AIR); b.set(1, F + 2, cz, AIR);
        airlock(b, 2, F + 1, cz, 'west', 1);
        b.set(1, F + 3, cz - 1, lamp('west'));
        b.set(cx, F + 1, cz, 'minecraft:iron_block'); b.set(cx, F + 2, cz, post('minecraft:andesite_wall'));
        for (const [y, z] of [[F + 3, cz], [F + 4, cz - 1], [F + 5, cz - 2], [F + 6, cz - 3]]) b.set(cx, y, z, 'minecraft:iron_block');
        b.set(cx, F + 7, cz - 4, 'minecraft:tinted_glass');
        for (const [x, z, f] of [[4, 6, 'east'], [4, 12, 'east'], [14, 6, 'west'], [14, 12, 'west']]) { b.set(x, F + 1, z, slab('minecraft:polished_blackstone_slab', 'top')); b.set(x, F + 2, z, monitor(f)); }
        b.set(12, F + 1, 14, chest('north', 'science_complex'));
        b.set(9, F + 1, 15, station('electronics_fabricator', 'north'));
        b.set(10, F + 1, 15, battery('north')); b.set(8, F + 1, 15, CABLE); b.set(8, F + 1, 14, backupBattery());
        b.set(3, F + 5, 9, lamp('east'));
        wireCables(b);
        weather(b, {leafClumps: 2});
        L.rigid('observatory', b, 41, 8);
    })();

    // the greenhouse: an iron frame with most of its glass gone and plants run wild
    (() => {
        const b = new Build(14, 9, 10, 643);
        b.box(0, 0, 0, 13, 1, 9, 'minecraft:stone_bricks');
        for (let x = 0; x < 14; x++) for (let z = 0; z < 10; z++) b.set(x, F, z, x === 0 || x === 13 || z === 0 || z === 9 ? 'minecraft:stone_bricks' : b.pick(['minecraft:rooted_dirt', 'minecraft:moss_block', 'minecraft:coarse_dirt', 'minecraft:dirt']));
        for (let x = 0; x < 14; x += 3) for (const z of [0, 9]) b.box(x, F + 1, z, x, F + 4, z, 'minecraft:iron_block');
        for (let x = 0; x < 14; x++) for (let z = 0; z < 10; z++) {
            const edge = x === 0 || x === 13 || z === 0 || z === 9;
            if (edge) { for (let y = F + 1; y <= F + 4; y++) if (b.get(x, y, z) === undefined) b.set(x, y, z, b.chance(0.45) ? AIR : (z === 0 || z === 9 ? pane('x') : pane('z'))); }
            else for (let y = F + 1; y <= F + 4; y++) b.set(x, y, z, AIR);
            b.set(x, F + 5, z, b.chance(0.35) ? AIR : 'minecraft:glass');
        }
        for (let i = 0; i < 16; i++) {
            const x = 1 + Math.floor(b.rand() * 12), z = 1 + Math.floor(b.rand() * 8);
            if (name(b.get(x, F, z)) !== 'minecraft:rooted_dirt' && name(b.get(x, F, z)) !== 'minecraft:moss_block') b.set(x, F, z, 'minecraft:moss_block');
            b.set(x, F + 1, z, b.pick(['minecraft:fern', 'minecraft:azalea', 'minecraft:flowering_azalea', 'minecraft:short_grass']));
        }
        for (let i = 0; i < 6; i++) { const x = 1 + Math.floor(b.rand() * 12), z = 1 + Math.floor(b.rand() * 8); b.set(x, F + 4, z, leaves('azalea')); if (b.chance(0.5)) b.set(x, F + 3, z, leaves('flowering_azalea')); }
        b.set(1, F + 1, 1, ['minecraft:composter', {level: '3'}]); b.set(12, F + 1, 8, lootBarrel('science_complex'));
        b.set(6, F + 1, 9, AIR); b.set(6, F + 2, 9, AIR);
        weather(b, {webs: 0.05, leafClumps: 2, rubble: 3});
        L.rigid('greenhouse', b, 42, 32);
    })();

    // the grounds
    L.follow('path_main', pathStrip(22, 3, 'z', 644, ['minecraft:polished_andesite', 'minecraft:smooth_stone', 'minecraft:andesite', 'minecraft:gravel']), 19, 28);
    L.follow('path_obs', pathStrip(6, 3, 'x', 645, ['minecraft:polished_andesite', 'minecraft:smooth_stone', 'minecraft:gravel']), 37, 16);
    L.rigid('solar_1', solarRow(14, 646), 2, 44);
    L.rigid('solar_2', solarRow(14, 647), 2, 48);
    L.follow('fence_s', fenceLine(26, 'x', 648), 24, 53);
    L.follow('fence_e', fenceLine(26, 'z', 649), 61, 26);
    L.rigid('wreck_bay', wreckSpot('worker', 120, 'science_complex', 650), 28, 36);
    (() => {
        const b = new Build(9, 13, 9, 651);
        b.box(3, 0, 3, 5, 1, 5, 'minecraft:stone_bricks'); b.box(3, F, 3, 5, F, 5, 'minecraft:smooth_stone');
        b.box(4, F + 1, 4, 4, F + 3, 4, 'minecraft:iron_block');
        dish(b, 4, F + 6, 4, 3, 'south', 'minecraft:white_concrete');
        L.rigid('dish', b, 51, 43);
    })();
    [[18, 32], [22, 38], [18, 44], [36, 15], [43, 15]].forEach(([x, z], i) => L.follow('lamp_post_' + i, lampPost(652 + i), x, z));
    // what came out of the labs: a pack of hounds and ghouls in the grounds
    L.follow('hound_pack', guardPost('mutated_hound', 'west', 657, 3), 24, 30);
    [[30, 46], [40, 24]].forEach(([x, z], i) => L.follow('ghoul_' + i, guardPost('glowing_ghoul', 'south', 658 + i), x, z));
    return L;
}

// ------------------------------------------------------------------ abandoned factory

function abandonedFactory() {
    const L = new Layout('abandoned_factory', 74, 52, 'minecraft:cobblestone');
    const HALL = {wall: 'minecraft:gray_concrete', pillar: 'minecraft:polished_deepslate', trim: 'minecraft:stone_bricks', trimSlab: 'minecraft:stone_brick_slab',
        floor: 'minecraft:polished_andesite', roof: 'minecraft:weathered_cut_copper', glass: 'minecraft:glass_pane', bay: 6, lintel: 'minecraft:polished_deepslate'};

    // the hall: one tall storey with a mezzanine office, the furnace line, the crucible, presses, the conveyor, the machine room
    (() => {
        const M = 2, W = 37, D = 27;
        const b = new Build(W + 2 * M, 20, D + 2 * M, 661);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [12]}, HALL));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..32, z 0..22
        for (let x = s.x1 + 2; x < s.x2 - 1; x++) if ((x - s.x1) % 6 > 1 && (x - s.x1) % 6 < 5) for (const z of [s.z1 + 1, s.z2 - 1]) for (let y = g.y + 8; y <= g.y + 10; y++) b.set(x, y, z, pane('x'));
        for (let x = s.x1 + 1; x < s.x2; x++) for (const z of [s.z1 + 1, s.z2 - 1]) b.set(x, g.y + 1, z, 'minecraft:stone_bricks');
        for (let x = s.x1 + 1; x < s.x2; x++) for (let z = s.z1 + 1; z < s.z2; z++) if (Math.hypot(x - X(9), z - Z(7)) < 4.5 || Math.hypot(x - X(22), z - Z(18)) < 3.5) b.set(x, s.roofY, z, AIR);
        for (let x = X(0); x <= X(32); x += 6) for (let z = Z(0); z <= Z(22); z++) b.set(x, g.y + 11, z, bars('z'));
        for (const [x, z] of [[X(6), Z(4)], [X(12), Z(14)], [X(18), Z(3)], [X(24), Z(12)]]) { for (let y = g.y + 7; y <= g.y + 10; y++) b.set(x, y, z, chain); b.set(x, g.y + 6, z, hangingLantern); }
        entrance(b, s, 'west', Z(10), 0, 2, HALL);
        for (let x = X(20); x <= X(27); x++) {
            for (let y = g.y + 1; y <= g.y + 5; y++) { b.set(x, y, s.z2 - 1, AIR); b.set(x, y, s.z2, AIR); }
            b.set(x, g.y + 6, s.z2 - 1, bars('x')); b.set(x, g.y + 7, s.z2 - 1, 'minecraft:iron_block');
        }
        b.set(X(16), g.y + 5, s.z2 + 1, lamp('south')); b.set(X(28), g.y + 5, s.z2 + 1, lamp('south'));
        // the furnace line, a hood over it, glowing pits in front
        for (let x = X(1); x <= X(15); x += 2) { b.set(x, g.y + 1, Z(0), ['minecraft:blast_furnace', {facing: 'south', lit: 'false'}]); b.set(x, g.y, Z(2), 'minecraft:magma_block'); }
        for (let x = X(1); x <= X(15); x++) { b.set(x, g.y + 2, Z(0), 'minecraft:iron_block'); b.set(x, g.y + 3, Z(1), stair('minecraft:stone_brick_stairs', 'south', 'top')); }
        for (const x of [X(3), X(9), X(15)]) for (let y = g.y + 3; y <= g.y + 11; y++) b.set(x, y, Z(0), 'minecraft:oxidized_copper');
        // the crucible
        b.walls(X(20), g.y + 1, Z(0), X(26), g.y + 4, Z(5), 'minecraft:iron_block');
        b.box(X(21), g.y + 1, Z(1), X(25), g.y + 3, Z(4), 'minecraft:magma_block');
        b.box(X(22), g.y + 3, Z(2), X(24), g.y + 3, Z(3), 'minecraft:shroomlight');
        for (let y = g.y + 6; y <= g.y + 10; y++) b.set(X(23), y, Z(2), chain);
        // the conveyor and the presses, cables from the machine room
        for (let x = X(0); x <= X(32); x++) b.set(x, g.y + 1, Z(11), ['minecraft:rail', {shape: 'east_west', waterlogged: 'false'}]);
        for (const x of [X(7), X(8), X(25)]) b.set(x, g.y + 1, Z(11), AIR);
        for (const x of [X(4), X(10), X(16)]) {
            b.box(x - 1, g.y + 1, Z(14), x - 1, g.y + 5, Z(14), 'minecraft:polished_deepslate'); b.box(x + 1, g.y + 1, Z(14), x + 1, g.y + 5, Z(14), 'minecraft:polished_deepslate');
            b.box(x - 1, g.y + 6, Z(14), x + 1, g.y + 6, Z(14), 'minecraft:polished_deepslate');
            b.set(x, g.y + 5, Z(14), ['minecraft:piston', {facing: 'down', extended: 'false'}]);
            b.set(x, g.y + 1, Z(14), ['minecraft:damaged_anvil', {facing: 'east'}]);
            b.set(x, g.y + 1, Z(12), ['minecraft:hopper', {enabled: 'true', facing: 'down'}]);
        }
        cableLine(b, [[X(3), Z(16)], [X(31), Z(16)], [X(31), Z(22)]], g.y + 1);
        b.set(X(2), g.y + 1, Z(16), station('assembly_station', 'east'));
        // the machine room in the south-east under the mezzanine
        partition(b, 'x', Z(17), X(28), X(32), g.y, 4, 'minecraft:gray_concrete', [X(29)]);
        partition(b, 'z', X(27), Z(17), Z(22), g.y, 4, 'minecraft:gray_concrete', []);
        b.set(X(31), g.y + 1, Z(17), CABLE);
        b.set(X(30), g.y + 1, Z(22), generator('north')); b.set(X(32), g.y + 1, Z(22), generator('north'));
        b.set(X(32), g.y + 1, Z(19), battery('west')); b.set(X(32), g.y + 2, Z(19), battery('west'));
        b.set(X(28), g.y + 1, Z(19), chest('east', 'abandoned_factory'));
        b.set(X(30), g.y + 3, Z(18), lamp('south'));
        // the mezzanine office over it, stairs along the east wall
        b.box(X(27), g.y + 5, Z(17), X(32), g.y + 5, Z(22), slab('minecraft:smooth_stone_slab', 'top'));
        for (let x = X(27); x <= X(31); x++) b.set(x, g.y + 6, Z(17), bars('x'));
        stairRun(b, X(32), Z(12), 'south', g.y, 5, 1, 'minecraft:stone_brick_stairs', 'minecraft:stone_bricks');
        b.set(X(29), g.y + 6, Z(22), station('assembly_station', 'north'));
        b.set(X(31), g.y + 6, Z(22), station('electronics_fabricator', 'north'));
        b.set(X(30), g.y + 6, Z(22), CABLE); b.set(X(30), g.y + 6, Z(21), backupBattery());
        b.set(X(27), g.y + 6, Z(20), slab('minecraft:polished_blackstone_slab', 'top')); b.set(X(27), g.y + 7, Z(20), monitor('east'));
        b.set(X(32), g.y + 6, Z(20), chest('west', 'abandoned_factory'));
        // the store and the wrecks
        for (const [x, z] of [[X(0), Z(18)], [X(1), Z(20)], [X(3), Z(22)], [X(0), Z(22)], [X(5), Z(21)]]) b.set(x, g.y + 1, z, BARREL);
        for (const [x, z] of [[X(8), Z(22)], [X(9), Z(22)], [X(8), Z(21)]]) b.set(x, g.y + 1, z, lootBarrel('abandoned_factory'));
        b.set(X(9), g.y + 2, Z(22), plainBarrel());
        b.set(X(0), g.y + 1, Z(6), chest('east', 'abandoned_factory'));
        wreck(b, X(14), g.y + 1, Z(19), 'worker', 30);
        wreck(b, X(26), g.y + 1, Z(8), 'worker', -90);
        for (const [x, z, f] of [[X(0), Z(3), 'east'], [X(32), Z(5), 'west'], [X(16), Z(22), 'north'], [X(8), Z(0), 'south']]) b.set(x, g.y + 6, z, lamp(f));
        wireCables(b);
        weather(b, {moss: 0.01, leafClumps: 4, vines: 0.06});
        L.rigid('hall', b, 2, 6);
    })();

    // the boiler house and its two chimneys, smoke still rising
    (() => {
        const M = 1, W = 12, D = 20;
        const b = new Build(W + 2 * M, 38, D + 2 * M, 662);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [6]}, HALL, {glass: null, bay: 5, roof: 'minecraft:deepslate_tiles'}));
        const g = s.levels[0];
        entrance(b, s, 'west', M + 9, 0, 1, HALL);
        for (const z1 of [M + 3, M + 12]) {
            const x1 = M + 4;
            b.walls(x1, g.y + 1, z1, x1 + 4, 35, z1 + 4, 'minecraft:bricks');
            for (let y = g.y + 6; y <= 33; y += 6) b.walls(x1, y, z1, x1 + 4, y, z1 + 4, 'minecraft:deepslate_bricks');
            b.walls(x1 - 1, 34, z1 - 1, x1 + 5, 34, z1 + 5, slab('minecraft:brick_slab', 'top'));
            b.box(x1 + 1, g.y + 1, z1 + 1, x1 + 3, 34, z1 + 3, AIR);
            b.set(x1 + 2, 34, z1 + 2, 'minecraft:hay_block');
            b.set(x1 + 2, 35, z1 + 2, ['minecraft:campfire', {facing: 'north', lit: 'true', signal_fire: 'true', waterlogged: 'false'}]);
            b.set(x1 + 2, g.y + 1, z1, ['minecraft:blast_furnace', {facing: 'north', lit: 'false'}]);
        }
        b.set(M + 2, g.y + 1, M + 2, generator('east')); b.set(M + 2, g.y + 1, M + 17, chest('east', 'abandoned_factory'));
        b.set(M + 2, g.y + 4, M + 6, lamp('east'));
        weather(b, {leafClumps: 2});
        L.rigid('boiler_house', b, 44, 9);
    })();

    // the warehouse: shelves of barrels, a wreck that was loading them
    (() => {
        const M = 2, W = 15, D = 12;
        const b = new Build(W + 2 * M, 12, D + 2 * M, 663);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [5]}, HALL, {roof: 'minecraft:oxidized_cut_copper', bay: 7}));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..10, z 0..7
        for (const z of [Z(3), Z(4)]) for (let y = g.y + 1; y <= g.y + 4; y++) { b.set(s.x1 + 1, y, z, AIR); b.set(s.x1, y, z, AIR); }
        b.set(s.x1 - 1, g.y + 4, s.z1 + 7, lamp('west')); // on the pillar by the door
        for (const z of [Z(0), Z(7)]) for (let x = X(1); x <= X(10); x++) {
            b.set(x, g.y + 1, z, b.chance(0.3) ? lootBarrel('abandoned_factory', z === Z(0) ? 'south' : 'north') : plainBarrel(z === Z(0) ? 'south' : 'north'));
            b.set(x, g.y + 2, z, slab('minecraft:spruce_slab', 'bottom'));
            if (b.chance(0.5)) b.set(x, g.y + 3, z, plainBarrel('up'));
        }
        wreck(b, X(6), g.y + 1, Z(4), 'worker', 90);
        b.set(X(10), g.y + 3, Z(3), lamp('west'));
        weather(b);
        L.rigid('warehouse', b, 54, 34);
    })();

    // the yard
    (() => {
        const b = new Build(26, 2, 1, 664);
        for (let x = 0; x < 26; x++) { b.set(x, 0, 0, 'minecraft:gravel'); if (!b.chance(0.12)) b.set(x, 1, 0, ['minecraft:rail', {shape: 'east_west', waterlogged: 'false'}]); }
        L.follow('rails', b, 30, 40);
    })();
    L.follow('scrap_1', scrapHeap(665), 46, 44);
    L.follow('scrap_2', scrapHeap(666), 8, 40);
    (() => {
        const b = new Build(6, 2, 5, 667);
        for (let x = 0; x < 6; x++) for (let z = 0; z < 5; z++) { b.set(x, 0, z, b.pick(['minecraft:coarse_dirt', 'minecraft:mud', 'minecraft:gravel'])); if (b.chance(0.35)) b.set(x, 1, z, BARREL); }
        L.follow('waste_dump', b, 16, 42);
    })();
    (() => {
        const b = new Build(7, 7, 5, 668);
        b.box(0, 0, 0, 6, 1, 4, 'minecraft:cobblestone'); b.box(0, F, 0, 6, F, 4, 'minecraft:polished_andesite');
        for (const x of [1, 4]) { b.box(x, F + 1, 1, x + 1, F + 2, 2, 'minecraft:oxidized_copper'); b.set(x, F + 3, 1, 'minecraft:lightning_rod'); b.set(x + 1, F + 3, 2, 'minecraft:lightning_rod'); }
        for (let x = 0; x < 7; x++) { b.set(x, F + 1, 0, bars('x')); b.set(x, F + 1, 4, bars('x')); }
        b.set(3, F + 1, 3, BARREL);
        L.rigid('transformer', b, 62, 8);
    })();
    L.follow('path_load', pathStrip(10, 4, 'z', 669), 26, 37);
    L.follow('path_yard', pathStrip(30, 4, 'x', 670), 22, 47);
    L.follow('path_door', pathStrip(4, 3, 'x', 671), 0, 19);
    [[25, 40], [30, 45], [40, 51], [52, 46]].forEach(([x, z], i) => L.follow('lamp_post_' + i, lampPost(672 + i), x, z));
    // the factory's guards and a ghoul by the waste dump
    [[36, 43, 'west'], [6, 47, 'east']].forEach(([x, z, f], i) => L.follow('robot_post_' + i, guardPost('patrol_robot', f, 676 + i), x, z));
    L.follow('ghoul_0', guardPost('glowing_ghoul', 'south', 678), 24, 44);
    return L;
}

// ------------------------------------------------------------------ communication station

function commStation() {
    const L = new Layout('comm_station', 52, 52, 'minecraft:stone_bricks');
    const OPS = {wall: 'minecraft:gray_concrete', pillar: 'minecraft:polished_deepslate', trim: 'minecraft:deepslate_bricks', trimSlab: 'minecraft:deepslate_brick_slab',
        floor: 'minecraft:polished_andesite', roof: 'minecraft:deepslate_tiles', glass: 'minecraft:black_stained_glass_pane', bay: 4, lintel: 'minecraft:polished_deepslate'};

    // the mast on a fenced pad
    (() => {
        const b = new Build(13, 52, 13, 681);
        const POST = post('minecraft:polished_blackstone_wall');
        b.box(0, 0, 0, 12, 1, 12, 'minecraft:stone_bricks'); b.box(0, F, 0, 12, F, 12, 'minecraft:smooth_stone');
        for (let i = 0; i < 13; i++) for (const [x, z, axis] of [[i, 0, 'x'], [i, 12, 'x'], [0, i, 'z'], [12, i, 'z']]) if (!(x >= 5 && x <= 7 && z === 12)) b.set(x, F + 1, z, bars(axis));
        const sections = [[3, 9, 3, 9, F + 1, F + 16], [4, 8, 4, 8, F + 17, F + 30], [5, 7, 5, 7, F + 31, F + 40]];
        for (const [x1, x2, z1, z2, y1, y2] of sections) {
            for (const [x, z] of [[x1, z1], [x2, z1], [x1, z2], [x2, z2]]) b.box(x, y1, z, x, y2, z, POST);
            for (let y = y1 + 3; y <= y2; y += 4) {
                for (let x = x1 + 1; x < x2; x++) { b.set(x, y, z1, bars('x')); b.set(x, y, z2, bars('x')); }
                for (let z = z1 + 1; z < z2; z++) { b.set(x1, y, z, bars('z')); b.set(x2, y, z, bars('z')); }
            }
            b.set(x1 - 1, y2, z1, lamp('west')); b.set(x2 + 1, y2, z1, lamp('east')); b.set(x1 - 1, y2, z2, lamp('west')); b.set(x2 + 1, y2, z2, lamp('east'));
        }
        for (const [x1, x2, z1, z2, y] of [[3, 9, 3, 9, F + 16], [4, 8, 4, 8, F + 30]]) for (let x = x1 + 1; x < x2; x++) for (let z = z1 + 1; z < z2; z++) b.set(x, y, z, slab('minecraft:polished_blackstone_slab', 'top'));
        b.box(6, F + 1, 7, 6, F + 30, 7, 'minecraft:polished_deepslate');
        for (let y = F + 1; y <= F + 30; y++) b.set(6, y, 6, ladder('north'));
        b.box(6, F + 41, 6, 6, F + 43, 6, POST); b.set(6, F + 44, 6, 'minecraft:lightning_rod');
        for (const [x, z, f] of [[5, 6, 'west'], [7, 6, 'east'], [6, 5, 'north'], [6, 7, 'south']]) b.set(x, F + 43, z, lamp(f));
        dish(b, 6, F + 24, 10, 3, 'south', 'minecraft:white_concrete');
        dish(b, 10, F + 36, 6, 2, 'east', 'minecraft:white_concrete');
        turretAt(b, 5, F + 17, 5, 'north');
        for (let z = 8; z <= 12; z++) b.set(6, F + 1, z, CABLE);
        b.set(7, F + 1, 8, battery('west')); b.set(5, F + 1, 8, backupBattery());
        wreck(b, 9, F + 1, 11, 'mobile', 200);
        wireCables(b);
        weather(b, {vines: 0.03, leafClumps: 0, rubble: 2});
        L.rigid('mast', b, 28, 6);
    })();

    // the control building: entrance hall with the stair, the screen room, the server room, the generator room, the roof hut
    (() => {
        const M = 2, W = 21, D = 16;
        const b = new Build(W + 2 * M, 18, D + 2 * M, 682);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [5]}, OPS));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..16, z 0..11
        entrance(b, s, 'south', X(7), 0, 2, OPS);
        partition(b, 'x', Z(6), X(0), X(16), g.y, 5, OPS.wall, [X(7), X(8)]);
        partition(b, 'z', X(5), Z(7), Z(11), g.y, 5, OPS.wall, [Z(9)]);
        partition(b, 'z', X(11), Z(7), Z(11), g.y, 5, OPS.wall, [Z(9)]);
        for (let x = X(1); x <= X(15); x++) for (let y = g.y + 2; y <= g.y + 4; y++) b.set(x, y, Z(0), monitor('south'));
        for (let x = X(1); x <= X(15); x++) b.set(x, g.y + 1, Z(0), slab('minecraft:polished_blackstone_slab', 'top'));
        for (const x of [X(4), X(12)]) b.set(x, g.y + 5, Z(3), lampHang);
        for (let x = X(2); x <= X(14); x++) b.set(x, g.y + 1, Z(3), slab('minecraft:polished_blackstone_slab', 'top'));
        for (const x of [X(3), X(6), X(10), X(13)]) { b.set(x, g.y + 2, Z(3), monitor('south')); b.set(x, g.y + 1, Z(4), stair('minecraft:polished_blackstone_stairs', 'north')); }
        b.set(X(0), g.y + 1, Z(5), chest('east', 'comm_station'));
        b.set(X(16), g.y + 1, Z(1), station('shelter_control_panel', 'west'));
        for (const z of [Z(7), Z(8), Z(10), Z(11)]) { b.set(X(0), g.y + 1, z, rack('east')); b.set(X(0), g.y + 2, z, rack('east')); b.set(X(3), g.y + 1, z, rack('west')); b.set(X(3), g.y + 2, z, rack('west')); }
        b.set(X(4), g.y + 1, Z(11), lootBarrel('comm_station'));
        b.set(X(13), g.y + 1, Z(11), generator('north')); b.set(X(15), g.y + 1, Z(11), generator('north'));
        b.set(X(16), g.y + 1, Z(8), battery('west')); b.set(X(16), g.y + 2, Z(8), battery('west'));
        cableLine(b, [[X(14), Z(11)], [X(14), Z(7)], [X(16), Z(7)]], g.y + 1);
        cableLine(b, [[X(16), Z(9)], [X(16), Z(10)]], g.y + 1);
        b.set(s.x2 - 1, g.y + 1, Z(9), CABLE); b.set(s.x2, g.y + 1, Z(9), CABLE);
        b.set(X(12), g.y + 1, Z(7), chest('east', 'comm_station'));
        room(b, X(4), Z(5), X(11), Z(11), s.roofY, 4, OPS.wall, OPS.roof);
        stairRun(b, X(10), Z(11), 'north', g.y, 6, 1, 'minecraft:polished_deepslate_stairs', 'minecraft:polished_deepslate');
        b.set(X(8), g.y + 1, Z(10), slab('minecraft:polished_blackstone_slab', 'top')); b.set(X(8), g.y + 2, Z(10), monitor('south'));
        for (const [x, z, f] of [[X(0), Z(2), 'east'], [X(16), Z(4), 'west'], [X(7), Z(7), 'south'], [X(14), Z(10), 'north']]) b.set(x, g.y + 4, z, lamp(f));
        b.set(X(6), s.roofY + 1, Z(6), slab('minecraft:polished_blackstone_slab', 'top')); b.set(X(6), s.roofY + 2, Z(6), monitor('south'));
        b.set(X(8), s.roofY + 1, Z(6), rack('south')); b.set(X(8), s.roofY + 2, Z(6), rack('south'));
        b.set(X(5), s.roofY + 1, Z(10), chest('east', 'comm_station'));
        b.set(X(4), s.roofY + 2, Z(8), pane('z', OPS.glass)); b.set(X(11), s.roofY + 3, Z(8), pane('z', OPS.glass));
        for (const [x, z, h] of [[X(14), Z(2), 7], [X(2), Z(9), 5]]) { b.box(x, s.roofY + 1, z, x, s.roofY + h, z, post('minecraft:polished_blackstone_wall')); b.set(x, s.roofY + h + 1, z, 'minecraft:lightning_rod'); }
        wireCables(b);
        weather(b);
        L.rigid('control', b, 2, 28);
    })();

    // the cable trench to the mast, ground dishes, fuel tanks, fence, paths
    (() => {
        const b = new Build(10, 4, 24, 683);
        for (let z = 0; z < 24; z++) { b.box(9, 0, z, 9, 1, z, 'minecraft:cobblestone'); b.set(9, F, z, 'minecraft:black_concrete'); b.set(9, F + 1, z, CABLE); }
        for (let x = 0; x < 10; x++) { b.box(x, 0, 23, x, 1, 23, 'minecraft:cobblestone'); b.set(x, F, 23, 'minecraft:black_concrete'); b.set(x, F + 1, 23, CABLE); }
        wireCables(b);
        L.rigid('trench', b, 25, 18);
    })();
    for (const [i, x, z, f] of [[0, 4, 4, 'north'], [1, 15, 6, 'east']]) {
        const b = new Build(9, 13, 9, 684 + i);
        b.box(3, 0, 3, 5, 1, 5, 'minecraft:stone_bricks'); b.box(3, F, 3, 5, F, 5, 'minecraft:smooth_stone');
        b.box(4, F + 1, 4, 4, F + 3, 4, 'minecraft:iron_block');
        dish(b, 4, F + 6, 4, 3, f, 'minecraft:white_concrete');
        L.rigid('dish_' + i, b, x, z);
    }
    (() => {
        const b = new Build(7, 7, 4, 686);
        b.box(0, 0, 0, 6, 1, 3, 'minecraft:cobblestone'); b.box(0, F, 0, 6, F, 3, 'minecraft:polished_andesite');
        for (const x of [1, 4]) { b.box(x, F + 1, 1, x + 1, F + 3, 2, 'minecraft:white_concrete'); b.set(x, F + 4, 1, slab('minecraft:smooth_stone_slab')); }
        b.set(3, F + 1, 0, BARREL); b.set(6, F + 1, 3, lootBarrel('comm_station'));
        L.rigid('fuel_tanks', b, 40, 40);
    })();
    L.follow('fence_n', fenceLine(40, 'x', 687), 6, 0);
    L.follow('fence_e', fenceLine(40, 'z', 688), 51, 6);
    L.follow('path_door', pathStrip(5, 3, 'z', 689), 12, 47);
    L.follow('path_yard', pathStrip(20, 3, 'x', 690), 14, 24);
    [[11, 47], [15, 47], [20, 23], [27, 27]].forEach(([x, z], i) => L.follow('lamp_post_' + i, lampPost(691 + i), x, z));
    [[34, 28, 'west'], [44, 30, 'north']].forEach(([x, z, f], i) => L.follow('robot_post_' + i, guardPost('patrol_robot', f, 695 + i), x, z));
    return L;
}

// ------------------------------------------------------------------ defensive outpost

function defensiveOutpost() {
    const L = new Layout('defensive_outpost', 54, 46, 'minecraft:cobbled_deepslate');
    const WALL = 'minecraft:stone_bricks', TOW = 'minecraft:deepslate_bricks';
    const POST = {wall: 'minecraft:gray_concrete', pillar: 'minecraft:polished_deepslate', trim: 'minecraft:stone_bricks', trimSlab: 'minecraft:stone_brick_slab',
        floor: 'minecraft:spruce_planks', roof: 'minecraft:deepslate_tiles', glass: 'minecraft:glass_pane', bay: 4, lintel: 'minecraft:polished_deepslate'};
    L.rigid('tower_w', towerPiece(7, 16, TOW, 'minecraft:deepslate_tiles', 'east', {roof: true, light: true, turret: 'south'}, 701), 0, 36);
    L.rigid('tower_e', towerPiece(7, 16, TOW, 'minecraft:deepslate_tiles', 'west', {roof: true, light: true, turret: 'north'}, 702), 45, 0);
    L.follow('wall_n', wallLine(43, 'x', 5, WALL, 'minecraft:stone_brick_slab', 703), 2, 2);
    L.follow('wall_w', wallLine(33, 'z', 5, WALL, 'minecraft:stone_brick_slab', 704), 2, 3);
    L.follow('wall_e', wallLine(34, 'z', 5, WALL, 'minecraft:stone_brick_slab', 705), 50, 9);
    L.follow('wall_s1', wallLine(10, 'x', 5, WALL, 'minecraft:stone_brick_slab', 706), 9, 42);
    L.follow('wall_s2', wallLine(21, 'x', 5, WALL, 'minecraft:stone_brick_slab', 707), 31, 42);
    // the gate: two short towers, a lintel, a portcullis half up, lamps, a guard room
    (() => {
        const b = new Build(12, 12, 7, 708);
        for (const x0 of [0, 8]) {
            b.box(x0, 0, 0, x0 + 3, 1, 5, 'minecraft:cobbled_deepslate'); b.box(x0, F, 0, x0 + 3, F, 5, 'minecraft:deepslate_tiles');
            b.box(x0, F + 1, 0, x0 + 3, F + 7, 5, TOW); b.box(x0 + 1, F + 1, 1, x0 + 2, F + 6, 4, AIR);
            battlements(b, x0, 0, x0 + 3, 5, F + 8, TOW);
            b.set(x0 + 1, F + 5, 5, bars('x'));
        }
        b.box(4, F + 6, 0, 7, F + 8, 5, TOW);
        b.box(4, 0, 0, 7, 1, 5, 'minecraft:cobblestone');
        for (let x = 4; x <= 7; x++) for (let z = 0; z <= 5; z++) b.set(x, F, z, b.pick(['minecraft:gravel', 'minecraft:coarse_dirt']));
        for (let x = 4; x <= 7; x++) { b.set(x, F + 5, 3, bars('x')); b.set(x, F + 4, 3, bars('x')); }
        ironDoor(b, 3, F + 1, 2, 'east', true);
        b.set(1, F + 1, 3, chest('east', 'defensive_outpost'));
        b.set(2, F + 1, 1, slab('minecraft:spruce_slab', 'top')); b.set(2, F + 2, 1, monitor('south'));
        b.set(3, F + 4, 6, lamp('south')); b.set(8, F + 4, 6, lamp('south'));
        weather(b, {leafClumps: 1});
        L.rigid('gate', b, 19, 40);
    })();
    // the barracks
    (() => {
        const M = 2, W = 15, D = 9;
        const b = new Build(W + 2 * M, 12, D + 2 * M, 709);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [4]}, POST));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..10, z 0..4
        entrance(b, s, 'south', X(5), 0, 1, POST);
        for (const x of [X(0), X(2), X(7), X(9)]) bed(b, 'brown', x, g.y + 1, Z(0), 'south');
        for (const x of [X(1), X(8)]) b.set(x, g.y + 1, Z(0), plainBarrel('south'));
        b.set(X(4), g.y + 1, Z(0), lootBarrel('defensive_outpost', 'south'));
        b.set(X(10), g.y + 1, Z(4), heater());
        b.set(X(0), g.y + 1, Z(4), chest('east', 'defensive_outpost'));
        b.set(X(2), g.y + 1, Z(3), slab('minecraft:spruce_slab', 'top')); b.set(X(3), g.y + 1, Z(3), slab('minecraft:spruce_slab', 'top'));
        b.set(X(5), g.y + 3, Z(0), lamp('south'));
        weather(b);
        L.rigid('barracks', b, 7, 7);
    })();
    // the armory and command post
    (() => {
        const M = 2, W = 13, D = 11;
        const b = new Build(W + 2 * M, 12, D + 2 * M, 710);
        const s = shell(b, Object.assign({x0: M, z0: M, w: W, d: D, storeys: [4]}, POST, {floor: 'minecraft:polished_andesite'}));
        const g = s.levels[0];
        const X = x => M + 2 + x, Z = z => M + 2 + z; // inner x 0..8, z 0..6
        entrance(b, s, 'south', X(2), 0, 2, POST);
        partition(b, 'z', X(4), Z(0), Z(5), g.y, 4, POST.wall, [Z(3)]);
        for (let z = Z(0); z <= Z(5); z++) { b.set(X(0), g.y + 1, z, crate('east')); if (z % 2) b.set(X(0), g.y + 2, z, crate('east')); }
        b.set(X(3), g.y + 1, Z(0), station('weapon_workshop', 'south'));
        b.set(X(2), g.y + 1, Z(0), chest('south', 'defensive_outpost'));
        for (let z = Z(1); z <= Z(5); z++) { b.set(X(8), g.y + 3, z, monitor('west')); b.set(X(7), g.y + 1, z, slab('minecraft:polished_blackstone_slab', 'top')); }
        b.set(X(5), g.y + 1, Z(0), battery('south')); b.set(X(6), g.y + 1, Z(0), CABLE); b.set(X(7), g.y + 1, Z(0), station('shelter_control_panel', 'south'));
        b.set(X(5), g.y + 1, Z(6), lootBarrel('defensive_outpost'));
        b.set(X(4), g.y + 3, Z(1), lamp('east'));
        wireCables(b);
        weather(b);
        L.rigid('armory', b, 28, 9);
    })();
    L.follow('nest_1', sandbags('south', 'defensive_outpost', 711), 13, 33);
    L.follow('nest_2', sandbags('south', null, 712), 33, 33);
    L.follow('nest_3', sandbags('west', null, 713), 44, 22);
    L.rigid('floodlight_1', floodlight(714), 8, 26);
    L.rigid('floodlight_2', floodlight(715), 40, 30);
    L.rigid('wreck_gate', wreckSpot('mobile', 10, null, 716), 27, 30);
    L.follow('path_gate', pathStrip(20, 4, 'z', 717), 23, 21);
    L.follow('path_cross', pathStrip(22, 3, 'x', 718), 12, 22);
    L.follow('scrap', scrapHeap(719), 5, 16);
    [[22, 25], [27, 25], [22, 35], [27, 35], [14, 21]].forEach(([x, z], i) => L.follow('lamp_post_' + i, lampPost(720 + i), x, z));
    [[18, 28, 'south'], [38, 29, 'west']].forEach(([x, z, f], i) => L.follow('robot_post_' + i, guardPost('patrol_robot', f, 726 + i), x, z));
    return L;
}

// ------------------------------------------------------------------ contamination zone

function contaminationZone() {
    const L = new Layout('contamination_zone', 50, 50, 'minecraft:cobbled_deepslate');
    // the arch over its pool: a broken ring of dark stone with green lights in the inner face; the pool sunk into the ground
    (() => {
        const b = new Build(27, 20, 13, 731);
        const C = 13;
        for (let x = 0; x < 27; x++) for (let z = 0; z < 13; z++) {
            b.box(x, 0, z, x, 1, z, 'minecraft:cobbled_deepslate');
            b.set(x, F, z, b.pick(['minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:moss_block', 'minecraft:mud', 'minecraft:stone_bricks']));
        }
        for (let x = 0; x < 27; x++) for (let y = F + 1; y < 20; y++) {
            const r = y <= F + 4 ? Math.abs(x - C) : Math.hypot(x - C, y - (F + 4));
            if (r < 8.5 || r > 11.5) continue;
            const angle = Math.atan2(y - (F + 4), x - C);
            if (angle > 1.75 && angle < 2.25 && y > F + 10) continue;
            for (let z = 5; z <= 7; z++) {
                const block = r < 9.5 && z === 6 && y % 4 === 1 ? 'minecraft:verdant_froglight'
                    : b.chance(0.25) ? 'minecraft:cracked_deepslate_bricks' : b.pick(['minecraft:deepslate_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles']);
                b.set(x, y, z, block);
            }
        }
        for (let x = C - 5; x <= C + 5; x++) for (let z = 2; z <= 10; z++) {
            if (((x - C) / 5.5) ** 2 + ((z - 6) / 4.5) ** 2 > 1) continue;
            b.set(x, F - 1, z, 'minecraft:mud'); b.set(x, F, z, SLUDGE);
        }
        for (const [x, y, z] of [[6, F + 1, 9], [7, F + 1, 10], [19, F + 1, 3], [20, F + 2, 3], [20, F + 1, 4]]) b.set(x, y, z, 'minecraft:cracked_deepslate_bricks');
        for (let i = 0; i < 6; i++) { const x = Math.floor(b.rand() * 27), z = b.pick([0, 1, 11, 12]); b.set(x, F + 1, z, DEBRIS); }
        weather(b, {webs: 0, moss: 0, leafClumps: 3, vines: 0.12, rubble: 8});
        L.rigid('arch', b, 12, 17);
    })();
    // broken columns with green lights
    [[6, 6], [40, 6], [6, 40], [40, 40]].forEach(([x, z], i) => {
        const b = new Build(5, 14, 5, 732 + i);
        b.box(1, 0, 1, 3, 1, 3, 'minecraft:cobbled_deepslate'); b.box(0, F, 0, 4, F, 4, 'minecraft:mossy_stone_bricks');
        const h = 4 + Math.floor(b.rand() * 7);
        b.box(1, F + 1, 1, 3, F + h, 3, 'minecraft:deepslate_tiles');
        b.set(2, F + 3, 1, 'minecraft:verdant_froglight'); b.set(2, F + 3, 3, 'minecraft:verdant_froglight');
        b.box(1, F + h, 1, 3, F + h, 3, AIR); b.box(1, F + h, 1, 2, F + h, 2, 'minecraft:cracked_deepslate_bricks');
        b.set(4, F + 1, 2, slab('minecraft:deepslate_tile_slab')); b.set(0, F + 1, 3, 'minecraft:cracked_deepslate_bricks');
        weather(b, {webs: 0, moss: 0, leafClumps: 0, vines: 0.15, rubble: 3});
        L.rigid('column_' + i, b, x, z);
    });
    // the bunker below the ground: its roof flush with the ground, a stair cut down to it, a room flooded with sludge
    (() => {
        const b = new Build(13, 11, 12, 736);
        const R = 7; // the roof row lies on the ground
        b.box(0, 0, 0, 12, 1, 9, 'minecraft:stone_bricks');
        b.box(0, F, 0, 12, F, 9, 'minecraft:cracked_stone_bricks');
        b.box(1, F + 1, 1, 11, R - 1, 8, AIR);
        b.walls(0, F + 1, 0, 12, R - 1, 9, 'minecraft:cracked_stone_bricks');
        for (let x = 0; x <= 12; x++) for (let z = 0; z <= 9; z++) b.set(x, R, z, b.chance(0.35) ? b.pick(['minecraft:moss_block', 'minecraft:coarse_dirt']) : b.pick(['minecraft:mossy_stone_bricks', 'minecraft:stone_bricks']));
        b.box(3, R, 3, 5, R, 4, AIR); b.set(4, F + 1, 3, 'minecraft:moss_block'); b.set(4, F + 2, 3, 'minecraft:moss_carpet');
        for (let i = 0; i < 4; i++) {
            const z = 11 - i, y = R - 1 - i;
            b.set(9, y, z, stair('minecraft:stone_brick_stairs', 'south'));
            for (let k = 1; k <= 3; k++) b.set(9, y + k, z, AIR);
            for (let k = y; k <= R; k++) { b.set(8, k, z, 'minecraft:stone_bricks'); b.set(10, k, z, 'minecraft:stone_bricks'); }
            b.box(8, 0, z, 10, y - 1, z, 'minecraft:stone_bricks');
        }
        for (let x = 1; x <= 6; x++) for (let z = 1; z <= 5; z++) b.set(x, F, z, SLUDGE);
        for (let x = 8; x <= 11; x++) b.set(x, F + 1, 1, slab('minecraft:smooth_quartz_slab', 'top'));
        b.set(10, F + 2, 1, monitor('south')); b.set(11, F + 1, 3, tank()); b.set(11, F + 1, 5, chest('west', 'contamination_zone'));
        b.set(11, F + 1, 7, BARREL); b.set(2, F + 1, 7, BARREL);
        b.set(6, R - 1, 6, 'minecraft:verdant_froglight');
        b.set(1, R - 1, 1, 'minecraft:cobweb'); b.set(11, R - 1, 8, 'minecraft:cobweb');
        b.set(8, R + 1, 11, [P + 'sealed_hatch', {facing: 'south', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false'}]);
        L.rigid('bunker', b, 27, 33, R);
    })();
    const poolPiece = (rx, rz, seed) => {
        const b = new Build(2 * rx + 1, 1, 2 * rz + 1, seed);
        for (let x = 0; x <= 2 * rx; x++) for (let z = 0; z <= 2 * rz; z++) if (((x - rx) / (rx + 0.5)) ** 2 + ((z - rz) / (rz + 0.5)) ** 2 <= 1) b.set(x, 0, z, SLUDGE);
        return b;
    };
    L.follow('pool_1', poolPiece(3, 2, 737), 6, 32);
    L.follow('pool_2', poolPiece(2, 3, 738), 34, 10);
    L.follow('pool_3', poolPiece(2, 2, 739), 18, 6);
    for (const [i, x, z] of [[0, 18, 36], [1, 4, 14]]) {
        const b = new Build(9, 2, 9, 740 + i);
        for (let k = 0; k < 9; k++) { const dx = Math.floor(b.rand() * 9), dz = Math.floor(b.rand() * 9); b.set(dx, 0, dz, b.pick(['minecraft:coarse_dirt', 'minecraft:gravel'])); b.set(dx, 1, dz, DEBRIS); }
        for (let k = 0; k < 6; k++) { const dx = Math.floor(b.rand() * 9), dz = Math.floor(b.rand() * 9); if (!b.get(dx, 0, dz)) b.set(dx, 0, dz, b.pick(['minecraft:moss_block', 'minecraft:coarse_dirt', 'minecraft:podzol'])); }
        L.follow('debris_' + i, b, x, z);
    }
    [[3, 24, 6], [46, 16, 5], [14, 44, 4], [26, 3, 5]].forEach(([x, z, h], i) => L.follow('tree_' + i, deadTree(h, 742 + i), x, z));
    // the expedition's last camp: a fallen tent, a cold campfire, the wrecked armor and what it carried
    (() => {
        const b = new Build(9, 5, 7, 746);
        b.box(0, 0, 0, 8, 1, 6, 'minecraft:dirt');
        for (let x = 0; x < 9; x++) for (let z = 0; z < 7; z++) b.set(x, F, z, b.pick(['minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:grass_block']));
        b.set(1, F + 1, 1, 'minecraft:spruce_fence'); b.set(1, F + 2, 1, 'minecraft:spruce_fence'); b.set(4, F + 1, 1, 'minecraft:spruce_fence');
        for (let x = 1; x <= 4; x++) b.set(x, F + 1, 2, 'minecraft:green_carpet');
        b.set(2, F + 1, 1, 'minecraft:green_carpet'); b.set(1, F + 3, 1, 'minecraft:green_carpet');
        b.set(3, F + 1, 4, ['minecraft:campfire', {facing: 'north', lit: 'false', signal_fire: 'false', waterlogged: 'false'}]);
        wreck(b, 6, F + 1, 3, 'combat', 160);
        b.set(7, F + 1, 5, chest('west', 'contamination_zone'));
        b.set(2, F + 1, 5, chest('north', 'contamination_zone'));
        b.set(5, F + 1, 0, crate('south'));
        L.rigid('camp', b, 40, 24);
    })();
    // the zone's dwellers: ghouls by the pools, a pack of hounds, a brute at the old debris
    [[10, 30], [32, 14]].forEach(([x, z], i) => L.follow('ghoul_' + i, guardPost('glowing_ghoul', 'south', 747 + i), x, z));
    L.follow('hound_pack', guardPost('mutated_hound', 'north', 749, 3), 30, 24);
    L.follow('brute_post', guardPost('brute', 'east', 750), 8, 18);
    return L;
}

// ------------------------------------------------------------------ writing

fs.mkdirSync(OUT, {recursive: true});
const layouts = {};
for (const make of [militaryBase, scienceComplex, abandonedFactory, commStation, defensiveOutpost, contaminationZone]) {
    const L = make();
    const dir = path.join(OUT, L.id);
    fs.mkdirSync(dir, {recursive: true});
    for (const f of fs.readdirSync(dir)) if (f.endsWith('.nbt')) fs.unlinkSync(path.join(dir, f));
    const counts = {};
    let wrecks = 0;
    for (const part of L.parts) {
        const lamps = fixLamps(part.b);
        if (lamps.moved || lamps.removed) console.log('  ' + L.id + '/' + part.piece + ': lamps moved ' + lamps.moved + ', removed ' + lamps.removed);
        fs.writeFileSync(path.join(dir, part.piece + '.nbt'), nbtFile(part.b.toNbt()));
        wrecks += part.b.entities.length;
        for (const block of part.b.cells.values()) {
            const n = name(block);
            if (n.startsWith(P) || n === 'minecraft:chest') counts[n.replace(P, '')] = (counts[n.replace(P, '')] || 0) + 1;
        }
        const s = part.b.size;
        if (part.x < 0 || part.z < 0 || part.x + s[0] > L.sizeX + 1 || part.z + s[2] > L.sizeZ + 1) console.warn('  ' + L.id + '/' + part.piece + ' reaches out of the layout');
    }
    layouts[L.id] = {size_x: L.sizeX, size_z: L.sizeZ, foundation: L.foundation,
        parts: L.parts.map(p => Object.assign({template: P + 'ruins/' + L.id + '/' + p.piece, x: p.x, z: p.z}, p.follow ? {follow: true} : {floor: p.floor}))};
    console.log(L.id.padEnd(19), String(L.parts.length).padStart(2) + ' pieces', wrecks + ' wrecks', JSON.stringify(counts));
}
fs.writeFileSync(path.join(__dirname, 'layouts.json'), JSON.stringify(layouts, null, 2) + '\n');
for (const id of Object.keys(layouts)) { const old = path.join(OUT, id + '.nbt'); if (fs.existsSync(old)) fs.unlinkSync(old); }
