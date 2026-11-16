// Paints the block and item textures of Project: Rebirth inside Blockbench.
// Usage in Blockbench's console or through the MCP risky_eval tool:
//   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/build_textures.js', 'utf8'));
//   RebirthTextures.saveAll();                      // writes every PNG into the mod's resources
//   RebirthTextures.sheet('C:/temp/sheet.png', 8);  // a magnified sheet of all textures, for looking at them
//   RebirthTextures.showcase();                     // a project with every texture on a cube or a plane (saved as .bbmodel)
// Entity textures are made by build_models.js. An icon is a 16x16 text grid: each letter is a palette colour, '.' is empty.
(function () {
    const fs = require('fs');
    const ROOT = 'E:/Projects/Project-Rebirth-mod/';
    const ASSETS = ROOT + 'src/main/resources/assets/projectrebirth/textures/';

    function hex(c) {
        return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
    }

    function shade(c, f) {
        const [r, g, b] = hex(c);
        const ch = v => Math.max(0, Math.min(255, Math.round(v * f))).toString(16).padStart(2, '0');
        return '#' + ch(r) + ch(g) + ch(b);
    }

    function mix(a, b, t) {
        const x = hex(a), y = hex(b);
        const ch = i => Math.round(x[i] + (y[i] - x[i]) * t).toString(16).padStart(2, '0');
        return '#' + ch(0) + ch(1) + ch(2);
    }

    function random(seed) {
        let s = seed >>> 0 || 1;
        return () => {
            s ^= s << 13; s >>>= 0;
            s ^= s >>> 17;
            s ^= s << 5; s >>>= 0;
            return s / 4294967296;
        };
    }

    /** One 16x16 texture with small drawing operations. */
    class Painter {
        constructor(seed) {
            this.canvas = document.createElement('canvas');
            this.canvas.width = this.canvas.height = 16;
            this.ctx = this.canvas.getContext('2d');
            this.rand = random(seed || 12345);
        }

        rect(x, y, w, h, c) {
            this.ctx.fillStyle = c;
            this.ctx.fillRect(x, y, w, h);
            return this;
        }

        px(c, ...points) {
            for (const [x, y] of points) {
                this.rect(x, y, 1, 1, c);
            }
            return this;
        }

        /** Fills an area with a colour and a little per-pixel variation. */
        noise(x, y, w, h, c, amount) {
            for (let j = y; j < y + h; j++) {
                for (let i = x; i < x + w; i++) {
                    this.rect(i, j, 1, 1, shade(c, 1 + (this.rand() - 0.5) * 2 * amount));
                }
            }
            return this;
        }

        /** A raised plate: light on the top and left, dark on the bottom and right. */
        bevel(x, y, w, h, c, amount) {
            const a = amount === undefined ? 0.05 : amount;
            this.noise(x, y, w, h, c, a);
            this.rect(x, y, w, 1, shade(c, 1.28));
            this.rect(x, y, 1, h, shade(c, 1.18));
            this.rect(x, y + h - 1, w, 1, shade(c, 0.66));
            this.rect(x + w - 1, y, 1, h, shade(c, 0.72));
            return this;
        }

        /** A sunken area: dark on the top and left, light on the bottom and right. */
        inset(x, y, w, h, c, amount) {
            const a = amount === undefined ? 0.03 : amount;
            this.noise(x, y, w, h, c, a);
            this.rect(x, y, w, 1, shade(c, 0.6));
            this.rect(x, y, 1, h, shade(c, 0.7));
            this.rect(x, y + h - 1, w, 1, shade(c, 1.35));
            this.rect(x + w - 1, y, 1, h, shade(c, 1.25));
            return this;
        }

        rivets(c, inset) {
            const a = inset === undefined ? 1 : inset, b = 15 - a;
            return this.px(c, [a, a], [b, a], [a, b], [b, b]);
        }

        /** Draws a text grid; '.' leaves what is under it. */
        grid(rows, palette, ox, oy) {
            rows.forEach((row, y) => {
                [...row].forEach((ch, x) => {
                    if (ch !== '.') {
                        if (!palette[ch]) {
                            throw new Error('No colour for ' + ch);
                        }
                        this.rect((ox || 0) + x, (oy || 0) + y, 1, 1, palette[ch]);
                    }
                });
            });
            return this;
        }
    }

    function icon(rows, palette) {
        if (rows.length !== 16 || rows.some(row => row.length !== 16)) {
            throw new Error('An icon must be 16x16: ' + rows.map(row => row.length).join(','));
        }
        return new Painter().grid(rows, palette);
    }

    const TEXTURES = {};
    /** Block textures see-through on purpose (see clearPixels). */
    const CLEAR_BLOCKS = new Set(['assembly_frame', 'heavy_frame', 'decontamination_chamber_water', 'specimen_tank_liquid', 'lamp_glass',
        'tank_glass_broken', 'workshop_glass']);
    const block = (id, make) => { TEXTURES['block/' + id] = make; };
    // The weapon icons are made from the models since 1.6.0 (derive_textures.js, RebirthDerived.icon): left alone here.
    const DRAWN_FROM_MODELS = new Set(['assault_rifle', 'battle_rifle', 'combat_shotgun', 'power_armor', 'mobile_power_armor', 'worker_power_armor']);
    const item = (id, make) => { if (!DRAWN_FROM_MODELS.has(id)) TEXTURES['item/' + id] = make; };

    // ---------------------------------------------------------------- ores

    const STONE = '#7e7e7e';
    const DEEPSLATE = '#4c4c52';

    function stone(p) {
        p.noise(0, 0, 16, 16, STONE, 0.05);
        const patches = [[1, 2, 4, 1], [9, 1, 5, 1], [11, 5, 3, 1], [3, 7, 5, 1], [0, 11, 3, 1], [7, 12, 6, 1], [12, 9, 3, 1], [5, 15, 3, 1]];
        patches.forEach(([x, y, w, h], i) => p.noise(x, y, w, h, shade(STONE, i % 2 ? 0.86 : 1.09), 0.03));
        return p;
    }

    function deepslate(p) {
        p.noise(0, 0, 16, 16, DEEPSLATE, 0.05);
        [1, 4, 8, 11, 14].forEach((y, i) => {
            for (let x = 0; x < 16; x++) {
                if ((x * 5 + i * 7) % 11 < 7) {
                    p.rect(x, y, 1, 1, shade(DEEPSLATE, i % 2 ? 0.8 : 0.72));
                }
            }
        });
        [[2, 2], [10, 2], [6, 6], [13, 6], [3, 9], [9, 10], [12, 13], [1, 13]].forEach(([x, y]) => p.rect(x, y, 3, 1, shade(DEEPSLATE, 1.16)));
        return p;
    }

    // h highlight, o mineral, d mineral shadow, s glint
    const ORES = {
        tin: {c: {h: '#d8e0e2', o: '#a9b6ba', d: '#6f7c82', s: '#f4fafa'}, rows: [
            '................',
            '..dd.......oh...',
            '.dohd.....doood.',
            '.dosod.....dood.',
            '..ddd.......dd..',
            '.......dd.......',
            '......dhod......',
            '.....dosood..oh.',
            '......dood..dood',
            '.......dd....dd.',
            '.oh.............',
            'doood......dhd..',
            '.dood.....doood.',
            '..dd......dosd..',
            '...........dd...',
            '................']},
        aluminum: {c: {h: '#f2eef0', o: '#cbc6cf', d: '#8e8a96', s: '#ffffff'}, rows: [
            '.......dh.......',
            '......doood.....',
            '......dosod.....',
            '.hd....ddd...dd.',
            'dood........dhod',
            '.dd.........doo.',
            '.....dhd.....d..',
            '....doood.......',
            '....dosood...hd.',
            '.....dood...dood',
            '......dd....dsod',
            '.............dd.',
            '.dhd.....dd.....',
            'doood...dhod....',
            '.dood...dood....',
            '..dd.....dd.....']},
        silver: {c: {h: '#ffffff', o: '#d4e4f2', d: '#7f96ae', s: '#ffffff'}, rows: [
            '................',
            '.dh........dd...',
            'dosd......dhod..',
            '.ddd.....doosd..',
            '..........dd....',
            '....dhd.........',
            '...dosod....dh..',
            '...doood...dosd.',
            '....ddd.....dd..',
            '.h..............',
            'dod.....dhd.....',
            'dod....doood..d.',
            '.d.....dosod.dhd',
            '........ddd..dod',
            '..............d.',
            '................']},
        lithium: {c: {h: '#f7c8e6', o: '#d783c0', d: '#8a4688', s: '#ffe6f6'}, rows: [
            '..........d.....',
            '.........dhd....',
            '..dd....doosd...',
            '.dhod...doood...',
            '.dood....ddd....',
            '..dd............',
            '.........dh.....',
            '....dd..doo.....',
            '...dhsd.dood....',
            '...dood..dd..dd.',
            '....dd......dhod',
            '.............dd.',
            '.d......dd......',
            'dhd....dhod.....',
            'dsod...doosd....',
            '.dd.....ddd.....']},
        titanium: {c: {h: '#9fc4d8', o: '#5e8aa3', d: '#2c4656', s: '#d6f0fa'}, rows: [
            '................',
            '...ddd.......d..',
            '..dhood.....dhd.',
            '.dosood....doosd',
            '..dood......dod.',
            '...dd........d..',
            '........dd......',
            '.......dhod.....',
            '......dosood....',
            '.......doood....',
            '.dd.....ddd..dd.',
            'dhod........dhod',
            'dood.......doosd',
            '.dd.........ddd.',
            '................',
            '................']},
        uranium: {c: {h: '#d2e85a', o: '#8fb326', d: '#3d5a14', s: '#f0ff8c'}, rows: [
            '................',
            '..d.......dd....',
            '.dhd.....dhod...',
            '.dosd....doosd..',
            '..dd......dd....',
            '......dd........',
            '.....dhod....d..',
            '.....dosod..dhd.',
            '......ddd...dod.',
            '.dd..........d..',
            'dhod.....dd.....',
            'dood....dhod....',
            '.dsd....dosod...',
            '..d......ddd.dd.',
            '.........d..dhod',
            '.............dd.']}
    };

    Object.entries(ORES).forEach(([id, ore]) => {
        block(id + '_ore', () => stone(new Painter(id.length * 97)).grid(ore.rows, ore.c));
        const dark = {h: ore.c.h, o: shade(ore.c.o, 0.92), d: shade(ore.c.d, 0.72), s: ore.c.s};
        block('deepslate_' + id + '_ore', () => deepslate(new Painter(id.length * 131)).grid(ore.rows, dark));
    });

    // ---------------------------------------------------------------- functional blocks

    const STEEL = '#59626c', GUN = '#343a42', DARK = '#1e2228', LIGHT = '#8c97a3';

    /** The shared machine body: a frame with corner rivets, in a colour of its own for each station. */
    function casing(p, accent) {
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        p.inset(2, 2, 12, 12, GUN, 0.03);
        p.rivets(LIGHT, 1);
        if (accent) {
            p.rect(6, 0, 4, 1, accent).rect(6, 15, 4, 1, shade(accent, 0.7)).rect(0, 6, 1, 4, accent).rect(15, 6, 1, 4, shade(accent, 0.7));
        }
        return p;
    }

    function side(accent, seed) {
        return () => {
            const p = casing(new Painter(seed), accent);
            p.bevel(3, 3, 10, 10, shade(STEEL, 0.82), 0.03);
            [5, 7, 9].forEach(y => p.rect(5, y, 6, 1, DARK).rect(5, y + 1, 6, 1, shade(STEEL, 1.05)));
            return p;
        };
    }

    function top(accent, seed, draw) {
        return () => {
            const p = casing(new Painter(seed), accent);
            draw(p);
            return p;
        };
    }

    // Electronics fabricator: green circuits.
    const GREEN = '#35d07f';
    block('electronics_fabricator_side', side(GREEN, 21));
    block('electronics_fabricator_top', top(GREEN, 22, p => {
        p.inset(3, 3, 10, 10, '#1b5b3c', 0.04);
        p.grid([
            '..........',
            '.gg..g.gg.',
            '...g.g....',
            '.g.ggg.gg.',
            '.g.bbbb.g.',
            '.g.bbbb.g.',
            '...bbbb...',
            '.gg..g.gg.',
            '.....g....',
            '..........'], {g: '#e0b84c', b: '#14181d'}, 3, 3);
    }));
    block('electronics_fabricator_front', () => {
        const p = casing(new Painter(23), GREEN);
        p.inset(3, 3, 10, 6, '#0a2318', 0.02);
        p.grid([
            '........',
            '.g..ggg.',
            '.gg...g.',
            '..ggg.g.'], {g: GREEN}, 4, 4);
        p.bevel(3, 10, 6, 3, LIGHT, 0.02).rect(4, 11, 4, 1, DARK);
        p.px('#ff5a4d', [10, 11]).px(GREEN, [12, 11]);
        return p;
    });

    // Assembly station: orange tools and hazard stripes.
    const ORANGE = '#f08a24';
    function hazard(p, x, y, w, h) {
        for (let i = 0; i < w; i++) {
            for (let j = 0; j < h; j++) {
                p.rect(x + i, y + j, 1, 1, (i + j) % 4 < 2 ? '#f2c025' : '#23262b');
            }
        }
    }
    block('assembly_station_side', side(ORANGE, 31));
    block('assembly_station_top', top(ORANGE, 32, p => {
        p.bevel(3, 3, 10, 10, LIGHT, 0.03);
        p.rect(4, 7, 8, 2, GUN).rect(7, 4, 2, 8, GUN).inset(6, 6, 4, 4, DARK);
        p.rect(7, 7, 2, 2, ORANGE);
    }));
    block('assembly_station_front', () => {
        const p = casing(new Painter(33), ORANGE);
        p.inset(3, 3, 10, 7, '#111418', 0.02);
        p.grid([
            '.ooo......',
            '.oOo......',
            '..oo......',
            '...ooo....',
            '.....oo.c.',
            '......oocc',
            '.......o.c'], {o: ORANGE, O: '#ffd18a', c: LIGHT}, 3, 3);
        hazard(p, 3, 11, 10, 2);
        return p;
    });

    // Armor workstation: olive plates and a helmet.
    const OLIVE = '#7b8f3a';
    block('armor_workstation_side', () => {
        const p = casing(new Painter(41), OLIVE);
        p.bevel(3, 3, 10, 10, OLIVE, 0.04);
        p.rect(7, 3, 2, 10, shade(OLIVE, 0.62)).rect(3, 7, 10, 2, shade(OLIVE, 0.62));
        p.px(LIGHT, [5, 5], [10, 5], [5, 10], [10, 10]);
        return p;
    });
    block('armor_workstation_top', top(OLIVE, 42, p => {
        p.bevel(3, 3, 10, 10, GUN, 0.03);
        p.rect(5, 5, 6, 6, shade(OLIVE, 0.85)).rect(5, 5, 6, 1, shade(OLIVE, 1.2)).rect(5, 10, 6, 1, shade(OLIVE, 0.6));
        p.rect(7, 7, 2, 2, '#e5483d');
    }));
    block('armor_workstation_front', () => {
        const p = casing(new Painter(43), OLIVE);
        p.inset(3, 3, 10, 10, '#15181c', 0.02);
        p.grid([
            '..oooooo..',
            '.oHoooooo.',
            '.oooooood.',
            '.orrrrrrd.',
            '.orrrrrrd.',
            '.ooddddod.',
            '..oLLLLd..',
            '..oLLLLd..',
            '...dddd...',
            '..........'], {o: OLIVE, H: '#b7cb6a', d: shade(OLIVE, 0.58), r: '#e5483d', L: LIGHT}, 3, 3);
        return p;
    });

    // Maintenance station: yellow signal and a wrench.
    const YELLOW = '#f0c92f';
    block('maintenance_station_side', side(YELLOW, 51));
    block('maintenance_station_top', top(YELLOW, 52, p => {
        hazard(p, 3, 3, 10, 10);
        p.inset(5, 5, 6, 6, GUN).rect(7, 7, 2, 2, LIGHT);
    }));
    block('maintenance_station_front', () => {
        const p = casing(new Painter(53), YELLOW);
        p.inset(3, 3, 10, 10, '#15181c', 0.02);
        p.grid([
            '.......LL.',
            '......LL..',
            '......LLdL',
            '.....LLLLL',
            '....LLLdd.',
            '...LLLd...',
            '..LLLd....',
            '.LLLd.....',
            '.yyd......',
            '..........'], {L: '#c8d0d8', d: '#7a8590', y: YELLOW}, 3, 3);
        return p;
    });

    // Implant fabricator: a chip with contacts.
    const TEAL = '#35c4b4';
    block('implant_fabricator_side', side(TEAL, 54));
    block('implant_fabricator_top', top(TEAL, 55, p => {
        p.inset(4, 4, 8, 8, GUN).rect(6, 6, 4, 4, TEAL).rect(7, 7, 2, 2, LIGHT);
    }));
    block('implant_fabricator_front', () => {
        const p = casing(new Painter(56), TEAL);
        p.inset(3, 3, 10, 10, '#101518', 0.02);
        p.grid([
            '..c.c.c.c.',
            '.dddddddd.',
            'cdttttttdc',
            '.dtLLLLtd.',
            'cdtLttLtdc',
            '.dtLttLtd.',
            'cdtLLLLtdc',
            '.dttttttd.',
            '.dddddddd.',
            '..c.c.c.c.'], {d: shade(TEAL, 0.45), t: TEAL, L: LIGHT, c: '#c9882e'}, 3, 3);
        return p;
    });

    // Armor upgrade fabricator: a plate under a press.
    const RUST_RED = '#c8553a';
    block('armor_upgrade_fabricator_side', side(RUST_RED, 64));
    block('armor_upgrade_fabricator_top', top(RUST_RED, 65, p => {
        p.inset(4, 4, 8, 8, GUN).rect(5, 5, 6, 6, shade(RUST_RED, 0.8)).rect(5, 5, 6, 1, shade(RUST_RED, 1.2));
    }));
    block('armor_upgrade_fabricator_front', () => {
        const p = casing(new Painter(66), RUST_RED);
        p.inset(3, 3, 10, 10, '#14161a', 0.02);
        p.grid([
            '..LLLLLL..',
            '..LddddL..',
            '....dd....',
            '....dd....',
            '..........',
            '.rrrrrrrr.',
            '.rRRRRRRr.',
            '.rrrrrrrr.',
            '.dddddddd.',
            '..........'], {L: '#c8d0d8', d: '#7a8590', r: RUST_RED, R: '#ef8a6e'}, 3, 3);
        return p;
    });

    // Body modification station: the outline of a body with a marked place on it.
    // Weapon workshop: a rifle on a bench.
    const WORKSHOP = '#6b7a44';
    block('weapon_workshop_side', side(WORKSHOP, 92));
    block('weapon_workshop_top', top(WORKSHOP, 93, p => {
        p.inset(3, 5, 10, 6, GUN).rect(4, 7, 8, 2, DARK).rect(4, 7, 3, 1, LIGHT).rect(11, 6, 1, 4, shade(WORKSHOP, 1.2));
    }));
    block('weapon_workshop_front', () => {
        const p = casing(new Painter(94), WORKSHOP);
        p.inset(3, 3, 10, 10, '#14161a', 0.02);
        p.grid([
            '..........',
            '.....dd...',
            '.LLLrrrrr.',
            '....rrrrr.',
            '....d.dd..',
            '......d...',
            '..........',
            '.aaa.aaa.a',
            '.aaa.aaa.a',
            '..........'], {L: '#c8d0d8', r: '#8c9c5a', d: '#7a8590', a: '#e0a020'}, 3, 3);
        return p;
    });

    const VIOLET = '#9a6ae0';
    block('body_modification_station_side', side(VIOLET, 57));
    block('body_modification_station_top', top(VIOLET, 58, p => {
        p.inset(4, 3, 8, 10, GUN).rect(5, 4, 6, 8, shade(VIOLET, 0.75)).rect(7, 5, 2, 6, LIGHT);
    }));
    block('body_modification_station_front', () => {
        const p = casing(new Painter(59), VIOLET);
        p.inset(3, 3, 10, 10, '#12121a', 0.02);
        p.grid([
            '....LL....',
            '....LL....',
            '...LLLL...',
            '..L.LL.L..',
            '..L.vv.L..',
            '....vv....',
            '....LL....',
            '...L..L...',
            '...L..L...',
            '..........'], {L: '#c8d0d8', v: VIOLET}, 3, 3);
        return p;
    });

    // Combustion generator: firebox and vents.
    const BRICK = '#6a4a3c';
    block('combustion_generator_side', () => {
        const p = casing(new Painter(61), '#c9552b');
        p.inset(3, 3, 10, 10, DARK);
        [4, 6, 8, 10].forEach(y => p.rect(4, y, 8, 1, shade(STEEL, 0.95)));
        return p;
    });
    block('combustion_generator_top', top('#c9552b', 62, p => {
        p.bevel(3, 3, 10, 10, shade(STEEL, 0.9));
        p.inset(5, 5, 6, 6, '#0d0e10');
        p.rect(6, 6, 4, 4, '#2c2f33').rect(7, 7, 2, 2, '#0d0e10');
    }));
    function firebox(p) {
        p.bevel(0, 0, 16, 16, STEEL, 0.03).rivets(LIGHT, 1);
        p.bevel(2, 2, 12, 4, BRICK, 0.08);
        [3, 7, 11].forEach(x => p.rect(x, 3, 2, 1, '#22262b'));
        p.inset(3, 7, 10, 7, '#0b0b0d', 0.02);
        return p;
    }
    block('combustion_generator_front', () => {
        const p = firebox(new Painter(63));
        p.rect(4, 12, 8, 1, '#2a2a2c').rect(5, 11, 2, 1, '#242426').rect(9, 11, 2, 1, '#242426');
        return p;
    });
    const litFirebox = () => firebox(new Painter(63));

    // Solar panel: blue cells; the model is 6 high and shows the lower part of the side.
    block('solar_panel_top', () => {
        const p = new Painter(71);
        p.bevel(0, 0, 16, 16, LIGHT, 0.02);
        for (let y = 0; y < 3; y++) {
            for (let x = 0; x < 3; x++) {
                const cx = 1 + x * 5, cy = 1 + y * 5;
                p.noise(cx, cy, 4, 4, '#1f3f86', 0.08);
                p.rect(cx, cy, 2, 1, '#6f9de6').rect(cx, cy, 1, 2, '#4e7fce');
                p.rect(cx + 3, cy + 3, 1, 1, '#14285a');
            }
        }
        return p;
    });
    block('solar_panel_side', () => {
        const p = new Painter(72);
        p.noise(0, 0, 16, 16, GUN, 0.04);
        p.rect(0, 10, 16, 1, LIGHT);
        p.rect(0, 11, 16, 1, '#3a68c0');
        p.bevel(0, 12, 16, 4, STEEL, 0.03);
        [2, 7, 13].forEach(x => p.px(LIGHT, [x, 13]));
        return p;
    });

    // Battery: the front is the energy output and must stand out.
    block('battery_side', () => {
        const p = casing(new Painter(81), '#37c6e8');
        p.inset(5, 3, 6, 10, '#0c1e26', 0.02);
        p.rect(6, 4, 4, 1, '#ff5a4d').rect(6, 6, 4, 1, YELLOW).rect(6, 8, 4, 1, GREEN).rect(6, 10, 4, 1, GREEN);
        p.bevel(3, 3, 1, 10, LIGHT).bevel(12, 3, 1, 10, LIGHT);
        return p;
    });
    block('battery_front', () => {
        const p = new Painter(82);
        p.bevel(0, 0, 16, 16, '#d8a227', 0.03);
        p.rivets('#fff0b0', 1);
        p.inset(3, 3, 10, 10, '#16191d', 0.02);
        p.bevel(5, 5, 6, 6, '#c9882e', 0.03);
        p.rect(7, 7, 2, 2, '#37e6ff');
        p.px('#fff0b0', [6, 6]);
        return p;
    });

    // Cable: only the middle 8x8 is shown.
    block('cable', () => {
        const p = new Painter(91);
        p.noise(0, 0, 16, 16, '#23272c', 0.05);
        for (let x = 0; x < 16; x += 4) {
            p.rect(x, 4, 2, 8, '#c77b3b').rect(x, 4, 2, 1, '#eaa766').rect(x, 11, 2, 1, '#8a5022');
        }
        p.rect(0, 7, 16, 2, '#2f3338');
        return p;
    });

    // The cable's junction box: only the middle 6x6 is shown.
    block('cable_node', () => {
        const p = new Painter(92);
        p.noise(0, 0, 16, 16, '#3c4249', 0.04);
        p.rect(5, 5, 6, 1, '#6d747b').rect(5, 5, 1, 6, '#6d747b');
        p.rect(5, 10, 6, 1, '#23272c').rect(10, 5, 1, 6, '#23272c');
        p.rect(7, 7, 2, 2, '#c77b3b').px('#eaa766', [7, 7]);
        p.px('#9aa1a8', [6, 6], [9, 6], [6, 9], [9, 9]);
        return p;
    });

    // Faction core: a crystal in a dark frame.
    const CRYSTAL = {c: '#37d6ff', h: '#c9f6ff', d: '#1579a8', k: '#0a2c44'};
    block('faction_core_side', () => {
        const p = new Painter(101);
        p.bevel(0, 0, 16, 16, '#2a2d38', 0.03).rivets('#7f7fa8', 1);
        p.inset(3, 2, 10, 12, '#0c0e16', 0.02);
        p.grid([
            '....kk....',
            '...khdk...',
            '..khccdk..',
            '..khccdk..',
            '.khcccddk.',
            '.khcccddk.',
            '..kccddk..',
            '..kccddk..',
            '...kcdk...',
            '...kcdk...',
            '....kk....',
            '..........'], CRYSTAL, 3, 2);
        return p;
    });
    block('faction_core_top', () => {
        const p = new Painter(102);
        p.bevel(0, 0, 16, 16, '#2a2d38', 0.03).rivets('#7f7fa8', 1);
        p.inset(3, 3, 10, 10, '#0c0e16', 0.02);
        p.grid([
            '....kk....',
            '...kcck...',
            '..kchcck..',
            '.kchhccdk.',
            'kchhccdddk',
            'kcccddddkk',
            '.kccdddk..',
            '..kcddk...',
            '...kdk....',
            '....k.....'], CRYSTAL, 3, 3);
        return p;
    });

    // Transfer capsule.
    const CYAN = '#37e6ff';
    block('transfer_capsule_frame', () => {
        const p = new Painter(111);
        p.noise(0, 0, 16, 16, '#30363e', 0.04);
        p.rect(0, 0, 16, 1, '#56606b').rect(0, 15, 16, 1, '#1a1e23');
        p.rect(0, 0, 1, 16, '#56606b').rect(15, 0, 1, 16, '#1a1e23');
        p.rect(3, 1, 1, 14, CYAN).rect(4, 1, 1, 14, '#148aa6');
        p.rect(7, 2, 5, 12, '#262b31').rect(7, 2, 5, 1, '#454d57');
        [4, 7, 10, 13].forEach(y => p.px('#6d7884', [13, y]));
        return p;
    });
    // Used on both halves of the capsule, so it tiles from top to bottom.
    block('transfer_capsule_back', () => {
        const p = new Painter(112);
        p.noise(0, 0, 16, 16, '#1d2228', 0.04);
        p.rect(2, 0, 12, 16, '#2b3138');
        p.rect(2, 0, 1, 16, '#3d454e').rect(13, 0, 1, 16, '#191d22');
        p.rect(5, 0, 6, 16, '#0d1a22');
        p.rect(7, 0, 2, 16, '#148aa6').rect(7, 0, 1, 16, CYAN);
        p.rect(5, 2, 6, 1, '#1f9fbd').rect(5, 13, 6, 1, '#1f9fbd');
        p.rect(3, 6, 2, 4, '#1a1e23').rect(11, 6, 2, 4, '#1a1e23');
        p.px('#ff5a4d', [3, 7]).px('#35d07f', [12, 7]).px('#6d7884', [3, 8], [12, 8]);
        return p;
    });
    block('transfer_capsule_floor', () => {
        const p = new Painter(113);
        p.bevel(0, 0, 16, 16, '#3a424b', 0.03);
        p.inset(2, 2, 12, 12, '#1a1e23', 0.02);
        for (let i = 3; i < 13; i += 2) {
            p.rect(3, i, 10, 1, '#4a545f');
        }
        p.rect(6, 6, 4, 4, '#117d97').rect(7, 7, 2, 2, CYAN);
        return p;
    });

    // Solid colours of the 3D parts of block models, in 4x4 squares (see build_block_models.js).
    block('machine_parts', () => {
        const p = new Painter(121);
        const colors = ['#8c97a3', '#59626c', '#343a42', '#1e2228', '#c77b3b', '#d6a32a', '#23262b', '#6e4a31',
            '#37e6ff', '#35d07f', '#e5483d', '#f0c92f', '#f08a24', '#7b8f3a', '#e6e9ec', null];
        colors.forEach((c, i) => {
            const x = (i % 4) * 4, y = Math.floor(i / 4) * 4;
            if (c) {
                p.noise(x, y, 4, 4, c, 0.03);
            } else {
                hazard(p, x, y, 4, 4);
            }
        });
        return p;
    });

    // ---------------------------------------------------------------- animated textures of active blocks

    /** A vertical strip of frames with its .mcmeta; the game plays it by itself. */
    function animated(id, frames, frametime, draw) {
        TEXTURES['block/' + id] = () => {
            const strip = document.createElement('canvas');
            strip.width = 16;
            strip.height = 16 * frames;
            const ctx = strip.getContext('2d');
            for (let i = 0; i < frames; i++) {
                ctx.drawImage(draw(i).canvas, 0, i * 16);
            }
            return {canvas: strip, mcmeta: {animation: {frametime, interpolate: false}}};
        };
    }

    const baseOf = id => TEXTURES['block/' + id]();

    /** The side of a running machine: a fan turns in the middle. */
    function fanSide(id, accent) {
        animated(id + '_side_on', 4, 2, i => {
            const p = baseOf(id + '_side');
            p.inset(4, 4, 8, 8, '#14171b', 0.02);
            const blades = [
                ['..xx....', '..xxx...', '...xx.xx', '...ooxxx', 'xxxoo...', 'xx.xx...', '...xxx..', '....xx..'],
                ['.x....x.', 'xxx..xxx', '.xxxxxx.', '...oo...', '...oo...', '.xxxxxx.', 'xxx..xxx', '.x....x.'],
                ['....xx..', '...xxx..', 'xx.xx...', 'xxxoo...', '...ooxxx', '...xx.xx', '..xxx...', '..xx....'],
                ['...xx...', '...xx...', 'x..xx..x', 'xxxooxxx', 'xxxooxxx', 'x..xx..x', '...xx...', '...xx...']];
            p.grid(blades[i], {x: '#7b8794', o: accent}, 4, 4);
            return p;
        });
    }

    function blink(on, color) {
        return on ? color : shade(color, 0.35);
    }

    // Electronics fabricator: code runs on the screen, lights blink, signals run along the traces.
    fanSide('electronics_fabricator', GREEN);
    animated('electronics_fabricator_front_on', 8, 3, i => {
        const p = baseOf('electronics_fabricator_front');
        p.inset(3, 3, 10, 6, '#0a2318', 0.02);
        const code = [[1, 4], [3, 2], [6, 3], [2, 5], [0, 3], [5, 4], [1, 2], [4, 5]];
        for (let row = 0; row < 4; row++) {
            const [x, w] = code[(row + i) % 8];
            p.rect(4 + x, 4 + row, w, 1, row === 3 ? '#b6ffd8' : GREEN);
        }
        p.px(blink(i % 2 === 0, '#ff5a4d'), [10, 11]).px(blink(i % 4 < 2, GREEN), [12, 11]);
        p.rect(4, 11, 4, 1, i % 4 < 2 ? '#1a4a33' : DARK);
        return p;
    });
    animated('electronics_fabricator_top_on', 4, 3, i => {
        const p = baseOf('electronics_fabricator_top');
        const pulse = [[[4, 4], [11, 4]], [[6, 5], [8, 5]], [[4, 7], [11, 7]], [[6, 10], [11, 10]]][i];
        p.px('#fff4b0', ...pulse);
        p.px(GREEN, [7, 8]);
        return p;
    });

    // Assembly station: the arm on the screen works, sparks fly, the hazard stripes run, the turntable turns.
    fanSide('assembly_station', ORANGE);
    animated('assembly_station_front_on', 6, 3, i => {
        const p = baseOf('assembly_station_front');
        p.inset(3, 3, 10, 7, '#111418', 0.02);
        const reach = [0, 1, 2, 2, 1, 0][i];
        p.grid(['ooo', 'oOo', '.oo'], {o: ORANGE, O: '#ffd18a'}, 4, 3);
        p.rect(6, 6, 2 + reach, 1, ORANGE).rect(7 + reach, 7, 2, 1, ORANGE);
        p.rect(9 + reach, 8, 1, 1, LIGHT).rect(8 + reach, 8, 1, 1, LIGHT);
        if (reach === 2) {
            p.px('#fff27a', [11, 8], [12, 7], [10, 9]);
        }
        for (let x = 0; x < 10; x++) {
            for (let y = 0; y < 2; y++) {
                p.rect(3 + x, 11 + y, 1, 1, (x + y + i) % 4 < 2 ? '#f2c025' : '#23262b');
            }
        }
        return p;
    });
    animated('assembly_station_top_on', 4, 2, i => {
        const p = baseOf('assembly_station_top');
        p.inset(4, 4, 8, 8, GUN);
        const arms = [
            ['...xx...', '...xx...', '...xx...', 'xxxooxxx', 'xxxooxxx', '...xx...', '...xx...', '...xx...'],
            ['x......x', '.x....x.', '..x..x..', '...oo...', '...oo...', '..x..x..', '.x....x.', 'x......x']][i % 2];
        p.grid(arms, {x: LIGHT, o: ORANGE}, 4, 4);
        return p;
    });

    // Armor workstation: a scanning line runs over the helmet, its visor glows, the signal lights blink.
    animated('armor_workstation_front_on', 8, 2, i => {
        const p = baseOf('armor_workstation_front');
        const line = i < 5 ? 3 + i * 2 : 3 + (8 - i) * 2;
        p.rect(4, 6, 8, 2, i % 2 ? '#ff7a6e' : '#ff4b3e');
        p.rect(3, line, 10, 1, CYAN);
        return p;
    });
    animated('armor_workstation_top_on', 4, 4, i => {
        const p = baseOf('armor_workstation_top');
        p.rect(7, 7, 2, 2, i % 2 ? '#ff8a7a' : '#a82a22');
        return p;
    });
    animated('armor_workstation_side_on', 4, 4, i => {
        const p = baseOf('armor_workstation_side');
        p.px(blink(i === 0, CYAN), [5, 5]).px(blink(i === 1, CYAN), [10, 5]).px(blink(i === 2, CYAN), [10, 10]).px(blink(i === 3, CYAN), [5, 10]);
        return p;
    });

    // Maintenance station: the wrench turns, the warning stripes run, a warning light blinks.
    fanSide('maintenance_station', YELLOW);
    animated('maintenance_station_front_on', 4, 4, i => {
        const p = baseOf('maintenance_station_front');
        if (i % 2) {
            p.inset(3, 3, 10, 10, '#15181c', 0.02);
            p.grid([
                '.LL.......',
                '..LL......',
                'LdLL......',
                'LLLLL.....',
                '.ddLLL....',
                '...dLLL...',
                '....dLLL..',
                '.....dLLL.',
                '......dyy.',
                '..........'], {L: '#c8d0d8', d: '#7a8590', y: YELLOW}, 3, 3);
        }
        p.rect(3, 3, 10, 1, i < 2 ? YELLOW : shade(YELLOW, 0.4));
        return p;
    });
    animated('maintenance_station_top_on', 4, 2, i => {
        const p = baseOf('maintenance_station_top');
        for (let x = 0; x < 10; x++) {
            for (let y = 0; y < 10; y++) {
                if (x < 2 || x > 7 || y < 2 || y > 7) {
                    p.rect(3 + x, 3 + y, 1, 1, (x + y + i) % 4 < 2 ? '#f2c025' : '#23262b');
                }
            }
        }
        return p;
    });

    // Implant fabricator: the chip's middle pulses and the contacts light in turn.
    fanSide('implant_fabricator', TEAL);
    animated('implant_fabricator_front_on', 4, 3, i => {
        const p = baseOf('implant_fabricator_front');
        p.rect(7, 7, 2, 2, i % 2 ? '#c9fff8' : TEAL);
        p.rect(5 + i * 2, 3, 1, 1, '#fff0b0').rect(5 + i * 2, 12, 1, 1, '#fff0b0');
        return p;
    });
    animated('implant_fabricator_top_on', 4, 3, i => {
        const p = baseOf('implant_fabricator_top');
        p.rect(7, 7, 2, 2, i % 2 ? TEAL : '#c9fff8');
        return p;
    });

    // Armor upgrade fabricator: the press goes down and up, the plate glows when it is hit.
    fanSide('armor_upgrade_fabricator', RUST_RED);
    animated('armor_upgrade_fabricator_front_on', 4, 3, i => {
        const p = baseOf('armor_upgrade_fabricator_front');
        const down = [0, 1, 2, 1][i];
        p.rect(3, 3, 10, 5, '#14161a');
        p.rect(5, 3 + down, 6, 2, '#c8d0d8').rect(7, 5 + down, 2, 2, '#7a8590');
        p.rect(5, 9, 6, 1, i === 2 ? '#ffd9a0' : '#ef8a6e');
        return p;
    });
    animated('armor_upgrade_fabricator_top_on', 4, 3, i => {
        const p = baseOf('armor_upgrade_fabricator_top');
        p.rect(7, 7, 2, 2, i % 2 ? '#ffd9a0' : RUST_RED);
        return p;
    });

    // Weapon workshop: a tracer runs along the barrel, and rounds fill up one by one.
    fanSide('weapon_workshop', WORKSHOP);
    animated('weapon_workshop_front_on', 6, 2, i => {
        const p = baseOf('weapon_workshop_front');
        p.rect(4 + i, 5, 1, 1, '#ffe9a0');
        for (let n = 0; n <= i && n < 5; n++) {
            p.rect(4 + n * 2, 10, 1, 2, '#ffd070');
        }
        return p;
    });
    animated('weapon_workshop_top_on', 4, 3, i => {
        const p = baseOf('weapon_workshop_top');
        p.rect(5 + i, 7, 1, 1, i % 2 ? '#ffe9a0' : '#ffd070');
        return p;
    });

    // Body modification station: a scanning line runs down the body.
    fanSide('body_modification_station', VIOLET);
    animated('body_modification_station_front_on', 8, 2, i => {
        const p = baseOf('body_modification_station_front');
        p.rect(3, 3 + (i < 5 ? i * 2 : (8 - i) * 2), 10, 1, CYAN);
        return p;
    });
    animated('body_modification_station_top_on', 4, 3, i => {
        const p = baseOf('body_modification_station_top');
        p.rect(7, 5 + i, 2, 2, CYAN);
        return p;
    });

    // Combustion generator: the fire moves, the vents and the exhaust glow.
    const FIRE = [
        ['....r..r', '.r..rr.r', '.rr.orrr', 'rrorooor', 'roooyooo', 'ooyyyyyo'],
        ['.r....r.', '.rr.r.rr', 'rror.rro', 'rooorooy', 'ooyooyyo', 'oyyyyyyo'],
        ['...r.r..', 'r..rrr.r', 'rr.oorrr', 'roooyoor', 'ooyyyooo', 'oyyyyyyy'],
        ['r.....r.', 'rr.r..r.', 'orrrr.rr', 'ooorrooo', 'oyooooyo', 'yyyoyyyo'],
        ['..r..r..', '.rr.rr.r', 'rrorroor', 'oooooyor', 'yooyyooo', 'oyyyyyyo'],
        ['.r.r...r', '.rrrr.rr', 'rooorrro', 'ooyooooo', 'oyyyyooy', 'yyyyyyoo']];
    animated('combustion_generator_front_on', 6, 2, i => litFirebox().grid(FIRE[i], {r: '#d8421b', o: '#f58a1f', y: '#ffe05a'}, 4, 8));
    animated('combustion_generator_side_on', 4, 3, i => {
        const p = baseOf('combustion_generator_side');
        [4, 6, 8, 10].forEach((y, row) => p.rect(4, y, 8, 1, mix('#59626c', '#f58a1f', (row + i) % 4 === 3 ? 0.9 : 0.35)));
        return p;
    });
    animated('combustion_generator_top_on', 4, 3, i => {
        const p = baseOf('combustion_generator_top');
        p.rect(7, 7, 2, 2, ['#f58a1f', '#ffb347', '#d8421b', '#ffb347'][i]);
        return p;
    });

    // Solar panel: a shine runs across the cells.
    animated('solar_panel_top_on', 8, 3, i => {
        const p = baseOf('solar_panel_top');
        for (let y = 0; y < 16; y++) {
            for (let x = 0; x < 16; x++) {
                const onCell = x % 5 !== 0 && y % 5 !== 0;
                if (onCell && (x + y) % 16 >= i * 4 && (x + y) % 16 < i * 4 + 3 && i < 4) {
                    p.rect(x, y, 1, 1, '#a9d0ff');
                }
            }
        }
        return p;
    });

    // Battery: the core pulses and the charge bar runs while energy flows.
    animated('battery_front_on', 6, 2, i => {
        const p = baseOf('battery_front');
        const glow = ['#1d8fa8', '#37e6ff', '#a6f6ff', '#ffffff', '#a6f6ff', '#37e6ff'][i];
        p.rect(6, 6, 4, 4, shade(glow, 0.7)).rect(7, 7, 2, 2, glow);
        return p;
    });
    animated('battery_side_on', 5, 3, i => {
        const p = baseOf('battery_side');
        p.inset(5, 3, 6, 10, '#0c1e26', 0.02);
        const colors = [GREEN, GREEN, YELLOW, '#ff5a4d'];
        [10, 8, 6, 4].forEach((y, row) => {
            if (row <= i) {
                p.rect(6, y, 4, 1, colors[row]);
            }
        });
        return p;
    });

    // Always working: the faction core's crystal and the capsule's light line.
    const coreSide = TEXTURES['block/faction_core_side'], coreTop = TEXTURES['block/faction_core_top'];
    const pulse = (p, f, x, y, w, h) => {
        const data = p.ctx.getImageData(x, y, w, h);
        for (let k = 0; k < data.data.length; k += 4) {
            if (data.data[k + 2] > 120) {
                data.data[k] = Math.min(255, data.data[k] * f);
                data.data[k + 1] = Math.min(255, data.data[k + 1] * f);
                data.data[k + 2] = Math.min(255, data.data[k + 2] * f);
            }
        }
        p.ctx.putImageData(data, x, y);
        return p;
    };
    const PULSE = [0.65, 0.75, 0.9, 1.1, 1.25, 1.1, 0.9, 0.75];
    animated('faction_core_side', 8, 4, i => pulse(coreSide(), PULSE[i], 3, 2, 10, 12));
    animated('faction_core_top', 8, 4, i => pulse(coreTop(), PULSE[i], 3, 3, 10, 10));
    const capsuleBack = TEXTURES['block/transfer_capsule_back'];
    animated('transfer_capsule_back', 8, 3, i => {
        const p = capsuleBack();
        p.rect(7, 0, 2, 16, '#148aa6');
        for (let y = 0; y < 16; y++) {
            if ((y + i * 2) % 16 < 3) {
                p.rect(7, y, 2, 1, '#d8fbff');
            }
        }
        return p;
    });

    // ---------------------------------------------------------------- items: metals

    const INGOT = [
        '................',
        '................',
        '................',
        '........eeee....',
        '......eeaaabe...',
        '....eeaaabbbbe..',
        '..eeaaabbbbbcde.',
        '.eabbbbbbbccdde.',
        '.ecabbbbccdddde.',
        '.eccaabcdddddee.',
        '.edcccddddeee...',
        '..eddddeee......',
        '...eeee.........',
        '................',
        '................',
        '................'];
    const RAW = [
        '................',
        '................',
        '......eee.......',
        '....eeaabee.....',
        '...eabbbccdee...',
        '..eabbsbcccdde..',
        '..eabbbcccsdde..',
        '.eabbccsccdddde.',
        '.ebbcccccddddse.',
        '.ecbcsccdddddde.',
        '.edcccddddsddee.',
        '..eddddddddee...',
        '...eedddeee.....',
        '.....eee........',
        '................',
        '................'];

    const METALS = {
        tin: ['#eef3f4', '#c6d0d3', '#9aa7ab', '#6c787d', '#3a4246'],
        aluminum: ['#ffffff', '#e0dde3', '#b9b5bf', '#8a8691', '#4c4a54'],
        silver: ['#ffffff', '#e6f0fa', '#bccfe2', '#8499b0', '#3f4d5e'],
        lithium: ['#fde3f3', '#eaa6d6', '#c974b8', '#8e4b8b', '#4b2550'],
        titanium: ['#c5dfec', '#86adc4', '#5b8199', '#395668', '#1c2c37'],
        uranium: ['#e6f2a0', '#b9d24a', '#8ba52c', '#58701a', '#2a380c'],
        bronze: ['#ffe2a8', '#e6ab5c', '#bc7d36', '#865223', '#4a2b11']
    };

    Object.entries(METALS).forEach(([id, [a, b, c, d, e]]) => {
        item(id + '_ingot', () => icon(INGOT, {a, b, c, d, e}));
        if (id !== 'bronze') {
            // The raw lump is the metal in rough stone.
            const stoneShadow = mix(d, '#5a5a5a', 0.5);
            item('raw_' + id, () => icon(RAW, {a: mix(a, '#b0b0b0', 0.25), b: mix(b, '#8e8e8e', 0.35), c: mix(c, '#6f6f6f', 0.45),
                d: stoneShadow, e: mix(e, '#222222', 0.4), s: a}));
        }
    });

    // ---------------------------------------------------------------- items: parts

    item('circuit_board', () => icon([
        '................',
        '................',
        '.kkkkkkkkkkkkkk.',
        '.kGgggggggggggk.',
        '.kgyygg.yyyyggk.',
        '.kgggyg.gggyggk.',
        '.kgbbbbbbggyyyk.',
        '.kyybbbbbbgggyk.',
        '.kgybbbbbbyyggk.',
        '.kgybbbbbbggygk.',
        '.kgyyggyggggydk.',
        '.kggggyyyyggddk.',
        '.kdddddddddddkk.',
        '..kkkkkkkkkkkk..',
        '................',
        '................'].map(r => r.replace(/\./g, (m, i) => (i > 0 && i < 15 && r[i - 1] !== '.' && r[i + 1] !== '.') ? 'g' : '.')),
        {k: '#0e2f1d', G: '#3fbf73', g: '#238a50', d: '#176239', y: '#e6c052', b: '#14181d'}));

    item('processor', () => icon([
        '................',
        '...n.n.n.n.n....',
        '...n.n.n.n.n....',
        '..kkkkkkkkkkkk..',
        'nnkHHHHHHHHHmknn',
        '..kHsssssssdmk..',
        'nnkHsCCCCCsdmknn',
        '..kHsCcccCsdmk..',
        'nnkHsCcccCsdmknn',
        '..kHsCCCCCsdmk..',
        'nnkHsssssssdmknn',
        '..kmmmmmmmmmmk..',
        '..kkkkkkkkkkkk..',
        '...n.n.n.n.n....',
        '...n.n.n.n.n....',
        '................'], {k: '#14171c', H: '#59626c', s: '#343a42', d: '#262b31', m: '#23272c', C: '#149cc0', c: '#7df0ff', n: '#e0b84c'}));

    item('power_cell', () => icon([
        '................',
        '.....kkkkkk.....',
        '.....kLLLdk.....',
        '....kkkkkkkk....',
        '....kHssssdk....',
        '....kscccsdk....',
        '....kscCcsdk....',
        '....kscCcsdk....',
        '....kCCCCCdk....'.replace('kCCCCCdk', 'kCCWCCdk'),
        '....kCCWCCdk....',
        '....kCWWWCdk....',
        '....kCCWCCdk....',
        '....kCCCCCdk....',
        '....kHssssdk....',
        '....kkkkkkkk....',
        '................'].map(r => r.length === 16 ? r : r.padEnd(16, '.').slice(0, 16)),
        {k: '#14181d', H: '#8c97a3', s: '#59626c', d: '#343a42', L: '#e0b84c', c: '#148a6a', C: '#35e0a0', W: '#e7fff5'}));

    item('actuator', () => icon([
        '................',
        '..........kkk...',
        '.........kLLLk..',
        '.........kLkLk..',
        '.........kLLLk..',
        '........kCCkk...',
        '.......kCCk.....',
        '......kCCk......',
        '.....kssk.......',
        '....kHssdk......',
        '...kHssdk.......',
        '..kHssdk.yy.....',
        '.kHssdkkyyy.....',
        '.kssdkoyyy......',
        '.kkkk.oo........',
        '................'], {k: '#16191e', L: '#c8d0d8', C: '#e8eef4', H: '#a6b1bc', s: '#6a7581', d: '#3a4149', y: '#f08a24', o: '#a85510'}));

    item('synthetic_fiber', () => icon([
        '................',
        '.......kkk......',
        '.....kkWWWkk....',
        '....kWWccWWck...',
        '...kWccWWccWck..',
        '...kcWWccWWcck..',
        '..kWWccWWccWWck.',
        '..kccWWccWWccdk.',
        '..kWWccWWccWddk.',
        '..kccWWccWWcddk.',
        '...kWccWWccddk..',
        '...kcWWccWddck..',
        '....kccWWddck...',
        '.....kkdddkk....',
        '.......kkk......',
        '................'], {k: '#3d5a68', W: '#eafcff', c: '#a8dcea', d: '#6fa6b8'}));

    item('armor_plate', () => icon([
        '................',
        '..kkkkkkkkkkkk..',
        '.kHHHHHHHHHHHmk.',
        '.kHoooooooooomk.',
        '.kHoLoooooLoomk.',
        '.kHoooooooooomk.',
        '.kHooHHHHHoodmk.',
        '.kHooHooomodmmk.',
        '.kHooHooomodmmk.',
        '.kHoommmmmoodmk.',
        '.kHoooooooodmmk.',
        '.kHoLoooooLdmmk.',
        '.kmdddddddddmmk.',
        '..kmmmmmmmmmmk..',
        '...kkkkkkkkkk...',
        '................'], {k: '#20262b', H: '#b9c7cf', o: '#7f8f99', d: '#5d6b74', m: '#3f4a52', L: '#e6eef2'}));

    // ---------------------------------------------------------------- items: tools

    item('repair_wrench', () => icon([
        '................',
        '............kk..',
        '..........kkLLk.',
        '.........kLLLk..',
        '.........kLLk...',
        '.........kLLk.kk',
        '........kLLLkkLk',
        '.......kLLLLLLLk',
        '......kLLLdddLk.',
        '.....kLLdkkkkk..',
        '....kLLdk.......',
        '...kyydk........',
        '..kyyok.........',
        '.kyyok..........',
        '.kook...........',
        '..kk............'], {k: '#1a1d22', L: '#d6dee6', d: '#8a95a0', y: '#f0c92f', o: '#b58a14'}));

    item('rescue_tool', () => icon([
        '................',
        '..........kkkk..',
        '.........kLLLLk.',
        '........kkkkLLk.',
        '............kLk.',
        '...........kLLk.',
        '..........kLLk..',
        '.........kLdk...',
        '........krrk....',
        '.......krRrk....',
        '......krRrk.....',
        '.....krRrk......',
        '....krRrk.......',
        '...kkkkk........',
        '..kLLk..........',
        '..kkk...........'], {k: '#1c1416', L: '#d6dee6', d: '#8a95a0', r: '#d6352c', R: '#ff7a6e'}));

    item('repair_kit', () => icon([
        '................',
        '......kkkk......',
        '.....kLkkLk.....',
        '..kkkkkkkkkkkk..',
        '.kHHHHHHHHHHHmk.',
        '.kHssssssssssmk.',
        '.kHsssyyyysssmk.',
        '.kkkkkyyyykkkkk.',
        '.kHsssykkysssmk.',
        '.kHsssyyyysssmk.',
        '.kHssssssssssmk.',
        '.kHssrsssssrsmk.',
        '.kmmmmmmmmmmmmk.',
        '..kkkkkkkkkkkk..',
        '................',
        '................'], {k: '#16191e', L: '#8c97a3', H: '#8791a0', s: '#59626c', m: '#343a42', y: '#f0c92f', r: '#e5483d'}));

    item('transfer_capsule', () => icon([
        '.....kkkkkk.....',
        '....kHHHssmk....',
        '...kHsssssmmk...',
        '...kscCCCCcmk...',
        '...kscCWCCcmk...',
        '...kscCWCCcmk...',
        '...kscCCCCcmk...',
        '...kscCCCCcmk...',
        '...kscCCCCcmk...',
        '...kscCCCCcmk...',
        '...kscCCCCcmk...',
        '...kscccccсmk...'.replace('с', 'c'),
        '...kHsssssmmk...',
        '..kkkkkkkkkkkk..',
        '..kHHyyyyyyssk..',
        '..kkkkkkkkkkkk..'], {k: '#14171c', H: '#8c97a3', s: '#59626c', m: '#343a42', c: '#117d97', C: '#37d6f0', W: '#d8fbff', y: '#37e6ff'}));

    // ---------------------------------------------------------------- items: bodies (head and shoulders of the model)

    item('basic_robot_body', () => icon([
        '........r.......',
        '........k.......',
        '.....kkkkkkk....',
        '....kHHggggsk...',
        '....kHgggsssk...',
        '....kaaaaaaak...',
        '....kggsssmmk...',
        '....kgsnnnsmk...',
        '.....kkkkkkk....',
        '...kkkssssskkk..',
        '..kHgkgsssmkmmk.',
        '..kggksGGsmkmmk.',
        '..kggksGGsmkmmk.',
        '..kkkkuuuuukkkk.',
        '.....kuuuuuk....',
        '.....kkkkkkk....'], {r: '#e5483d', k: '#191c20', H: '#a6adb4', g: '#7d858d', s: '#5e656d', m: '#42484f', a: '#f2a530', n: '#23272c', G: '#35d07f', u: '#8a5a32'}));

    item('improved_robot_body', () => icon([
        '.......bbb......',
        '.....kkbbbkk....',
        '....kWWbbbggk...',
        '....kWWWgggsk...',
        '....kcccccccK...'.replace('K', 'k'),
        '....kcCccCcck...'.replace('cCccCcc', 'CCkkCCk'),
        '....kWggggssk...',
        '....kgnnnnnsk...',
        '.....kkkkkkk....',
        '.kkkkkbbbbbkkkk.',
        'kBbbkWggggskbbbk',
        'kbbdkgCCCCskbddk',
        'kbbdkgCWWCskbddk',
        '.kkkkgCCCCskkkk.',
        '....kkbbbbkk....',
        '.....kkkkkk.....'], {k: '#141a24', W: '#f2f5f8', g: '#c5ccd4', s: '#8e97a2', b: '#2a78d6', B: '#6aa8f0', d: '#17508f', c: '#0f2a33', C: '#4ff0ff', n: '#23272c'}));

    item('advanced_synthetic_body', () => icon([
        '.....kkkkkk.....',
        '....kSSSSSSk....',
        '....kPsPPPPk....'.replace('PsPPPP', 'SSSSSS').replace('kSSSSSSk', 'ksPPPPdk'),
        '....kPPPPPPk....'.replace('PPPPPP', 'PPPPPs'),
        '....kCCPPCCk....',
        '....kPPssPdk....',
        '....kPPssPdk....',
        '....kPPPPddk....',
        '.....kkkkkk.....',
        '...kkkSSSSkkk...',
        '..kPkGGGGGGkPk..',
        '..kPkGDCCDGkPk..',
        '..kdkGDDDDGkdk..',
        '..kkkGDGGDGkkk..',
        '.....kGDDGk.....',
        '.....kkkkkk.....'], {k: '#22262b', S: '#a59f96', s: '#b7b1a8', P: '#d9d4cc', d: '#a59f96', C: '#37e6ff', G: '#59616b', D: '#333941'}));

    item('hightech_synthetic_body', () => icon([
        '.....kkkkkk.....',
        '....kHHHHHHk....',
        '....kHAAAAAk....',
        '....kAPPPPAk....',
        '....kCCCCCCk....',
        '....kPPssPAk....',
        '....kPPssPdk....',
        '....kPPPPddk....',
        '.....kkkkkk.....',
        '...kkkDDDDkkk...',
        '.kDDkCDDDDCkDDk.',
        '.kDCkDCCCCDkCDk.',
        '.kDCkCDDCDDkCDk.',
        '.kkkkDDCCDDkkkk.',
        '.....kDDDDk.....',
        '.....kkkkkk.....'], {k: '#0e1116', H: '#e4e9ef', A: '#b4bcc6', P: '#c7b8aa', s: '#a3948a', d: '#a3948a', C: '#3df2ff', D: '#20242c'}));

    item('premium_synthetic_body', () => icon([
        '.....kkkkkk.....',
        '....kBBBBBBk....',
        '....kBHHHBBk....',
        '....kBPPBPBk....',
        '....kWEPPWEk....',
        '....kPPssPdk....',
        '....kPPMMPdk....',
        '.....kPPPdk.....',
        '...kkkkkkkkkk...',
        '..kWWGkKKkGWWk..',
        '..kWsGPKKPGsWk..',
        '..kWsGWKKWGsWk..',
        '..kkkGWWWWGkkk..',
        '.....GWWWWG.....',
        '.....kGGGGk.....',
        '.....kkkkkk.....'], {k: '#1f1712', B: '#4a2f1f', H: '#6e4a31', P: '#e0b48f', d: '#c4946e', s: '#c9997a', M: '#b5655a', W: '#f2f0ea', E: '#2d5fa8', G: '#d6a32a', K: '#2a2c33'}));

    // ---------------------------------------------------------------- items: power armor (the whole suit, front view)

    item('power_armor', () => icon([
        '......kkkk......',
        '......kmmk......',
        '......krrk......',
        '.kkkkkkmmkkkkkk.',
        'kHooookkkkkHoodk',
        'kooodkHoooOkoodk',
        'kggggkooOOOkgggk',
        '.kggkoomOOdkggk.',
        '.kookggggggkook.',
        '.kookgyGyGgkook.',
        '.kkkkkkggkkkkkk.',
        '.kmmk.koook.kmk.',
        '.....kooOkook...'.replace('kooOkook', 'kookkook'),
        '.....kggkkggk...',
        '.....kmmkkmmk...',
        '.....kkkkkkkk...'].map(r => r.padEnd(16, '.').slice(0, 16)),
        {k: '#15181b', H: '#b7cb6a', o: '#7b8f3a', O: '#94a94a', d: '#566629', g: '#4a525b', G: '#22262b', m: '#343a42', r: '#ff4b3e', y: '#f0c030'}));

    item('mobile_power_armor', () => icon([
        '......kkkk......',
        '......kWbk......',
        '......kCCk......',
        'b...kkkWWkkk...b',
        'bb.kBbkkkkbbk.bb',
        '.bbkbbkWWkbdkbb.',
        '..bkWWkCCkWgkb..',
        '...kWgkbbkWgk...',
        '...kbdkkkkbdk...',
        '...kkkkgbkkkk...'.replace('kkkkgbkkkk', 'kkkkbbkkkk'),
        '....kkbbbbkk....',
        '.....kWkkWk.....',
        '.....kWkkgk.....',
        '.....kbkkbk.....',
        '.....kBkkBk.....',
        '.....kkkkkk.....'], {k: '#111722', W: '#f2f5f8', g: '#c0c8d0', b: '#2a78d6', B: '#6aa8f0', d: '#17508f', C: '#4ff0ff'}));

    item('worker_power_armor', () => icon([
        '.....kkkkkk.....',
        '.kkk.kyyyyk.kkk.',
        'kOOOkkkkkkkkOOOk',
        'kOooyyyyyyyyooOk',
        'kooky.CCCCc.kook'.replace(/\./g, 'y'),
        'kdokyCWCCCcykodk',
        'kyykyCCCCCcykyyk',
        'kyykyccccccykyyk',
        'kyykoooooooDkyyk'.replace('D', 'o'),
        'kgggkhnhnhnkgggk',
        'kgggkkkkkkkkgggk',
        '.kkkkyykkyykkkk.',
        '....kyhkkyhk....',
        '....kyykkyyk....',
        '...kggggkgggk...'.replace('kggggkgggk', 'kgggkkgggk'),
        '...kkkkkkkkkk...'], {k: '#15161a', y: '#e8b818', O: '#ff9a3c', o: '#e8701a', d: '#a84c0e', C: '#7fd6e6', W: '#e5fbff', c: '#3f8ea0', g: '#55585e', h: '#f0c929', n: '#23262b'}));

    // ---------------------------------------------------------------- items: ability slot icons (light outlines, as the owner asked)

    const OUTLINE = {w: '#f2f6fa', s: '#6f7b86'};

    item('combat_armor_ability', () => icon([
        '................',
        '............www.',
        '...........wsws.',
        '..........wsswk.'.replace('k', 's'),
        '.........wsswss.',
        '........wsswss..',
        '.......wsswss...',
        '......wsswss....',
        '.ww..wsswss.....',
        '.wsw.wswss......',
        '.ssw.wwss.......',
        '..ssw.ss........',
        '..w.ssw.........',
        '.www.ssw........',
        '.www..sw........',
        '......ss........'].map(r => r), OUTLINE));

    item('mobile_armor_ability', () => icon([
        '................',
        '................',
        '.w..............',
        '.wwww..wwwwww...',
        '.sswwwwwsssws...',
        '..ssswwws..ws...',
        '...wwwsws..ws...',
        '...ssswws..ws...',
        '.....sws...ws...',
        '......ws...wwww.',
        '......ws......ww',
        '......ws.......w',
        '......wwwwwwwwww',
        '......ssssssssss',
        '................',
        '................'], OUTLINE));

    item('worker_armor_ability', () => icon([
        '................',
        '.....wwwwwww....',
        '...wwssssssswws.',
        '..wwsws...wwswws'.slice(0, 16),
        '.wws.ss...wsswsw'.slice(0, 16),
        '.ws......wss.wws'.slice(0, 16),
        '.s......wss...ws',
        '.......wss.....w',
        '......wss......s',
        '.....wss........',
        '....wss.........',
        '...wss..........',
        '..wss...........',
        '.wss............',
        '.ss.............',
        '................'], OUTLINE));

    // Upgrades of bodies and armors: gadgets are small, armor upgrades are heavy.
    const PLATE_ROWS = [
        '................',
        '................',
        '...kkkkkkkkkk...',
        '..kHHHHHHHHHHk..',
        '..kHmmmmmmmmdk..',
        '..kHmHmmmmHmdk..',
        '..kHmmmmmmmmdk..',
        '..kHmmmmmmmmdk..',
        '..kHmmmmmmmmdk..',
        '...kHmmmmmmdk...',
        '...kHmmmmmmdk...',
        '....kHmmmmdk....',
        '.....kHmmdk.....',
        '......kkkk......',
        '................',
        '................'];
    const ARMOR_PLATE_ROWS = [
        '................',
        '.kkkkkkkkkkkkkk.',
        '.kHHHHHHHHHHHdk.',
        '.kHmHmmmmmmHmdk.',
        '.kHmmmmmmmmmmdk.',
        '.kHmmmmmmmmmmdk.',
        '.kkkkkkkkkkkkkk.',
        '.kHHHHHHHHHHHdk.',
        '.kHmHmmmmmmHmdk.',
        '.kHmmmmmmmmmmdk.',
        '.kHmmmmmmmmmmdk.',
        '.kkkkkkkkkkkkkk.',
        '.kHmHmmmmmmHmdk.',
        '.kdddddddddddddk'.slice(0, 15) + '.',
        '.kkkkkkkkkkkkkk.',
        '................'];
    [['copper', '#f0a877', '#c9743f', '#8f4c22'], ['iron', '#eef1f4', '#b9bfc6', '#7d858c'], ['titanium', '#c4d6ef', '#8199bd', '#55698a']].forEach(([id, H, m, d]) => {
        item(id + '_plating', () => icon(PLATE_ROWS, {k: '#14181d', H, m, d}));
        item('armor_' + id + '_plating', () => icon(ARMOR_PLATE_ROWS, {k: '#14181d', H, m, d}));
    });
    // A full face mask with lenses, a valve and two filters (1.3.0).
    item('sealing_module', () => icon([
        '................',
        '.....kkkkkk.....',
        '....kHHHHHHk....',
        '...kHsssssssk...',
        '...kHsccsccsk...',
        '...kHsccsccsk...',
        '...kHssssssdk...',
        '.kkkkHsbbsdkkkk.',
        'kyyykHbggbdkyyyk',
        'kyYykHbggbdkyYyk',
        'kyYykkbggbkkyYyk',
        'kyyyk.kbbk.kyyyk',
        '.kkk...kk...kkk.',
        '................',
        '................',
        '................'], {k: '#14181d', H: '#b9c2cc', s: '#6d7680', d: '#3a4149', b: '#20262e', c: '#55c8ff', g: '#0b0e12', y: '#e0b33a', Y: '#8a6a1a'}));
    item('jetpack', () => icon([
        '................',
        '...kkkk..kkkk...',
        '..kHssdkkHssdk..',
        '..kHssdkkHssdk..',
        '..kHrrdkkHrrdk..',
        '..kHssdkkHssdk..',
        '..kHssdkkHssdk..',
        '..kHssdkkHssdk..',
        '..kHssdkkHssdk..',
        '..kkkkkkkkkkkk..',
        '...kddk..kddk...',
        '...kddk..kddk...',
        '....oo....oo....',
        '...oyyo..oyyo...',
        '....oo....oo....',
        '................'], {k: '#14181d', H: '#b9c2cc', s: '#6d7680', d: '#3a4149', r: '#e5483d', o: '#ff8a24', y: '#ffe27a'}));
    item('compressed_air_module', () => icon([
        '................',
        '......kkkk......',
        '......kCCk......',
        '.....kkkkkk.....',
        '....kHHHHHdk....',
        '....kHssssdk....',
        '....kHrrrrdk....',
        '....kHssssdk....',
        '....kHssssdk....',
        '....kHrrrrdk....',
        '....kHssssdk....',
        '....kHssssdk....',
        '....kHssssdk....',
        '....kddddddk....',
        '.....kkkkkk.....',
        '................'], {k: '#14181d', C: '#c8d0d8', H: '#c9d2da', s: '#7d8996', d: '#3a4149', r: '#c9352b'}));
    item('floodlight', () => icon([
        '................',
        '..kkkkkkkkk.....',
        '.kHsssssssdk....',
        '.kHLLLLLLLdk.y..',
        '.kHLWWWWWLdk..y.',
        '.kHLWWWWWLdk....',
        '.kHLWWWWWLdk.yy.',
        '.kHLWWWWWLdk....',
        '.kHLLLLLLLdk..y.',
        '.kHsssssssdk.y..',
        '..kkkkkkkkk.....',
        '.....kdk........',
        '.....kdk........',
        '...kkkkkkk......',
        '...kddddddk.....'.slice(0, 16),
        '................'], {k: '#14181d', H: '#b9c2cc', s: '#6d7680', d: '#3a4149', L: '#ffe9a0', W: '#fffbe0', y: '#fff2b0'}));
    const WEAPON_PALETTE = {k: '#14181d', g: '#4c5359', d: '#25282c', s: '#8b9299', o: '#6b7a44', O: '#8c9c5a', a: '#e0a020', c: '#7fe8ff', r: '#c9352b', R: '#e8604f', b: '#d6a83c', B: '#f2cf6d', n: '#9a6b2f', u: '#b8693a'};

    item('assault_rifle', () => icon([
        '................',
        '................',
        '................',
        '................',
        '.........a......',
        '.......kkkkkk...',
        '.....kkggggggkkk',
        'ssskOoogsggggkgk',
        '....koogggggkkgk',
        '.....kkkkkdkkkkk',
        '......kddkkdk...',
        '......kddk.kdk..',
        '.......kdk..kk..',
        '.......kk.......',
        '................',
        '................'], WEAPON_PALETTE));

    item('battle_rifle', () => icon([
        '................',
        '................',
        '................',
        '.......kkkkkkk..',
        '.......cddddddk.',
        '.......kkkkkkk..',
        '.........k..k...',
        'sssssskOOooooggk',
        '.....kkoooooogkk',
        '......kkkkdkkkgk',
        '.........kddk.kk',
        '.........kddk...',
        '..........kk....',
        '................',
        '................',
        '................'], WEAPON_PALETTE));

    item('combat_shotgun', () => icon([
        '................',
        '................',
        '................',
        '................',
        '..a.........k...',
        'kkkkkkkkkkkkkk..',
        'kggggggggggggkk.',
        'kkkkkkkkkgdddkgk',
        '.kdddddddkdddkgk',
        '.kkOOOOokkkakkgk',
        '...kooookkddkkkk',
        '....kkkk.kddk...',
        '.........kddk...',
        '..........kk....',
        '................',
        '................'], WEAPON_PALETTE));

    item('assault_rifle_ammo', () => icon([
        '................',
        '................',
        '................',
        '....u...u...u...',
        '...kuk.kuk.kuk..',
        '...kBk.kBk.kBk..',
        '...kbk.kbk.kbk..',
        '...kbk.kbk.kbk..',
        '...kbk.kbk.kbk..',
        '...kbk.kbk.kbk..',
        '...knk.knk.knk..',
        '...kkk.kkk.kkk..',
        '................',
        '................',
        '................',
        '................'], WEAPON_PALETTE));

    item('battle_rifle_ammo', () => icon([
        '................',
        '.......ss.......',
        '......kssk......',
        '......kssk......',
        '......kuuk......',
        '......kBbk......',
        '.....kkBbkk.....',
        '.....kBBbnk.....',
        '.....kBBbnk.....',
        '.....kBBbnk.....',
        '.....kBBbnk.....',
        '.....kBBbnk.....',
        '.....kBBbnk.....',
        '.....kkkkkk.....',
        '.....knnnnk.....',
        '.....kkkkkk.....'], WEAPON_PALETTE));

    item('shotgun_shell', () => icon([
        '................',
        '................',
        '.....kkkkkk.....',
        '....kRrrrrrk....',
        '....kRrrrrrk....',
        '....kRrrkrrk....',
        '....kRrrrrrk....',
        '....kRrrrrrk....',
        '....kRrrrrrk....',
        '....kRrrrrrk....',
        '....kkkkkkkk....',
        '....kBbbbbbk....',
        '...kBBbbbbnnk...',
        '...kkkkkkkkkk...',
        '................',
        '................'], WEAPON_PALETTE));

    const SYMPTOM_PALETTE = {k: '#14181d', g: '#8fd14f', G: '#d2f58a', y: '#d9c63a', Y: '#f2e88a', d: '#3f5a22'};
    TEXTURES['mob_effect/radiation_sickness'] = () => icon([
        '................',
        '.....kkkkkk.....',
        '...kkdggggdkk...',
        '..kdggGGGGggdk..',
        '..kgggkkkkgggk..',
        '.kgGgkkggkkgGgk.',
        '.kgGgkgGGgkgGgk.',
        '.kggGkgGGgkGggk.',
        '.kgggkkggkkgggk.',
        '.kdgggkkkkgggdk.',
        '..kdgggggggggk..',
        '..kkdggGGggdkk..',
        '....kkddddkk....',
        '......kkkk......',
        '................',
        '................'], SYMPTOM_PALETTE);
    TEXTURES['mob_effect/toxic_poisoning'] = () => icon([
        '................',
        '......kkkk......',
        '......kYYk......',
        '......kYYk......',
        '.....kyYYyk.....',
        '....kyyYYyyk....',
        '...kyyyyyyyyk...',
        '...kyykkkkyyk...',
        '..kyyyk.kkyyyk..',
        '..kyyykkkyyyyk..',
        '..kyyyyyyykyyk..',
        '..kyyyykkkkyyk..',
        '...kyyyyyyyyk...',
        '....kkkkkkkk....',
        '................',
        '................'], SYMPTOM_PALETTE);

    item('upgrade_mounting_tool', () => icon([
        '................',
        '...........kkk..',
        '..........kHHdk.',
        '.........kHssdk.',
        '.........kHskk..',
        '........kHsk.c..',
        '.......kHsk..c..',
        '......kHsk..cc..',
        '.....kkkk.......',
        '....kyyyk.......',
        '...kyyyk........',
        '..kyyyk.........',
        '..kyyk..........',
        '..kkk...........',
        '................',
        '................'], {k: '#14181d', H: '#c9d2da', s: '#7d8996', d: '#3a4149', y: '#e8b420', c: '#55c8ff'}));

    // ---------------------------------------------------------------- window and HUD parts

    // One 256x256 texture; client/GuiAtlas.java knows where each part is. The panel stays light: window text is dark.
    // ---------------------------------------------------------------- protective gear (1.3.0)

    const HAZMAT = {k: '#14181d', H: '#ffe58a', m: '#e0b33a', d: '#9a7420', v: '#7fd6e8', w: '#e6fbff', a: '#2b3038', g: '#0b0e12', z: '#6b5a1a', b: '#23272d'};
    const HEAVY = {k: '#14181d', H: '#8a9a5a', m: '#5f6b3e', d: '#3a4226', v: '#1d2a2e', w: '#e0a030', a: '#454b52', g: '#22262b', z: '#3a4226', b: '#2c3020'};
    const GEAR_ICONS = {
        helmet: [
            '................',
            '....kkkkkkkk....',
            '...kHmmmmmmdk...',
            '..kHmmmmmmmmdk..',
            '..kmmvvvvvvmdk..',
            '..kmvwvvvvvvdk..',
            '..kmvvvvvvvvdk..',
            '..kmmvvvvvvmdk..',
            '..kmmmaaaammdk..',
            '..kmmaaggaamdk..',
            '..kdmmaaaammdk..',
            '...kddmmmmddk...',
            '....kkkkkkkk....',
            '................',
            '................',
            '................'],
        chest: [
            '................',
            '..kkk......kkk..',
            '.kHmmk....kmmdk.',
            'kHmmmmkkkkmmmmdk',
            'kmmmmmmzmmmmmmdk',
            'kmmkmmmzmmmmkmdk',
            'kmmkmmmzmaamkmdk',
            'kddkmmmzmaamkddk',
            'kggkmmmzmmmmkggk',
            '.kkkmmmzmmmmkkk.',
            '...kbbbbbbbbk...',
            '...kmmmzmmmdk...',
            '...kmmmzmmmdk...',
            '...kddddddddk...',
            '...kkkkkkkkkk...',
            '................'],
        legs: [
            '................',
            '................',
            '...kkkkkkkkkk...',
            '...kbbbbbbbbk...',
            '...kHmmmmmmdk...',
            '...kmmmmmmmdk...',
            '...kmmmkkmmdk...',
            '...kmmmkkmmdk...',
            '...kmmk..kmdk...',
            '...kaak..kaak...',
            '...kmmk..kmdk...',
            '...kmmk..kmdk...',
            '...kmmk..kmdk...',
            '...kddk..kddk...',
            '...kkkk..kkkk...',
            '................'],
        boots: [
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '...kkkk..kkkk...',
            '...kHmk..kHmk...',
            '...kmmk..kmmk...',
            '...kmmk..kmmk...',
            '...kmmk..kmmk...',
            '..kmmmk.kmmmk...',
            '.kHmmmk.kHmmmk..',
            '.kddddk.kddddk..',
            '.kkkkkk.kkkkkk..',
            '................']
    };
    const RUBBER = {H: '#555c66', m: '#30353c', d: '#1c2026'};
    item('hazmat_helmet', () => icon(GEAR_ICONS.helmet, HAZMAT));
    item('hazmat_suit', () => icon(GEAR_ICONS.chest, HAZMAT));
    item('hazmat_trousers', () => icon(GEAR_ICONS.legs, Object.assign({}, HAZMAT, {a: '#c9962a'})));
    item('hazmat_boots', () => icon(GEAR_ICONS.boots, Object.assign({}, HAZMAT, RUBBER)));
    item('heavy_suit_helmet', () => icon(GEAR_ICONS.helmet, Object.assign({}, HEAVY, {a: '#2b3038', g: '#e0a030'})));
    item('heavy_suit', () => icon(GEAR_ICONS.chest, Object.assign({}, HEAVY, {a: '#e0c040'})));
    item('heavy_suit_trousers', () => icon(GEAR_ICONS.legs, Object.assign({}, HEAVY, {a: '#8a9a5a'})));
    item('heavy_suit_boots', () => icon(GEAR_ICONS.boots, Object.assign({}, HEAVY, {H: '#4a4f3a', m: '#2c3020', d: '#16180f'})));
    item('respirator', () => icon([
        '................',
        '................',
        '................',
        '.ss..........ss.',
        '..ss........ss..',
        '...sskkkkkkss...',
        '....kHmmmmdk....',
        '...kHmmmmmmdk...',
        '..kyykmggmkyyk..',
        '..kyYkmggmkYyk..',
        '..kyykmmmmkyyk..',
        '...kkkdmmdkkk...',
        '......kkkk......',
        '................',
        '................',
        '................'], {k: '#14181d', s: '#3a3f46', H: '#6d7680', m: '#3a4149', d: '#20262e', g: '#0b0e12', y: '#e0b33a', Y: '#8a6a1a'}));
    item('filter', () => icon([
        '................',
        '................',
        '.....kkkkkk.....',
        '....kHHHHHdk....',
        '....kgggggdk....',
        '...kkkkkkkkkk...',
        '...kYyyyyyydk...',
        '...kyhyhyhydk...',
        '...kyyyyyyydk...',
        '...kyhyhyhydk...',
        '...kyyyyyyydk...',
        '...kyhyhyhydk...',
        '...kddddddddk...',
        '...kkkkkkkkkk...',
        '................',
        '................'], {k: '#14181d', H: '#d8dde3', g: '#6d7680', Y: '#ffe58a', y: '#e0b33a', h: '#3a3020', d: '#8a6a1a'}));
    item('dosimeter', () => icon([
        '................',
        '................',
        '....kkkkkkkk....',
        '...kHHHHHHHHk...',
        '...kHkkkkkkdk...',
        '...kHkggggkdk...',
        '...kHkgGggkdk...',
        '...kHkkkkkkdk...',
        '...kmmmmmmmdk...',
        '...kmrmmmymdk...',
        '...kmmmmmmmdk...',
        '...kmssssssdk...',
        '...kmmmmmmmdk...',
        '...kddddddddk...',
        '....kkkkkkkk....',
        '................'], {k: '#14181d', H: '#e8d76a', m: '#d4b82e', d: '#8a7418', g: '#3fbf5a', G: '#b8ffbf', r: '#d84a3a', y: '#ffe58a', s: '#5a4a10'}));
    const bottle = (symbol, palette) => icon([
        '................',
        '......kkkk......',
        '......kCCk......',
        '......kkkk......',
        '......kwwk......',
        '.....kwwwwk.....',
        '....kwllllwk....',
        '....kllllllk....',
        symbol[0], symbol[1], symbol[2],
        '....kWWWWWWk....',
        '....kllllllk....',
        '.....kkkkkk.....',
        '................',
        '................'], Object.assign({k: '#14181d', w: '#cfe9ee', W: '#f2f4f5'}, palette));
    // Antiradin: orange liquid, a black radiation mark on the label.
    item('antiradin', () => bottle(['....kWyWWyWk....', '....kWWyyWWk....', '....kWWWyWWk....'], {C: '#d84a3a', l: '#f0a830', y: '#14181d'}));
    // Sorbent: black charcoal liquid, a green cross on the label.
    item('sorbent', () => bottle(['....kWWyyWWk....', '....kWyyyyWk....', '....kWWyyWWk....'], {C: '#4caf50', l: '#3a3f46', y: '#3d9a42'}));

    // Worn gear (respirator and suits) is painted with its 3D model by blockbench/tools/build_gear.js since 1.5.1.

    TEXTURES['gui/widgets'] = () => {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = 256;
        const ctx = canvas.getContext('2d');
        const rand = random(777);
        const rect = (x, y, w, h, c) => { ctx.fillStyle = c; ctx.fillRect(x, y, w, h); };
        const noise = (x, y, w, h, c, amount) => {
            for (let i = 0; i < w; i++) {
                for (let j = 0; j < h; j++) {
                    rect(x + i, y + j, 1, 1, shade(c, 1 + (rand() - 0.5) * 2 * amount));
                }
            }
        };
        const frame = (x, y, w, h, topLeft, bottomRight) => {
            rect(x, y, w, 1, topLeft);
            rect(x, y, 1, h, topLeft);
            rect(x, y + h - 1, w, 1, bottomRight);
            rect(x + w - 1, y, 1, h, bottomRight);
        };

        // Panel, 32x32 with corners of 8: brushed steel, a dark edge, a coloured line along the edge, riveted corners.
        noise(0, 0, 32, 32, '#c4c9cd', 0.012);
        rect(0, 0, 32, 1, '#2e343a'); rect(0, 31, 32, 1, '#2e343a'); rect(0, 0, 1, 32, '#2e343a'); rect(31, 0, 1, 32, '#2e343a');
        frame(1, 1, 30, 30, '#eef1f3', '#858c93');
        rect(2, 2, 28, 1, '#7fb4c6');
        for (const [cx, cy] of [[0, 0], [24, 0], [0, 24], [24, 24]]) {
            rect(cx + 1, cy + 1, 6, 6, '#7d858c');
            frame(cx + 1, cy + 1, 6, 6, '#aab1b7', '#4a5158');
            rect(cx + 3, cy + 3, 2, 2, '#39c2e0');
            rect(cx + 3, cy + 3, 1, 1, '#b8f2ff');
        }

        // Slot, 18x18.
        rect(32, 0, 18, 18, '#6f767d');
        frame(32, 0, 18, 18, '#1d2126', '#b4bac0');
        rect(33, 1, 16, 1, '#555b62');
        rect(33, 1, 1, 16, '#555b62');

        // Output slot, 26x26: the same with coloured corners.
        rect(52, 0, 26, 26, '#6f767d');
        frame(52, 0, 26, 26, '#1d2126', '#b4bac0');
        rect(53, 1, 24, 1, '#555b62');
        rect(53, 1, 1, 24, '#555b62');
        for (const [cx, cy, dx, dy] of [[54, 2, 1, 1], [75, 2, -1, 1], [54, 23, 1, -1], [75, 23, -1, -1]]) {
            for (let i = 0; i < 3; i++) {
                rect(cx + dx * i, cy, 1, 1, '#39c2e0');
                rect(cx, cy + dy * i, 1, 1, '#39c2e0');
            }
        }

        // Recess of any size, 12x12 with corners of 3.
        rect(80, 0, 12, 12, '#6f767d');
        frame(80, 0, 12, 12, '#1d2126', '#b4bac0');
        rect(81, 1, 10, 1, '#555b62');
        rect(81, 1, 1, 10, '#555b62');

        // Bar frame, 9x9 with corners of 2.
        rect(96, 0, 9, 9, '#2b3036');
        frame(96, 0, 9, 9, '#0f1215', '#0f1215');

        // Arrows, 24x16: empty and full.
        const arrow = (y, shaft, head, edge) => {
            rect(0, y + 6, 15, 4, shaft);
            rect(0, y + 6, 15, 1, edge);
            for (let i = 0; i < 8; i++) {
                rect(15 + i, y + i, 1, 16 - 2 * i, head);
                rect(15 + i, y + i, 1, 1, edge);
            }
        };
        arrow(34, '#8f969c', '#8f969c', '#6d747b');
        arrow(52, '#3fc8e6', '#3fc8e6', '#b8f2ff');

        // Small arrow, 16x11: the inventory's crafting arrow.
        rect(60, 38, 10, 3, '#8f969c');
        for (let i = 0; i < 6; i++) {
            rect(70 + i, 34 + i, 1, 11 - 2 * i, '#8f969c');
        }

        // The mark of a sealed body, 9x9: a drop in a dark frame.
        rect(80, 34, 9, 9, '#14242c');
        frame(80, 34, 9, 9, '#0f1215', '#0f1215');
        [[84, 36, 1], [84, 37, 1], [83, 38, 3], [82, 39, 5], [82, 40, 5], [83, 41, 3]].forEach(([x, y, w]) => rect(x, y, w, 1, '#55c8ff'));
        rect(83, 39, 1, 1, '#c9f0ff');

        // The marks of the hazards, 9x9 each (1.3.0): polluted air, radiation, acid.
        const mark = (ox, rows, palette) => rows.forEach((row, y) => [...row].forEach((ch, x) => {
            if (ch !== '.') {
                rect(ox + x, 34 + y, 1, 1, palette[ch]);
            }
        }));
        rect(92, 34, 9, 9, '#1a1d12'); rect(102, 34, 9, 9, '#1a1d12'); rect(112, 34, 9, 9, '#122016');
        mark(92, ['.........', '...gg....', '..gggg.g.', '.ggGgggg.', 'ggggggggg', '.ggggggg.', '.........', '.d..d..d.', '.........'], {g: '#d4e157', G: '#f0ff9a', d: '#9aa83a'});
        mark(102, ['.........', '.yy...yy.', '.yyy.yyy.', '..yy.yy..', '....y....', '...yyy...', '..yyyyy..', '...yyy...', '.........'], {y: '#f0d030'});
        mark(112, ['....a....', '....a....', '...aaa...', '..aaaaa..', '..aaHaa..', '..aaaaa..', '...aaa...', '.........', '.........'], {a: '#9ccc65', H: '#e6ffcc'});

        // Energy, a piece of the gauge: upright 8x4 and lying 4x8.
        rect(30, 34, 8, 3, '#ff8a3c'); rect(30, 34, 8, 1, '#ffc27a'); rect(30, 37, 8, 1, '#a8461c');
        rect(40, 34, 3, 8, '#ff8a3c'); rect(40, 34, 3, 1, '#ffc27a'); rect(43, 34, 1, 8, '#a8461c');

        // Status bar, a piece 8x7: light grey, tinted in the code; the last column is a notch.
        const greys = ['#ffffff', '#f2f2f2', '#e4e4e4', '#d8d8d8', '#cccccc', '#bfbfbf', '#a8a8a8'];
        greys.forEach((c, i) => rect(50, 34 + i, 8, 1, c));
        rect(57, 34, 1, 7, '#6f6f6f');

        // 1.5.0, block G. The marks of the dust and the EMI hazards, 9x9, next to the other hazards.
        rect(122, 34, 9, 9, '#211b12'); rect(132, 34, 9, 9, '#1c1426');
        mark(122, ['.........', '.ss..s...', '..sssss..', 's..sS.ss.', '.sssssss.', '..s.sss.s', '.sss..ss.', '...s.s...', '.........'], {s: '#d7b377', S: '#f3dcae'});
        mark(132, ['.........', '.....v...', '....vv...', '...vv....', '..vvvvv..', '....vv...', '...vv....', '...v.....', '.........'], {v: '#ce93d8'});

        // The alarms of a body, 9x9 on a dark ground (1.5.0): no charge, overheated, critical wear, interference;
        // and the heat and cold of a human.
        const alarm = (ox, ground, rows, palette) => {
            rect(ox, 46, 9, 9, ground);
            frame(ox, 46, 9, 9, '#0f1215', '#0f1215');
            rows.forEach((row, y) => [...row].forEach((ch, x) => {
                if (ch !== '.') {
                    rect(ox + x, 46 + y, 1, 1, palette[ch]);
                }
            }));
        };
        // an empty battery with a red bolt
        alarm(80, '#2a1414', ['.........', '...ww....', '..wwwww..', '..w..rw..', '..w.r.w..', '..wr..w..', '..wwwww..', '.........', '.........'], {w: '#cfd8dc', r: '#ff5252'});
        // a flame
        alarm(90, '#2a1a10', ['.........', '....o....', '...oo....', '...ooo.o.', '..ooyoo..', '..oyyyo..', '..oyyyo..', '...ooo...', '.........'], {o: '#ff6d00', y: '#ffd740'});
        // a broken gear
        alarm(100, '#24160f', ['.........', '...g.g...', '..ggggg..', '.gg.r.gg.', '..g.r.g..', '.gg.r.gg.', '..ggggg..', '...g.g...', '.........'], {g: '#bcaaa4', r: '#ff5252'});
        // static: a broken wave
        alarm(110, '#1c1426', ['.........', '.........', '.v...v...', 'v.v.v.v.v', '...v...v.', '.........', '.w.w..w..', '.........', '.........'], {v: '#ce93d8', w: '#f3e5f5'});
        // a sun
        alarm(120, '#2a1a10', ['.........', '....y....', '.y.....y.', '...ooo...', '.y.ooo.y.', '...ooo...', '.y.....y.', '....y....', '.........'], {o: '#ff7043', y: '#ffd740'});
        // a snowflake
        alarm(130, '#10202a', ['.........', '....b....', '.b..b..b.', '..b.b.b..', '.bbbwbbb.', '..b.b.b..', '.b..b..b.', '....b....', '.........'], {b: '#4fc3f7', w: '#e1f5fe'});

        // The small marks of the body's thin bars, 5x5 (1.5.0): charge (a bolt), heat (a thermometer), wear (a gear).
        const small = (ox, rows, palette) => rows.forEach((row, y) => [...row].forEach((ch, x) => {
            if (ch !== '.') {
                rect(ox + x, 56 + y, 1, 1, palette[ch]);
            }
        }));
        small(80, ['..yy.', '.yy..', 'yyyyy', '..yy.', '.yy..'], {y: '#ffd54f'});
        small(86, ['.ww..', '.ww..', '.wr..', 'rrrr.', '.rr..'], {w: '#cfd8dc', r: '#ff7043'});
        small(92, ['g.g.g', '.ggg.', 'gg.gg', '.ggg.', 'g.g.g'], {g: '#bcaaa4'});
        return {canvas};
    };

    // ---------------------------------------------------------------- shelter (version 1.4.0)

    const FILTER_TAN = '#c8b77a', ALARM = '#ff4a3d';
    const FAN_BLADES = [
        ['..xx....', '..xxx...', '...xx.xx', '...ooxxx', 'xxxoo...', 'xx.xx...', '...xxx..', '....xx..'],
        ['.x....x.', 'xxx..xxx', '.xxxxxx.', '...oo...', '...oo...', '.xxxxxx.', 'xxx..xxx', '.x....x.'],
        ['....xx..', '...xxx..', 'xx.xx...', 'xxxoo...', '...ooxxx', '...xx.xx', '..xxx...', '..xx....'],
        ['...xx...', '...xx...', 'x..xx..x', 'xxxooxxx', 'xxxooxxx', 'x..xx..x', '...xx...', '...xx...']];

    /** Air filtration unit: a big fan behind a round grille, yellow marks. */
    function filtrationFront(frame) {
        const p = casing(new Painter(141), YELLOW);
        p.inset(2, 2, 12, 12, '#14171b', 0.02);
        p.rect(3, 2, 10, 1, '#2a3036').rect(2, 3, 1, 10, '#2a3036');
        p.grid(FAN_BLADES[frame], {x: '#8c97a3', o: YELLOW}, 4, 4);
        // the grille in front of the fan
        [3, 6, 9, 12].forEach(y => p.rect(3, y, 10, 1, shade(STEEL, 0.9)));
        p.px(LIGHT, [3, 3], [12, 3], [3, 12], [12, 12]);
        return p;
    }
    block('air_filtration_unit_front', () => filtrationFront(0));
    animated('air_filtration_unit_front_on', 4, 1, i => filtrationFront(i));
    block('air_filtration_unit_side', () => {
        const p = casing(new Painter(142), YELLOW);
        // a window onto the filter cartridge inside
        p.inset(4, 3, 8, 10, '#0c1e26', 0.02);
        for (let x = 5; x < 11; x++) {
            p.rect(x, 4, 1, 8, x % 2 ? FILTER_TAN : shade(FILTER_TAN, 1.2));
        }
        p.rect(5, 4, 6, 1, shade(FILTER_TAN, 0.7)).rect(5, 11, 6, 1, shade(FILTER_TAN, 0.7));
        p.bevel(3, 13, 10, 1, LIGHT, 0.02);
        return p;
    });
    block('air_filtration_unit_top', () => {
        const p = casing(new Painter(143), YELLOW);
        p.bevel(3, 3, 10, 10, shade(STEEL, 0.9), 0.03);
        [4, 6, 8, 10].forEach(y => p.rect(4, y, 8, 1, DARK).rect(4, y + 1, 8, 1, shade(STEEL, 1.1)));
        p.px(YELLOW, [7, 12], [8, 12]);
        return p;
    });


    // ---------------------------------------------------------------- polluted water (version 1.6.0, block C)

    const MURK = '#6b7a3c', MURK_DARK = '#4f5a2b', MURK_FOAM = '#a7b456', CLEAN = '#3f9fd8', CLEAN_LIGHT = '#8fd3f5';
    /** Water purifier: two glass tanks behind the front, the murky one on the left, the clean one on the right, a filter between. */
    function purifierFront(frame) {
        const p = casing(new Painter(161), '#37c6e8');
        p.inset(2, 2, 12, 12, '#14171b', 0.02);
        // the tanks
        p.inset(3, 3, 4, 10, MURK, 0.06).inset(9, 3, 4, 10, CLEAN, 0.04);
        p.rect(3, 3, 4, 1, MURK_DARK).rect(9, 3, 4, 1, shade(CLEAN, 0.8));
        // the filter between them, a tan cartridge in a frame
        p.rect(7, 3, 2, 10, '#2a3036');
        for (let y = 4; y < 12; y += 2) p.rect(7, y, 2, 1, FILTER_TAN);
        // bubbles rising in the murky tank and in the clean one
        const rise = y => 3 + ((y - frame * 2) % 10 + 10) % 10;
        p.px(MURK_FOAM, [4, rise(11)], [5, rise(7)], [4, rise(4)]);
        p.px(CLEAN_LIGHT, [10, rise(9)], [11, rise(5)], [10, rise(2)]);
        // glass shine
        p.px(LIGHT, [3, 4], [9, 4], [3, 5], [9, 5]);
        return p;
    }
    block('water_purifier_front', () => purifierFront(0));
    animated('water_purifier_front_on', 5, 2, i => purifierFront(i));
    block('water_purifier_side', () => {
        const p = casing(new Painter(162), '#37c6e8');
        // a window onto the cartridge, and a gauge of the clean water
        p.inset(4, 3, 8, 7, '#0c1e26', 0.02);
        for (let x = 5; x < 11; x++) p.rect(x, 4, 1, 5, x % 2 ? FILTER_TAN : shade(FILTER_TAN, 1.2));
        p.bevel(4, 11, 8, 2, '#0c1e26', 0.02);
        p.rect(5, 11, 4, 1, CLEAN).rect(9, 11, 2, 1, shade(CLEAN, 0.5));
        return p;
    });
    block('water_purifier_top', () => {
        const p = casing(new Painter(163), '#37c6e8');
        // the inlet: a round hatch with water under a grille
        p.bevel(4, 4, 8, 8, shade(STEEL, 0.9), 0.03);
        p.inset(5, 5, 6, 6, MURK, 0.05);
        [6, 8].forEach(x => p.rect(x, 5, 1, 6, DARK));
        [6, 8].forEach(y => p.rect(5, y, 6, 1, DARK));
        p.px('#37c6e8', [7, 13], [8, 13]);
        return p;
    });
    // ---------------------------------------------------------------- weather station (version 1.6.0, block D)

    const SKY_BLUE = '#4fa3ff', RADAR_DARK = '#08170f', RADAR_RING = '#1d4f30', RADAR_SWEEP = '#5dff9a', STORM_BLIP = '#ffb13d';
    /** Weather station: a round radar screen with rings, a sweeping beam and the blip of a storm coming. */
    function stationFront(frame, on) {
        const p = casing(new Painter(171), SKY_BLUE);
        p.inset(2, 2, 12, 12, RADAR_DARK, 0.02);
        const cx = 7.5, cy = 7.5;
        for (let y = 3; y < 13; y++) {
            for (let x = 3; x < 13; x++) {
                const d = Math.hypot(x - cx, y - cy);
                if (Math.abs(d - 4.6) < 0.5 || Math.abs(d - 2.2) < 0.45) p.px(RADAR_RING, [x, y]);
            }
        }
        p.px(RADAR_RING, [7, 3], [8, 3], [7, 12], [8, 12], [3, 7], [3, 8], [12, 7], [12, 8]);
        if (on) {
            const angle = frame * Math.PI / 4 - Math.PI / 2;
            for (let r = 1; r <= 5; r++) {
                p.px(RADAR_SWEEP, [Math.round(cx + Math.cos(angle) * r), Math.round(cy + Math.sin(angle) * r)]);
            }
            p.px(STORM_BLIP, [10, 5], [11, 5], [10, 4]);
        }
        p.px(LIGHT, [7, 7], [8, 8]);
        return p;
    }
    block('weather_station_front', () => stationFront(0, false));
    animated('weather_station_front_on', 8, 2, i => stationFront(i, true));
    block('weather_station_side', () => {
        const p = casing(new Painter(172), SKY_BLUE);
        // a barometer dial and a strip of louvres
        p.bevel(4, 3, 8, 8, '#d8dde2', 0.03);
        p.inset(5, 4, 6, 6, '#eef2f5', 0.02);
        p.px('#c0392b', [8, 6], [9, 5]);
        p.px(DARK, [7, 7]);
        for (let y = 12; y < 14; y++) p.rect(4, y, 8, 1, y % 2 ? GUN : STEEL);
        return p;
    });
    block('weather_station_top', () => {
        const p = casing(new Painter(173), SKY_BLUE);
        p.bevel(5, 5, 6, 6, STEEL, 0.03);
        p.rect(7, 7, 2, 2, DARK);
        return p;
    });
    // The long range forecast module: a board with a processor and a small dish antenna.
    item('long_range_forecast_module', () => icon([
        '................',
        '..........WWW...',
        '.........WwwwW..',
        '.........WwbwW..',
        '..........WwwW..',
        '...........kk...',
        '.kkkkkkkkkkkkkk.',
        '.kggggggggggggk.',
        '.kgyygPPPPgyygk.',
        '.kgggggPpPPggggk',
        '.kgyygPPPPgyygk.',
        '.kggggggggggggk.',
        '.kgcgcgcgcgcgck.',
        '.kkkkkkkkkkkkkk.',
        '................',
        '................'].map(r => r.slice(0, 16).padEnd(16, '.')), {k: '#1b2a1f', g: '#2f7a46', y: '#d9b44a', P: '#2b2f36', p: '#6f7884',
        c: '#c58a3a', W: '#d8dde2', w: '#eef2f5', b: SKY_BLUE}));

    // ---------------------------------------------------------------- monitoring station (version 1.6.0, block E)

    const MAP_GROUND = '#1c2328', MAP_RAD = '#e6d23a', MAP_TOX = '#5fbf3a', MAP_LINE = '#9fe8ff';
    /** Monitoring station: a screen with a map of chunks, yellow and green blots, and a scan line running down it. */
    function monitoringFront(frame, on) {
        const p = casing(new Painter(181), '#5fbf3a');
        p.inset(2, 2, 12, 12, MAP_GROUND, 0.02);
        const blots = [[4, 4, MAP_RAD], [5, 4, MAP_RAD], [4, 5, shade(MAP_RAD, 0.7)], [9, 9, MAP_TOX], [10, 9, MAP_TOX], [10, 10, shade(MAP_TOX, 0.7)],
            [9, 10, shade(MAP_TOX, 0.6)], [11, 5, shade(MAP_RAD, 0.5)], [6, 11, shade(MAP_TOX, 0.5)]];
        blots.forEach(([x, y, c]) => p.px(on ? c : shade(c, 0.5), [x, y]));
        for (let i = 3; i < 13; i += 3) {
            p.rect(i, 3, 1, 10, '#2a333a');
            p.rect(3, i, 10, 1, '#2a333a');
        }
        p.px('#ffffff', [7, 7]);
        if (on) p.rect(3, 3 + frame, 10, 1, MAP_LINE);
        return p;
    }
    block('monitoring_station_front', () => monitoringFront(0, false));
    animated('monitoring_station_front_on', 10, 2, i => monitoringFront(i, true));
    block('monitoring_station_side', () => {
        const p = casing(new Painter(182), '#5fbf3a');
        // the sample intake grille and a gauge
        p.inset(4, 3, 8, 6, '#14181c', 0.02);
        for (let y = 4; y < 8; y += 2) p.rect(5, y, 6, 1, STEEL);
        p.bevel(4, 10, 8, 3, '#14181c', 0.02);
        p.rect(5, 11, 3, 1, MAP_RAD).rect(8, 11, 2, 1, MAP_TOX);
        return p;
    });
    block('monitoring_station_top', () => {
        const p = casing(new Painter(183), '#5fbf3a');
        p.bevel(4, 4, 8, 8, STEEL, 0.03);
        p.inset(6, 6, 4, 4, DARK, 0.02);
        return p;
    });
    // A printed contamination map: a sheet with a grid, yellow and green blots and the station's white mark.
    item('contamination_map', () => icon([
        '................',
        '..pppppppppppp..',
        '..pPPPPPPPPPPp..',
        '..pPyyPPgPPPPp..',
        '..pPyYPPPPPPPp..',
        '..pPPPPlPPPPPp..',
        '..pPPPPPPPggPp..',
        '..pPPPwPPPgGPp..',
        '..pPPPPPPPPPPp..',
        '..pPgPPPPyPPPp..',
        '..pPggPPPPPPPp..',
        '..pPPPPPPPPPPp..',
        '..pPPPPlPPPPPp..',
        '..pppppppppppp..',
        '................',
        '................'], {p: '#b9ad8f', P: '#e8e0c8', y: '#e6d23a', Y: '#b8a42a', g: '#5fbf3a', G: '#3f8f2a', w: '#2b2f36', l: '#c9c0a6'}));

    // A bottle of polluted water: the game's glass bottle with murky green-brown water and a cork.
    item('polluted_water_bottle', () => icon([
        '................',
        '.......cc.......',
        '......kcck......',
        '......kwwk......',
        '......kwwk......',
        '.....kwwwwk.....',
        '....kwmmmmwk....',
        '...kwmmgmmmwk...',
        '...kmmmmmMmmk...',
        '...kmMmmmmmmk...',
        '...kmmmmgmMmk...',
        '...kmmgmmmmmk...',
        '....kmmmMmmk....',
        '.....kkkkkk.....',
        '................',
        '................'], {k: '#2b3236', c: '#8a6a44', w: '#cfe0e0', m: MURK, M: MURK_DARK, g: MURK_FOAM}));
    // ---------------------------------------------------------------- scouting in the field (version 1.6.0, block F)

    // The field analyzer: a hand-held box with a screen showing a grid of yellow and green, a probe on top and a grip.
    item('field_analyzer', () => icon([
        '..........ss....',
        '..........sS....',
        '....kkkkkkkSk...',
        '...kmmmmmmmmmk..',
        '...kmddddddddmk.',
        '...kmdgdyYdddmk.',
        '...kmddddydgdmk.',
        '...kmdgGdddddmk.',
        '...kmddddddyYmk.',
        '...kmddddddddmk.',
        '...kmmmmmmmmmmk.',
        '...kmrmbmmmmmmk.',
        '....kmmmmmmmmk..',
        '.....kGGGGGGk...',
        '.....kGGGGGGk...',
        '......kkkkkk....'], {k: '#1e2228', m: '#59626c', d: '#14181c', g: '#3f8f2a', G: '#343a42', y: '#b8a42a', Y: '#e6d23a',
        s: '#8c97a3', S: '#c3c8cd', r: '#ff4a3d', b: '#37c6e8'}));
    // The sensor module of bodies: a small board with a dish antenna and a green lens.
    item('sensor_module', () => icon([
        '................',
        '.........WWW....',
        '........WwwwW...',
        '........WwbwW...',
        '.........WwW....',
        '..........k.....',
        '..kkkkkkkkkkkk..',
        '..kgggggggggk...',
        '..kgPPPgLLgggk..',
        '..kgPpPgLlggk...',
        '..kgPPPgLLgggk..',
        '..kgggggggggk...',
        '..kgcgcgcgcgk...',
        '..kkkkkkkkkkkk..',
        '................',
        '................'].map(r => r.padEnd(16, '.').slice(0, 16)), {k: '#1b2a1f', g: '#2f7a46', P: '#2b2f36', p: '#6f7884', L: '#5fbf3a', l: '#b6f28a',
        c: '#c58a3a', W: '#d8dde2', w: '#eef2f5', b: '#37c6e8'}));
    // The sensor unit of power armor: a helmet-mounted block with two lenses and a short antenna.
    item('sensor_unit', () => icon([
        '................',
        '...........s....',
        '...........s....',
        '..........sss...',
        '...kkkkkkkkkkk..',
        '..kmmmmmmmmmmmk.',
        '..kmLLmmmmLLmmk.',
        '..kLllLmmLllLmk.',
        '..kLllLmmLllLmk.',
        '..kmLLmmmmLLmmk.',
        '..kmmmmmmmmmmmk.',
        '..kGGGGGGGGGGGk.',
        '..kGyGyGyGyGyGk.',
        '...kkkkkkkkkkk..',
        '................',
        '................'], {k: '#1e2228', m: '#59626c', G: '#343a42', L: '#2f7a46', l: '#5fbf3a', s: '#8c97a3', y: '#e6d23a'}));

    // ---------------------------------------------------------------- multiblocks (version 1.7.0, block A)

    const PORT_YELLOW = '#e0b33a', PORT_GREY = '#59626c';
    // The energy port: a steel casing with a copper socket and a yellow lightning bolt, the same on every side.
    block('energy_port', () => {
        const p = casing(new Painter(191), PORT_YELLOW);
        p.bevel(4, 4, 8, 8, '#2a3036', 0.02);
        p.inset(5, 5, 6, 6, '#b06a3a', 0.04);
        p.px('#ffd54f', [9, 5], [8, 6], [7, 7], [8, 7], [9, 7], [8, 8], [7, 9], [6, 10]);
        return p;
    });
    // The cargo port: a hatch with a grille, hazard stripes round it; the top and bottom with a hopper mouth.
    block('cargo_port', () => {
        const p = casing(new Painter(192), '#37c6e8');
        p.inset(3, 3, 10, 10, '#1e2228', 0.02);
        for (let y = 4; y < 12; y += 2) p.rect(4, y, 8, 1, PORT_GREY);
        p.px('#37c6e8', [3, 3], [12, 3], [3, 12], [12, 12]);
        return p;
    });
    block('cargo_port_top', () => {
        const p = casing(new Painter(193), '#37c6e8');
        p.bevel(3, 3, 10, 10, PORT_GREY, 0.03);
        p.inset(5, 5, 6, 6, '#14171b', 0.02);
        return p;
    });
    // The controller of the tests: a screen with the grid of a plan.
    function controllerFront(on) {
        const p = casing(new Painter(194), '#e0b33a');
        p.inset(2, 2, 12, 9, '#14181c', 0.02);
        for (let y = 3; y < 10; y += 2) for (let x = 3; x < 13; x += 2) p.px(on ? '#37c6e8' : '#2a3a44', [x, y]);
        if (on) p.px('#5fbf3a', [7, 5], [9, 7]);
        p.rect(4, 12, 3, 2, on ? '#5fbf3a' : '#3a2a2a').rect(9, 12, 3, 2, '#2a3036');
        return p;
    }
    block('multiblock_controller_front', () => controllerFront(false));
    block('multiblock_controller_front_on', () => controllerFront(true));
    block('multiblock_controller_side', () => casing(new Painter(195), '#e0b33a'));

    // ---------------------------------------------------------------- multiblocks (version 1.7.0, block B)

    // The assembly plate: the floor of the workshops, a heavy steel plate in four tiles with a yellow mark in the middle.
    block('assembly_plate', () => {
        const p = new Painter(196);
        for (const [x, y] of [[0, 0], [8, 0], [0, 8], [8, 8]]) p.bevel(x, y, 8, 8, '#6b737c', 0.04);
        p.px('#9aa3ad', [1, 1], [14, 1], [1, 14], [14, 14], [9, 1], [6, 14]);
        p.rect(6, 6, 4, 4, '#e0b33a').rect(7, 7, 2, 2, '#23262b');
        return p;
    });
    // An open frame: steel beams round the edge and a diagonal brace, see-through between them.
    function frame(seed, beam, width, accent) {
        const p = new Painter(seed);
        p.bevel(0, 0, 16, width, beam, 0.04).bevel(0, 16 - width, 16, width, beam, 0.04);
        p.bevel(0, 0, width, 16, beam, 0.04).bevel(16 - width, 0, width, 16, beam, 0.04);
        for (let i = width; i < 16 - width; i++) p.rect(i, i, 1, 1, shade(beam, 1.1)).rect(i + 1, i, 1, 1, shade(beam, 0.8));
        p.px(accent, [0, 0], [15, 0], [0, 15], [15, 15]);
        return p;
    }
    block('assembly_frame', () => frame(197, '#7c858f', 2, '#e0b33a'));
    block('heavy_frame', () => {
        const p = frame(198, '#4c535b', 3, '#c9352b');
        hazard(p, 5, 0, 6, 2);
        hazard(p, 5, 14, 6, 2);
        return p;
    });
    // The crane beam: an I-beam with a rail under it and a hook trolley mark.
    block('crane_beam', () => {
        const p = new Painter(199);
        p.bevel(0, 0, 16, 4, '#d9a62e', 0.04).bevel(0, 12, 16, 4, '#d9a62e', 0.04);
        p.inset(0, 4, 16, 8, '#3a3f46', 0.03);
        p.rect(0, 7, 16, 2, '#8c97a3');
        p.px('#23262b', [2, 1], [6, 1], [10, 1], [14, 1], [2, 14], [6, 14], [10, 14], [14, 14]);
        return p;
    });
    // The hall of the body workshop (drawn over its frames once whole): steel wall panels, dark corner posts, a glass front
    // like the game's glass panes (a pale rim and a few gleams, the rest clear).
    block('workshop_wall', () => {
        const p = new Painter(208);
        p.bevel(0, 0, 16, 16, '#76808a', 0.04);
        p.inset(2, 2, 12, 12, '#646e78', 0.03);
        p.rect(2, 7, 12, 2, '#4f5861').rect(2, 7, 12, 1, '#454d55');
        p.rivets('#a6b0ba', 1);
        p.px('#e0b33a', [7, 0], [8, 0], [7, 15], [8, 15]);
        return p;
    });
    block('workshop_post', () => {
        const p = new Painter(209);
        p.bevel(0, 0, 16, 16, '#40464d', 0.05);
        p.rect(0, 6, 16, 1, '#e0b33a').rect(0, 9, 16, 1, '#e0b33a');
        return p;
    });
    const glassTint = (r, g, b, a) => 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
    block('workshop_glass', () => {
        const p = new Painter(210);
        p.rect(1, 1, 14, 14, glassTint(205, 232, 246, 0.14));
        p.rect(0, 0, 16, 1, glassTint(222, 236, 244, 0.95)).rect(0, 15, 16, 1, glassTint(150, 175, 190, 0.95));
        p.rect(0, 1, 1, 14, glassTint(222, 236, 244, 0.95)).rect(15, 1, 1, 14, glassTint(150, 175, 190, 0.95));
        for (const [x, y] of [[3, 4], [4, 3], [5, 2], [3, 6], [4, 5], [5, 4], [6, 3], [7, 2], [10, 12], [11, 11], [12, 10]]) {
            p.rect(x, y, 1, 1, glassTint(255, 255, 255, 0.55));
        }
        return p;
    });
    block('crane_beam_end', () => {
        const p = new Painter(200);
        p.inset(0, 0, 16, 16, '#3a3f46', 0.03);
        p.bevel(0, 0, 16, 4, '#d9a62e', 0.04).bevel(0, 12, 16, 4, '#d9a62e', 0.04).bevel(6, 4, 4, 8, '#d9a62e', 0.04);
        return p;
    });
    // The controllers: the workshop with an orange screen showing a body, the dock with a red one showing a power armor.
    function workshopFront(on, figure, accent, seed) {
        const p = casing(new Painter(seed), accent);
        p.inset(2, 2, 12, 10, '#101418', 0.02);
        p.grid(figure, {f: on ? '#37c6e8' : '#26323a', e: on ? '#5fbf3a' : '#26323a'}, 3, 3);
        p.rect(3, 13, 4, 1, on ? '#5fbf3a' : '#3a2a2a').rect(9, 13, 4, 1, accent);
        return p;
    }
    const BODY = ['....ff....', '....ff....', '..ffffff..', '.f.ffff.f.', '.f.ffff.f.', '...f..f...', '...f..f...', 'eeeeeeeeee'];
    const ARMOR = ['...ffff...', '...feef...', '.ffffffff.', 'ff.ffff.ff', 'f..ffff..f', '..ff..ff..', '..ff..ff..', 'eeeeeeeeee'];
    block('body_workshop_controller_front', () => workshopFront(false, BODY, '#f08a24', 201));
    block('body_workshop_controller_front_on', () => workshopFront(true, BODY, '#f08a24', 201));
    block('body_workshop_controller_side', () => casing(new Painter(202), '#f08a24'));
    block('heavy_dock_controller_front', () => workshopFront(false, ARMOR, '#c9352b', 203));
    block('heavy_dock_controller_front_on', () => workshopFront(true, ARMOR, '#c9352b', 203));
    block('heavy_dock_controller_side', () => {
        const p = casing(new Painter(204), '#c9352b');
        hazard(p, 3, 6, 10, 4);
        return p;
    });
    // Parts: the heavy actuator, the power frame, the three armor hulls and the manipulators' icons.
    item('heavy_actuator', () => icon([
        '................',
        '..kkkk..........',
        '.kSSSSk.........',
        '.kSyySkkkkkkkk..',
        '.kSyySmmmmmmmmk.',
        '.kSSSSmMMMMMMmk.',
        '..kkkkmmmmmmmmk.',
        '.....kkkkkkkkkk.',
        '.........kssk...',
        '.........kssk...',
        '........kSSSSk..',
        '........kSyySk..',
        '........kSyySk..',
        '........kSSSSk..',
        '.........kkkk...',
        '................'], {k: '#1e2228', S: '#c3c8cd', s: '#8c97a3', y: '#e0b33a', m: '#59626c', M: '#8c97a3'}));
    item('power_frame', () => icon([
        '................',
        '.....kkkkkk.....',
        '.....kmmmmk.....',
        '..kkkkmyymkkkk..',
        '..kmmmmmmmmmmk..',
        '..km.kmmmmk.mk..',
        '..km.kmyymk.mk..',
        '..kk.kmmmmk.kk..',
        '.....kmmmmk.....',
        '.....kmkkmk.....',
        '.....kmkkmk.....',
        '.....kmkkmk.....',
        '....kkmkkmkk....',
        '....kmmkkmmk....',
        '....kkkkkkkk....',
        '................'], {k: '#1e2228', m: '#7c858f', y: '#e0b33a'}));
    function hull(seed, c, accent) {
        return () => icon([
            '................',
            '....kkkkkkkk....',
            '...kccccccccK...',
            '..kccaaccaacck..',
            '.kcccccccccccck.',
            '.kcckkkkkkkkcck.',
            '.kcck......kcck.',
            '.kcck......kcck.',
            '.kcck......kcck.',
            '.kcckkkkkkkkcck.',
            '.kccccccccccccK.',
            '..kcccaaaacccK..',
            '...kccccccccK...',
            '....kkkkkkkk....',
            '................',
            '................'], {k: '#1e2228', K: '#14171b', c, a: accent});
    }
    item('combat_armor_hull', hull(205, '#6b737c', '#c9352b'));
    item('mobile_armor_hull', hull(206, '#a7b0b8', '#37c6e8'));
    item('worker_armor_hull', hull(207, '#d9a62e', '#23262b'));
    // The manipulators' icons: the same arm, the turret in the colour of the kind and its tool at the end.
    function manipulatorIcon(accent, tip) {
        return () => icon([
            '................',
            '.....kkkkkkkkk..',
            '....kmyyyyyyyyk.',
            '....kmkkkkkkkmk.',
            '....kyk....' + tip[0],
            '....kyk....' + tip[1],
            '....kyk....' + tip[2],
            '....kyk....' + tip[3],
            '....kyk.........',
            '...kmmmk........',
            '..kaaaaak.......',
            '..kaaaaak.......',
            '.kkkkkkkkk......',
            '.kmmmmmmmk......',
            '.kkkkkkkkk......',
            '................'], {k: '#1e2228', m: '#4a4f56', y: '#d9a62e', a: accent, b: '#9fd8ff', o: '#b06a3a', s: '#c3c8cd'});
    }
    item('welding_manipulator', manipulatorIcon('#e07a24', ['.kmk.', '.kok.', '.kok.', '..b..']));
    item('gripper_manipulator', manipulatorIcon('#3a8fd0', ['kmmmk', 's.s.s', 's.s.s', 's...s']));
    item('screwdriver_manipulator', manipulatorIcon('#5aa04a', ['.kmk.', '.kak.', '..s..', '..s..']));

    // The bucket of polluted water is the game's bucket with a liquid layer over it: blockbench/tools/polluted_bucket.js.

    /** Airlock door: a heavy steel door, the lower half with a hazard band and a locking wheel, the upper with a thick window. */
    block('airlock_door_bottom', () => {
        const p = new Painter(151);
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        p.inset(2, 0, 12, 11, shade(STEEL, 0.85), 0.03);
        p.rect(2, 0, 12, 1, shade(STEEL, 0.85));
        hazard(p, 1, 12, 14, 3);
        // the locking wheel
        p.grid([
            '..www..',
            '.w.d.w.',
            'w..d..w',
            'wddddw.',
            'w..d..w',
            '.w.d.w.',
            '..www..'], {w: YELLOW, d: '#9a7a1a'}, 5, 2);
        p.rect(8, 5, 1, 1, DARK);
        p.px(LIGHT, [1, 1], [14, 1], [1, 10], [14, 10]);
        return p;
    });
    block('airlock_door_top', () => {
        const p = new Painter(152);
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        p.inset(2, 2, 12, 14, shade(STEEL, 0.85), 0.03);
        // a thick round window
        p.grid([
            '...ffff...',
            '.ffggggff.',
            '.fGgggggf.',
            'fgGggggggf',
            'fggggggggf',
            'fggggggggf',
            'fgggggggGf',
            '.fggggGgf.',
            '.ffggggff.',
            '...ffff...'], {f: GUN, g: '#4d8ea6', G: '#c6eef8'}, 3, 3);
        p.px(LIGHT, [1, 1], [14, 1], [1, 14], [14, 14]);
        return p;
    });
    block('sealed_hatch', () => {
        const p = new Painter(153);
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        hazard(p, 0, 0, 16, 1);
        hazard(p, 0, 15, 16, 1);
        p.inset(2, 2, 12, 12, shade(STEEL, 0.8), 0.03);
        p.grid([
            '...wwww...',
            '..w.dd.w..',
            '.w..dd..w.',
            'w...dd...w',
            'wddddddddw',
            'wddddddddw',
            'w...dd...w',
            '.w..dd..w.',
            '..w.dd.w..',
            '...wwww...'], {w: YELLOW, d: '#9a7a1a'}, 3, 3);
        p.rect(7, 7, 2, 2, DARK);
        return p;
    });

    /** Backup battery: like the battery, with a red mark and a charge window on every side. */
    function backupSide(level) {
        const p = casing(new Painter(161), ALARM);
        p.inset(5, 3, 6, 10, '#1a0c0c', 0.02);
        p.bevel(3, 3, 1, 10, LIGHT).bevel(12, 3, 1, 10, LIGHT);
        [11, 9, 7, 5].forEach((y, row) => p.rect(6, y, 4, 1, row < level ? (row < 2 ? GREEN : YELLOW) : '#3a1a1a'));
        p.rect(6, 3, 4, 1, ALARM);
        return p;
    }
    block('backup_battery_side', () => backupSide(4));
    animated('backup_battery_side_on', 4, 4, i => {
        const p = backupSide(4 - i);
        p.rect(6, 3, 4, 1, i % 2 ? ALARM : '#ffd0c8');
        return p;
    });
    block('backup_battery_top', () => {
        const p = casing(new Painter(162), ALARM);
        p.bevel(3, 3, 10, 10, shade(STEEL, 0.85), 0.03);
        p.bevel(4, 6, 3, 4, '#c83a30').bevel(9, 6, 3, 4, '#2a2e33');
        p.px('#ffffff', [5, 7]).px(LIGHT, [10, 7]);
        return p;
    });

    /** Decontamination chamber: white tiles inside, a drain grate below, shower nozzles above, a falling water curtain. */
    block('decontamination_chamber_tiles', () => {
        const p = new Painter(171);
        p.noise(0, 0, 16, 16, '#dfe6e9', 0.02);
        for (let i = 0; i < 16; i += 4) {
            p.rect(i, 0, 1, 16, '#a9b4ba').rect(0, i, 16, 1, '#a9b4ba');
        }
        p.rect(0, 13, 16, 1, '#57b9d8');
        return p;
    });
    block('decontamination_chamber_grate', () => {
        const p = new Painter(172);
        p.noise(0, 0, 16, 16, GUN, 0.04);
        for (let i = 1; i < 16; i += 3) {
            p.rect(i, 1, 1, 14, '#14171b');
        }
        p.inset(6, 6, 4, 4, '#0c0e10', 0.01);
        p.rect(0, 0, 16, 1, STEEL).rect(0, 15, 16, 1, STEEL);
        return p;
    });
    block('decontamination_chamber_ceiling', () => {
        const p = new Painter(173);
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        p.inset(3, 3, 10, 10, GUN, 0.02);
        for (let y = 4; y < 12; y += 2) {
            for (let x = 4; x < 12; x += 2) {
                p.rect(x, y, 1, 1, '#9fe0ff');
            }
        }
        return p;
    });
    animated('decontamination_chamber_water', 8, 2, i => {
        const p = new Painter(174 + i);
        for (let x = 1; x < 16; x += 2) {
            const offset = (x * 5 + i * 2) % 16;
            for (let k = 0; k < 3; k++) {
                const y = (offset + k * 6) % 16;
                p.rect(x, y, 1, 3, k === 0 ? 'rgba(220,245,255,0.85)' : 'rgba(120,200,240,0.55)');
            }
        }
        return p;
    });
    block('decontamination_chamber_side', () => {
        const p = casing(new Painter(175), CYAN);
        p.inset(3, 3, 10, 10, '#dfe6e9', 0.02);
        p.rect(3, 9, 10, 1, '#57b9d8');
        p.grid(['.ww.', 'wwww', 'wwww', '.ww.'], {w: '#57b9d8'}, 6, 4);
        return p;
    });

    /** Radiation purge station: a platform with an emitter ring that glows while it works. */
    function purgeTop(glow) {
        const p = new Painter(181);
        p.bevel(0, 0, 16, 16, GUN, 0.03);
        hazard(p, 0, 0, 16, 1);
        hazard(p, 0, 15, 16, 1);
        const ring = glow === null ? '#2f5a3c' : ['#4fcf7a', '#8ff0a8', '#d4ffe0', '#8ff0a8'][glow];
        p.grid([
            '....rrrr....',
            '..rr....rr..',
            '.r..dddd..r.',
            '.r.d....d.r.',
            'r.d..cc..d.r',
            'r.d.cccc.d.r',
            'r.d.cccc.d.r',
            'r.d..cc..d.r',
            '.r.d....d.r.',
            '.r..dddd..r.',
            '..rr....rr..',
            '....rrrr....'], {r: ring, d: '#1a2a20', c: glow === null ? '#3a6a4a' : '#ffffff'}, 2, 2);
        return p;
    }
    block('radiation_purge_station_top', () => purgeTop(null));
    animated('radiation_purge_station_top_on', 4, 4, i => purgeTop(i));
    block('radiation_purge_station_side', () => {
        const p = new Painter(182);
        p.bevel(0, 0, 16, 16, STEEL, 0.03);
        hazard(p, 0, 12, 16, 4);
        [2, 6, 10].forEach(x => p.inset(x, 13, 3, 2, DARK, 0.01));
        p.rect(0, 11, 16, 1, '#2f5a3c');
        return p;
    });

    /** Shelter control panel: a status screen, three lamps and keys; while it sounds the alarm the screen flashes red. */
    function panelFront(alarm, frame) {
        const p = casing(new Painter(191), CYAN);
        const red = alarm && frame % 2 === 0;
        p.inset(3, 3, 10, 7, red ? '#3a0c0c' : '#0a2318', 0.02);
        if (alarm) {
            p.grid(['...rr...', '...rr...', '...rr...', '........', '...rr...'], {r: red ? ALARM : '#7a2a24'}, 4, 4);
        } else {
            p.rect(4, 4, 5, 1, GREEN).rect(4, 6, 7, 1, shade(GREEN, 0.8)).rect(4, 8, 3, 1, GREEN).rect(9, 8, 2, 1, YELLOW);
        }
        p.px(alarm ? '#2a4a2a' : GREEN, [4, 11]).px(YELLOW, [6, 11]).px(red ? ALARM : '#5a1a1a', [8, 11]);
        p.bevel(10, 11, 3, 2, LIGHT, 0.02);
        return p;
    }
    block('shelter_control_panel_front', () => panelFront(false, 0));
    animated('shelter_control_panel_front_on', 2, 6, i => panelFront(true, i));
    block('shelter_control_panel_side', side(CYAN, 192));
    block('shelter_control_panel_top', top(CYAN, 193, p => {
        p.bevel(3, 3, 10, 10, shade(STEEL, 0.9), 0.03);
        p.inset(6, 6, 4, 4, DARK);
        p.rect(7, 7, 2, 2, ALARM);
    }));

    // Items of the shelter.
    item('filter_cartridge', () => icon([
        '................',
        '.....kkkkkk.....',
        '....kHHHHHHk....',
        '....kkkkkkkk....',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '....kkkkkkkk....',
        '....kHHHHHHk....',
        '.....kkkkkk.....',
        '................'], {k: '#14181d', H: '#b9c2cb', g: '#6d7680', Y: '#ffe58a', y: '#e0b33a', h: '#9a7a1a'}));
    item('spent_filter_cartridge', () => icon([
        '................',
        '.....kkkkkk.....',
        '....kHHHHHHk....',
        '....kkkkkkkk....',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '...kgYyyYyygk...',
        '...kgyhyhhhgk...',
        '...kgYyYhYygk...',
        '...kgyhhhyhgk...',
        '...kgYyYyYygk...',
        '...kgyhyhyhgk...',
        '....kkkkkkkk....',
        '....kHHHHHHk....',
        '.....kkkkkk.....',
        '................'], {k: '#14181d', H: '#7b838a', g: '#4a5157', Y: '#6a6350', y: '#4b4636', h: '#2a2618'}));
    item('decontamination_solution', () => bottle(['....kWWyyWWk....', '....kWyyyyWk....', '....kWWyyWWk....'], {C: '#37c6e8', l: '#57b9d8', y: '#f0c92f'}));
    item('airlock_door', () => icon([
        '....kkkkkkkk....',
        '....kSSSSSSk....',
        '....kSkkkkSk....',
        '....kSkggkSk....',
        '....kSkgGkSk....',
        '....kSkkkkSk....',
        '....kSSSSSSk....',
        '....kSSSSSSk....',
        '....kSSwwSSk....',
        '....kSwSSwSk....',
        '....kSSwwSSk....',
        '....kSSSSSSk....',
        '....kyzyzyzk....',
        '....kzyzyzyk....',
        '....kSSSSSSk....',
        '....kkkkkkkk....'], {k: '#14181d', S: '#8c97a3', g: '#4d8ea6', G: '#c6eef8', w: YELLOW, y: '#f2c025', z: '#23262b'}));

    // ---------------------------------------------------------------- technical limits (version 1.5.0)

    const CHARGE_YELLOW = '#ffd54f', HEAT_ORANGE = '#ff7a1a';

    /** Charging pad: a dark plate with hazard edges, a ring of brass contacts and a socket; the ring runs while it charges. */
    function padTop(step) {
        const p = new Painter(191);
        p.bevel(0, 0, 16, 16, GUN, 0.03);
        hazard(p, 0, 0, 16, 1);
        hazard(p, 0, 15, 16, 1);
        p.inset(2, 2, 12, 12, DARK, 0.02);
        const ring = [[5, 2], [6, 2], [7, 2], [8, 2], [9, 2], [10, 2], [11, 3], [12, 4], [13, 5], [13, 6], [13, 7], [13, 8], [13, 9], [13, 10],
            [12, 11], [11, 12], [10, 13], [9, 13], [8, 13], [7, 13], [6, 13], [5, 13], [4, 12], [3, 11], [2, 10], [2, 9], [2, 8], [2, 7],
            [2, 6], [2, 5], [3, 4], [4, 3]];
        ring.forEach(([x, y], i) => {
            const lit = step === null ? false : (i + step * 4) % 8 < 3;
            p.px(step === null ? '#8a6a1a' : lit ? '#fff3b0' : CHARGE_YELLOW, [x, y]);
        });
        p.bevel(5, 5, 6, 6, STEEL, 0.03);
        p.inset(6, 6, 4, 4, '#14171b', 0.01);
        p.grid(['.y..', 'yy..', '.yy.', '..y.'], {y: step === null ? '#5c5a4a' : CHARGE_YELLOW}, 6, 6);
        return p;
    }
    block('charging_pad_top', () => padTop(null));
    animated('charging_pad_top_on', 8, 2, i => padTop(i));
    block('charging_pad_side', () => {
        const p = new Painter(192);
        p.noise(0, 0, 16, 16, GUN, 0.03);
        p.rect(0, 11, 16, 1, STEEL);
        hazard(p, 0, 12, 16, 4);
        [3, 12].forEach(x => p.inset(x, 13, 2, 2, DARK, 0.01));
        return p;
    });

    /** Heater: a steel box, an orange grille on every side that glows and shimmers while it heats. */
    function heaterSide(glow) {
        const p = casing(new Painter(193), HEAT_ORANGE);
        p.inset(3, 3, 10, 10, '#14171b', 0.02);
        for (let y = 4; y < 12; y += 2) {
            const c = glow === null ? '#4a2a1a' : [HEAT_ORANGE, '#ffb347', '#ffd27a', '#ffb347'][(y / 2 + glow) % 4];
            p.rect(4, y, 8, 1, c);
        }
        p.rect(4, 12, 8, 1, STEEL);
        return p;
    }
    block('heater_side', () => heaterSide(null));
    animated('heater_side_on', 4, 4, i => heaterSide(i));
    block('heater_top', () => {
        const p = casing(new Painter(194), HEAT_ORANGE);
        p.inset(3, 3, 10, 10, DARK, 0.02);
        for (let x = 4; x < 12; x += 2) {
            p.rect(x, 4, 1, 8, '#2b3036');
        }
        p.bevel(11, 11, 2, 2, '#c83a30');
        return p;
    });

    const POWER_PALETTE = {k: '#14181d', H: '#b9c2cc', s: '#6d7680', d: '#3a4149', b: '#20262e', y: CHARGE_YELLOW, Y: '#fff3b0',
        o: '#e0a800', g: '#3fae5a', G: '#9be0a8', c: '#4fc3f7', C: '#c9f0ff', u: '#d9894a', U: '#f3c08a', n: '#5a3a1e', v: '#a970ff', V: '#dcc6ff',
        t: '#c9a27a', T: '#e2c9a5', m: '#6d4c33', w: '#f2f6fa', r: '#ff5252'};
    /** A battery: a steel case with terminals, a level window and a band in its own colour. */
    function batteryIcon(band) {
        return () => icon([
            '................',
            '.....kk..kk.....',
            '.....kHk.kHk....',
            '...kkkkkkkkkkk..',
            '...kHHHHHHHHsk..',
            '...kH' + band.repeat(7) + 'dk..',
            '...kH' + band.repeat(7) + 'dk..',
            '...kHskkkkkkdk..',
            '...kHskYyyykdk..',
            '...kHskyyyykdk..',
            '...kHskyoookdk..',
            '...kHskooookdk..',
            '...kHskkkkkkdk..',
            '...kssssssssdk..',
            '...kkkkkkkkkkk..',
            '................'], POWER_PALETTE);
    }
    item('body_battery', batteryIcon('o'));
    item('extra_battery', batteryIcon('g'));
    item('cooling_module', () => icon([
        '................',
        '..kkkkkkkkkkkk..',
        '.kHsHsHsHsHsHsk.',
        '.kHsHsHsHsHsHsk.',
        '.kHsHskkkkHsHsk.',
        '.kHsHkcCCckHsHk.',
        '.kHsHkCccCkHsHk.',
        '.kHsHkCccCkHsHk.',
        '.kHsHkcCCckHsHk.',
        '.kHsHskkkkHsHsk.',
        '.kHsHsHsHsHsHsk.',
        '.kddddddddddddk.',
        '..kkkkkkkkkkkk..',
        '...kk......kk...',
        '................',
        '................'], POWER_PALETTE));
    item('thermal_liner', () => icon([
        '................',
        '..mmmmmmmmmmmm..',
        '.mTtTtTtTtTtTtm.',
        '.mtTtTtTtTtTtTm.',
        '.mTtTtTtTtTtTtm.',
        '.mmmmmmmmmmmmmm.',
        '.mTtTtTtTtTtTtm.',
        '.mtTtTtTtTtTtTm.',
        '.mTtTtTtTtTtTtm.',
        '.mmmmmmmmmmmmmm.',
        '.mTtTtTtTtTtTtm.',
        '.mtTtTtTtTtTtTm.',
        '.mTtTtTtTtTtTtm.',
        '..mmmmmmmmmmmm..',
        '...w...w...w....',
        '................'], POWER_PALETTE));
    /** A plate with a grid over it: the copper mesh of the shielding, the violet grid of the EMI screen. */
    function meshIcon(wire, light, rim) {
        return () => icon([
            '................',
            '..kkkkkkkkkkkk..',
            '.k' + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + 'k.',
            '.k' + rim + light + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + 'k.',
            '.k' + rim + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + rim + 'k.',
            '.k' + rim + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + 'k.',
            '.k' + rim + 'd' + wire + 'd' + light + 'd' + wire + 'd' + wire + 'd' + rim + rim + 'k.',
            '.k' + rim + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + 'k.',
            '.k' + rim + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + light + 'd' + rim + rim + 'k.',
            '.k' + rim + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + 'k.',
            '.k' + rim + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + wire + 'd' + rim + rim + 'k.',
            '.k' + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + rim + 'k.',
            '..kkkkkkkkkkkk..',
            '....kk....kk....',
            '................',
            '................'], POWER_PALETTE);
    }
    item('emi_shielding', meshIcon('u', 'U', 'n'));
    item('emi_screen', meshIcon('v', 'V', 's'));

    // Symptoms of heat and cold: a sun burning on a body, a snowflake.
    const THERMAL_PALETTE = {k: '#14181d', o: '#ff7043', O: '#ffd27a', r: '#b3361a', b: '#4fc3f7', B: '#e1f5fe', d: '#1f4f7a'};
    TEXTURES['mob_effect/heatstroke'] = () => icon([
        '................',
        '.......kk.......',
        '..kk..kOOk..kk..',
        '..kOk..kk..kOk..',
        '...kk.kkkk.kk...',
        '.....kooook.....',
        '.kk.kooOOook.kk.',
        'kOOkkoOOOOokkOOk',
        'kOOkkoOOOOokkOOk',
        '.kk.kooOOook.kk.',
        '.....kooook.....',
        '...kk.krrk.kk...',
        '..kOk..kk..kOk..',
        '..kk..kOOk..kk..',
        '.......kk.......',
        '................'], THERMAL_PALETTE);
    TEXTURES['mob_effect/hypothermia'] = () => icon([
        '................',
        '.......kk.......',
        '...kk.kBBk.kk...',
        '...kBkkbbkkBk...',
        '....kBkbbkBk....',
        '.kk..kbbbbk..kk.',
        'kBBkkkbBBbkkkBBk',
        'kbbbbbBBBBbbbbbk',
        'kbbbbbBBBBbbbbbk',
        'kBBkkkbBBbkkkBBk',
        '.kk..kbbbbk..kk.',
        '....kBkbbkBk....',
        '...kBkkbbkkBk...',
        '...kk.kBBk.kk...',
        '.......kk.......',
        '................'], THERMAL_PALETTE);

    // ---------------------------------------------------------------- output

    // ---------------------------------------------------------------- uranium and radiation sources (1.6.0)

    const RAD = '#b5d62a', RAD_GLOW = '#e4ff5c';

    // Radioactive debris: dark rubble with a green glow in the cracks.
    block('radioactive_debris', () => {
        const p = new Painter(601);
        p.noise(0, 0, 16, 16, '#2b2e2a', 0.12);
        [[0, 1, 5, 4], [6, 0, 6, 3], [11, 3, 5, 5], [2, 6, 5, 4], [8, 7, 4, 4], [0, 11, 6, 5], [7, 12, 5, 4], [12, 10, 4, 5]].forEach(([x, y, w, h], i) => {
            p.bevel(x, y, w, h, i % 3 === 0 ? '#4a4d48' : i % 3 === 1 ? '#3a3a37' : '#55463a', 0.09);
        });
        // The glow: cracks and specks, a darker green halo around the bright core.
        [[5, 3], [6, 3], [5, 7], [11, 6], [12, 7], [5, 12], [6, 11], [2, 15], [11, 13], [10, 14], [15, 8], [3, 4]].forEach(([x, y]) => p.px('#3f8a24', [x, y]));
        [[5, 4], [5, 5], [6, 5], [11, 7], [11, 8], [5, 13], [6, 12], [12, 14], [3, 5], [8, 9], [14, 8]].forEach(([x, y]) => p.px('#7dff3a', [x, y]));
        [[5, 5], [11, 8], [6, 12]].forEach(([x, y]) => p.px('#d8ff9a', [x, y]));
        return p;
    });

    // Leaking waste barrel: rust, two bands, a hazard sign and a toxic stain running down from a crack.
    // The model shows columns 2..13 of the side and 2..13 of the lid.
    const LEAK = '#9bd424', LEAK_DARK = '#4f7a16';
    block('leaking_waste_barrel_side', () => {
        const p = new Painter(611);
        p.noise(0, 0, 16, 16, '#8b4a2b', 0.07);
        [2, 3, 12, 13].forEach(x => p.rect(x, 0, 1, 16, shade('#8b4a2b', x < 8 ? 0.82 : 0.74)));
        [[3, 4], [10, 11]].forEach(([a, b]) => p.rect(0, a, 16, 1, '#7a4a30').rect(0, b, 16, 1, '#3d2216'));
        p.rect(0, 0, 16, 1, '#3d2216').rect(0, 15, 16, 1, '#3d2216');
        // Rust streaks and a few flaked patches.
        [[4, 6], [9, 7], [6, 12], [12, 1]].forEach(([x, y]) => p.rect(x, y, 1, 2, '#5e2f1a'));
        [[3, 8], [8, 13], [7, 2]].forEach(([x, y]) => p.px('#b06a3a', [x, y]));
        // Hazard sign.
        p.grid(['kkkkkk', 'kybbyk', 'kbbbbk', 'kybbyk', 'kkkkkk'], {k: '#15171a', y: '#e6c02a', b: '#15171a'}, 4, 5);
        p.px('#e6c02a', [6, 7], [7, 7]);
        // The leak: a crack, a stain, a puddle at the foot.
        p.px('#15171a', [11, 5], [12, 6]);
        p.rect(11, 6, 2, 1, LEAK).rect(11, 7, 1, 7, LEAK).rect(12, 8, 1, 4, LEAK_DARK).rect(10, 12, 3, 1, LEAK).rect(9, 13, 5, 2, LEAK).rect(10, 14, 3, 1, LEAK_DARK);
        p.px('#d6ff7a', [11, 8], [11, 10], [10, 13], [12, 13]);
        return p;
    });
    block('leaking_waste_barrel_top', () => {
        const p = new Painter(612);
        p.noise(0, 0, 16, 16, '#7a4328', 0.06);
        p.rect(2, 2, 12, 12, '#6e3b23');
        p.inset(3, 3, 10, 10, '#5a3220', 0.05);
        p.rect(7, 7, 2, 2, '#2a2a2c').rect(6, 6, 1, 1, '#8c97a3').rect(10, 9, 1, 1, '#8c97a3');
        // Toxic residue around the bung.
        p.rect(9, 5, 3, 2, LEAK).px(LEAK_DARK, [11, 6], [12, 7]).px(LEAK, [4, 10], [5, 10], [5, 11]);
        return p;
    });

    // Radioisotope generator: yellow-green accent, cooling fins at the sides, a shielded core on top.
    function rtgSide() {
        const p = casing(new Painter(701), RAD);
        p.inset(3, 3, 10, 10, DARK);
        [4, 6, 8, 10].forEach(x => p.rect(x, 4, 1, 8, shade(STEEL, 0.95)).rect(x + 1, 4, 1, 8, '#101215'));
        return p;
    }
    function rtgTop(glow) {
        const p = casing(new Painter(702), RAD);
        p.bevel(3, 3, 10, 10, LIGHT, 0.03);
        p.inset(5, 5, 6, 6, '#14171b');
        p.rect(6, 6, 4, 4, glow ? RAD : '#2c3a14').rect(7, 7, 2, 2, glow ? '#f4ffb0' : '#14171b');
        return p;
    }
    function rtgFront(glow) {
        const p = casing(new Painter(703), RAD);
        p.inset(3, 3, 10, 7, '#0e1208', 0.02);
        const c = glow ? RAD_GLOW : '#6a7c2a';
        p.grid(['.yy....yy.', '.yyy..yyy.', '..yyyyyy..', '...yyyy...', '....yy....'], {y: c}, 3, 4);
        p.px(glow ? '#ffffff' : '#0e1208', [7, 6], [8, 6]);
        hazard(p, 3, 11, 10, 2);
        return p;
    }
    block('radioisotope_generator_side', () => rtgSide());
    block('radioisotope_generator_top', () => rtgTop(false));
    block('radioisotope_generator_front', () => rtgFront(false));
    animated('radioisotope_generator_side_on', 4, 4, i => {
        const p = baseOf('radioisotope_generator_side');
        [4, 6, 8, 10].forEach((x, k) => p.rect(x, 4, 1, 8, mix('#59626c', RAD_GLOW, (k + i) % 4 === 0 ? 0.95 : 0.55)));
        return p;
    });
    animated('radioisotope_generator_top_on', 4, 4, i => {
        const p = rtgTop(true);
        p.rect(7, 7, 2, 2, ['#f4ffb0', '#d8f060', '#b5d62a', '#d8f060'][i]);
        return p;
    });
    animated('radioisotope_generator_front_on', 4, 4, i => {
        const p = rtgFront(true);
        p.px(['#7dff3a', '#2c3a14', '#7dff3a', '#b5ff8a'][i], [12, 9]);
        return p;
    });

    // ---------------------------------------------------------------- structure decoration (1.6.0, large structures)

    // Wall monitor: a dark-blue screen with scrolling data lines, a graph and a blinking cursor.
    function monitorScreen(i) {
        const p = new Painter(801);
        p.rect(0, 0, 16, 16, '#0a1626');
        p.noise(1, 1, 14, 14, '#0d2340', 0.06);
        const rows = [[2, 6], [2, 9], [2, 4], [2, 8], [2, 5], [2, 7], [2, 3], [2, 9]];
        for (let r = 0; r < 4; r++) {
            const [x, w] = rows[(r + i) % rows.length];
            p.rect(x, 2 + r * 2, w, 1, r === 0 ? '#9fe8ff' : '#3f9fd8');
        }
        // A small bar graph on the right that rises and falls.
        [[11, 3], [12, 5], [13, 2], [14, 4]].forEach(([x, h], k) => {
            const hh = 1 + (h + i + k) % 5;
            p.rect(x, 9 - hh, 1, hh, '#37e6ff');
        });
        // A wave along the bottom.
        for (let x = 2; x < 14; x++) p.px('#5fd0ff', [x, 12 + Math.round(Math.sin((x + i * 2) * 0.8))]);
        if (i % 2 === 0) p.rect(2 + 2 * 4, 10, 1, 1, '#e6f8ff');
        return p;
    }
    animated('wall_monitor_screen_on', 4, 8, i => monitorScreen(i));

    // Server rack: rows of drive bays with small status lights that blink.
    function rackFront(i) {
        const p = new Painter(811);
        p.noise(0, 0, 16, 16, '#22262c', 0.05);
        for (let r = 0; r < 5; r++) {
            const y = 1 + r * 3;
            p.bevel(1, y, 14, 2, '#30353d', 0.03);
            p.rect(2, y + 1, 8, 1, '#15181c');
            const lights = ['#35d07f', '#35d07f', '#f0c92f', '#37e6ff'];
            p.px((r + i) % 3 === 0 ? '#0e2a1a' : lights[(r + i) % 4], [11, y + 1]);
            p.px((r * 2 + i) % 4 === 0 ? '#e5483d' : '#35d07f', [13, y + 1]);
        }
        return p;
    }
    animated('server_rack_front', 4, 6, i => rackFront(i));
    block('server_rack_side', () => {
        const p = new Painter(812);
        p.noise(0, 0, 16, 16, '#2a2f36', 0.05);
        [3, 6, 9, 12].forEach(y => p.rect(2, y, 12, 1, '#16191d'));
        p.rect(0, 0, 16, 1, '#3d434c').rect(0, 15, 16, 1, '#16191d');
        return p;
    });

    // Specimen tank liquid: translucent blue water with rising bubbles and a brighter glass edge; the specimen is a 3D model inside.
    function tankLiquid(i) {
        const p = new Painter(821);
        const rgba = (r, g, b, a) => 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
            const v = p.rand() * 0.06;
            p.ctx.clearRect(x, y, 1, 1);
            p.rect(x, y, 1, 1, rgba(25 + Math.round(v * 200), 135 + Math.round(v * 300), 205, 0.5 + (y / 16) * 0.14));
        }
        // the glass: a lighter edge on the left, a darker one on the right, a long highlight streak
        for (let y = 0; y < 16; y++) { p.ctx.clearRect(0, y, 1, 1); p.rect(0, y, 1, 1, rgba(150, 235, 255, 0.7)); p.ctx.clearRect(15, y, 1, 1); p.rect(15, y, 1, 1, rgba(20, 90, 120, 0.7)); }
        for (let y = 2; y < 13; y++) { p.ctx.clearRect(2, y, 1, 1); p.rect(2, y, 1, 1, rgba(220, 250, 255, 0.55)); }
        // bubbles rising
        [[4, 13], [11, 9], [5, 6], [12, 2], [9, 14], [7, 3]].forEach(([x, y], k) => {
            const yy = (y - i * 4 + 64 + k) % 16;
            p.ctx.clearRect(x, yy, 1, 1);
            p.rect(x, yy, 1, 1, rgba(225, 252, 255, 0.85));
        });
        return p;
    }
    animated('specimen_tank_liquid', 4, 5, i => tankLiquid(i));

    // The specimen floating in the tank: pale grey-green skin with darker folds; the head's front has closed dark eyes.
    block('specimen', () => {
        const p = new Painter(822);
        p.noise(0, 0, 16, 16, '#9fb7a6', 0.08);
        [[1, 9], [4, 12], [9, 10], [13, 13], [6, 14], [11, 7]].forEach(([x, y]) => p.px('#7c9584', [x, y], [x + 1, y]));
        // the face (u 0..6, v 0..6): brow, two closed eyes, a slit of a mouth
        p.noise(0, 0, 6, 6, '#a9c1b0', 0.05);
        p.rect(1, 2, 1, 1, '#2c3a33').rect(4, 2, 1, 1, '#2c3a33').rect(2, 4, 2, 1, '#6d8577').rect(0, 1, 6, 1, '#93ab9a');
        // veins on the body area
        p.px('#6e7fa0', [9, 2], [10, 3], [10, 4], [11, 5], [8, 8], [8, 9]);
        return p;
    });
    block('tank_metal', () => {
        const p = new Painter(823);
        p.noise(0, 0, 16, 16, '#4a525c', 0.05);
        p.rect(0, 0, 16, 1, '#6b7580').rect(0, 15, 16, 1, '#2a2f36');
        [[2, 7], [13, 7]].forEach(([x, y]) => p.bevel(x, y, 2, 2, '#8c97a3', 0.02));
        p.rect(5, 6, 6, 4, '#1e2228').rect(6, 7, 1, 2, '#37e6ff').rect(8, 7, 2, 1, '#35d07f');
        return p;
    });

    // ---------------------------------------------------------------- turret and alarm lamp in Minecraft's own manner: a few close
    // tones laid in short horizontal strokes and specks, as on the anvil, iron and deepslate textures; the models place them one
    // pixel to a sixteenth of a block.
    /** Fills the painter with strokes of a palette (dark to light): a middle ground, then lighter and darker strokes and specks. */
    function strokes(p, palette, seed, count) {
        const r = random(seed);
        const mid = Math.floor(palette.length / 2);
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) p.rect(x, y, 1, 1, palette[mid - (r() < 0.5 ? 1 : 0)]);
        for (let i = 0; i < (count || 46); i++) {
            const x = Math.floor(r() * 16), y = Math.floor(r() * 16), len = 2 + Math.floor(r() * 4);
            const tone = palette[Math.floor(r() * palette.length)];
            for (let k = 0; k < len; k++) p.rect((x + k) % 16, y, 1, 1, tone);
        }
        for (let i = 0; i < 14; i++) p.px(palette[r() < 0.5 ? 0 : palette.length - 1], [Math.floor(r() * 16), Math.floor(r() * 16)]);
        return p;
    }
    const STEEL_TONES = ['#7c8289', '#8b9198', '#989ea5', '#a5abb2', '#b3b9bf', '#c3c8cd'];
    const IRON_DARK = ['#2f2f31', '#38383a', '#3f3f42', '#47474a', '#505053', '#5b5b5e'];
    const HOUSING_TONES = ['#242426', '#2d2d30', '#37373a', '#414145', '#4c4d51', '#58595d'];
    const CAGE_TONES = ['#45494f', '#51555b', '#5d6167', '#696d74', '#777b82'];
    /** A panel seam across the middle and rivets near the corners, in the palette's darkest and lightest tones. */
    function seams(p, palette) {
        const dark = palette[0], light = palette[palette.length - 1];
        p.rect(0, 7, 16, 1, dark).rect(0, 8, 16, 1, light);
        for (const [x, y] of [[2, 2], [13, 2], [2, 12], [13, 12]]) p.px(light, [x, y]).px(dark, [x + 1, y + 1]);
        return p;
    }
    block('turret_casing', () => seams(strokes(new Painter(931), STEEL_TONES, 931), STEEL_TONES));
    block('turret_frame', () => strokes(new Painter(932), IRON_DARK, 932));
    block('turret_bore', () => {
        const p = strokes(new Painter(933), IRON_DARK, 933);
        // the muzzle's face takes pixels 5-10 across and 6-11 down: a ring of 4 x 4 and the bore of 2 x 2 in its middle, metal round them
        p.rect(6, 7, 4, 4, '#1c1c1e').rect(7, 8, 2, 2, '#0a0a0b');
        return p;
    });
    /** The broken turret: cracks, soot from a hit and a spot or two of rust over the same metal. */
    function wrecked(p, seed, palette) {
        const r = random(seed);
        const dark = palette[0], darker = palette === STEEL_TONES ? '#5f646a' : '#242426';
        // a crack or two in the plate's own darkest tones, a dull scorch, one small spot of rust
        [[11, 1], [10, 2], [10, 3], [9, 4], [9, 5], [4, 11], [5, 12], [5, 13]].forEach(([x, y]) => p.px(darker, [x, y]));
        [[10, 1], [9, 3], [4, 12]].forEach(([x, y]) => p.px(dark, [x, y]));
        for (let i = 0; i < 6; i++) p.px(dark, [1 + Math.floor(r() * 4), 10 + Math.floor(r() * 4)]);
        p.px('#7b5136', [12, 11], [13, 12]).px('#8a5a3a', [12, 12]);
        return p;
    }
    block('turret_casing_broken', () => wrecked(TEXTURES['block/turret_casing'](), 961, STEEL_TONES));
    block('turret_frame_broken', () => wrecked(TEXTURES['block/turret_frame'](), 962, IRON_DARK));

    // alarm lamp
    block('lamp_housing', () => seams(strokes(new Painter(941), HOUSING_TONES, 941), HOUSING_TONES));
    block('lamp_vents', () => {
        const p = strokes(new Painter(942), HOUSING_TONES, 942);
        for (const y of [1, 4, 7, 10, 13]) p.rect(1, y, 14, 1, '#141416').rect(1, y + 1, 14, 1, HOUSING_TONES[5]);
        return p;
    });
    block('lamp_metal', () => strokes(new Painter(943), CAGE_TONES, 943));
    block('lamp_glass', () => {
        // like the red stained glass: a see-through red with lighter streaks, a little brighter, it glows
        const p = new Painter(911);
        const r = random(911);
        const rgba = (c, a) => 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + a + ')';
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { p.ctx.clearRect(x, y, 1, 1); p.rect(x, y, 1, 1, rgba(r() < 0.3 ? [176, 44, 36] : [190, 52, 40], 0.66)); }
        for (const [x0, y0, len] of [[2, 3, 4], [9, 1, 3], [4, 10, 5], [11, 9, 3]]) for (let k = 0; k < len; k++) { p.ctx.clearRect(x0 + k, y0 + k, 1, 1); p.rect(x0 + k, y0 + k, 1, 1, rgba([238, 120, 98], 0.8)); }
        return p;
    });
    block('lamp_bulb', () => {
        const p = new Painter(912);
        const r = random(912);
        const tones = ['#f09149', '#f6b25a', '#f9c966', '#ffe0a0', '#fff3d2'];
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
            const d = Math.hypot(x - 7.5, y - 7.5) / 8;
            p.rect(x, y, 1, 1, tones[Math.max(0, Math.min(4, Math.round(4 - d * 4 + (r() - 0.5))))]);
        }
        return p;
    });

    // ---------------------------------------------------------------- broken variants
    block('wall_monitor_screen_broken', () => {
        const p = new Painter(921);
        p.rect(0, 0, 16, 16, '#0b0f15');
        p.noise(1, 1, 14, 14, '#10161f', 0.08);
        // a dead screen: a spider crack from a hit, a corner of glass gone
        [[9, 5], [8, 6], [7, 7], [10, 6], [11, 7], [12, 8], [9, 4], [10, 3], [8, 8], [7, 9], [6, 10], [5, 11], [9, 7], [10, 9], [10, 10]].forEach(([x, y]) => p.px('#7d8a99', [x, y]));
        p.px('#c5ced8', [9, 5], [8, 6]);
        p.rect(1, 11, 4, 4, '#05070a').px('#3a4450', [1, 10], [2, 10], [5, 12], [5, 13]);
        p.rect(3, 3, 2, 1, '#173040');
        return p;
    });
    block('server_rack_front_broken', () => {
        const p = new Painter(922);
        p.noise(0, 0, 16, 16, '#1d2126', 0.05);
        for (let r = 0; r < 5; r++) {
            const y = 1 + r * 3;
            if (r === 1 || r === 3) { p.rect(1, y, 14, 2, '#08090b'); continue; } // bays pulled out: empty holes
            p.bevel(1, y, 14, 2, '#2b3037', 0.03);
            p.rect(2, y + 1, 8, 1, '#121417');
            p.px('#1f2a22', [11, y + 1]).px('#2a1a1a', [13, y + 1]);
        }
        p.rect(4, 7, 1, 3, '#c77b3b').rect(6, 7, 1, 4, '#3a6d9a'); // loose wires in an empty bay
        return p;
    });
    block('tank_glass_broken', () => {
        const p = new Painter(923);
        const rgba = (r, g, b, a) => 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
        // a pane with a jagged top edge: transparent above the break
        const edge = [6, 5, 7, 9, 8, 6, 4, 5, 7, 10, 9, 6, 5, 6, 8, 7];
        for (let x = 0; x < 16; x++) for (let y = 0; y < 16; y++) {
            p.ctx.clearRect(x, y, 1, 1);
            if (y >= edge[x]) p.rect(x, y, 1, 1, rgba(215, 235, 240, y === edge[x] ? 0.75 : 0.14));
        }
        [[3, 11], [4, 12], [5, 12], [10, 13], [11, 12]].forEach(([x, y]) => { p.ctx.clearRect(x, y, 1, 1); p.rect(x, y, 1, 1, rgba(230, 250, 255, 0.85)); });
        return p;
    });
    block('specimen_dead', () => {
        const p = new Painter(924);
        p.noise(0, 0, 16, 16, '#7f8f78', 0.08);
        [[2, 9], [5, 12], [10, 10], [12, 13], [7, 14]].forEach(([x, y]) => p.px('#5d6b57', [x, y], [x + 1, y]));
        p.noise(0, 0, 6, 6, '#879a80', 0.05);
        p.rect(1, 2, 1, 1, '#1d221c').rect(4, 2, 1, 1, '#1d221c').rect(2, 4, 2, 1, '#4f5c49');
        return p;
    });

    // ---------------------------------------------------------------- rusted machines: the same faces, gone to rust
    /** A rusted copy of a texture: colours dulled and darkened, rust patches and streaks running down, screens dead. */
    function rusted(id, seed) {
        TEXTURES['block/' + id + '_rusted'] = () => {
            const made = TEXTURES['block/' + id]();
            const src = made.canvas;
            const p = new Painter(seed);
            p.ctx.drawImage(src, 0, 0, 16, 16, 0, 0, 16, 16);
            const img = p.ctx.getImageData(0, 0, 16, 16), d = img.data;
            const rust = [[122, 66, 36], [146, 82, 40], [96, 54, 34], [168, 98, 52]];
            const patch = [];
            for (let i = 0; i < 5; i++) patch.push([p.rand() * 16, p.rand() * 16, 1.5 + p.rand() * 2.5]);
            for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
                const k = (y * 16 + x) * 4;
                if (d[k + 3] === 0) continue;
                let [r, g, b] = [d[k], d[k + 1], d[k + 2]];
                const max = Math.max(r, g, b), min = Math.min(r, g, b);
                // screens and lights die: bright, coloured pixels go dark
                if (max > 150 && max - min > 70) { r = r * 0.22 + 14; g = g * 0.22 + 14; b = b * 0.22 + 16; }
                // dull and darken
                const grey = (r + g + b) / 3;
                r = (r * 0.55 + grey * 0.45) * 0.82; g = (g * 0.55 + grey * 0.45) * 0.8; b = (b * 0.55 + grey * 0.45) * 0.76;
                // rust in patches and in streaks running down from them
                let t = 0;
                for (const [px, py, pr] of patch) {
                    const dist = Math.hypot(x - px, (y - py) * (y > py ? 0.35 : 1));
                    t = Math.max(t, 1 - dist / pr);
                }
                t = Math.min(1, t + (p.rand() < 0.08 ? 0.5 : 0));
                if (t > 0) {
                    const c = rust[Math.floor(p.rand() * rust.length)];
                    r = r * (1 - t * 0.85) + c[0] * t * 0.85; g = g * (1 - t * 0.85) + c[1] * t * 0.85; b = b * (1 - t * 0.85) + c[2] * t * 0.85;
                }
                d[k] = Math.round(r); d[k + 1] = Math.round(g); d[k + 2] = Math.round(b);
            }
            p.ctx.putImageData(img, 0, 0);
            return p;
        };
    }

    // the textures of the rusted machines (structures/StructureBlocks.RUSTED_IDS)
    ['electronics_fabricator', 'assembly_station', 'armor_workstation', 'maintenance_station', 'implant_fabricator', 'body_modification_station',
        'armor_upgrade_fabricator', 'weapon_workshop', 'combustion_generator', 'air_filtration_unit', 'shelter_control_panel'].forEach((id, i) => {
        ['front', 'side', 'top'].forEach((k, j) => rusted(id + '_' + k, 950 + i * 3 + j));
    });
    ['battery_front', 'battery_side', 'solar_panel_top', 'heater_side', 'heater_top', 'backup_battery_side', 'backup_battery_top', 'machine_parts', 'server_rack_side']
        .forEach((id, i) => rusted(id, 990 + i));

    function savePng(canvas, path) {
        const data = canvas.toDataURL('image/png').split(',')[1];
        fs.writeFileSync(path, Buffer.from(data, 'base64'));
    }

    /**
     * Block textures with see-through pixels (1.7.0). A full block's faces must be whole: a clear pixel shows as a hole. Only
     * the blocks meant to be see-through (CLEAR_BLOCKS: frames, panes, glass, lattices) may have them; a new texture that does
     * is refused by saveAll until it is named there on purpose. Returns "block/id: n pixels".
     */
    function clearPixels() {
        const out = [];
        for (const id of Object.keys(TEXTURES).filter(id => id.startsWith('block/'))) {
            const canvas = TEXTURES[id]().canvas;
            const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
            let clear = 0;
            for (let i = 3; i < data.length; i += 4) {
                if (data[i] < 255) {
                    clear++;
                }
            }
            if (clear > 0) {
                out.push(id + ': ' + clear + ' pixels');
            }
        }
        return out;
    }

    function saveAll() {
        const ids = Object.keys(TEXTURES);
        const holes = clearPixels().filter(line => !CLEAR_BLOCKS.has(line.slice(6, line.indexOf(':'))));
        if (holes.length) {
            throw new Error('Block textures with see-through pixels (holes on a full block; name them in CLEAR_BLOCKS if meant): ' + holes.join('; '));
        }
        fs.mkdirSync(ASSETS + 'gui', {recursive: true});
        fs.mkdirSync(ASSETS + 'models/armor', {recursive: true});
        ids.forEach(id => {
            const result = TEXTURES[id]();
            savePng(result.canvas, ASSETS + id + '.png');
            const meta = ASSETS + id + '.png.mcmeta';
            if (result.mcmeta) {
                fs.writeFileSync(meta, JSON.stringify(result.mcmeta, null, 2) + '\n');
            } else if (fs.existsSync(meta)) {
                fs.unlinkSync(meta);
            }
        });
        return ids.length;
    }

    /** All textures, magnified, on a checkered background; optionally only the ids that start with a prefix. */
    function sheet(path, scale, prefix) {
        const ids = Object.keys(TEXTURES).filter(id => !id.startsWith('gui/') && !id.startsWith('models/') && (!prefix || id.startsWith(prefix)));
        const s = scale || 8, cols = 10, cell = 16 * s + 6;
        const canvas = document.createElement('canvas');
        canvas.width = cols * cell;
        canvas.height = Math.ceil(ids.length / cols) * cell;
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = false;
        ctx.fillStyle = '#6b6b6b';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ids.forEach((id, i) => {
            const x = (i % cols) * cell + 3, y = Math.floor(i / cols) * cell + 3;
            ctx.drawImage(TEXTURES[id]().canvas, 0, 0, 16, 16, x, y, 16 * s, 16 * s);
        });
        savePng(canvas, path);
        return ids;
    }

    /** A project that shows all textures: blocks on cubes, items on planes. For looking at them and painting further. */
    function showcase() {
        newProject(Formats.free);
        Project.name = 'rebirth_textures';
        const ids = Object.keys(TEXTURES);
        const tex = {};
        ids.forEach(id => {
            const texture = new Texture({name: id.replace('/', '_')}).fromPath(ASSETS + id + '.png').add(false);
            tex[id] = texture;
        });
        const blocks = ids.filter(id => id.startsWith('block/')), items = ids.filter(id => id.startsWith('item/'));
        const place = (id, from, to) => {
            const cube = new Cube({name: id.replace('/', '_'), from, to, box_uv: false}).init();
            ['north', 'south', 'east', 'west', 'up', 'down'].forEach(face => {
                cube.faces[face].texture = tex[id].uuid;
                cube.faces[face].uv = [0, 0, 16, 16];
            });
            return cube;
        };
        blocks.forEach((id, i) => {
            const x = (i % 7) * 20, z = Math.floor(i / 7) * 20;
            place(id, [x, 0, z], [x + 16, 16, z + 16]);
        });
        items.forEach((id, i) => {
            const x = (i % 8) * 18, z = 120 + Math.floor(i / 8) * 18;
            place(id, [x, 0, z], [x + 16, 0, z + 16]);
        });
        Canvas.updateAll();
        const compiled = Codecs.project.compile();
        fs.writeFileSync(ROOT + 'blockbench/rebirth_textures.bbmodel', typeof compiled === 'string' ? compiled : JSON.stringify(compiled));
        return {blocks: blocks.length, items: items.length};
    }

    window.RebirthTextures = {TEXTURES, saveAll, sheet, showcase, clearPixels};
})();
