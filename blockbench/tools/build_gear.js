// Builds the 3D models and textures of the protective gear (respirator, hazmat suit, heavy protective suit).
// Usage: node blockbench/tools/build_gear.js
//
// Every model is the vanilla armor model (HumanoidModel.createMesh, texture layout of a 64x32 armor layer in the top-left
// corner of a 128x64 texture) plus extra cubes on the head, body, arms and legs: masks, filters, straps, belts, gloves,
// boots. This script writes, for each model, a JSON description that the game reads (assets/projectrebirth/gear/*.json,
// client/render/GearModels.java) and the texture painted to fit it (textures/models/armor/*_layer_N.png), so the two
// always agree. Model coordinates are those of the parts of HumanoidModel: y goes down, the front is -z, +x is the
// wearer's left. A part on the left arm or leg shares the texture of its twin on the right.
const fs = require('fs');
const zlib = require('zlib');
const ROOT = 'E:/Projects/Project-Rebirth-mod/';
const ASSETS = ROOT + 'src/main/resources/assets/projectrebirth/';
const W = 128, H = 64;

// ---------------------------------------------------------------- PNG and painting

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); }
function png(px) {
    const raw = Buffer.alloc((W * 4 + 1) * H);
    for (let y = 0; y < H; y++) { raw[y * (W * 4 + 1)] = 0; px.copy(raw, y * (W * 4 + 1) + 1, y * W * 4, (y + 1) * W * 4); }
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function random(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const hex = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
const shade = (c, f) => '#' + hex(c).map(v => Math.max(0, Math.min(255, Math.round(v * f))).toString(16).padStart(2, '0')).join('');

class Canvas {
    constructor(seed) { this.px = Buffer.alloc(W * H * 4); this.rand = random(seed); }
    set(x, y, c, a) {
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        const [r, g, b] = hex(c), i = (y * W + x) * 4;
        this.px[i] = r; this.px[i + 1] = g; this.px[i + 2] = b; this.px[i + 3] = a === undefined ? 255 : a;
    }
    clear(x, y, w, h) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) if (i >= 0 && j >= 0 && i < W && j < H) this.px.fill(0, (j * W + i) * 4, (j * W + i) * 4 + 4); }
    rect(x, y, w, h, c) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, c); }
    /** A colour with a little per-pixel variation, like fabric or rubber. */
    noise(x, y, w, h, c, amount) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, shade(c, 1 + (this.rand() - 0.5) * 2 * amount)); }
    /** Light on the top edge, dark on the bottom edge: a raised look. */
    bevel(x, y, w, h, c, amount) {
        this.noise(x, y, w, h, c, amount === undefined ? 0.04 : amount);
        this.rect(x, y, w, 1, shade(c, 1.22));
        this.rect(x, y + h - 1, w, 1, shade(c, 0.7));
    }
}

// ---------------------------------------------------------------- the box layout of a cube

/** The faces of a cube on the texture: u, v and its size (w, h, d), as Minecraft lays them out. */
function faces(uv, size) {
    const [u, v] = uv, [w, h, d] = size;
    return {
        top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
        right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h]
    };
}
const SIDES = ['right', 'front', 'left', 'back'];
/** The vanilla armor parts (64x32 layout), by bone. */
const BASE = {
    head: {uv: [0, 0], size: [8, 8, 8]}, hat: {uv: [32, 0], size: [8, 8, 8]}, body: {uv: [16, 16], size: [8, 12, 4]},
    arm: {uv: [40, 16], size: [4, 12, 4]}, leg: {uv: [0, 16], size: [4, 12, 4]}
};
const base = name => faces(BASE[name].uv, BASE[name].size);

// ---------------------------------------------------------------- packing the extra cubes

/** Places the extra cubes on the free part of the texture, row by row: right of the vanilla layout, then below it. */
function pack(parts) {
    const used = [];
    const free = (x, y, w, h) => x + w <= W && y + h <= H && !(x < 64 && y < 32)
        && used.every(([a, b, c, d]) => x >= a + c || a >= x + w || y >= b + d || b >= y + h);
    const placed = {};
    for (const part of parts) {
        if (part.twin) {
            continue;
        }
        const [w, h, d] = part.size, pw = 2 * (w + d), ph = d + h;
        let spot = null;
        for (let y = 0; y + ph <= H && !spot; y++) for (let x = 0; x + pw <= W && !spot; x++) if (free(x, y, pw, ph)) spot = [x, y];
        if (!spot) throw new Error('No room for ' + part.name);
        used.push([spot[0], spot[1], pw, ph]);
        placed[part.name] = spot;
    }
    for (const part of parts) part.uv = placed[part.twin || part.name];
    return parts;
}

// ---------------------------------------------------------------- the models

/** Mirrors a part of the right arm or leg to the left one (x flipped, the same texture). */
function mirror(part, bone) {
    const [x, y, z] = part.origin, [w] = part.size;
    const out = {...part, name: part.name.replace('right', 'left'), bone, twin: part.name, origin: [-x - w, y, z]};
    if (part.pivot) out.pivot = [-part.pivot[0], part.pivot[1], part.pivot[2]];
    if (part.rotation) out.rotation = [part.rotation[0], -part.rotation[1], -part.rotation[2]];
    return out;
}
/** A part of the right arm placed the same on the left arm: each arm box is centred on its own pivot side, so the mirror
 * of x..x+w is -x-w..-x, as for the legs. */
function arms(part) { return [{...part, bone: 'right_arm'}, mirror(part, 'left_arm')]; }
function legs(part) { return [{...part, bone: 'right_leg'}, mirror(part, 'left_leg')]; }

const RESPIRATOR = {
    id: 'respirator', inflate: 0.3,
    parts: [
        {bone: 'head', name: 'mask', origin: [-2.5, -3.2, -5.4], size: [5, 3, 1], grow: 0.25},
        {bone: 'head', name: 'nose', origin: [-1, -4, -5.1], size: [2, 1, 1], grow: 0.1},
        {bone: 'head', name: 'valve', origin: [-1, -2.6, -6.5], size: [2, 2, 1], grow: 0.05},
        {bone: 'head', name: 'filter_right', origin: [-1.5, -1.5, -1], size: [3, 3, 2], pivot: [-3.0, -2.0, -4.9], rotation: [0, 30, 0]},
        {bone: 'head', name: 'filter_left', twin: 'filter_right', origin: [-1.5, -1.5, -1], size: [3, 3, 2], pivot: [3.0, -2.0, -4.9], rotation: [0, -30, 0]}
    ]
};

const HAZMAT_OUTER = {
    id: 'hazmat_layer_1', inflate: 1.0,
    parts: [
        // the hood: a rim around the visor and a mask with one filter
        {bone: 'head', name: 'mask', origin: [-2.5, -3, -6], size: [5, 3, 1], grow: 0.2},
        {bone: 'head', name: 'filter', origin: [-1.5, -1.5, -2], size: [3, 3, 2], pivot: [0, -1.2, -6.2], rotation: [-15, 0, 0]},
        {bone: 'head', name: 'ridge', origin: [-1, -9.7, -5], size: [2, 1, 10], grow: 0.0},
        // the suit: harness, belt, air tank
        {bone: 'body', name: 'strap_chest_right', origin: [-3, 0, -3.4], size: [1, 9, 1], grow: -0.15},
        {bone: 'body', name: 'strap_chest_left', twin: 'strap_chest_right', origin: [2, 0, -3.4], size: [1, 9, 1], grow: -0.15},
        {bone: 'body', name: 'belt', origin: [-5, 8.5, -3], size: [10, 2, 6], grow: 0.3},
        {bone: 'body', name: 'tank', origin: [-2, 1, 3.2], size: [4, 7, 2], grow: 0.2},
        {bone: 'body', name: 'collar', origin: [-5, -1.4, -3], size: [10, 1, 6], grow: 0.3},
        ...arms({name: 'glove_right', origin: [-4, 6.5, -3], size: [6, 5, 6], grow: 0.3}),
        ...legs({name: 'boot_right', origin: [-3, 8.2, -3], size: [6, 4, 6], grow: 0.3}),
        ...legs({name: 'sole_right', origin: [-3, 12.4, -3.6], size: [6, 1, 7], grow: 0.3})
    ]
};
const HAZMAT_INNER = {
    id: 'hazmat_layer_2', inflate: 0.5,
    parts: [
        ...legs({name: 'knee_right', origin: [-2, 3.5, -2.9], size: [4, 3, 1], grow: 0.1}),
        {bone: 'body', name: 'waist', origin: [-5, 9.6, -3], size: [10, 2, 6], grow: 0.0}
    ]
};

const HEAVY_OUTER = {
    id: 'heavy_suit_layer_1', inflate: 1.0,
    parts: [
        // a rubber gas mask over the whole face with round eyepieces and a canister below
        {bone: 'head', name: 'mask', origin: [-3.5, -7, -5.6], size: [7, 7, 1], grow: 0.1},
        {bone: 'head', name: 'eye_right', origin: [-3, -5.6, -6.4], size: [2, 2, 1], grow: 0.15},
        {bone: 'head', name: 'eye_left', twin: 'eye_right', origin: [1, -5.6, -6.4], size: [2, 2, 1], grow: 0.15},
        {bone: 'head', name: 'snout', origin: [-1.5, -3, -6.6], size: [3, 3, 1], grow: 0.1},
        {bone: 'head', name: 'canister', origin: [-1.5, -1.5, -3], size: [3, 3, 3], pivot: [0, -1.0, -6.6], rotation: [-30, 0, 0]},
        {bone: 'head', name: 'ridge', origin: [-1, -9.7, -5], size: [2, 1, 10], grow: 0.0},
        // the jacket: webbing, belt with a pouch, a bag on the back, shoulder pads
        {bone: 'body', name: 'webbing_right', origin: [-3.2, 0, -3.5], size: [1, 9, 1], grow: -0.1},
        {bone: 'body', name: 'webbing_left', twin: 'webbing_right', origin: [2.2, 0, -3.5], size: [1, 9, 1], grow: -0.1},
        {bone: 'body', name: 'belt', origin: [-5, 8.4, -3], size: [10, 2, 6], grow: 0.35},
        {bone: 'body', name: 'pouch', origin: [0.6, 8.8, -4.6], size: [3, 3, 1], grow: 0.1},
        {bone: 'body', name: 'bag', origin: [-3, 1.5, 3.1], size: [6, 6, 2], grow: 0.1},
        {bone: 'body', name: 'collar', origin: [-5, -1.4, -3], size: [10, 1, 6], grow: 0.35},
        ...arms({name: 'pad_right', origin: [-4, -3.4, -3], size: [6, 3, 6], grow: 0.45}),
        ...arms({name: 'glove_right', origin: [-4, 6.5, -3], size: [6, 5, 6], grow: 0.35}),
        ...legs({name: 'boot_right', origin: [-3, 8, -3], size: [6, 4, 6], grow: 0.35}),
        ...legs({name: 'sole_right', origin: [-3, 12.4, -3.6], size: [6, 1, 7], grow: 0.35})
    ]
};
const HEAVY_INNER = {
    id: 'heavy_suit_layer_2', inflate: 0.5,
    parts: [
        ...legs({name: 'knee_right', origin: [-2, 3.5, -3], size: [4, 3, 1], grow: 0.15}),
        ...legs({name: 'pocket_right', origin: [-2.9, 1, -1.5], size: [1, 3, 3], grow: 0.1}),
        {bone: 'body', name: 'waist', origin: [-5, 9.6, -3], size: [10, 2, 6], grow: 0.0}
    ]
};

// ---------------------------------------------------------------- painting helpers

function paintBox(c, f, fill) { for (const [name, r] of Object.entries(f)) fill(name, ...r); }
const byName = (model, name) => model.parts.find(p => p.name === name);
const box = (model, name) => faces(byName(model, name).uv, byName(model, name).size);
/** Paints every face of a cube with one fabric colour, its top lighter and bottom darker. */
function plain(c, f, col, amount) {
    paintBox(c, f, (name, x, y, w, h) => {
        if (name === 'top') c.noise(x, y, w, h, shade(col, 1.15), amount || 0.05);
        else if (name === 'bottom') c.noise(x, y, w, h, shade(col, 0.7), amount || 0.05);
        else c.bevel(x, y, w, h, col, amount || 0.05);
    });
}

// ---------------------------------------------------------------- the respirator

function paintRespirator(m) {
    const c = new Canvas(501);
    const RUBBER = '#3b3f37', HOUSING = '#545b4c', STRAP = '#7b5a35', METAL = '#8d9296', RUST = '#6b3b22';
    // the head itself stays bare: only the straps around it
    const head = base('head');
    for (const side of ['right', 'left']) {
        const [x, y, w, h] = head[side];
        // a strap from the cheek up and back over the ear
        for (let i = 0; i < w; i++) c.rect(x + (side === 'right' ? i : w - 1 - i), y + 5 - Math.floor(i * 0.6), 1, 2, i % 3 === 0 ? shade(STRAP, 1.15) : STRAP);
        c.set(x + (side === 'right' ? 1 : w - 2), y + 5, METAL);
    }
    const [bx, by, bw] = head.back;
    c.noise(bx, by + 2, bw, 2, STRAP, 0.06);
    c.rect(bx, by + 2, bw, 1, shade(STRAP, 1.2));
    c.rect(bx + 3, by + 2, 2, 2, METAL);
    // the half mask: dark rubber with a moulded ridge
    plain(c, box(m, 'mask'), RUBBER, 0.05);
    const mf = box(m, 'mask').front;
    c.rect(mf[0], mf[1], mf[2], 1, shade(RUBBER, 1.3));
    c.rect(mf[0] + 2, mf[1] + 1, 2, mf[3] - 1, shade(RUBBER, 0.8));
    plain(c, box(m, 'nose'), shade(RUBBER, 1.1), 0.04);
    // the valve: a round cover with a rusty ring
    plain(c, box(m, 'valve'), HOUSING, 0.04);
    const vf = box(m, 'valve').front;
    c.rect(vf[0], vf[1], vf[2], vf[3], '#3f433b');
    c.set(vf[0], vf[1], shade(HOUSING, 1.3));
    for (const name of ['right', 'left', 'top', 'bottom']) { const r = box(m, 'valve')[name]; c.rect(r[0], r[1], r[2], r[3], RUST); }
    // the filters: a grey-green housing, the front a fine white grid
    plain(c, box(m, 'filter_right'), HOUSING, 0.05);
    const ff = box(m, 'filter_right').front;
    for (let y = 0; y < ff[3]; y++) for (let x = 0; x < ff[2]; x++) c.set(ff[0] + x, ff[1] + y, (x + y) % 2 ? '#8f8a74' : '#6c6858');
    c.set(ff[0] + 1, ff[1] + 1, '#a8a289');
    return c;
}

// ---------------------------------------------------------------- the hazmat suit

const YELLOW = '#e5b52d', BLACK = '#202226', WHITE = '#d9dcd8', GREY = '#5d6166';
function paintHazmatOuter(m) {
    const c = new Canvas(511);
    const head = base('head');
    // the hood: yellow with seams, a window in front (the face shows through) and the mask below it
    for (const [name, r] of Object.entries(head)) {
        const [x, y, w, h] = r;
        c.noise(x, y, w, h, name === 'bottom' ? shade(YELLOW, 0.7) : YELLOW, 0.05);
        if (SIDES.includes(name)) { c.rect(x, y + h - 1, w, 1, shade(YELLOW, 0.75)); c.rect(x + Math.floor(w / 2), y, 1, h - 1, shade(YELLOW, 0.88)); }
    }
    const [fx, fy] = head.front;
    c.clear(fx + 1, fy + 1, 6, 4);
    c.rect(fx + 1, fy, 6, 1, GREY);
    c.rect(fx + 1, fy + 5, 6, 1, GREY);
    c.rect(fx, fy + 1, 1, 4, GREY); c.rect(fx + 7, fy + 1, 1, 4, GREY);
    c.set(fx + 1, fy + 1, '#cfeff5'); c.set(fx + 2, fy + 1, '#a8dbe6'); c.set(fx + 6, fy + 2, '#cfeff5');
    // the hat layer stays empty
    plain(c, box(m, 'mask'), BLACK, 0.05);
    const mk = box(m, 'mask').front;
    c.rect(mk[0], mk[1], mk[2], 1, shade(BLACK, 1.6));
    plain(c, box(m, 'filter'), '#9a9ea3', 0.04);
    const fl = box(m, 'filter').front;
    c.rect(fl[0], fl[1], fl[2], fl[3], '#3a3d41');
    c.set(fl[0] + 1, fl[1] + 1, '#e5b52d');
    c.set(fl[0], fl[1], '#c9ced3'); c.set(fl[0] + 2, fl[1] + 2, '#2a2c2f');
    plain(c, box(m, 'ridge'), shade(YELLOW, 0.85), 0.05);
    // the suit: yellow with a zip; black harness and belt, a white tank with a red band on the back
    const body = base('body');
    for (const [name, r] of Object.entries(body)) c.noise(...r, name === 'bottom' ? shade(YELLOW, 0.7) : YELLOW, 0.05);
    const [ux, uy, uw, uh] = body.front;
    c.rect(ux + 4, uy, 1, uh, shade(YELLOW, 0.8));
    for (let y = 1; y < uh; y += 2) c.set(ux + 4, uy + y, '#8a8f94');
    c.rect(ux, uy + uh - 1, uw, 1, shade(YELLOW, 0.75));
    const [kx, ky, kw, kh] = body.back;
    c.rect(kx + 1, ky + 1, kw - 2, 1, shade(YELLOW, 0.85));
    for (const name of ['strap_chest_right', 'collar']) plain(c, box(m, name), name === 'collar' ? shade(YELLOW, 0.9) : BLACK, 0.05);
    plain(c, box(m, 'belt'), BLACK, 0.05);
    const bt = box(m, 'belt').front;
    c.rect(bt[0] + 4, bt[1], 2, bt[3], '#8a8f94');
    plain(c, box(m, 'tank'), WHITE, 0.03);
    for (const name of ['front', 'right', 'left', 'back']) { const r = box(m, 'tank')[name]; c.rect(r[0], r[1] + 3, r[2], 1, '#b3312a'); }
    // arms: yellow sleeves with seams, black gloves with a white cuff
    const arm = base('arm');
    for (const [name, r] of Object.entries(arm)) {
        const [x, y, w, h] = r;
        c.noise(x, y, w, h, name === 'bottom' ? BLACK : YELLOW, 0.05);
        if (SIDES.includes(name)) c.rect(x, y + 4, w, 1, shade(YELLOW, 0.85));
    }
    plain(c, box(m, 'glove_right'), BLACK, 0.06);
    for (const name of SIDES) { const r = box(m, 'glove_right')[name]; c.rect(r[0], r[1], r[2], 1, WHITE); c.rect(r[0], r[1] + 1, r[2], 1, shade(WHITE, 0.8)); }
    // boots (the legs of this layer show only with the boots on)
    const leg = base('leg');
    for (const [name, r] of Object.entries(leg)) { const [x, y, w, h] = r; c.clear(x, y, w, h); if (SIDES.includes(name)) c.noise(x, y + h - 5, w, 5, BLACK, 0.05); else if (name === 'bottom') c.rect(x, y, w, h, BLACK); }
    plain(c, box(m, 'boot_right'), BLACK, 0.05);
    for (const name of SIDES) { const r = box(m, 'boot_right')[name]; c.rect(r[0], r[1], r[2], 1, shade(BLACK, 1.8)); }
    plain(c, box(m, 'sole_right'), '#3b3d40', 0.04);
    return c;
}
function paintHazmatInner(m) {
    const c = new Canvas(512);
    const body = base('body');
    // the trousers: only the waist of the body part
    const [fx, fy, fw, fh] = body.front;
    c.noise(fx, fy + 8, fw, 4, YELLOW, 0.05);
    const [bx, by, bw, bh] = body.back;
    c.noise(bx, by + 8, bw, 4, YELLOW, 0.05);
    c.noise(body.right[0], body.right[1] + 8, 4, 4, YELLOW, 0.05);
    c.noise(body.left[0], body.left[1] + 8, 4, 4, YELLOW, 0.05);
    const leg = base('leg');
    for (const [name, r] of Object.entries(leg)) {
        const [x, y, w, h] = r;
        c.noise(x, y, w, h, YELLOW, 0.05);
        if (SIDES.includes(name)) { c.rect(x, y + 6, w, 1, shade(YELLOW, 0.85)); c.rect(x, y + h - 1, w, 1, shade(YELLOW, 0.75)); }
    }
    plain(c, box(m, 'knee_right'), '#2b2d31', 0.05);
    const kn = box(m, 'knee_right').front;
    c.rect(kn[0], kn[1], kn[2], 1, '#55585d');
    plain(c, box(m, 'waist'), shade(YELLOW, 0.92), 0.05);
    return c;
}

// ---------------------------------------------------------------- the heavy protective suit

const OLIVE = '#5f6b3e', RUBBER = '#2c2f31', WEB = '#23251f', METAL = '#8a8f94';
function paintHeavyOuter(m) {
    const c = new Canvas(521);
    const head = base('head');
    // the hood: olive canvas with seams; the face is all mask
    for (const [name, r] of Object.entries(head)) {
        const [x, y, w, h] = r;
        c.noise(x, y, w, h, name === 'bottom' ? shade(OLIVE, 0.7) : OLIVE, 0.06);
        if (SIDES.includes(name)) { c.rect(x, y + h - 1, w, 1, shade(OLIVE, 0.75)); c.rect(x + 2, y + 1, 1, h - 2, shade(OLIVE, 0.85)); }
    }
    plain(c, box(m, 'mask'), RUBBER, 0.05);
    const mk = box(m, 'mask').front;
    c.rect(mk[0], mk[1], mk[2], 1, shade(RUBBER, 1.4));
    c.rect(mk[0], mk[1] + mk[3] - 1, mk[2], 1, shade(RUBBER, 0.7));
    // the eyepieces: a metal ring round dark glass with a glint
    plain(c, box(m, 'eye_right'), METAL, 0.04);
    const ey = box(m, 'eye_right').front;
    c.rect(ey[0], ey[1], ey[2], ey[3], '#33494b');
    c.set(ey[0], ey[1], '#a9d3d6');
    plain(c, box(m, 'snout'), shade(RUBBER, 1.15), 0.05);
    // the canister: ribbed olive drum with a dark screw end
    plain(c, box(m, 'canister'), '#4f5745', 0.05);
    for (const name of SIDES) { const r = box(m, 'canister')[name]; c.rect(r[0], r[1] + 1, r[2], 1, shade('#4f5745', 0.7)); }
    const cf = box(m, 'canister').bottom;
    c.rect(cf[0], cf[1], cf[2], cf[3], '#2a2d27');
    c.set(cf[0] + 1, cf[1] + 1, '#6f765f');
    plain(c, box(m, 'ridge'), shade(OLIVE, 0.8), 0.06);
    // the jacket: olive with a zip and a diagonal bag strap; black webbing and belt; a bag on the back
    const body = base('body');
    for (const [name, r] of Object.entries(body)) c.noise(...r, name === 'bottom' ? shade(OLIVE, 0.7) : OLIVE, 0.06);
    const [ux, uy, uw, uh] = body.front;
    c.rect(ux + 4, uy, 1, uh, shade(OLIVE, 0.75));
    for (let i = 0; i < uw; i++) c.set(ux + i, uy + 1 + i, '#6e5a3a');
    c.rect(ux, uy + uh - 1, uw, 1, shade(OLIVE, 0.7));
    for (const name of ['webbing_right']) plain(c, box(m, name), WEB, 0.05);
    plain(c, box(m, 'belt'), WEB, 0.05);
    const bt = box(m, 'belt').front;
    c.rect(bt[0] + 4, bt[1], 2, bt[3], METAL);
    plain(c, box(m, 'pouch'), shade(OLIVE, 0.85), 0.06);
    const pf = box(m, 'pouch').front;
    c.rect(pf[0], pf[1], pf[2], 1, shade(OLIVE, 0.65));
    plain(c, box(m, 'bag'), shade(OLIVE, 0.9), 0.06);
    const bg = box(m, 'bag').back;
    c.rect(bg[0], bg[1] + 1, bg[2], 1, shade(OLIVE, 0.6));
    c.rect(bg[0] + 2, bg[1] + 2, 2, 1, METAL);
    plain(c, box(m, 'collar'), shade(OLIVE, 0.85), 0.05);
    // arms: olive sleeves, rubber shoulder pads, black gloves
    const arm = base('arm');
    for (const [name, r] of Object.entries(arm)) { const [x, y, w, h] = r; c.noise(x, y, w, h, name === 'bottom' ? RUBBER : OLIVE, 0.06); if (SIDES.includes(name)) c.rect(x, y + 6, w, 1, shade(OLIVE, 0.8)); }
    plain(c, box(m, 'pad_right'), RUBBER, 0.05);
    const pd = box(m, 'pad_right').top;
    c.rect(pd[0], pd[1] + 1, pd[2], 1, shade(RUBBER, 1.4));
    plain(c, box(m, 'glove_right'), RUBBER, 0.06);
    for (const name of SIDES) { const r = box(m, 'glove_right')[name]; c.rect(r[0], r[1], r[2], 1, OLIVE); }
    // boots
    const leg = base('leg');
    for (const [name, r] of Object.entries(leg)) { const [x, y, w, h] = r; c.clear(x, y, w, h); if (SIDES.includes(name)) c.noise(x, y + h - 5, w, 5, '#26282a', 0.05); else if (name === 'bottom') c.rect(x, y, w, h, '#26282a'); }
    plain(c, box(m, 'boot_right'), '#26282a', 0.05);
    const bf = box(m, 'boot_right').front;
    for (let y = 0; y < 3; y++) c.rect(bf[0] + 1, bf[1] + y, bf[2] - 2, 1, y % 2 ? '#26282a' : '#6a6d70');
    plain(c, box(m, 'sole_right'), '#3a3326', 0.05);
    return c;
}
function paintHeavyInner(m) {
    const c = new Canvas(522);
    const body = base('body');
    const [fx, fy, fw] = body.front;
    c.noise(fx, fy + 8, fw, 4, OLIVE, 0.06);
    c.noise(body.back[0], body.back[1] + 8, 8, 4, OLIVE, 0.06);
    c.noise(body.right[0], body.right[1] + 8, 4, 4, OLIVE, 0.06);
    c.noise(body.left[0], body.left[1] + 8, 4, 4, OLIVE, 0.06);
    const leg = base('leg');
    for (const [name, r] of Object.entries(leg)) {
        const [x, y, w, h] = r;
        c.noise(x, y, w, h, OLIVE, 0.06);
        if (SIDES.includes(name)) { c.rect(x, y + h - 1, w, 1, shade(OLIVE, 0.7)); c.rect(x + 1, y + 1, 1, h - 2, shade(OLIVE, 0.88)); }
    }
    plain(c, box(m, 'knee_right'), RUBBER, 0.05);
    c.rect(box(m, 'knee_right').front[0], box(m, 'knee_right').front[1], 4, 1, shade(RUBBER, 1.4));
    plain(c, box(m, 'pocket_right'), shade(OLIVE, 0.85), 0.06);
    const pk = box(m, 'pocket_right').right;
    c.rect(pk[0], pk[1], pk[2], 1, shade(OLIVE, 0.65));
    plain(c, box(m, 'waist'), shade(OLIVE, 0.9), 0.06);
    return c;
}

// ---------------------------------------------------------------- output

function save(model, paint) {
    pack(model.parts);
    const json = {
        texture: [W, H], inflate: model.inflate,
        parts: model.parts.map(p => ({bone: p.bone, name: p.name, uv: p.uv, origin: p.origin, size: p.size, grow: p.grow || 0,
            pivot: p.pivot || [0, 0, 0], rotation: p.rotation || [0, 0, 0]}))
    };
    fs.mkdirSync(ASSETS + 'gear', {recursive: true});
    fs.writeFileSync(ASSETS + 'gear/' + model.id + '.json', JSON.stringify(json, null, 2) + '\n');
    fs.writeFileSync(ASSETS + 'textures/models/armor/' + (model.id === 'respirator' ? 'respirator_layer_1' : model.id) + '.png', png(paint(model).px));
    console.log(model.id + ': ' + model.parts.length + ' parts');
}
save(RESPIRATOR, paintRespirator);
save(HAZMAT_OUTER, paintHazmatOuter);
save(HAZMAT_INNER, paintHazmatInner);
save(HEAVY_OUTER, paintHeavyOuter);
save(HEAVY_INNER, paintHeavyInner);
