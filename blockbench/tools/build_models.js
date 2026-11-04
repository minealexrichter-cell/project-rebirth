// Builds the Project: Rebirth entity models inside Blockbench (GeckoLib Animated Model projects).
// Usage in Blockbench's console or through the MCP risky_eval tool, with a new geckolib_model project active:
//   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/build_models.js', 'utf8'));
//   RebirthModels.build('basic_robot_body');
// Then, in a SEPARATE call: RebirthModels.save('basic_robot_body'). The texture is not ready within the build call, and
// saving there writes a blank PNG (the model is invisible in game).
// All coordinates below are geo.json coordinates (the character faces -Z and its right side is -X); PR.build converts
// them to Blockbench's. The bones and sizes follow docs/MODEL_SPEC.md: the main arm cubes of bodies use the player skin
// layout, and the arm cubes of armors must match the table in client/render/ArmorArmRenderer.java.
(function () {
    const fs = require('fs');
    const ROOT = 'E:/Projects/Project-Rebirth-mod/';
    const ASSETS = ROOT + 'src/main/resources/assets/projectrebirth/';

    // ------------------------------------------------------------------ helpers

    function rgb(hex) {
        const n = parseInt(hex.slice(1), 16);
        return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
    }

    function shade(hex, f) {
        const [r, g, b] = rgb(hex);
        const c = v => Math.max(0, Math.min(255, Math.round(v * f))).toString(16).padStart(2, '0');
        return '#' + c(r) + c(g) + c(b);
    }

    /** A cube: origin, size, paint; opts may hold uv, inflate, rot (Blockbench degrees) and pivot. */
    function C(o, s, p, opts) {
        return Object.assign({o, s, p}, opts || {});
    }

    /** The same cube on the other side of the body; shares its texture area unless it has its own uv. */
    function mirror(c, uv) {
        const m = Object.assign({}, c, {o: [-(c.o[0] + c.s[0]), c.o[1], c.o[2]]});
        if (uv) {
            m.uv = uv;
        } else {
            m.share = c;
            delete m.uv;
        }
        if (c.pivot) {
            m.pivot = [-c.pivot[0], c.pivot[1], c.pivot[2]];
        }
        if (c.rot) {
            m.rot = [c.rot[0], -c.rot[1], -c.rot[2]];
        }
        return m;
    }

    /** A right-side bone and its mirror. uvLeft: {index in cubes: uv} for cubes with their own area on the left. */
    function limbs(name, parent, pivot, cubes, uvLeft) {
        const left = cubes.map((c, i) => mirror(c, uvLeft && uvLeft[i]));
        return [
            {name: 'right_' + name, parent, pivot, cubes},
            {name: 'left_' + name, parent, pivot: [-pivot[0], pivot[1], pivot[2]], cubes: left}
        ];
    }

    /** Diagonal hazard stripes as paint operations. */
    function hazard(x, y, w, h, a, b, period) {
        const ops = [];
        for (let i = 0; i < w; i++) {
            for (let j = 0; j < h; j++) {
                ops.push([x + i, y + j, 1, 1, (i + j) % (period * 2) < period ? a : b]);
            }
        }
        return ops;
    }

    /** Vertical rows of small lines: vents and grilles. */
    function vents(x, y, w, rows, col, step) {
        const ops = [];
        for (let i = 0; i < rows; i++) {
            ops.push([x, y + i * (step || 2), w, 1, col]);
        }
        return ops;
    }

    // ------------------------------------------------------------------ painting

    function random(seed) {
        let s = seed >>> 0 || 1;
        return () => {
            s ^= s << 13; s >>>= 0;
            s ^= s >>> 17;
            s ^= s << 5; s >>>= 0;
            return s / 4294967296;
        };
    }

    function netOf(u, v, [w, h, d]) {
        return {
            top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
            east: [u, v + d, d, h], north: [u + d, v + d, w, h],
            west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h]
        };
    }

    function paintFace(ctx, rect, p, face, rand) {
        const [x, y, w, h] = rect;
        if (w <= 0 || h <= 0) {
            return;
        }
        ctx.save();
        ctx.beginPath();
        ctx.rect(x, y, w, h);
        ctx.clip();
        const base = (p.faces && p.faces[face]) || p.c;
        ctx.fillStyle = base;
        ctx.fillRect(x, y, w, h);
        const noise = p.n === undefined ? 0.05 : p.n;
        if (noise > 0) {
            for (let i = 0; i < w; i++) {
                for (let j = 0; j < h; j++) {
                    const f = 1 + (rand() - 0.5) * 2 * noise;
                    ctx.fillStyle = shade(base, f);
                    ctx.fillRect(x + i, y + j, 1, 1);
                }
            }
        }
        if (p.edge !== false && w >= 3 && h >= 3) {
            const dark = shade(base, 0.72);
            ctx.fillStyle = dark;
            ctx.fillRect(x, y + h - 1, w, 1);
            ctx.fillRect(x + w - 1, y, 1, h);
            ctx.fillStyle = shade(base, 1.18);
            ctx.fillRect(x, y, w - 1, 1);
            ctx.fillRect(x, y, 1, h - 1);
        }
        const keys = {north: 'f', south: 'k', east: 'r', west: 'l', top: 'tp', bottom: 'bt'};
        const ops = [];
        if (face !== 'top' && face !== 'bottom' && p.all) {
            ops.push(...p.all);
        }
        if ((face === 'east' || face === 'west') && p.sd) {
            ops.push(...p.sd);
        }
        if (p[keys[face]]) {
            ops.push(...p[keys[face]]);
        }
        for (const [ox, oy, ow, oh, col] of ops) {
            ctx.fillStyle = col;
            ctx.fillRect(x + (ox < 0 ? w + ox : ox), y + (oy < 0 ? h + oy : oy), ow, oh);
        }
        ctx.restore();
    }

    /** Gives every cube a texture area: fixed uvs first, then first free place, mirrored cubes reuse their partner's. */
    function pack(cubes, res) {
        // A mirrored cube whose partner is not part of the model gets its own area.
        cubes.filter(c => c.share && !cubes.includes(c.share)).forEach(c => { delete c.share; });
        const used = new Uint8Array(res * res);
        const size = c => [Math.ceil(2 * (c.s[0] + c.s[2])), Math.ceil(c.s[2] + c.s[1])];
        const mark = (u, v, w, h) => {
            for (let y = v; y < v + h; y++) {
                for (let x = u; x < u + w; x++) {
                    if (x >= res || y >= res) {
                        throw new Error('Texture area outside the texture at ' + u + ',' + v);
                    }
                    if (used[y * res + x]) {
                        throw new Error('Texture areas overlap at ' + x + ',' + y);
                    }
                    used[y * res + x] = 1;
                }
            }
        };
        const free = (u, v, w, h) => {
            if (u + w > res || v + h > res) {
                return false;
            }
            for (let y = v; y < v + h; y++) {
                for (let x = u; x < u + w; x++) {
                    if (used[y * res + x]) {
                        return false;
                    }
                }
            }
            return true;
        };
        cubes.filter(c => c.uv).forEach(c => mark(c.uv[0], c.uv[1], ...size(c)));
        // Biggest first, so the small parts fill the gaps.
        const area = c => size(c)[0] * size(c)[1];
        for (const c of [...cubes].sort((a, b) => area(b) - area(a))) {
            if (c.uv || c.share) {
                continue;
            }
            const [w, h] = size(c);
            let placed = false;
            for (let v = 0; v <= res - h && !placed; v++) {
                for (let u = 0; u <= res - w && !placed; u++) {
                    if (free(u, v, w, h)) {
                        mark(u, v, w, h);
                        c.uv = [u, v];
                        placed = true;
                    }
                }
            }
            if (!placed) {
                throw new Error('No texture space for a cube of size ' + c.s);
            }
        }
        cubes.filter(c => c.share).forEach(c => { c.uv = c.share.uv; });
    }

    function paintTexture(spec, cubes) {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = spec.res;
        const ctx = canvas.getContext('2d');
        const rand = random(spec.seed || 12345);
        for (const c of cubes) {
            if (c.share) {
                continue;
            }
            const net = netOf(c.uv[0], c.uv[1], c.s.map(Math.ceil));
            for (const face of Object.keys(net)) {
                paintFace(ctx, net[face], c.p, face, rand);
            }
        }
        return canvas;
    }

    /** The burnt look of a wreck: same texture areas, dark, scorched, with a few glowing burns. */
    function wreckedFrom(canvas, seed) {
        const out = document.createElement('canvas');
        out.width = canvas.width;
        out.height = canvas.height;
        const ctx = out.getContext('2d');
        ctx.drawImage(canvas, 0, 0);
        const image = ctx.getImageData(0, 0, out.width, out.height);
        const rand = random(seed);
        const blots = [];
        for (let i = 0; i < 90; i++) {
            blots.push([rand() * out.width, rand() * out.height, 1.5 + rand() * 5]);
        }
        for (let y = 0; y < out.height; y++) {
            for (let x = 0; x < out.width; x++) {
                const a = (y * out.width + x) * 4;
                if (image.data[a + 3] === 0) {
                    continue;
                }
                const grey = (image.data[a] + image.data[a + 1] + image.data[a + 2]) / 3;
                let k = 0.4 + rand() * 0.12;
                let burn = false;
                for (const [bx, by, br] of blots) {
                    const dist = Math.hypot(x - bx, y - by);
                    if (dist < br) {
                        k *= 0.45;
                        burn = burn || (dist < 1 && br > 5);
                    }
                }
                image.data[a] = burn ? 214 : Math.min(255, (grey * 0.75 + image.data[a] * 0.25) * k + 8);
                image.data[a + 1] = burn ? 92 : (grey * 0.75 + image.data[a + 1] * 0.25) * k + 6;
                image.data[a + 2] = burn ? 24 : (grey * 0.75 + image.data[a + 2] * 0.25) * k + 4;
            }
        }
        ctx.putImageData(image, 0, 0);
        return out;
    }

    // ------------------------------------------------------------------ animation shorthands

    /** A value at evenly spaced moments of the loop. */
    function steps(length, values) {
        const out = {};
        values.forEach((v, i) => { out[(length * i / (values.length - 1)).toFixed(3)] = v; });
        return out;
    }

    function walk(c) {
        const l = c.length, s = c.stride, a = c.arm, k = c.armZ || 0;
        const bones = {
            right_leg: {r: steps(l, [[s, 0, 0], [-s, 0, 0], [s, 0, 0]])},
            left_leg: {r: steps(l, [[-s, 0, 0], [s, 0, 0], [-s, 0, 0]])},
            right_arm: {r: steps(l, [[-a, 0, k], [a, 0, k], [-a, 0, k]])},
            left_arm: {r: steps(l, [[a, 0, -k], [-a, 0, -k], [a, 0, -k]])},
            torso: {
                p: steps(l, [[0, 0, 0], [0, c.bob, 0], [0, 0, 0], [0, c.bob, 0], [0, 0, 0]]),
                r: steps(l, [[c.lean || 0, c.twist || 0, c.roll], [c.lean || 0, 0, 0], [c.lean || 0, -(c.twist || 0), -c.roll], [c.lean || 0, 0, 0], [c.lean || 0, c.twist || 0, c.roll]])
            }
        };
        return {length: l, loop: 'loop', ease: c.ease, bones};
    }

    function idle(c) {
        const l = c.length;
        const bones = {
            torso: {p: steps(l, [[0, 0, 0], [0, c.bob, 0], [0, 0, 0]])},
            right_arm: {r: steps(l, [[0, 0, 0], [c.arm, 0, c.armZ], [0, 0, 0]])},
            left_arm: {r: steps(l, [[0, 0, 0], [c.arm, 0, -c.armZ], [0, 0, 0]])}
        };
        if (c.sway) {
            bones.torso.r = steps(l, [[0, 0, 0], [0, 0, c.sway], [0, 0, 0], [0, 0, -c.sway], [0, 0, 0]]);
        }
        if (c.antenna) {
            bones.antenna = {r: steps(l, [[0, 0, 0], [0, 0, c.antenna], [0, 0, 0], [0, 0, -c.antenna], [0, 0, 0]])};
        }
        return {length: l, loop: 'loop', ease: c.ease, bones};
    }

    function bodyAnimations(cfg) {
        return {
            'animation.body.idle': idle(cfg.idle),
            'animation.body.walk': walk(cfg.walk),
            'animation.body.interact': cfg.interact
        };
    }

    // ------------------------------------------------------------------ artificial bodies

    /** The bones every body shares; the main cubes keep the player skin layout (see MODEL_SPEC). */
    function bodyBones(b) {
        const armW = b.slim ? 3 : 4;
        const armX = b.slim ? -7 : -8;
        const pivotX = b.slim ? -5.5 : -6;
        return [
            {name: 'root', pivot: [0, 0, 0], cubes: []},
            ...limbs('leg', 'root', [-2, 12, 0],
                [C([-4, 0, -2], [4, 12, 4], b.leg, {uv: [0, 16]}), ...b.legX], {0: [16, 48]}),
            {name: 'torso', parent: 'root', pivot: [0, 12, 0],
                cubes: [C([-4, 12, -2], [8, 12, 4], b.torso, {uv: [16, 16]}), ...b.torsoX]},
            {name: 'head', parent: 'torso', pivot: [0, 24, 0],
                cubes: [C([-4, 24, -4], [8, 8, 8], b.head, {uv: [0, 0]}), ...b.headX]},
            ...(b.antenna ? [{name: 'antenna', parent: 'head', pivot: b.antenna.pivot, cubes: b.antenna.cubes}] : []),
            ...limbs('arm', 'torso', [pivotX, 22, 0],
                [C([armX, 12, -2], [armW, 12, 4], b.arm, {uv: [40, 16]}), ...b.armX], {0: [32, 48]})
        ];
    }

    const INTERACT_NOD = {length: 0.7, loop: 'once', ease: 'easeInOutSine', bones: {
        torso: {r: {'0.000': [0, 0, 0], '0.300': [10, 0, 0], '0.700': [0, 0, 0]}}
    }};

    const SPECS = {};

    // 1. Basic robot: a cheap welded machine. Scratched grey steel, rust, one amber eye slot, exposed joints.
    (function () {
        const steel = '#7d848c', dark = '#3a3f46', joint = '#4c5158', rust = '#8c5a38', amber = '#ffb02e';
        SPECS.basic_robot_body = {
            res: 64, seed: 101,
            bones: bodyBones({
                head: {c: steel, n: 0.08,
                    f: [[1, 2, 6, 2, dark], [2, 2, 4, 1, amber], [3, 3, 2, 1, shade(amber, 0.7)],
                        ...vents(2, 5, 4, 2, dark), [0, 0, 8, 1, shade(steel, 1.2)], [0, 7, 2, 1, rust]],
                    sd: [[2, 2, 4, 3, shade(steel, 0.8)], [3, 3, 2, 1, dark], [0, 6, 3, 2, rust]],
                    k: [[2, 1, 4, 3, shade(steel, 0.75)], [3, 5, 2, 1, dark]],
                    tp: [[1, 1, 6, 6, shade(steel, 0.9)], [3, 3, 2, 2, dark]]},
                headX: [
                    C([-5, 26, -1.5], [1, 3, 3], {c: joint, edge: false}),
                    mirror(C([-5, 26, -1.5], [1, 3, 3], {c: joint, edge: false})),
                    C([-2.5, 24, -5], [5, 2, 1], {c: dark, f: [[1, 0, 1, 2, joint], [3, 0, 1, 2, joint]], edge: false})
                ],
                antenna: {pivot: [2.5, 32, 0], cubes: [
                    C([2, 32, -0.5], [1, 4, 1], {c: dark, edge: false}),
                    C([1.5, 36, -1], [2, 1, 2], {c: '#c9352b', edge: false})
                ]},
                torso: {c: steel, n: 0.08,
                    f: [[0, 0, 8, 1, shade(steel, 1.2)], [3, 8, 2, 2, dark], [0, -2, 8, 2, joint], [6, 4, 2, 3, rust]],
                    k: [[1, 1, 6, 8, shade(steel, 0.8)], ...vents(2, 2, 4, 3, dark)],
                    sd: [[1, 2, 2, 7, shade(steel, 0.75)]]},
                torsoX: [
                    C([-3, 17, -3], [6, 5, 1], {c: shade(steel, 0.85),
                        f: [[1, 1, 2, 2, '#42e06a'], [4, 1, 1, 1, '#d94c3a'], [4, 3, 1, 1, amber], [1, 3, 2, 1, dark]]}),
                    C([-3, 14, 2], [6, 8, 2], {c: rust, n: 0.12,
                        k: [...vents(1, 1, 4, 3, dark)]}),
                    C([-4.5, 12, -2.5], [9, 2, 5], {c: joint, f: [[1, 0, 1, 2, dark], [7, 0, 1, 2, dark]]})
                ],
                arm: {c: steel, n: 0.08, all: [[0, 3, 4, 2, joint], [0, -3, 4, 1, dark], [0, -1, 4, 1, dark]]},
                armX: [
                    C([-8.5, 21.5, -2.5], [5, 3, 5], {c: shade(steel, 0.9), n: 0.1, all: [[0, -1, 5, 1, dark]]}),
                    C([-8.5, 16, -2.5], [5, 1, 5], {c: joint, edge: false})
                ],
                leg: {c: steel, n: 0.08, all: [[0, 0, 4, 2, joint], [0, 5, 4, 2, joint], [0, -1, 4, 1, dark]]},
                legX: [
                    C([-4.5, 6, -2.5], [5, 2, 5], {c: rust, n: 0.12}),
                    C([-4.5, 0, -3], [5, 2, 6], {c: dark, f: [[1, 0, 3, 1, joint]]})
                ]
            }),
            animations: bodyAnimations({
                idle: {length: 3, bob: -0.2, arm: -2, armZ: 1, antenna: 14},
                walk: {length: 1.1, stride: 22, arm: 16, bob: -0.5, roll: 2.5},
                interact: {length: 0.8, loop: 'once', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.150': [-150, 0, 10], '0.300': [-150, 0, -6], '0.450': [-150, 0, 10], '0.800': [0, 0, 0]}},
                    antenna: {r: {'0.000': [0, 0, 0], '0.200': [0, 0, 22], '0.400': [0, 0, -22], '0.800': [0, 0, 0]}}
                }}
            })
        };
    })();

    // 2. Improved robot: a factory-finished machine. White shell, blue plates, a glowing core and a sensor band.
    (function () {
        const white = '#dfe5ea', blue = '#2f6fc4', dark = '#20262e', cyan = '#55e8ff', grey = '#8a939c';
        SPECS.improved_robot_body = {
            res: 64, seed: 202,
            bones: bodyBones({
                head: {c: white, n: 0.03,
                    f: [[0, 2, 8, 3, dark], [1, 3, 2, 1, cyan], [5, 3, 2, 1, cyan], [2, 6, 4, 1, grey], [3, 0, 2, 2, blue]],
                    sd: [[0, 2, 8, 3, dark], [3, 5, 2, 2, blue]],
                    k: [[3, 0, 2, 8, blue], [1, 5, 6, 1, grey]],
                    tp: [[3, 0, 2, 8, blue]]},
                headX: [
                    C([-1, 32, -3], [2, 1, 6], {c: blue, n: 0.03}),
                    C([-5, 26.5, -1], [1, 2, 2], {c: blue, sd: [[0, 0, 2, 2, cyan]], edge: false}),
                    mirror(C([-5, 26.5, -1], [1, 2, 2], {c: blue, sd: [[0, 0, 2, 2, cyan]], edge: false}))
                ],
                antenna: {pivot: [3, 32, 2], cubes: [
                    C([2.5, 32, 1.5], [1, 3, 1], {c: grey, edge: false})
                ]},
                torso: {c: white, n: 0.03,
                    f: [[0, 7, 8, 5, blue], [3, 7, 2, 5, dark], [1, 0, 6, 1, grey]],
                    k: [[1, 1, 6, 6, grey], [0, 7, 8, 5, blue]],
                    sd: [[0, 7, 4, 5, blue]]},
                torsoX: [
                    C([-3, 18, -3], [6, 4, 1], {c: white, n: 0.03, f: [[2, 1, 2, 2, cyan]]}),
                    C([-1.5, 18.5, -3.25], [3, 3, 0.5], {c: cyan, n: 0, edge: false}),
                    C([-3, 15, 2], [6, 8, 3], {c: blue, n: 0.04, k: [...vents(1, 1, 4, 3, cyan)]}),
                    C([-4.5, 12, -2.5], [9, 1, 5], {c: dark, n: 0, edge: false})
                ],
                arm: {c: white, n: 0.03, all: [[0, 4, 4, 1, dark], [0, -5, 4, 1, grey]]},
                armX: [
                    C([-9, 21, -3], [5, 4, 6], {c: blue, n: 0.04, sd: [[1, 1, 4, 2, cyan]], f: [[0, 0, 5, 1, white]]}),
                    C([-8.5, 13, -2.5], [5, 4, 5], {c: blue, n: 0.04})
                ],
                leg: {c: white, n: 0.03, all: [[0, 5, 4, 1, dark], [0, -1, 4, 1, dark]]},
                legX: [
                    C([-4.5, 2, -2.5], [5, 4, 1], {c: blue, n: 0.04, f: [[2, 0, 1, 4, cyan]]}),
                    C([-4.5, 0, -3.5], [5, 2, 6], {c: dark, n: 0.04, tp: [[0, 0, 5, 2, grey]]}),
                    C([-4.5, 8, -2.5], [5, 3, 1], {c: blue, n: 0.04})
                ]
            }),
            animations: bodyAnimations({
                idle: {length: 3.5, bob: -0.25, arm: -3, armZ: 2, antenna: 8, ease: 'easeInOutSine'},
                walk: {length: 0.9, stride: 30, arm: 26, bob: -0.5, roll: 2, ease: 'easeInOutSine'},
                interact: {length: 0.9, loop: 'once', ease: 'easeInOutSine', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.250': [-90, 25, 0], '0.650': [-90, 25, 0], '0.900': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.250': [0, 8, 0], '0.650': [0, 8, 0], '0.900': [0, 0, 0]}}
                }}
            })
        };
    })();

    // 3. Advanced synthetic: a mannequin. Pale polymer skin with visible seams, grey suit, simple glowing eyes.
    (function () {
        const skin = '#d9d4cc', seam = '#a59f96', suit = '#59616b', dark = '#333941', cyan = '#6fe3f5';
        SPECS.advanced_synthetic_body = {
            res: 64, seed: 303,
            bones: bodyBones({
                slim: true,
                head: {c: skin, n: 0.02,
                    f: [[1, 3, 2, 1, cyan], [5, 3, 2, 1, cyan], [3, 4, 2, 1, seam], [3, 6, 2, 1, seam], [0, 1, 8, 1, seam], [0, 0, 8, 1, shade(skin, 0.92)]],
                    sd: [[0, 1, 8, 1, seam], [3, 2, 1, 6, seam], [5, 3, 1, 2, shade(skin, 0.85)]],
                    k: [[0, 1, 8, 1, seam], [3, 1, 2, 7, shade(skin, 0.9)], [3, 4, 2, 1, seam]],
                    tp: [[3, 0, 2, 8, seam]]},
                headX: [],
                torso: {c: suit, n: 0.03,
                    f: [[3, 0, 2, 12, dark], [1, 2, 6, 1, skin], [1, 9, 6, 1, dark], [3, 3, 2, 2, cyan], [0, -1, 8, 1, dark]],
                    k: [[3, 0, 2, 12, dark], [0, 3, 8, 1, skin]],
                    sd: [[1, 0, 2, 12, dark]]},
                torsoX: [
                    C([-3, 23, -2.5], [6, 1, 5], {c: skin, n: 0.02, edge: false}),
                    C([-2, 18, 2], [4, 4, 1], {c: dark, k: [[1, 1, 2, 2, cyan]], edge: false})
                ],
                arm: {c: skin, n: 0.02,
                    all: [[0, 2, 4, 1, seam], [0, 6, 4, 1, seam], [0, 3, 4, 3, suit], [0, 9, 4, 1, seam], [0, 10, 4, 1, dark]]},
                armX: [],
                leg: {c: suit, n: 0.03,
                    all: [[0, 5, 4, 1, seam], [0, -3, 4, 3, dark]], f: [[1, 0, 1, 12, dark]]},
                legX: [C([-4.25, 0, -2.75], [4.5, 2, 5], {c: dark, n: 0.03, tp: [[0, 0, 5, 1, skin]]})]
            }),
            animations: bodyAnimations({
                idle: {length: 4, bob: -0.2, arm: -3, armZ: 2, sway: 0.6, ease: 'easeInOutSine'},
                walk: {length: 1, stride: 34, arm: 30, bob: -0.35, roll: 1, ease: 'easeInOutSine'},
                interact: INTERACT_NOD
            })
        };
    })();

    // 4. High-tech synthetic: a dark bodysuit with glowing circuit lines, a silver crop of hair and a light band for eyes.
    (function () {
        const skin = '#c7b8aa', shadow = '#a3948a', suit = '#20242c', plate = '#333945', cyan = '#3df2ff', hair = '#c4ccd6';
        const circuit = [[1, 1, 1, 5, cyan], [1, 6, 3, 1, cyan], [6, 2, 1, 6, cyan], [4, 8, 3, 1, cyan]];
        SPECS.hightech_synthetic_body = {
            res: 64, seed: 404,
            bones: bodyBones({
                slim: true,
                head: {c: skin, n: 0.02,
                    f: [[0, 0, 8, 2, hair], [1, 3, 6, 1, cyan], [3, 5, 2, 1, shadow], [3, 6, 2, 1, shade(shadow, 0.8)], [0, 2, 1, 2, hair], [7, 2, 1, 2, hair]],
                    sd: [[0, 0, 8, 3, hair], [0, 3, 3, 2, hair], [5, 3, 1, 2, shadow]],
                    k: [[0, 0, 8, 6, hair], [0, 6, 8, 1, shade(hair, 0.8)]],
                    tp: [[0, 0, 8, 8, hair]], bt: [[0, 0, 8, 8, skin]]},
                headX: [
                    C([-4, 30, -4], [8, 2, 8], {c: hair, n: 0.07, edge: false, f: [[1, 0, 2, 1, shade(hair, 1.15)]]}, {inflate: 0.3})
                ],
                torso: {c: suit, n: 0.02,
                    f: [...circuit, [3, 2, 2, 2, cyan], [0, -2, 8, 1, plate]],
                    k: [...circuit, [3, 0, 2, 12, plate]],
                    sd: [[1, 2, 1, 7, cyan]]},
                torsoX: [
                    C([-3, 22, -3], [6, 2, 6], {c: plate, n: 0.03, f: [[0, 1, 6, 1, cyan]], edge: false}),
                    C([-4.25, 13, -2.25], [8.5, 1, 4.5], {c: plate, n: 0.02, f: [[3, 0, 2, 1, cyan]], edge: false})
                ],
                arm: {c: suit, n: 0.02,
                    all: [[0, 0, 4, 3, plate], [1, 3, 1, 6, cyan], [0, -3, 4, 1, cyan], [0, -2, 4, 2, skin]]},
                armX: [C([-7.5, 21.5, -2.5], [4, 3, 5], {c: plate, n: 0.03, sd: [[0, 1, 5, 1, cyan]]})],
                leg: {c: suit, n: 0.02,
                    all: [[1, 0, 1, 7, cyan], [0, -4, 4, 4, plate]], f: [[1, 7, 2, 1, cyan]]},
                legX: [C([-4.25, 0, -3], [4.5, 2, 5.5], {c: plate, n: 0.03, f: [[0, 1, 5, 1, cyan]]})]
            }),
            animations: bodyAnimations({
                idle: {length: 4.5, bob: -0.25, arm: -2, armZ: 3, sway: 0.8, ease: 'easeInOutSine'},
                walk: {length: 0.85, stride: 36, arm: 24, bob: -0.3, roll: 1.2, lean: 2, ease: 'easeInOutSine'},
                interact: {length: 1, loop: 'once', ease: 'easeInOutSine', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.300': [-75, -35, 0], '0.700': [-75, -35, 0], '1.000': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.300': [6, 0, 0], '0.700': [6, 0, 0], '1.000': [0, 0, 0]}}
                }}
            })
        };
    })();

    // 5. Premium synthetic: nearly human. A real face, dark hair, an elegant white jacket with gold trim.
    (function () {
        const skin = '#e3b799', shadow = '#c99b7e', hair = '#3e2a20', white = '#e9e7e2', gold = '#d6a83a', dark = '#2c2d36', eye = '#2f6fb0';
        SPECS.premium_synthetic_body = {
            res: 64, seed: 505,
            bones: bodyBones({
                slim: true,
                head: {c: skin, n: 0.015, edge: false,
                    f: [[0, 0, 8, 2, hair], [0, 2, 1, 1, hair], [7, 2, 1, 1, hair], [2, 2, 1, 1, hair],
                        [1, 3, 2, 1, '#ffffff'], [5, 3, 2, 1, '#ffffff'], [2, 3, 1, 1, eye], [5, 3, 1, 1, eye],
                        [3, 4, 2, 1, shadow], [3, 6, 2, 1, '#b5655a'], [1, 2, 2, 1, shade(hair, 1.2)], [5, 2, 2, 1, shade(hair, 1.2)]],
                    sd: [[0, 0, 8, 3, hair]],
                    r: [[0, 3, 4, 3, hair], [4, 4, 1, 2, shadow]], l: [[4, 3, 4, 3, hair], [3, 4, 1, 2, shadow]],
                    k: [[0, 0, 8, 7, hair], [0, 7, 8, 1, shade(hair, 0.85)]],
                    tp: [[0, 0, 8, 8, hair]], bt: [[0, 0, 8, 8, shadow]]},
                headX: [
                    C([-4, 30, -4], [8, 2, 8], {c: hair, n: 0.05, edge: false, f: [[0, 0, 8, 1, shade(hair, 1.2)]],
                        k: [[1, 1, 2, 2, shade(hair, 1.2)]]}, {inflate: 0.25}),
                    C([-4, 24.5, 3], [8, 4, 1], {c: hair, n: 0.05, edge: false}, {inflate: 0.25})
                ],
                torso: {c: white, n: 0.015,
                    f: [[3, 0, 2, 3, dark], [3, 0, 1, 1, skin], [4, 0, 1, 1, skin], [3, 3, 2, 8, shade(white, 0.92)], [2, 0, 1, 11, gold], [5, 0, 1, 11, gold],
                        [0, -2, 8, 1, dark], [3, -2, 2, 1, gold], [6, 2, 1, 1, gold]],
                    k: [[3, 1, 2, 9, shade(white, 0.9)], [0, -2, 8, 1, dark]],
                    sd: [[0, -2, 4, 1, dark]]},
                torsoX: [
                    C([-3.25, 22.5, -2.5], [6.5, 1.5, 5], {c: white, n: 0.015, edge: false, f: [[0, 0, 7, 1, gold], [2, 0, 3, 2, skin]]}),
                    C([-4.25, 12, -2.25], [8.5, 2.5, 4.5], {c: white, n: 0.015, edge: false, f: [[3, 0, 3, 3, shade(white, 0.92)], [0, 2, 9, 1, gold]]})
                ],
                arm: {c: white, n: 0.015,
                    all: [[0, 0, 4, 1, gold], [0, -4, 4, 1, gold], [0, -3, 4, 3, skin]]},
                armX: [],
                leg: {c: dark, n: 0.02, all: [[0, -3, 4, 3, shade(dark, 0.7)], [0, 0, 4, 1, shade(dark, 1.2)]], f: [[2, 1, 1, 8, shade(dark, 1.3)]]},
                legX: [C([-4.25, 0, -3], [4.5, 2, 5.5], {c: '#1c1a1e', n: 0.02, f: [[0, 0, 5, 1, gold]]})]
            }),
            animations: bodyAnimations({
                idle: {length: 5, bob: -0.2, arm: -2.5, armZ: 2.5, sway: 1.2, ease: 'easeInOutSine'},
                walk: {length: 1, stride: 38, arm: 34, bob: -0.25, roll: 1.5, twist: 3, ease: 'easeInOutSine'},
                interact: {length: 1.2, loop: 'once', ease: 'easeInOutSine', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.300': [-160, 0, 12], '0.500': [-160, 0, -12], '0.700': [-160, 0, 12], '0.900': [-160, 0, -4], '1.200': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.300': [0, 0, -3], '0.900': [0, 0, -3], '1.200': [0, 0, 0]}}
                }}
            })
        };
    })();

    // ------------------------------------------------------------------ power armors

    function armorBones(a) {
        return [
            {name: 'root', pivot: [0, 0, 0], cubes: []},
            ...limbs('leg', 'root', a.legPivot, a.leg),
            {name: 'torso', parent: 'root', pivot: [0, 16, 0], cubes: a.torso},
            {name: 'head', parent: 'torso', pivot: a.headPivot, cubes: a.head},
            ...limbs('arm', 'torso', a.armPivot, a.arm, a.armLeftUv),
            {name: 'right_hand', parent: 'right_arm', pivot: a.hand, cubes: []},
            {name: 'left_hand', parent: 'left_arm', pivot: [-a.hand[0], a.hand[1], a.hand[2]], cubes: []}
        ];
    }

    /** Uv areas of the three arm cubes that the first-person arm also draws; see ArmorArmRenderer. */
    const ARM_UV = {shoulderR: [0, 100], armR: [40, 100], fistR: [70, 100], shoulderL: [0, 70], armL: [40, 70], fistL: [70, 70]};
    const ARM_LEFT_UV = {0: ARM_UV.shoulderL, 1: ARM_UV.armL, 2: ARM_UV.fistL};

    /** A value on a closed curve: smooth between the [phase, value] points; phase 0..1 wraps around. */
    function curve(points, phase) {
        const t = ((phase % 1) + 1) % 1;
        for (let i = 0; i < points.length; i++) {
            const [a, va] = points[i];
            const [b, vb] = points[i + 1] || [points[0][0] + 1, points[0][1]];
            if (t >= a && t < b) {
                const k = (t - a) / (b - a);
                return va + (vb - va) * (1 - Math.cos(k * Math.PI)) / 2;
            }
        }
        return points[0][1];
    }

    /**
     * One flowing movement through the given moments, sampled often. An easing on each keyframe would stop the limb at
     * every keyframe; here it keeps its speed through them and only rests at the start and the end.
     */
    function flowing(length, bones) {
        const STEP = 0.025;
        const sample = points => {
            const times = Object.keys(points).map(parseFloat).sort((a, b) => a - b);
            const values = times.map(t => points[t.toFixed(3)]);
            const slope = (i, axis) => (i === 0 || i === times.length - 1) ? 0
                : (values[i + 1][axis] - values[i - 1][axis]) / (times[i + 1] - times[i - 1]);
            const out = {};
            const count = Math.round(length / STEP);
            for (let n = 0; n <= count; n++) {
                const t = n === count ? length : n * STEP;
                let i = 0;
                while (i < times.length - 2 && t > times[i + 1]) {
                    i++;
                }
                const h = times[i + 1] - times[i], k = (t - times[i]) / h;
                out[t.toFixed(3)] = [0, 1, 2].map(axis => {
                    const a = values[i][axis], b = values[i + 1][axis];
                    const value = (2 * k * k * k - 3 * k * k + 1) * a + (k * k * k - 2 * k * k + k) * h * slope(i, axis)
                        + (-2 * k * k * k + 3 * k * k) * b + (k * k * k - k * k) * h * slope(i + 1, axis);
                    return Math.round(value * 100) / 100;
                });
            }
            return out;
        };
        const out = {};
        for (const [bone, channels] of Object.entries(bones)) {
            out[bone] = {};
            for (const [channel, points] of Object.entries(channels)) {
                out[bone][channel] = sample(points);
            }
        }
        return {length, loop: 'once', bones: out};
    }

    /**
     * A walk with knees and elbows, sampled from curves. Phase 0: the right foot lands in front.
     * Thigh and upper arm: negative is forward. Shin: positive bends the knee back. Forearm: negative bends the elbow.
     */
    function gait(c) {
        const SAMPLES = 16;
        const thigh = [[0, -c.stride], [0.15, -c.stride * 0.75], [0.35, 0], [0.55, c.stride * 0.95], [0.72, c.stride * 0.15], [0.87, -c.stride * 0.85]];
        const shin = [[0, c.knee[0]], [0.12, c.knee[1]], [0.35, c.knee[0]], [0.55, c.knee[2]], [0.72, c.knee[3]], [0.9, c.knee[4]]];
        const channel = fn => {
            const out = {};
            for (let i = 0; i <= SAMPLES; i++) {
                const t = (i % SAMPLES) / SAMPLES;
                out[(c.length * i / SAMPLES).toFixed(3)] = fn(t).map(v => Math.round(v * 100) / 100);
            }
            return out;
        };
        const armAngle = t => c.arm * Math.cos(2 * Math.PI * (t - c.armLag));
        const roll = t => c.roll * Math.sin(2 * Math.PI * t);
        const twist = t => c.twist * Math.cos(2 * Math.PI * t);
        const splay = c.splay || 0;
        return {length: c.length, loop: 'loop', bones: {
            right_leg: {r: channel(t => [curve(thigh, t), 0, splay])},
            left_leg: {r: channel(t => [curve(thigh, t + 0.5), 0, -splay])},
            right_shin: {r: channel(t => [curve(shin, t), 0, 0])},
            left_shin: {r: channel(t => [curve(shin, t + 0.5), 0, 0])},
            right_arm: {r: channel(t => [armAngle(t), 0, c.armOut])},
            left_arm: {r: channel(t => [armAngle(t + 0.5), 0, -c.armOut])},
            // The elbow bends more when the arm swings forward.
            right_forearm: {r: channel(t => [-(c.elbow[0] + c.elbow[1] * (0.5 - armAngle(t) / (2 * c.arm))), 0, 0])},
            left_forearm: {r: channel(t => [-(c.elbow[0] + c.elbow[1] * (0.5 - armAngle(t + 0.5) / (2 * c.arm))), 0, 0])},
            torso: {
                // Lowest when both feet are on the ground, highest over the standing leg.
                p: channel(t => [0, -c.bob * (0.5 + 0.5 * Math.cos(4 * Math.PI * t)), 0]),
                r: channel(t => [c.lean, twist(t), roll(t)])
            },
            // The head keeps looking ahead.
            head: {r: channel(t => [-c.lean * 0.8, -twist(t) * 0.7, -roll(t) * 0.6])}
        }};
    }

    function armorAnimations(c) {
        return {
            'animation.power_armor.idle': idle(c.idle),
            'animation.power_armor.walk': gait(c.walk),
            'animation.power_armor.attack': c.attack,
            'animation.power_armor.shutdown': c.shutdown,
            'animation.power_armor.wrecked': c.wrecked
        };
    }

    const SLUMP = (lean, drop, armsDown, ease) => ({length: 1.5, loop: 'hold', ease, bones: {
        torso: {r: {'0.000': [0, 0, 0], '1.000': [lean, 0, 0], '1.500': [lean - 2, 0, 0]},
            p: {'0.000': [0, 0, 0], '1.000': [0, -drop, 0], '1.500': [0, -drop + 0.2, 0]}},
        head: {r: {'0.000': [0, 0, 0], '1.500': [lean + 4, 0, 0]}},
        right_arm: {r: {'0.000': [0, 0, 0], '1.000': [-armsDown, 0, 6], '1.500': [-armsDown + 2, 0, 6]}},
        left_arm: {r: {'0.000': [0, 0, 0], '1.000': [-armsDown, 0, -6], '1.500': [-armsDown + 2, 0, -6]}}
    }});

    const COLLAPSE = (x, y, z, drop) => ({length: 1, loop: 'hold', ease: 'easeOutBounce', bones: {
        torso: {r: {'0.000': [0, 0, 0], '1.000': [x, y, z]}, p: {'0.000': [0, 0, 0], '1.000': [0, -drop, 0]}},
        head: {r: {'0.000': [0, 0, 0], '1.000': [x + 4, -y * 2, z * -1.5]}},
        right_arm: {r: {'0.000': [0, 0, 0], '1.000': [-x, 8, 14]}},
        left_arm: {r: {'0.000': [0, 0, 0], '1.000': [-x * 0.6, -6, -22]}},
        right_leg: {r: {'0.000': [0, 0, 0], '1.000': [-10, 4, 4]}},
        left_leg: {r: {'0.000': [0, 0, 0], '1.000': [6, -4, -4]}}
    }});

    // Combat: the heavy tank. Gunmetal and olive plates, red visor, massive shoulders, jump pack.
    (function () {
        const metal = '#4c5359', olive = '#5f6b3e', dark = '#25282c', red = '#ff3b2f', steel = '#8b9299', amber = '#e0a020';
        SPECS.power_armor = {
            res: 128, seed: 606, wrecked: true,
            joints: {knee: 8, elbow: 20},
            armTable: 'combat',
            bones: armorBones({
                legPivot: [-4, 16, 0], headPivot: [0, 34, 0], armPivot: [-11, 31, 0], hand: [-11.5, 11, 0],
                armLeftUv: ARM_LEFT_UV,
                leg: [
                    C([-7, 3, -3], [6, 13, 6], {c: metal, all: [[0, 6, 6, 1, dark]], sd: [[1, 1, 4, 4, shade(metal, 0.8)]]}),
                    C([-7.5, 0, -5], [7, 3, 8], {c: dark, n: 0.06, f: [[1, 0, 5, 1, steel]], tp: [[1, 1, 5, 6, metal]]}),
                    C([-7.5, 7, -4], [7, 5, 1], {c: olive, f: [[3, 0, 1, 5, dark], ...hazard(0, 4, 7, 1, amber, dark, 1)]}),
                    C([-7.5, 12, -3.5], [7, 4, 7], {c: olive, sd: [[1, 1, 5, 2, shade(olive, 0.8)]], f: [[1, 1, 5, 1, dark]]})
                ],
                torso: [
                    C([-7, 16, -4], [14, 4, 8], {c: dark, n: 0.05, f: [...vents(1, 1, 12, 2, metal)]}),
                    C([-9, 20, -5], [18, 14, 10], {c: metal,
                        f: [[0, 0, 18, 1, steel], [1, 11, 16, 2, dark], [8, 11, 2, 2, amber]],
                        k: [[2, 2, 14, 10, dark]], sd: [[1, 2, 8, 2, olive], [1, 9, 8, 3, dark]], tp: [[3, 2, 12, 6, dark]]}),
                    C([-6, 26, -6], [12, 7, 1], {c: olive, f: [[0, 0, 12, 1, shade(olive, 1.25)], [5, 1, 2, 5, dark], [1, 2, 3, 3, shade(olive, 0.78)], [8, 2, 3, 3, shade(olive, 0.78)]]}),
                    C([-5, 21, -6], [10, 4, 1], {c: dark, n: 0.05, f: [...vents(1, 1, 8, 2, steel)]}),
                    C([-6, 22, 5], [12, 11, 3], {c: olive, k: [[1, 1, 10, 2, dark], ...vents(2, 4, 8, 3, dark)]}),
                    C([-5, 19, 5.5], [3, 3, 2], {c: dark, n: 0.05, k: [[1, 1, 1, 1, '#ff8a2e']], edge: false}),
                    C([2, 19, 5.5], [3, 3, 2], {c: dark, n: 0.05, k: [[1, 1, 1, 1, '#ff8a2e']], edge: false})
                ],
                head: [
                    C([-4, 34, -4], [8, 6, 8], {c: metal,
                        f: [[0, 1, 8, 3, dark], [1, 2, 6, 1, red], [3, 4, 2, 2, steel]],
                        sd: [[0, 1, 5, 2, dark], [5, 3, 3, 3, olive]], k: [...vents(1, 1, 6, 3, dark)], tp: [[3, 0, 2, 8, olive]]}),
                    C([-5, 34, -3], [1, 3, 5], {c: olive}),
                    mirror(C([-5, 34, -3], [1, 3, 5], {c: olive})),
                    C([-0.5, 40, -3], [1, 1, 6], {c: red, n: 0.03, edge: false})
                ],
                arm: [
                    C([-15, 28, -4.5], [7, 7, 9], {c: olive, f: [[1, 1, 5, 1, shade(olive, 1.25)], ...hazard(0, 5, 7, 1, amber, dark, 1)], sd: [[1, 2, 7, 3, shade(olive, 0.8)]]}, {uv: ARM_UV.shoulderR}),
                    C([-14, 12, -3], [5, 16, 6], {c: metal, all: [[0, 3, 6, 1, dark], [0, 8, 6, 1, dark]]}, {uv: ARM_UV.armR}),
                    C([-14.5, 8, -3.5], [6, 4, 7], {c: dark, n: 0.06, f: [[1, 1, 1, 2, steel], [3, 1, 1, 2, steel], [5, 1, 1, 2, steel]], all: [[0, 0, 6, 1, steel]]}, {uv: ARM_UV.fistR}),
                    C([-15.5, 33, -5], [8, 3, 10], {c: metal, tp: [[1, 1, 6, 8, shade(metal, 1.15)]], sd: [[0, 2, 10, 1, dark]]}),
                    C([-15, 12, -4], [7, 7, 8], {c: olive, f: [[3, 1, 1, 5, dark]], sd: [[1, 1, 6, 1, dark]], bt: [[1, 1, 5, 6, dark]]})
                ]
            }),
            animations: armorAnimations({
                idle: {length: 4, bob: -0.5, arm: -3, armZ: 2},
                // A heavy, even march: short knee lift, arms held low, a solid drop on every step.
                walk: {length: 0.95, stride: 24, knee: [4, 14, 14, 58, 20], arm: 15, armLag: 0.04, armOut: 3, elbow: [12, 16], bob: 1.0, roll: 1.8, lean: 4, twist: 5},
                // A right cross: the shoulder winds back, the whole body turns into the punch, the left arm covers.
                attack: {length: 0.6, loop: 'once', ease: 'easeInOutSine', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.130': [25, 0, 12], '0.250': [-95, -8, -4], '0.340': [-88, -6, -4], '0.600': [0, 0, 0]}},
                    right_forearm: {r: {'0.000': [0, 0, 0], '0.130': [-105, 0, 0], '0.250': [-4, 0, 0], '0.340': [-16, 0, 0], '0.600': [0, 0, 0]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '0.130': [-48, 0, 10], '0.250': [12, 0, -6], '0.600': [0, 0, 0]}},
                    left_forearm: {r: {'0.000': [0, 0, 0], '0.130': [-95, 0, 0], '0.250': [-60, 0, 0], '0.600': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.130': [-3, 20, 0], '0.250': [9, -22, 0], '0.340': [7, -18, 0], '0.600': [0, 0, 0]},
                        p: {'0.000': [0, 0, 0], '0.250': [0, -0.8, -1], '0.600': [0, 0, 0]}},
                    head: {r: {'0.000': [0, 0, 0], '0.130': [2, -14, 0], '0.250': [-4, 16, 0], '0.600': [0, 0, 0]}}
                }},
                shutdown: SLUMP(22, 2, 20),
                wrecked: COLLAPSE(32, 8, -6, 3)
            })
        };
    })();

    // Mobile: the sprinter. Slim white and blue shell, narrow visor, swept fins, thrusters on the back and calves.
    (function () {
        const white = '#dde3ea', blue = '#1f7fe0', dark = '#1e2733', cyan = '#6df0ff', orange = '#ff8a24', grey = '#7d8996';
        SPECS.mobile_power_armor = {
            res: 128, seed: 707, wrecked: true,
            joints: {knee: 9, elbow: 21},
            armTable: 'mobile',
            bones: armorBones({
                legPivot: [-3.5, 17, 0], headPivot: [0, 33, 0], armPivot: [-9, 31, 0], hand: [-9.5, 13, 0],
                armLeftUv: ARM_LEFT_UV,
                leg: [
                    C([-6, 3, -2.5], [5, 14, 5], {c: white, n: 0.03, all: [[0, 6, 5, 1, dark]], f: [[2, 7, 1, 7, blue]]}),
                    C([-6.5, 0, -5], [6, 3, 7], {c: blue, n: 0.04, f: [[1, 1, 4, 1, cyan]], tp: [[1, 0, 4, 3, white]]}),
                    C([-6, 5, 2.5], [5, 7, 2], {c: dark, n: 0.04, k: [[1, 1, 3, 2, orange], [1, 4, 3, 2, cyan]], sd: [[0, 0, 2, 2, blue]]}),
                    C([-6.5, 12, -3], [6, 5, 6], {c: blue, n: 0.04, f: [[1, 1, 4, 1, white]]})
                ],
                torso: [
                    C([-5, 17, -3], [10, 4, 6], {c: dark, n: 0.04, f: [...vents(1, 1, 8, 2, grey)]}),
                    C([-7, 21, -4], [14, 12, 8], {c: white, n: 0.03,
                        f: [[0, 8, 14, 4, blue], [6, 8, 2, 4, dark], [0, 0, 14, 1, shade(white, 0.85)]],
                        k: [[2, 2, 10, 8, dark]], sd: [[1, 7, 6, 2, blue]], tp: [[2, 1, 10, 6, shade(white, 0.85)]]}),
                    C([-4, 26, -5], [8, 6, 1], {c: blue, n: 0.04, f: [[1, 1, 6, 1, cyan], [2, 3, 4, 1, cyan]]}),
                    C([-7, 22, 4], [3, 10, 3], {c: dark, n: 0.04, sd: [[0, 0, 3, 2, blue]], k: [[1, 8, 1, 1, orange]]}),
                    mirror(C([-7, 22, 4], [3, 10, 3], {c: dark, n: 0.04, sd: [[0, 0, 3, 2, blue]], k: [[1, 8, 1, 1, orange]]})),
                    C([-7, 19, 4.5], [3, 3, 2], {c: orange, n: 0.05, edge: false}),
                    mirror(C([-7, 19, 4.5], [3, 3, 2], {c: orange, n: 0.05, edge: false})),
                    C([-13, 26, 4.5], [6, 7, 1], {c: blue, n: 0.04, f: [[1, 1, 4, 1, cyan]], k: [[1, 1, 4, 1, cyan]]}, {rot: [0, 0, -18], pivot: [-7, 29, 5]}),
                    mirror(C([-13, 26, 4.5], [6, 7, 1], {c: blue, n: 0.04, f: [[1, 1, 4, 1, cyan]], k: [[1, 1, 4, 1, cyan]]}, {rot: [0, 0, -18], pivot: [-7, 29, 5]}))
                ],
                head: [
                    C([-3.5, 33, -4], [7, 5, 8], {c: white, n: 0.03,
                        f: [[0, 1, 7, 2, dark], [1, 1, 5, 1, cyan], [3, 3, 1, 2, blue]],
                        sd: [[0, 1, 4, 2, dark], [4, 3, 4, 2, blue]], tp: [[3, 0, 1, 8, blue]], k: [[1, 1, 5, 2, blue]]}),
                    C([-0.5, 38, -2], [1, 2, 7], {c: blue, n: 0.04, edge: false}, {rot: [-10, 0, 0], pivot: [0, 38, 0]})
                ],
                arm: [
                    C([-12, 29, -3.5], [5, 5, 7], {c: blue, n: 0.04, f: [[1, 1, 3, 1, cyan]], sd: [[1, 1, 5, 2, white]]}, {uv: ARM_UV.shoulderR}),
                    C([-11.5, 14, -2.5], [4, 15, 5], {c: white, n: 0.03, all: [[0, 7, 5, 1, dark]]}, {uv: ARM_UV.armR}),
                    C([-12, 10, -3], [5, 4, 6], {c: dark, n: 0.05, all: [[0, 0, 6, 1, blue]], f: [[1, 1, 1, 2, grey], [3, 1, 1, 2, grey]]}, {uv: ARM_UV.fistR}),
                    C([-12, 15, -3], [5, 5, 6], {c: blue, n: 0.04, sd: [[1, 2, 4, 1, cyan]]})
                ]
            }),
            animations: armorAnimations({
                idle: {length: 2.8, bob: -0.45, arm: -4, armZ: 3, sway: 0.6, ease: 'easeInOutSine'},
                // A light run: leaning forward, heels kicking high, arms bent and pumping.
                walk: {length: 0.5, stride: 44, knee: [8, 22, 30, 105, 45], arm: 38, armLag: 0.02, armOut: 5, elbow: [62, 34], bob: 0.8, roll: 1, lean: 13, twist: 9},
                // A slash with the right hand: from up and out on the right, across the body and down to the left.
                // The left arm only pulls back to balance. In the file a positive Y turn moves the right side back.
                attack: flowing(0.6, {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.160': [-135, 45, 12], '0.320': [-58, -52, -6], '0.400': [-50, -60, -6], '0.600': [0, 0, 0]}},
                    right_forearm: {r: {'0.000': [0, 0, 0], '0.160': [-45, 0, 0], '0.300': [-6, 0, 0], '0.400': [-18, 0, 0], '0.600': [0, 0, 0]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '0.160': [-18, 0, -8], '0.340': [26, 0, -13], '0.600': [0, 0, 0]}},
                    left_forearm: {r: {'0.000': [0, 0, 0], '0.160': [-55, 0, 0], '0.340': [-40, 0, 0], '0.600': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.160': [-3, 22, 2], '0.330': [10, -28, -3], '0.420': [8, -28, -3], '0.600': [0, 0, 0]},
                        p: {'0.000': [0, 0, 0], '0.330': [0, -0.6, -0.8], '0.600': [0, 0, 0]}},
                    head: {r: {'0.000': [0, 0, 0], '0.160': [0, -12, 0], '0.340': [-5, 16, 0], '0.600': [0, 0, 0]}}
                }),
                shutdown: SLUMP(16, 1.2, 12, 'easeInOutSine'),
                wrecked: COLLAPSE(26, -12, 8, 3.5)
            })
        };
    })();

    // Worker: the walking excavator. Hazard yellow, a big glass cab in the chest, work lamps, roll bars, huge clamp hands.
    (function () {
        const yellow = '#e8b420', orange = '#d9701c', dark = '#2a2b2e', steel = '#7c8187', glass = '#7fc8d8', lamp = '#fff2b0';
        const bar = {c: dark, n: 0.05, edge: false};
        SPECS.worker_power_armor = {
            res: 128, seed: 808, wrecked: true,
            joints: {knee: 8, elbow: 20},
            armTable: 'worker',
            bones: armorBones({
                legPivot: [-4.5, 15, 0], headPivot: [0, 34, 0], armPivot: [-12, 31, 0], hand: [-12.5, 11, 0],
                armLeftUv: ARM_LEFT_UV,
                leg: [
                    C([-8, 3, -4], [7, 12, 8], {c: yellow, all: [[0, 4, 8, 1, dark], ...hazard(0, 7, 8, 2, yellow, dark, 2)]}),
                    C([-8.5, 0, -6], [8, 3, 11], {c: dark, n: 0.07, f: [[0, 0, 8, 1, steel], [1, 2, 6, 1, steel]], sd: [[0, 2, 11, 1, steel]], tp: [[1, 1, 6, 9, steel]]}),
                    C([-8.5, 11, -4.5], [8, 4, 9], {c: orange, sd: [[1, 1, 7, 1, dark]]})
                ],
                torso: [
                    C([-8, 15, -4.5], [16, 5, 9], {c: dark, n: 0.06, f: [...hazard(0, 3, 16, 2, yellow, dark, 2)], sd: [[1, 1, 7, 1, steel]]}),
                    C([-9, 20, -5], [18, 14, 10], {c: yellow,
                        f: [[2, 1, 14, 9, dark], [3, 2, 12, 7, glass], [4, 3, 3, 1, '#d4f2f8'], [4, 4, 1, 2, '#d4f2f8'], [1, 11, 16, 2, orange], [7, 11, 4, 2, dark]],
                        k: [[3, 2, 12, 10, orange]], sd: [[2, 2, 6, 5, glass], [0, 9, 10, 2, orange]], tp: [[2, 2, 14, 6, steel]]}),
                    C([-6, 21, 5], [12, 12, 4], {c: orange, k: [[1, 1, 10, 3, dark], ...vents(2, 5, 8, 3, dark)], sd: [[1, 1, 2, 5, dark]]}),
                    C([3, 31, 6], [2, 8, 2], {c: steel, n: 0.05, tp: [[0, 0, 2, 2, dark]], edge: false})
                ],
                head: [
                    C([-5, 34, -4], [10, 3, 8], {c: yellow, f: [[1, 1, 2, 1, lamp], [4, 1, 2, 1, lamp], [7, 1, 2, 1, lamp]], k: [...hazard(0, 0, 10, 3, yellow, dark, 2)], tp: [[1, 1, 8, 6, steel]]}),
                    C([-6, 37, -5], [12, 1, 1], bar),
                    C([-6, 34, -5], [1, 3, 1], bar),
                    C([5, 34, -5], [1, 3, 1], bar),
                    C([-5, 37, -4], [10, 1, 8], {c: dark, n: 0.05, tp: [[0, 0, 10, 8, steel]], edge: false})
                ],
                arm: [
                    C([-16, 27, -5], [8, 8, 10], {c: orange, f: [[1, 1, 6, 2, lamp], [1, 3, 6, 1, dark]], sd: [[1, 1, 8, 2, dark], ...hazard(0, 5, 10, 2, yellow, dark, 2)]}, {uv: ARM_UV.shoulderR}),
                    C([-15, 13, -3.5], [6, 14, 7], {c: yellow, all: [[0, 3, 7, 1, dark], [0, 9, 7, 1, dark]]}, {uv: ARM_UV.armR}),
                    C([-16, 8, -4.5], [8, 5, 9], {c: dark, n: 0.07, all: [[0, 0, 9, 1, steel]], f: [[1, 1, 1, 3, steel], [3, 1, 2, 3, steel], [6, 1, 1, 3, steel]], bt: [[3, 1, 2, 7, steel]]}, {uv: ARM_UV.fistR}),
                    C([-15.5, 35, -2], [3, 2, 4], {c: dark, n: 0.05, f: [[0, 0, 3, 2, lamp]], edge: false}),
                    C([-16, 13, -4], [8, 5, 8], {c: orange, sd: [[1, 1, 6, 1, dark]]})
                ]
            }),
            animations: armorAnimations({
                idle: {length: 5, bob: -0.3, arm: -2, armZ: 1.5},
                // A slow waddle from foot to foot: wide stance, short steps, the heavy hands hang and barely swing.
                walk: {length: 1.35, stride: 15, knee: [3, 8, 6, 32, 10], arm: 7, armLag: 0.1, armOut: 4, elbow: [6, 6], bob: 1.1, roll: 4.5, lean: 1, twist: 2, splay: 3},
                // A two-handed slam: a slow lift over the head, a fast drop, a pause in the low pose, a heavy recovery.
                attack: {length: 1, loop: 'once', ease: 'easeInOutQuad', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.400': [-165, 0, -8], '0.540': [-58, 0, -4], '0.680': [-55, 0, -4], '1.000': [0, 0, 0]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '0.400': [-165, 0, 8], '0.540': [-58, 0, 4], '0.680': [-55, 0, 4], '1.000': [0, 0, 0]}},
                    right_forearm: {r: {'0.000': [0, 0, 0], '0.400': [-45, 0, 0], '0.540': [-6, 0, 0], '1.000': [0, 0, 0]}},
                    left_forearm: {r: {'0.000': [0, 0, 0], '0.400': [-45, 0, 0], '0.540': [-6, 0, 0], '1.000': [0, 0, 0]}},
                    torso: {r: {'0.000': [0, 0, 0], '0.400': [-13, 0, 0], '0.540': [24, 0, 0], '0.680': [22, 0, 0], '1.000': [0, 0, 0]},
                        p: {'0.000': [0, 0, 0], '0.400': [0, 0.8, 0], '0.540': [0, -2.2, -1], '0.680': [0, -2, -1], '1.000': [0, 0, 0]}},
                    head: {r: {'0.000': [0, 0, 0], '0.400': [8, 0, 0], '0.540': [-14, 0, 0], '1.000': [0, 0, 0]}}
                }},
                shutdown: SLUMP(12, 2.5, 8),
                wrecked: COLLAPSE(20, 4, 12, 4)
            })
        };
    })();

    // ------------------------------------------------------------------ Blockbench project

    /** Cuts a part of a limb at a joint. On the lower piece the texture continues where the upper piece ends. */
    function cutAt(cube, joint) {
        const top = cube.o[1] + cube.s[1];
        const upper = Object.assign({}, cube, {o: [cube.o[0], joint, cube.o[2]], s: [cube.s[0], top - joint, cube.s[2]]});
        const lower = Object.assign({}, cube, {s: [cube.s[0], joint - cube.o[1], cube.s[2]], uv: [cube.uv[0], cube.uv[1] + top - joint]});
        return [upper, lower];
    }

    /**
     * Gives the armor's limbs a second joint: shins and forearms become bones of their own. Only the bones change, not
     * the look or the texture; it runs after the texture is painted. The hands follow the forearms.
     */
    function jointedBones(spec) {
        if (!spec.joints) {
            return spec.bones;
        }
        const limbs = {
            right_leg: ['right_shin', spec.joints.knee], left_leg: ['left_shin', spec.joints.knee],
            right_arm: ['right_forearm', spec.joints.elbow], left_arm: ['left_forearm', spec.joints.elbow]
        };
        const out = [];
        for (const bone of spec.bones) {
            const limb = limbs[bone.name];
            if (!limb) {
                out.push(bone.name.endsWith('_hand')
                    ? Object.assign({}, bone, {parent: bone.parent.replace('_arm', '_forearm')}) : bone);
                continue;
            }
            const [name, joint] = limb;
            const upper = [], lower = [];
            for (const cube of bone.cubes) {
                const top = cube.o[1] + cube.s[1];
                if (cube.rot ? cube.o[1] + cube.s[1] / 2 < joint : top <= joint) {
                    lower.push(cube);
                } else if (cube.rot || cube.o[1] >= joint) {
                    upper.push(cube);
                } else {
                    const [a, b] = cutAt(cube, joint);
                    upper.push(a);
                    lower.push(b);
                }
            }
            out.push(Object.assign({}, bone, {cubes: upper}));
            // The joint lies on the axis of the limb's main part.
            const main = lower.reduce((best, c) => (c.s[1] * c.s[0] > best.s[1] * best.s[0] ? c : best), lower[0]);
            const x = bone.name.endsWith('_leg') ? bone.pivot[0] : main.o[0] + main.s[0] / 2;
            out.push({name, parent: bone.name, pivot: [x, joint, 0], cubes: lower});
        }
        return out;
    }

    function allCubes(spec) {
        return spec.bones.flatMap(b => b.cubes);
    }

    function clearProject() {
        [...Animation.all].forEach(a => a.remove(false));
        [...Outliner.elements].forEach(e => e.remove());
        [...Group.all].forEach(g => g.remove(false));
        [...Texture.all].forEach(t => t.remove(true));
    }

    function addAnimations(spec) {
        const groups = {};
        Group.all.forEach(g => { groups[g.name] = g; });
        for (const [name, a] of Object.entries(spec.animations)) {
            const anim = new Animation({name, length: a.length, loop: a.loop, snapping: 20}).add(false);
            for (const [bone, channels] of Object.entries(a.bones)) {
                if (!groups[bone]) {
                    throw new Error('Animation ' + name + ' uses the unknown bone ' + bone);
                }
                const animator = anim.getBoneAnimator(groups[bone]);
                const map = {r: 'rotation', p: 'position', s: 'scale'};
                for (const [ch, frames] of Object.entries(channels)) {
                    // The specs hold the values of the animation file; Blockbench's editor shows rotation X and Y negated.
                    const signX = ch === 'r' ? -1 : 1;
                    Object.entries(frames).map(([t, v]) => [parseFloat(t), v]).sort((x, y) => x[0] - y[0]).forEach(([time, v], index) => {
                        const kf = animator.addKeyframe({
                            channel: map[ch], time, interpolation: 'linear',
                            data_points: [{x: v[0] * signX, y: v[1] * signX, z: v[2]}]
                        });
                        if (a.ease && index > 0 && kf) {
                            kf.easing = a.ease;
                        }
                    });
                }
            }
        }
    }

    function build(id) {
        const spec = SPECS[id];
        if (!spec) {
            throw new Error('Unknown model ' + id);
        }
        if (Format.id !== 'geckolib_model') {
            throw new Error('Activate a GeckoLib project first');
        }
        clearProject();
        Project.name = id;
        Project.model_identifier = id;
        Project.geckolib_modid = 'projectrebirth';
        Project.geckolib_model_type = 'Entity';
        Project.texture_width = spec.res;
        Project.texture_height = spec.res;
        Project.box_uv = true;
        Project.visible_box = spec.res === 128 ? [3.5, 4, 2] : [2, 3, 1.5];

        const cubes = allCubes(spec);
        pack(cubes, spec.res);
        const canvas = paintTexture(spec, cubes);
        const texture = new Texture({name: id + '.png'}).fromDataURL(canvas.toDataURL('image/png')).add(false);

        const groups = {};
        for (const b of jointedBones(spec)) {
            const group = new Group({name: b.name, origin: [-b.pivot[0], b.pivot[1], b.pivot[2]]});
            group.addTo(b.parent ? groups[b.parent] : undefined).init();
            groups[b.name] = group;
            b.cubes.forEach((c, i) => {
                const data = {
                    name: b.name + '_' + i,
                    from: [-(c.o[0] + c.s[0]), c.o[1], c.o[2]],
                    to: [-c.o[0], c.o[1] + c.s[1], c.o[2] + c.s[2]],
                    box_uv: true,
                    uv_offset: [c.uv[0], c.uv[1]],
                    autouv: 0
                };
                if (c.inflate) {
                    data.inflate = c.inflate;
                }
                const cube = new Cube(data);
                if (c.rot) {
                    cube.rotation = [c.rot[0], c.rot[1], c.rot[2]];
                    cube.origin = [-c.pivot[0], c.pivot[1], c.pivot[2]];
                } else {
                    cube.origin = [-b.pivot[0], b.pivot[1], b.pivot[2]];
                }
                cube.addTo(group).init();
                cube.applyTexture(texture, true);
            });
        }
        addAnimations(spec);
        Canvas.updateAll();
        return {id, cubes: Cube.all.length, bones: Group.all.length, animations: Animation.all.length, textures: Texture.all.length};
    }

    /** Writes the texture(s) and the editable project next to the mod's other files. */
    function save(id) {
        const spec = SPECS[id];
        const texture = Texture.all[0];
        const data = texture.canvas || texture.img;
        const write = (path, canvas) => fs.writeFileSync(path, Buffer.from(canvas.toDataURL('image/png').split(',')[1], 'base64'));
        const source = document.createElement('canvas');
        source.width = source.height = spec.res;
        source.getContext('2d').drawImage(data, 0, 0, spec.res, spec.res);
        write(ASSETS + 'textures/entity/' + id + '.png', source);
        if (spec.wrecked) {
            write(ASSETS + 'textures/entity/' + id + '_wrecked.png', wreckedFrom(source, spec.seed + 1));
        }
        fs.mkdirSync(ROOT + 'blockbench', {recursive: true});
        fs.writeFileSync(ROOT + 'blockbench/' + id + '.bbmodel', Codecs.project.compile());
        return {saved: id};
    }

    window.RebirthModels = {build, save, SPECS};
})();
