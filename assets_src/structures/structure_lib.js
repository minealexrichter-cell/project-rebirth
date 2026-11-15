// The shared part of the structure builders: a small NBT writer and a block-by-block building that becomes a Minecraft
// structure file (gzipped NBT). Used by build_structures.js (the ruins of block B2) and build_large_structures.js.
const zlib = require('zlib');

const DATA_VERSION = 3955; // Minecraft 1.21.1

// ------------------------------------------------------------------ NBT writer

const T = {END: 0, BYTE: 1, SHORT: 2, INT: 3, LONG: 4, FLOAT: 5, DOUBLE: 6, STRING: 8, LIST: 9, COMPOUND: 10};

class Writer {
    constructor() { this.parts = []; }
    u8(v) { const b = Buffer.alloc(1); b.writeUInt8(v); this.parts.push(b); }
    i16(v) { const b = Buffer.alloc(2); b.writeInt16BE(v); this.parts.push(b); }
    i32(v) { const b = Buffer.alloc(4); b.writeInt32BE(v); this.parts.push(b); }
    f32(v) { const b = Buffer.alloc(4); b.writeFloatBE(v); this.parts.push(b); }
    f64(v) { const b = Buffer.alloc(8); b.writeDoubleBE(v); this.parts.push(b); }
    str(s) { const b = Buffer.from(s, 'utf8'); this.i16(b.length); this.parts.push(b); }
    buffer() { return Buffer.concat(this.parts); }
}

// a value is {t: type, v: value}; lists {t: LIST, of: type, v: [values]}; compounds {t: COMPOUND, v: {name: value}}
const int = v => ({t: T.INT, v});
const str = v => ({t: T.STRING, v});
const byte = v => ({t: T.BYTE, v});
const float = v => ({t: T.FLOAT, v});
const double = v => ({t: T.DOUBLE, v});
const list = (of, v) => ({t: T.LIST, of, v});
const compound = v => ({t: T.COMPOUND, v});

function payload(w, value) {
    switch (value.t) {
        case T.BYTE: w.u8(value.v & 0xFF); break;
        case T.INT: w.i32(value.v); break;
        case T.FLOAT: w.f32(value.v); break;
        case T.DOUBLE: w.f64(value.v); break;
        case T.STRING: w.str(value.v); break;
        case T.LIST:
            w.u8(value.v.length ? value.of : T.END);
            w.i32(value.v.length);
            for (const item of value.v) payload(w, item);
            break;
        case T.COMPOUND:
            for (const [name, item] of Object.entries(value.v)) {
                w.u8(item.t);
                w.str(name);
                payload(w, item);
            }
            w.u8(T.END);
            break;
        default: throw new Error('type ' + value.t);
    }
}

function nbtFile(root) {
    const w = new Writer();
    w.u8(T.COMPOUND);
    w.str('');
    payload(w, root);
    return zlib.gzipSync(w.buffer());
}

// ------------------------------------------------------------------ building

/** A deterministic random generator, so that the files do not change from one run to the next. */
function random(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s + 0x6D2B79F5) >>> 0;
        let t = s;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

class Build {
    constructor(sx, sy, sz, seed) {
        this.size = [sx, sy, sz];
        this.cells = new Map();
        this.entities = [];
        this.rand = random(seed);
    }
    key(x, y, z) { return x + ',' + y + ',' + z; }
    inside(x, y, z) { return x >= 0 && y >= 0 && z >= 0 && x < this.size[0] && y < this.size[1] && z < this.size[2]; }
    /** block: 'minecraft:stone' or ['minecraft:chest', {facing: 'north'}, {LootTable: '...'}] */
    set(x, y, z, block) {
        if (this.inside(x, y, z)) this.cells.set(this.key(x, y, z), block);
    }
    get(x, y, z) { return this.cells.get(this.key(x, y, z)); }
    box(x1, y1, z1, x2, y2, z2, block) {
        for (let x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (let y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (let z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) this.set(x, y, z, block);
    }
    /** Walls of a box, open top and bottom. */
    walls(x1, y1, z1, x2, y2, z2, block) {
        for (let y = y1; y <= y2; y++) {
            for (let x = x1; x <= x2; x++) { this.set(x, y, z1, block); this.set(x, y, z2, block); }
            for (let z = z1; z <= z2; z++) { this.set(x1, y, z, block); this.set(x2, y, z, block); }
        }
    }
    /** An entity standing on the middle of a block; nbt is a compound value (see the typed helpers) that holds its id. */
    entity(x, y, z, nbt) {
        if (this.inside(x, y, z)) this.entities.push({x, y, z, nbt});
    }
    chance(p) { return this.rand() < p; }
    pick(list) { return list[Math.floor(this.rand() * list.length)]; }

    toNbt() {
        const palette = [];
        const index = new Map();
        const blocks = [];
        for (const [k, block] of this.cells) {
            const [x, y, z] = k.split(',').map(Number);
            const [name, props, nbt] = Array.isArray(block) ? block : [block, null, null];
            const id = name + (props ? JSON.stringify(props) : '');
            if (!index.has(id)) {
                index.set(id, palette.length);
                const entry = {Name: str(name)};
                if (props) entry.Properties = compound(Object.fromEntries(Object.entries(props).map(([pk, pv]) => [pk, str(String(pv))])));
                palette.push(compound(entry));
            }
            const entry = {pos: list(T.INT, [int(x), int(y), int(z)]), state: int(index.get(id))};
            if (nbt) entry.nbt = compound(Object.fromEntries(Object.entries(nbt).map(([nk, nv]) => [nk, str(nv)])));
            blocks.push(compound(entry));
        }
        return compound({
            DataVersion: int(DATA_VERSION),
            size: list(T.INT, this.size.map(int)),
            palette: list(T.COMPOUND, palette),
            blocks: list(T.COMPOUND, blocks),
            entities: list(T.COMPOUND, this.entities.map(e => compound({
                pos: list(T.DOUBLE, [double(e.x + 0.5), double(e.y), double(e.z + 0.5)]),
                blockPos: list(T.INT, [int(e.x), int(e.y), int(e.z)]),
                nbt: e.nbt
            })))
        });
    }
}

module.exports = {nbtFile, Build, T, int, str, byte, float, double, list, compound};
