// Draws the 16x16 item icons of the three power armors (1.5.2) from pixel maps, after the new models.
// Usage: node blockbench/tools/armor_icons.js  (writes src/main/resources/assets/projectrebirth/textures/item/*.png)
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projectrebirth/textures/item');

function png(width, height, rgba) {
    const crcTable = [];
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        crcTable[n] = c >>> 0;
    }
    const crc = buf => {
        let c = 0xffffffff;
        for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
        return (c ^ 0xffffffff) >>> 0;
    };
    const chunk = (type, data) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
        const td = Buffer.concat([Buffer.from(type), data]);
        const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
        return Buffer.concat([len, td, c]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
    const raw = Buffer.alloc((width * 4 + 1) * height);
    for (let y = 0; y < height; y++) {
        raw[y * (width * 4 + 1)] = 0;
        rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
    }
    return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

function draw(name, palette, rows, scale) {
    if (rows.length !== 16 || rows.some(r => r.length !== 16)) {
        throw new Error(name + ': every row must be 16 wide: ' + rows.map(r => r.length).join(','));
    }
    const s = scale || 1;
    const buf = Buffer.alloc(16 * s * 16 * s * 4);
    rows.forEach((row, y) => [...row].forEach((ch, x) => {
        if (ch === '.') return;
        const hex = palette[ch];
        if (!hex) throw new Error(name + ': no colour for ' + ch);
        const n = parseInt(hex.slice(1), 16);
        for (let dy = 0; dy < s; dy++) for (let dx = 0; dx < s; dx++) {
            const i = ((y * s + dy) * 16 * s + x * s + dx) * 4;
            buf[i] = (n >> 16) & 255; buf[i + 1] = (n >> 8) & 255; buf[i + 2] = n & 255; buf[i + 3] = 255;
        }
    }));
    return png(16 * s, 16 * s, buf);
}

const ICONS = {
    // the combat armor from the front: lens eyes, an amber respirator, valves on the shoulders, olive chest plate
    power_armor: [{k: '#0e0f11', l: '#a7afb5', p: '#5d646b', e: '#7f878e', d: '#2a2e33', o: '#58623e', a: '#e0a020', c: '#8ff4ff'}, [
        '....kkkkkkkk....',
        '...kllllllllk...',
        '...kpccppccpk...',
        '...kppdaadppk...',
        '.kkkkkddddkkkkk.',
        'kllllkpllpkllllk',
        'kpappkeooekppapk',
        'kpppkpeppepkpppk',
        'kdpdkppaappkdpdk',
        'kppkkpddddpkkppk',
        'kllk.kppppk.kllk',
        'kkkk.kpkkpk.kkkk',
        '.....kpk.kpk....',
        '....kkpk.kpkk...',
        '....kllk.kllk...',
        '....kkkk.kkkk...']],
    // the mobile armor from the side: the long helmet with a red eye, the body leaning forward, the tail curling up
    // behind, a reverse-knee leg with claws
    mobile_power_armor: [{k: '#0e0f11', g: '#4d5249', L: '#868c7f', o: '#525836', d: '#232628', r: '#c4391f', a: '#bf8420'}, [
        '..........kkkkk.',
        '.........kLLLLLk',
        '.........kgggrgk',
        'kk.......kgdddkk',
        'kLk.....kkgkk...',
        '.kgk...kgLggk...',
        '..kgkkkgLLggok..',
        '...kgggggggaok..',
        '....kkgggggook..',
        '......kogdddk...',
        '......kooggk....',
        '.......kdgk.....',
        '......kgdk......',
        '.......kgk......',
        '.......kgLk.....',
        '.......kkLLLk...']],
    // the worker armor from the front: the glass cab with the pilot in it, a hazard sill, the saw and the clamp
    worker_power_armor: [{k: '#0e0f11', Y: '#efc24a', y: '#d6a21e', g: '#5a5c5f', G: '#9fe8ff', d: '#37383b', c: '#c9eef0', h: '#4a3020',
        s: '#b0805a', e: '#2a1c12', t: '#2f8f8a', x: '#1c1d1f', w: '#a9adb0', W: '#6c7074'}, [
        '...kkkkkkkkkk...',
        '..kYYYYYYYYYYk..',
        'kkkyccccccccykkk',
        'kYkycchhhhccykYk',
        'kykychsssshcykyk',
        'kGkycsessescykGk',
        'kykycsssssscykyk',
        'kkdycttttttcydkk',
        'kgdycttttttcydgk',
        'wgdkxyxyxyxykdgk',
        'wWgkkggggggkkgdk',
        'wwk.kyyk.kyyk.k.',
        '.k..kxyk.kxyk...',
        '....kggk.kggk...',
        '...kggggkggggk..',
        '...kkkkkkkkkkk..']]
};

const preview = process.argv.includes('--preview');
for (const [name, [palette, rows]] of Object.entries(ICONS)) {
    if (preview) {
        fs.writeFileSync(path.join(process.argv[process.argv.indexOf('--preview') + 1], name + '_x16.png'), draw(name, palette, rows, 16));
    } else {
        fs.writeFileSync(path.join(OUT, name + '.png'), draw(name, palette, rows));
    }
}
console.log(preview ? 'previews written' : 'icons written');
