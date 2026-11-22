// The icons of the three guns (1.8.0 block G, the icon audit): the old ones were dark grey shapes without light edges and were
// lost on dark slots. Side views in the mod's palette: light steel with a dark outline, amber and turquoise details.
// Run: node blockbench/tools/build_weapon_icons.js  →  textures/item/{assault_rifle,battle_rifle,combat_shotgun}.png
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const PALETTE = {
    '.': null,
    'o': '#0b0c0d', // outline
    'd': '#3a3e42', // dark steel
    'm': '#6b7177', // steel
    'l': '#a3aab0', // light edge
    'w': '#d8dde1', // shine
    'a': '#ffb648', // amber
    'A': '#c7843a', // dark amber
    't': '#4fd1c5', // turquoise lens
    'b': '#5a4a3a', // grip
};

// 16×16, the muzzle at the top right, the stock at the bottom left
const ICONS = {
    assault_rifle: [
        '................',
        '..............oo',
        '.............olo',
        '............olmo',
        '.......oo..olmo.',
        '......oaaoolmo..',
        '.....olllllmo...',
        '....olwmmmmo....',
        '...olmmddmmo....',
        '..oldmmoddoo....',
        '.oldmoooodo.....',
        'olmmo..obo......',
        'oddo...obo......',
        'odo....oo.......',
        '.o..............',
        '................',
    ],
    battle_rifle: [
        '...............o',
        '..............ol',
        '.............olo',
        '.........oo.olo.',
        '........ottoolo.',
        '.......ooooolmo.',
        '......ollllmmo..',
        '.....olwmmmdo...',
        '....olmmdmmo....',
        '...olmmoAAoo....',
        '..oldmo.oAo.....',
        '.oldmo..obo.....',
        'olddo...oo......',
        'oddo............',
        '.oo.............',
        '................',
    ],
    combat_shotgun: [
        '................',
        '.............ooo',
        '............olwo',
        '...........olmmo',
        '..........olmmo.',
        '.........olmdo..',
        '........oaaao...',
        '.......olllmo...',
        '......olwmdo....',
        '.....olmmdo.....',
        '....olddmo......',
        '...obbbdoo......',
        '..obbboo........',
        '.obbbo..........',
        '.ooo............',
        '................',
    ],
};

const hex = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };

const dir = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projectrebirth', 'textures', 'item');
for (const [name, rows] of Object.entries(ICONS)) {
    const raw = Buffer.alloc(16 * (16 * 4 + 1));
    rows.forEach((row, y) => {
        if (row.length !== 16) throw new Error(name + ' row ' + y + ' is ' + row.length);
        raw[y * 65] = 0;
        [...row].forEach((ch, x) => {
            const color = PALETTE[ch];
            if (color === undefined) throw new Error(name + ': unknown ' + ch);
            if (color) {
                const [r, g, b] = hex(color);
                raw.set([r, g, b, 255], y * 65 + 1 + x * 4);
            }
        });
    });
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(16, 0); ihdr.writeUInt32BE(16, 4); ihdr[8] = 8; ihdr[9] = 6;
    fs.writeFileSync(path.join(dir, name + '.png'), Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
    console.log('wrote ' + name);
}
