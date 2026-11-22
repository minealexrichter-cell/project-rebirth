// The mod's logo for the mod list (1.8.0 block G): a banner in the style "Industrial terminal" — a graphite plate with rivets,
// a transfer capsule with its green glow on the left, PROJECT: REBIRTH in amber pixel letters, hazard stripes below.
// Run: node blockbench/tools/build_logo.js  →  src/main/resources/projectrebirth_logo.png (pixel art, scaled ×2).
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const W = 160, H = 48, SCALE = 2;
const px = Buffer.alloc(W * H * 4);
const hex = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
function set(x, y, c, a = 255) {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const [r, g, b] = hex(c), i = (y * W + x) * 4;
    px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
}
function rect(x, y, w, h, c, a) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, c, a); }

// the plate
rect(0, 0, W, H, '#0b0c0d');
rect(1, 1, W - 2, H - 2, '#2b2e31');
rect(1, 1, W - 2, 1, '#3e4246');
rect(1, H - 2, W - 2, 1, '#1b1d1f');
let seed = 11;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
for (let i = 0; i < 260; i++) set(2 + Math.floor(rnd() * (W - 4)), 2 + Math.floor(rnd() * (H - 4)), rnd() < 0.5 ? '#26292c' : '#303438');
for (const [x, y] of [[3, 3], [W - 5, 3], [3, H - 5], [W - 5, H - 5]]) { rect(x, y, 2, 2, '#55595e'); set(x, y, '#8a9096'); }

// the capsule: a glass tube with green fluid between two caps
const cx = 8, cy = 6;
rect(cx, cy, 20, 4, '#4a4f54'); rect(cx + 1, cy + 1, 18, 2, '#6b7177');
rect(cx + 1, cy + 4, 18, 26, '#0b0c0d');
rect(cx + 2, cy + 5, 16, 24, '#1f6f63');
for (let y = cy + 5; y < cy + 29; y++) for (let x = cx + 2; x < cx + 18; x++) if ((x + y) % 7 === 0) set(x, y, '#2f9a88');
rect(cx + 3, cy + 6, 2, 20, '#7fe3d2', 150);
rect(cx + 8, cy + 12, 4, 7, '#9ff0e0'); rect(cx + 9, cy + 10, 2, 2, '#9ff0e0');
rect(cx, cy + 29, 20, 5, '#4a4f54'); rect(cx + 1, cy + 30, 18, 3, '#2b2e31');
for (let i = 0; i < 4; i++) set(cx + 3 + i * 4, cy + 31, i === 0 ? '#ff6a5a' : '#ffb648');

// pixel letters, 5×7
const FONT = {
    P: ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
    R: ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    O: ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    J: ['00111', '00010', '00010', '00010', '10010', '10010', '01100'],
    E: ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
    C: ['01110', '10001', '10000', '10000', '10000', '10001', '01110'],
    T: ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
    B: ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    I: ['01110', '00100', '00100', '00100', '00100', '00100', '01110'],
    H: ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    ':': ['00000', '01100', '01100', '00000', '01100', '01100', '00000'],
};
function text(str, x, y, size, color, shadow) {
    for (const ch of str) {
        const glyph = FONT[ch];
        if (!glyph) { x += 3 * size; continue; }
        glyph.forEach((row, j) => [...row].forEach((bit, i) => {
            if (bit === '1') {
                if (shadow) rect(x + i * size + 1, y + j * size + 1, size, size, shadow);
                rect(x + i * size, y + j * size, size, size, color);
            }
        }));
        x += 6 * size;
    }
}
text('PROJECT:', 36, 7, 1, '#c7a46a', '#151617');
text('REBIRTH', 36, 17, 2, '#ffb648', '#5a3f12');
rect(36, 34, 82, 1, '#ffb648', 120);

// hazard stripes along the bottom right
for (let x = 122; x < W - 3; x++) for (let y = 36; y < 42; y++) set(x, y, Math.floor((x + y) / 3) % 2 === 0 ? '#e0a63a' : '#1b1d1f');
rect(121, 35, W - 124, 1, '#0b0c0d'); rect(121, 42, W - 124, 1, '#0b0c0d');
// a lamp
rect(W - 12, 8, 5, 5, '#0b0c0d'); rect(W - 11, 9, 3, 3, '#7bd88f'); set(W - 11, 9, '#d8ffe0');

// scaled PNG
const SW = W * SCALE, SH = H * SCALE;
const raw = Buffer.alloc((SW * 4 + 1) * SH);
for (let y = 0; y < SH; y++) {
    raw[y * (SW * 4 + 1)] = 0;
    for (let x = 0; x < SW; x++) px.copy(raw, y * (SW * 4 + 1) + 1 + x * 4, ((y / SCALE | 0) * W + (x / SCALE | 0)) * 4, ((y / SCALE | 0) * W + (x / SCALE | 0)) * 4 + 4);
}
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(SW, 0); ihdr.writeUInt32BE(SH, 4); ihdr[8] = 8; ihdr[9] = 6;
const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
const out = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'projectrebirth_logo.png');
fs.writeFileSync(out, png);
console.log('wrote ' + out + ' ' + SW + 'x' + SH);
