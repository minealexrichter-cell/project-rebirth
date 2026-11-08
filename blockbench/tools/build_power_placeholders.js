// PLACEHOLDER textures of version 1.5.0 block A (charging pad, body battery, extra battery).
// They only make the new things visible for testing; the real look comes in block H (docs/PLAN_1.5.md).
// Run: node blockbench/tools/build_power_placeholders.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); }
function png(w, h, px) {
    const raw = Buffer.alloc((w * 4 + 1) * h);
    for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; for (let x = 0; x < w; x++) { const p = px[y * w + x] || [0, 0, 0, 0]; raw.set(p, y * (w * 4 + 1) + 1 + x * 4); } }
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const hex = (s, a = 255) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), a];
const ROOT = path.resolve(__dirname, '../../src/main/resources/assets/projectrebirth/textures/') + '/';

function image(file, painter) {
    const px = new Array(256);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px[y * 16 + x] = painter(x, y);
    fs.writeFileSync(ROOT + file, png(16, 16, px));
}

// pad top: dark plate, a ring of yellow contacts; brighter when it charges
function padTop(on) {
    return (x, y) => {
        const edge = x === 0 || y === 0 || x === 15 || y === 15;
        if (edge) return hex('#2b2f33');
        const d = Math.hypot(x - 7.5, y - 7.5);
        if (d > 4.5 && d < 6.0) return on ? hex('#ffe066') : hex('#b8962e');
        if (d < 2.2) return on ? hex('#fff3b0') : hex('#5c5a4a');
        return (x + y) % 2 ? hex('#44494e') : hex('#3d4246');
    };
}
image('block/charging_pad_top.png', padTop(false));
image('block/charging_pad_top_on.png', padTop(true));
image('block/charging_pad_side.png', (x, y) => y < 13 ? [0, 0, 0, 0] : y === 13 ? hex('#5a6066') : (x % 4 === 1 ? hex('#c9a227') : hex('#33383c')));

// battery items: a cell with a coloured band and a level window
function cell(band) {
    return (x, y) => {
        if (x < 4 || x > 11 || y < 2 || y > 14) return [0, 0, 0, 0];
        if (y === 2 && (x < 6 || x > 9)) return [0, 0, 0, 0];
        if (x === 4 || x === 11 || y === 14 || y === 2) return hex('#1f2326');
        if (y >= 5 && y <= 7) return hex(band);
        if (y >= 9 && y <= 12 && x >= 6 && x <= 9) return hex('#ffd54f');
        return hex('#4a5056');
    };
}
image('item/body_battery.png', cell('#e0a800'));
image('item/extra_battery.png', cell('#3fae5a'));
// cooling module (block B): a plate with fins and a blue core
image('item/cooling_module.png', (x, y) => {
    if (x < 2 || x > 13 || y < 3 || y > 12) return [0, 0, 0, 0];
    if (x === 2 || x === 13 || y === 3 || y === 12) return hex('#1f2326');
    if (Math.hypot(x - 7.5, y - 7.5) < 2.3) return hex('#4fc3f7');
    return x % 2 ? hex('#9aa5ad') : hex('#5d666d');
});
// block C: heater (a box with a grille that glows when it works), thermal liner, symptom icons
const grille = (on) => (x, y) => {
    if (x === 0 || y === 0 || x === 15 || y === 15) return hex('#2b2f33');
    if (x >= 3 && x <= 12 && y >= 4 && y <= 11) return y % 2 ? (on ? hex('#ff7a1a') : hex('#4a2a1a')) : hex('#1c1f22');
    return (x + y) % 3 ? hex('#5d666d') : hex('#535b61');
};
image('block/heater_side.png', grille(false));
image('block/heater_side_on.png', grille(true));
image('block/heater_top.png', (x, y) => (x === 0 || y === 0 || x === 15 || y === 15) ? hex('#2b2f33') : (x % 3 === 0 ? hex('#3d4246') : hex('#6b747b')));
image('item/thermal_liner.png', (x, y) => {
    if (x < 2 || x > 13 || y < 3 || y > 13) return [0, 0, 0, 0];
    if (x === 2 || x === 13 || y === 3 || y === 13) return hex('#6d4c33');
    return (x + y) % 4 < 2 ? hex('#c9a27a') : hex('#e2c9a5');
});
const drop = (core, rim) => (x, y) => {
    const d = Math.hypot(x - 7.5, y - 8.5);
    if (d > 6.5) return [0, 0, 0, 0];
    return d > 5.2 ? hex(rim) : d < 2.5 ? hex('#ffffff') : hex(core);
};
image('mob_effect/heatstroke.png', drop('#ff7043', '#8d2a00'));
image('mob_effect/hypothermia.png', drop('#81d4fa', '#1f4f7a'));
console.log('placeholders written');
