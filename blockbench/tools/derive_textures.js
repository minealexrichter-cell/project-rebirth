// Textures derived from an entity's own texture (1.6.0, part B3d), run inside Blockbench (it has a canvas):
//   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/derive_textures.js', 'utf8'));
//   await RebirthDerived.wreck('patrol_robot');            // textures/entity/patrol_robot_wreck.png
//   await RebirthDerived.glowmask('glowing_ghoul', test);  // textures/entity/glowing_ghoul_glowmask.png
// wreck: darker, scorched in patches, rust in the scratches, a few holes burnt black. glowmask: only the pixels that pass the
// test stay (GeckoLib draws them full bright), the rest is transparent.
(function () {
    const fs = require('fs');
    const DIR = 'E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/textures/entity/';

    async function load(id) {
        const img = new Image();
        await new Promise(r => { img.onload = r; img.src = 'data:image/png;base64,' + fs.readFileSync(DIR + id + '.png').toString('base64'); });
        const c = document.createElement('canvas');
        c.width = img.width;
        c.height = img.height;
        const x = c.getContext('2d');
        x.drawImage(img, 0, 0);
        return {c, x, d: x.getImageData(0, 0, c.width, c.height)};
    }

    function save(t, name) {
        t.x.putImageData(t.d, 0, 0);
        fs.writeFileSync(DIR + name + '.png', Buffer.from(t.c.toDataURL('image/png').split(',')[1], 'base64'));
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

    async function wreck(id, seed) {
        const t = await load(id);
        const {width: w, height: h} = t.c;
        const rand = random(seed || 7);
        // soot blotches and rust streaks, in texture space
        const soot = [], rust = [];
        for (let i = 0; i < w * h / 90; i++) soot.push([rand() * w, rand() * h, 1.5 + rand() * 3.5]);
        for (let i = 0; i < w * h / 160; i++) rust.push([rand() * w, rand() * h, 1 + rand() * 2.5]);
        const near = (list, x, y) => list.some(([cx, cy, r]) => (x - cx) * (x - cx) + (y - cy) * (y - cy) < r * r);
        const p = t.d.data;
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                const i = (y * w + x) * 4;
                if (p[i + 3] === 0) continue;
                let r = p[i] * 0.72, g = p[i + 1] * 0.7, b = p[i + 2] * 0.68;
                if (near(soot, x, y)) {
                    const k = 0.35 + rand() * 0.2;
                    r *= k; g *= k; b *= k;
                } else if (near(rust, x, y)) {
                    r = r * 0.4 + 120 * 0.6; g = g * 0.4 + 62 * 0.6; b = b * 0.4 + 34 * 0.6;
                }
                const n = 1 + (rand() - 0.5) * 0.12;
                p[i] = Math.min(255, r * n); p[i + 1] = Math.min(255, g * n); p[i + 2] = Math.min(255, b * n);
            }
        }
        save(t, id + '_wreck');
        return id + '_wreck';
    }

    async function glowmask(id, test) {
        const t = await load(id);
        const p = t.d.data;
        let kept = 0;
        for (let i = 0; i < p.length; i += 4) {
            if (p[i + 3] > 0 && test(p[i], p[i + 1], p[i + 2])) kept++;
            else p[i + 3] = 0;
        }
        save(t, id + '_glowmask');
        return kept;
    }

    window.RebirthDerived = {wreck, glowmask};
})();
