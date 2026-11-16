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

    /**
     * An inventory icon from a side view of a weapon model (a Blockbench capture, its ground clear): the ground made clear,
     * the weapon turned to point up and right as the game's own weapons do, and brought down to 16x16 a pixel at a time
     * (a pixel is kept where the weapon covers enough of it, in the average of its colours).
     */
    async function icon(capturePath, outPath, angle) {
        const img = new Image();
        await new Promise(r => { img.onload = r; img.src = 'data:image/png;base64,' + fs.readFileSync(capturePath).toString('base64'); });
        const c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        const x = c.getContext('2d');
        x.drawImage(img, 0, 0);
        const d = x.getImageData(0, 0, c.width, c.height);
        let minX = 1e9, minY = 1e9, maxX = -1, maxY = -1;
        for (let y = 0; y < c.height; y++) for (let i = 0; i < c.width; i++) {
            const k = (y * c.width + i) * 4;
            if (d.data[k + 3] < 10) { d.data[k + 3] = 0; continue; }
            minX = Math.min(minX, i); maxX = Math.max(maxX, i); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        x.putImageData(d, 0, 0);
        const w = maxX - minX + 1, h = maxY - minY + 1;
        const big = document.createElement('canvas');
        big.width = big.height = 128;
        const b = big.getContext('2d');
        b.imageSmoothingEnabled = false;
        b.translate(64, 64);
        b.rotate(-(angle || 40) * Math.PI / 180);
        b.scale(-1, 1);
        const fit = 128 / w;
        b.drawImage(c, minX, minY, w, h, -w * fit / 2, -h * fit / 2, w * fit, h * fit);
        const bd = b.getImageData(0, 0, 128, 128).data;
        const out = document.createElement('canvas');
        out.width = out.height = 16;
        const o = out.getContext('2d');
        const od = o.createImageData(16, 16);
        for (let py = 0; py < 16; py++) for (let px = 0; px < 16; px++) {
            let n = 0, r = 0, g = 0, bl = 0;
            for (let yy = 0; yy < 8; yy++) for (let xx = 0; xx < 8; xx++) {
                const k = ((py * 8 + yy) * 128 + px * 8 + xx) * 4;
                if (bd[k + 3] > 128) { n++; r += bd[k]; g += bd[k + 1]; bl += bd[k + 2]; }
            }
            const k = (py * 16 + px) * 4;
            if (n >= 22) { od.data[k] = Math.min(255, r / n * 1.35); od.data[k + 1] = Math.min(255, g / n * 1.35); od.data[k + 2] = Math.min(255, bl / n * 1.35); od.data[k + 3] = 255; }
        }
        // a dark outline round the weapon, as the game's items have
        const solid = (px, py) => px >= 0 && py >= 0 && px < 16 && py < 16 && od.data[(py * 16 + px) * 4 + 3] === 255;
        const edge = [];
        for (let py = 0; py < 16; py++) for (let px = 0; px < 16; px++) {
            if (!solid(px, py) && (solid(px - 1, py) || solid(px + 1, py) || solid(px, py - 1) || solid(px, py + 1))) edge.push([px, py]);
        }
        for (const [px, py] of edge) {
            const k = (py * 16 + px) * 4;
            od.data[k] = 24; od.data[k + 1] = 25; od.data[k + 2] = 28; od.data[k + 3] = 255;
        }
        o.putImageData(od, 0, 0);
        fs.writeFileSync(outPath, Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
        return [w, h];
    }

    window.RebirthDerived = {wreck, glowmask, icon};
})();
