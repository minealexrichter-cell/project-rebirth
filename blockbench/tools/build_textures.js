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
    const block = (id, make) => { TEXTURES['block/' + id] = make; };
    const item = (id, make) => { TEXTURES['item/' + id] = make; };

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
            '................']}
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

    // ---------------------------------------------------------------- window and HUD parts

    // One 256x256 texture; client/GuiAtlas.java knows where each part is. The panel stays light: window text is dark.
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

        // Energy, a piece of the gauge: upright 8x4 and lying 4x8.
        rect(30, 34, 8, 3, '#ff8a3c'); rect(30, 34, 8, 1, '#ffc27a'); rect(30, 37, 8, 1, '#a8461c');
        rect(40, 34, 3, 8, '#ff8a3c'); rect(40, 34, 3, 1, '#ffc27a'); rect(43, 34, 1, 8, '#a8461c');

        // Status bar, a piece 8x7: light grey, tinted in the code; the last column is a notch.
        const greys = ['#ffffff', '#f2f2f2', '#e4e4e4', '#d8d8d8', '#cccccc', '#bfbfbf', '#a8a8a8'];
        greys.forEach((c, i) => rect(50, 34 + i, 8, 1, c));
        rect(57, 34, 1, 7, '#6f6f6f');
        return {canvas};
    };

    // ---------------------------------------------------------------- output

    function savePng(canvas, path) {
        const data = canvas.toDataURL('image/png').split(',')[1];
        fs.writeFileSync(path, Buffer.from(data, 'base64'));
    }

    function saveAll() {
        const ids = Object.keys(TEXTURES);
        fs.mkdirSync(ASSETS + 'gui', {recursive: true});
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
        const ids = Object.keys(TEXTURES).filter(id => !id.startsWith('gui/') && (!prefix || id.startsWith(prefix)));
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

    window.RebirthTextures = {TEXTURES, saveAll, sheet, showcase};
})();
