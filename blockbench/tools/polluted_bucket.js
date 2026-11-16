// Paints the liquid of the bucket of polluted water (1.6.0, block C) inside Blockbench: the pixels where the game's water bucket
// differs from its empty bucket, in the murky green of polluted water, shaded like the water. The item model lays it over the
// game's own bucket texture (minecraft:item/bucket), so the bucket is the vanilla one and no vanilla texture is copied.
// Usage: extract assets/minecraft/textures/item/bucket.png and water_bucket.png from the Minecraft client jar into a folder, then
//   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/polluted_bucket.js', 'utf8'));
//   await RebirthPollutedBucket.paint('<that folder>/');
(function () {
    const fs = require('fs');
    const OUT = 'E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/textures/item/polluted_water_bucket_liquid.png';
    // the colours of the old icon: murk, its dark and its foam
    const MURK = [0x6b, 0x7a, 0x3c], DARK = [0x4f, 0x5a, 0x2b], FOAM = [0xa7, 0xb4, 0x56];

    function load(path) {
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => {
                const c = document.createElement('canvas');
                c.width = img.width;
                c.height = img.height;
                c.getContext('2d').drawImage(img, 0, 0);
                resolve(c.getContext('2d').getImageData(0, 0, img.width, img.height));
            };
            img.onerror = reject;
            img.src = 'data:image/png;base64,' + fs.readFileSync(path).toString('base64');
        });
    }

    async function paint(dir) {
        const empty = await load(dir + 'bucket.png'), water = await load(dir + 'water_bucket.png');
        const w = water.width, h = water.height;
        const out = document.createElement('canvas');
        out.width = w;
        out.height = h;
        const ctx = out.getContext('2d');
        const data = ctx.createImageData(w, h);
        // the brightness of the water pixels, to spread the three colours over them as the water's shades go
        const lights = [];
        const water_px = [];
        for (let i = 0; i < w * h; i++) {
            const o = i * 4;
            const same = [0, 1, 2, 3].every(k => empty.data[o + k] === water.data[o + k]);
            if (!same && water.data[o + 3] > 0) {
                const light = water.data[o] + water.data[o + 1] + water.data[o + 2];
                lights.push(light);
                water_px.push([o, light]);
            }
        }
        const sorted = [...new Set(lights)].sort((a, b) => a - b);
        for (const [o, light] of water_px) {
            const rank = sorted.length <= 1 ? 0.5 : sorted.indexOf(light) / (sorted.length - 1);
            const c = rank < 0.34 ? DARK : rank > 0.8 ? FOAM : MURK;
            data.data[o] = c[0];
            data.data[o + 1] = c[1];
            data.data[o + 2] = c[2];
            data.data[o + 3] = 255;
        }
        ctx.putImageData(data, 0, 0);
        fs.writeFileSync(OUT, Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
        return {pixels: water_px.length, shades: sorted.length};
    }

    window.RebirthPollutedBucket = {paint};
})();
