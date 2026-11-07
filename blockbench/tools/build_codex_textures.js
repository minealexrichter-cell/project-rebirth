// Paints the textures of the Rebirth Codex (version 1.4.1): the book's frame, parchment, map nodes, tabs, arrows and the
// item icon. A plain Node script (no Blockbench needed): node blockbench/tools/build_codex_textures.js
// The atlas layout is mirrored by client/codex/CodexAtlas.java.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const ROOT = path.resolve(__dirname, '../..') + '/src/main/resources/assets/projectrebirth/textures/';

// ---------------------------------------------------------------- PNG
const CRC = (() => { const t = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); }

class Image {
    constructor(w, h, seed) { this.w = w; this.h = h; this.px = new Uint8Array(w * h * 4); let s = seed || 7; this.rand = () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
    set(x, y, c, a) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.px[i] = c[0]; this.px[i + 1] = c[1]; this.px[i + 2] = c[2]; this.px[i + 3] = a === undefined ? 255 : a; }
    rect(x, y, w, h, c, a) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, c, a); return this; }
    noise(x, y, w, h, c, amount) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, shade(c, 1 + (this.rand() - 0.5) * 2 * amount)); return this; }
    png() {
        const raw = Buffer.alloc(this.h * (1 + this.w * 4));
        for (let y = 0; y < this.h; y++) { raw[y * (1 + this.w * 4)] = 0; Buffer.from(this.px.buffer, y * this.w * 4, this.w * 4).copy(raw, y * (1 + this.w * 4) + 1); }
        const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(this.w, 0); ihdr.writeUInt32BE(this.h, 4); ihdr[8] = 8; ihdr[9] = 6;
        return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
    }
}
const hex = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
function shade(c, f) { const v = typeof c === 'string' ? hex(c) : c; return v.map(x => Math.max(0, Math.min(255, Math.round(x * f)))); }

const LEATHER = '#5a3424', LEATHER_DARK = '#3a2016', BRASS = '#c9a24a', BRASS_DARK = '#7a5a1e', BRASS_LIGHT = '#f2dc8a';
const PARCHMENT = '#e8d6ae', INK = '#3b2a1a', RED_INK = '#7a2414';

/** A brass corner piece, 14x14, the inner corner at (0, 0) of its quarter. */
function corner(img, x, y, flipX, flipY) {
    const shape = [
        'bbbbbbbbbbbbbb',
        'bLLLLLLLLLLLdb',
        'bLlllllllllldb',
        'bLlbbbbbbbbldb',
        'bLlbddddddbldb',
        'bLlbd.....bldb',
        'bLlbd.....dldb',
        'bLlbd....dldb.',
        'bLlbd...dldb..',
        'bLlbbddddldb..',
        'bLllllllldb...',
        'bddddddddb....',
        'bdddddddb.....',
        'bbbbbbbb......'];
    const pal = {b: BRASS_DARK, L: BRASS_LIGHT, l: BRASS, d: shade(BRASS, 0.75)};
    shape.forEach((row, j) => [...row].forEach((ch, i) => {
        if (ch === '.') return;
        img.set(x + (flipX ? 13 - i : i), y + (flipY ? 13 - j : j), typeof pal[ch] === 'string' ? hex(pal[ch]) : pal[ch]);
    }));
}

// ---------------------------------------------------------------- atlas 256x256
const atlas = new Image(256, 256, 41);

// Frame, 48x48 at (0, 0), nine-slice with corners of 16: leather with a stitched inner edge and brass corners; the middle is clear.
atlas.noise(0, 0, 48, 48, LEATHER, 0.06);
atlas.rect(16, 16, 16, 16, [0, 0, 0], 0);
for (let i = 0; i < 48; i++) {
    atlas.set(i, 0, hex(LEATHER_DARK)); atlas.set(i, 47, hex(LEATHER_DARK)); atlas.set(0, i, hex(LEATHER_DARK)); atlas.set(47, i, hex(LEATHER_DARK));
    if (i % 4 < 2) { atlas.set(i, 3, hex('#b08860')); atlas.set(i, 44, hex('#b08860')); atlas.set(3, i, hex('#b08860')); atlas.set(44, i, hex('#b08860')); }
}
// the inner edge of the frame casts a shadow onto the parchment
for (let i = 0; i < 48; i++) { for (const [x, y] of [[i, 15], [15, i]]) atlas.set(x, y, hex(LEATHER_DARK)); for (const [x, y] of [[i, 32], [32, i]]) atlas.set(x, y, hex(LEATHER_DARK)); }
atlas.rect(16, 16, 16, 16, [0, 0, 0], 0);
corner(atlas, 1, 1, false, false); corner(atlas, 33, 1, true, false); corner(atlas, 1, 33, false, true); corner(atlas, 33, 33, true, true);

// Parchment, 64x64 at (160, 0), tiles seamlessly: warm paper with fibres and faint stains.
atlas.noise(160, 0, 64, 64, PARCHMENT, 0.035);
for (let k = 0; k < 40; k++) { const x = Math.floor(atlas.rand() * 64), y = Math.floor(atlas.rand() * 64), len = 2 + Math.floor(atlas.rand() * 5);
    for (let i = 0; i < len; i++) atlas.set(160 + (x + i) % 64, y, shade(PARCHMENT, 0.9)); }
for (let k = 0; k < 6; k++) { const cx = Math.floor(atlas.rand() * 64), cy = Math.floor(atlas.rand() * 64), r = 3 + atlas.rand() * 4;
    for (let j = -8; j <= 8; j++) for (let i = -8; i <= 8; i++) if (i * i + j * j < r * r) { const x = 160 + ((cx + i + 64) % 64), y = (cy + j + 64) % 64; atlas.set(x, y, shade(PARCHMENT, 0.95)); } }

// Map nodes, 26x26 at (0, 48): normal, hovered, the first entry of a category (gold).
function node(x, ring, inner, glow) {
    for (let j = 0; j < 26; j++) for (let i = 0; i < 26; i++) {
        const dx = i - 12.5, dy = j - 12.5, d = Math.sqrt(dx * dx + dy * dy);
        if (d < 12.6) atlas.set(x + i, 48 + j, d > 11 ? shade(ring, 0.6) : d > 9.5 ? (dy < 0 ? shade(ring, 1.25) : shade(ring, 0.8)) : shade(inner, 1 + (atlas.rand() - 0.5) * 0.06));
        if (glow && d >= 12.6 && d < 13.2) atlas.set(x + i, 48 + j, hex(glow), 160);
    }
}
node(0, hex(BRASS), hex('#efe2c0'), null);
node(26, hex(BRASS_LIGHT), hex('#fff4d6'), '#fff0a0');
node(52, hex('#d8a020'), hex('#f6e6b0'), null);

// Tabs, 30x26 at (0, 80): inactive and active leather tongues sticking out of the frame to the left.
function tab(x, c) {
    atlas.noise(x, 80, 30, 26, c, 0.06);
    for (let i = 0; i < 30; i++) { atlas.set(x + i, 80, hex(LEATHER_DARK)); atlas.set(x + i, 105, hex(LEATHER_DARK)); }
    for (let j = 0; j < 26; j++) atlas.set(x, 80 + j, hex(LEATHER_DARK));
    for (let i = 3; i < 30; i += 2) { atlas.set(x + i, 83, hex('#b08860')); atlas.set(x + i, 102, hex('#b08860')); }
}
tab(0, shade(LEATHER, 0.8)); tab(30, LEATHER);

// Arrows, 18x10 at (0, 112): next, previous, then their hovered forms; a back button 14x14 at (72, 108) and hovered (86, 108).
function arrow(x, y, right, c) {
    for (let j = 0; j < 10; j++) for (let i = 0; i < 18; i++) {
        const tip = right ? i : 17 - i, body = j >= 3 && j <= 6 && tip < 11, head = tip >= 10 && Math.abs(j - 4.5) <= (17 - tip) * 0.6;
        if (body || head) atlas.set(x + i, y + j, j < 4 ? shade(c, 1.2) : shade(c, 0.9));
    }
}
arrow(0, 112, true, hex(RED_INK)); arrow(18, 112, false, hex(RED_INK)); arrow(36, 112, true, hex('#c0401c')); arrow(54, 112, false, hex('#c0401c'));
function back(x, c) {
    for (let j = 0; j < 14; j++) for (let i = 0; i < 14; i++) { const d = Math.hypot(i - 6.5, j - 6.5); if (d < 6.8) atlas.set(x + i, 108 + j, d > 5.6 ? shade(c, 0.6) : c); }
    [[4, 7], [5, 6], [5, 7], [5, 8], [6, 5], [6, 9], [7, 4], [7, 10]].forEach(([i, j]) => atlas.set(x + i, 108 + j, hex('#f6e6b0')));
    for (let i = 5; i < 10; i++) atlas.set(x + i, 108 + 7, hex('#f6e6b0'));
}
back(72, hex(RED_INK)); back(86, hex('#c0401c'));

// Spine, 16x128 at (128, 0): the fold between the pages, tiled vertically.
for (let j = 0; j < 128; j++) for (let i = 0; i < 16; i++) {
    const d = Math.abs(i - 7.5) / 7.5;
    atlas.set(128 + i, j, shade(PARCHMENT, 0.62 + 0.38 * d * d + (atlas.rand() - 0.5) * 0.03));
}

// A slot for recipes, 18x18 at (0, 128): a pressed square on the parchment.
atlas.noise(1, 129, 16, 16, shade(PARCHMENT, 0.85), 0.03);
for (let i = 0; i < 18; i++) { atlas.set(i, 128, shade(INK, 1.2)); atlas.set(0, 128 + i, shade(INK, 1.2)); atlas.set(i, 145, hex('#fff4dc')); atlas.set(17, 128 + i, hex('#fff4dc')); }

// A recipe arrow, 22x15 at (24, 128), and a title ornament, 96x7 at (0, 150).
for (let j = 0; j < 15; j++) for (let i = 0; i < 22; i++) { const body = j >= 5 && j <= 9 && i < 14, head = i >= 13 && Math.abs(j - 7) <= (21 - i) * 0.9; if (body || head) atlas.set(24 + i, 128 + j, shade(INK, 1.4)); }
for (let i = 0; i < 96; i++) { const d = Math.abs(i - 47.5) / 47.5; if (d < 0.97) atlas.set(i, 153, hex(RED_INK), Math.round(255 * (1 - d * d))); }
[[47, 150], [48, 150], [46, 151], [49, 151], [45, 152], [50, 152], [45, 154], [50, 154], [46, 155], [49, 155], [47, 156], [48, 156]].forEach(([x, y]) => atlas.set(x, y, hex(RED_INK)));

fs.mkdirSync(ROOT + 'gui', {recursive: true});
fs.writeFileSync(ROOT + 'gui/codex.png', atlas.png());

// ---------------------------------------------------------------- the item: a leather book with brass corners and a green emblem
const icon = new Image(16, 16, 5);
const rows = [
    '................',
    '..bbbbbbbbbbbb..',
    '.bcllllllllllcb.',
    '.blLLLLLLLLLLlpb',
    '.blLLLggggLLLlpb',
    '.blLLgGGGGgLLlpb',
    '.blLLgGeeGgLLlpb',
    '.blLLgGeeGgLLlpb',
    '.blLLgGGGGgLLlpb',
    '.blLLLggggLLLlpb',
    '.blLLLLLLLLLLlpb',
    '.blLLLLLLLLLLlpb',
    '.bcllllllllllcpb',
    '..bbbbbbbbbbbbpb',
    '...ppppppppppppb',
    '....bbbbbbbbbbb.'];
const pal = {b: hex(LEATHER_DARK), l: hex(LEATHER), L: shade(LEATHER, 1.15), c: hex(BRASS), p: hex(PARCHMENT), g: hex(BRASS), G: hex('#2f8a52'), e: hex('#9ff0b8')};
rows.forEach((row, j) => [...row].forEach((ch, i) => { if (ch !== '.') icon.set(i, j, pal[ch]); }));
fs.mkdirSync(ROOT + 'item', {recursive: true});
fs.writeFileSync(ROOT + 'item/rebirth_codex.png', icon.png());
console.log('codex textures written');
