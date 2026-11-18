// Paints the sprites of the mod's interface in the style "Industrial terminal" (docs/UI_STYLE.md).
// Usage: node blockbench/tools/build_ui.js
//
// Every sprite is written to assets/projectrebirth/textures/gui/sprites/ui/<name>.png; a sprite that can have any size
// gets a .mcmeta with "nine_slice" scaling (corners stay sharp, the edges and the middle repeat) or "tile" scaling.
// The game stitches them into its GUI atlas, the code draws them with GuiGraphics.blitSprite (client/ui/UiSprites).
// Colours come from the palette below; the same values are in client/ui/UiColors.java.
const fs = require('fs');
const zlib = require('zlib');
const ROOT = 'E:/Projects/Project-Rebirth-mod/';
const OUT = ROOT + 'src/main/resources/assets/projectrebirth/textures/gui/sprites/ui/';

const P = {
    outline: '#0B0C0D', body: '#2B2E31', bevelLight: '#4C5055', bevelDark: '#16181A',
    rivet: '#62686E', rivetShade: '#1D1F21', rivetLight: '#8A9096',
    screen: '#0E1311', screenLine: '#111816', screenEdge: '#08090A', screenShadow: '#040505', screenLight: '#3A3F43',
    slot: '#1B1E21', slotDark: '#0A0B0C', slotLight: '#41464B',
    button: '#3A3E42', buttonLight: '#5A5F64', buttonHover: '#464B50', buttonHoverLight: '#70767C',
    buttonPressed: '#2C2F32', buttonDisabled: '#2F3235',
    amber: '#FFB648', amberLight: '#FFCF85', amberBack: '#2A2416', chipOn: '#3D3420', amberTrack: '#2A2213', amberTrackEdge: '#6B5326',
    chipOff: '#22282A', chipOffEdge: '#343C3F', field: '#1A201D', fieldEdge: '#2D3430', fieldFocus: '#5C6B62',
    hazard: '#D9A326', hazardDark: '#1B1C1D',
    error: '#FF8A7A', errorBack: '#3A2321', ok: '#7BD88F', okBack: '#1D2F22', info: '#5A6066', infoBack: '#1E2124',
    energy: '#4FC3FF', energyDark: '#2A7FB0', text: '#E6E3DC', hover: '#1C231F', hoverEdge: '#3E4842'
};

// ---------------------------------------------------------------- PNG

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); }
function random(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const hex = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
const mix = (a, b, t) => '#' + hex(a).map((v, i) => Math.round(v + (hex(b)[i] - v) * t).toString(16).padStart(2, '0')).join('');
const shade = (c, f) => '#' + hex(c).map(v => Math.max(0, Math.min(255, Math.round(v * f))).toString(16).padStart(2, '0')).join('');

class Sprite {
    constructor(w, h, seed) { this.w = w; this.h = h; this.px = Buffer.alloc(w * h * 4); this.rand = random(seed || 7); }
    set(x, y, c, a) {
        if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
        const [r, g, b] = hex(c), i = (y * this.w + x) * 4;
        this.px[i] = r; this.px[i + 1] = g; this.px[i + 2] = b; this.px[i + 3] = a === undefined ? 255 : a;
    }
    rect(x, y, w, h, c, a) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, c, a); }
    /** A 1 px frame. */
    frame(x, y, w, h, c) { this.rect(x, y, w, 1, c); this.rect(x, y + h - 1, w, 1, c); this.rect(x, y, 1, h, c); this.rect(x + w - 1, y, 1, h, c); }
    /** Light on the top and left edges, dark on the bottom and right: a raised (or, swapped, sunken) look. */
    bevel(x, y, w, h, light, dark) {
        this.rect(x, y, w, 1, light); this.rect(x, y, 1, h, light);
        this.rect(x, y + h - 1, w, 1, dark); this.rect(x + w - 1, y, 1, h, dark);
    }
    png() {
        const { w, h } = this;
        const raw = Buffer.alloc((w * 4 + 1) * h);
        for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; this.px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
        const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
        return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
    }
}

const written = [];
function save(name, sprite, scaling) {
    fs.mkdirSync(require('path').dirname(OUT + name), { recursive: true });
    fs.writeFileSync(OUT + name + '.png', sprite.png());
    const meta = OUT + name + '.png.mcmeta';
    if (scaling) fs.writeFileSync(meta, JSON.stringify({ gui: { scaling } }, null, 2) + '\n');
    else if (fs.existsSync(meta)) fs.unlinkSync(meta);
    written.push(name);
}
const nine = (w, h, border) => ({ type: 'nine_slice', width: w, height: h, border });

// ---------------------------------------------------------------- window parts

/** The body of a window: dark graphite metal with a bevel, a black outline and rivets in the corners. */
function windowBody() {
    const s = new Sprite(32, 32, 11);
    s.rect(0, 0, 32, 32, P.body);
    // faint scuffs, only in the middle so the repeat stays quiet
    for (let i = 0; i < 10; i++) {
        const x = 8 + Math.floor(s.rand() * 16), y = 8 + Math.floor(s.rand() * 16);
        s.set(x, y, shade(P.body, s.rand() < 0.5 ? 1.08 : 0.93));
    }
    s.frame(0, 0, 32, 32, P.outline);
    s.bevel(1, 1, 30, 30, P.bevelLight, P.bevelDark);
    for (const [x, y] of [[3, 3], [26, 3], [3, 26], [26, 26]]) {
        s.rect(x, y, 3, 3, P.rivet);
        s.set(x, y, P.rivetLight);
        s.set(x + 2, y + 2, P.rivetShade); s.set(x + 1, y + 2, shade(P.rivet, 0.7)); s.set(x + 2, y + 1, shade(P.rivet, 0.7));
    }
    save('window', s, nine(32, 32, 7));
}

/** A sunken screen: lists, the process, the description. Faint scan lines every other row. */
function screen() {
    const s = new Sprite(16, 16);
    for (let y = 0; y < 16; y++) s.rect(0, y, 16, 1, y % 2 ? P.screenLine : P.screen);
    s.frame(0, 0, 16, 16, P.screenEdge);
    s.rect(1, 1, 14, 1, P.screenShadow); s.rect(1, 1, 1, 14, P.screenShadow);
    s.rect(1, 14, 14, 1, P.screenLight); s.rect(14, 1, 1, 14, P.screenLight);
    save('screen', s, nine(16, 16, 3));
}

/** The 1 px line under the title. */
function divider() {
    const s = new Sprite(8, 2);
    s.rect(0, 0, 8, 1, P.bevelDark); s.rect(0, 1, 8, 1, shade(P.body, 1.12));
    save('divider', s, { type: 'tile', width: 8, height: 2 });
}

/** Hazard stripes of the title bar, already mixed at 70 % over the window body. */
function hazard() {
    const s = new Sprite(8, 5);
    const a = mix(P.body, P.hazard, 0.7), b = mix(P.body, P.hazardDark, 0.7);
    for (let y = 0; y < 5; y++) for (let x = 0; x < 8; x++) s.set(x, y, ((x + y) % 8) < 4 ? a : b);
    save('hazard', s, { type: 'tile', width: 8, height: 5 });
}

// ---------------------------------------------------------------- slots

function slotBase(s, x, y, w, h) {
    s.rect(x, y, w, h, P.slot);
    s.bevel(x, y, w, h, P.slotDark, P.slotLight);
}

function slots() {
    let s = new Sprite(18, 18); slotBase(s, 0, 0, 18, 18); save('slot', s);
    // a big output slot with an amber ring
    s = new Sprite(26, 26); s.frame(0, 0, 26, 26, P.amber); slotBase(s, 1, 1, 24, 24); save('slot_output', s);
    // a slot that misses an ingredient: dashed red frame inside
    s = new Sprite(18, 18); slotBase(s, 0, 0, 18, 18);
    for (let i = 2; i < 16; i++) {
        if (((i - 2) >> 1) % 2 === 0) { s.set(i, 2, P.error); s.set(i, 15, P.error); s.set(2, i, P.error); s.set(15, i, P.error); }
    }
    save('slot_missing', s);
    // a disabled slot: crossed
    s = new Sprite(18, 18); slotBase(s, 0, 0, 18, 18);
    for (let i = 4; i < 14; i++) { s.set(i, i, P.slotLight); s.set(17 - i, i, P.slotLight); }
    save('slot_disabled', s);
}

// ---------------------------------------------------------------- buttons and chips

function button(name, fill, light, dark, edge) {
    const s = new Sprite(16, 16);
    s.rect(0, 0, 16, 16, fill);
    s.frame(0, 0, 16, 16, edge || P.outline);
    s.rect(1, 1, 14, 1, light); s.rect(1, 1, 1, 14, light);
    if (dark) { s.rect(1, 14, 14, 1, dark); s.rect(14, 1, 1, 14, dark); }
    save(name, s, nine(16, 16, 2));
}

/** A flat box with a 1 px coloured edge: filter chips, list rows, the search field, status plates. */
function box(name, fill, edge) {
    const s = new Sprite(12, 12);
    s.rect(0, 0, 12, 12, fill);
    s.frame(0, 0, 12, 12, edge);
    save(name, s, nine(12, 12, 1));
}

function buttons() {
    button('button', P.button, P.buttonLight, shade(P.button, 0.8));
    button('button_hover', P.buttonHover, P.buttonHoverLight, shade(P.buttonHover, 0.8), mix(P.outline, P.amber, 0.55));
    button('button_pressed', P.buttonPressed, shade(P.buttonPressed, 0.7), null);
    button('button_disabled', P.buttonDisabled, shade(P.buttonDisabled, 1.1), null);
    box('chip_on', P.chipOn, P.amber);
    box('chip_off', P.chipOff, P.chipOffEdge);
    box('chip_off_hover', mix(P.chipOff, P.chipOffEdge, 0.4), mix(P.chipOffEdge, P.amber, 0.4));
    box('field', P.field, P.fieldEdge);
    box('field_focus', P.field, P.fieldFocus);
    box('row_selected', P.amberBack, P.amber);
    box('row_hover', P.hover, P.hoverEdge);
    box('plate_error', P.errorBack, P.error);
    box('plate_warning', P.amberBack, P.amber);
    box('plate_ok', P.okBack, P.ok);
    box('plate_info', P.infoBack, P.info);
    box('track_amber', P.amberTrack, P.amberTrackEdge);
}

// ---------------------------------------------------------------- icons (7 x 7, drawn from text grids)

function icon(name, color, rows) {
    const s = new Sprite(rows[0].length, rows.length);
    rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') s.set(x, y, color); else if (ch === '+') s.set(x, y, shade(color, 0.6)); }));
    save('icon/' + name, s);
}

function icons() {
    icon('check', P.ok, ['......#', '.....##', '#...##.', '##.##..', '.###...', '..#....', '.......']);
    icon('cross', P.error, ['#.....#', '##...##', '.##.##.', '..###..', '.##.##.', '##...##', '#.....#']);
    icon('warning', P.error, ['...#...', '..#.#..', '..#.#..', '.#.#.#.', '.#...#.', '#..#..#', '#######']);
    icon('warning_amber', P.amber, ['...#...', '..#.#..', '..#.#..', '.#.#.#.', '.#...#.', '#..#..#', '#######']);
    icon('search', '#8FA39A', ['.###...', '#...#..', '#...#..', '#...#..', '.####..', '....##.', '.....##']);
    icon('dot_ok', P.ok, ['.###.', '#####', '#####', '#####', '.###.']);
    icon('dot_warning', P.amber, ['.###.', '#####', '#####', '#####', '.###.']);
    icon('dot_error', P.error, ['.###.', '#####', '#####', '#####', '.###.']);
    icon('dot_idle', '#8A9096', ['.###.', '#####', '#####', '#####', '.###.']);
    icon('close', P.text, ['#.....#', '.#...#.', '..#.#..', '...#...', '..#.#..', '.#...#.', '#.....#']);
    icon('help', P.amber, ['.###.', '#...#', '....#', '..##.', '..#..', '.....', '..#..']);
    // the station mark of the title: an amber frame with a cross (as in the mock-up)
    icon('station', P.amber, ['#########', '#.......#', '#...#...#', '#...#...#', '#.#####.#', '#...#...#', '#...#...#', '#.......#', '#########']);
}

// ---------------------------------------------------------------- run

windowBody(); screen(); divider(); hazard(); slots(); buttons(); icons();
console.log('Wrote ' + written.length + ' sprites to ' + OUT);
