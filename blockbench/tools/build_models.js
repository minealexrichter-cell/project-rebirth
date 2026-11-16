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
        // clear glass (1.5.2): the face is left transparent and only its marks are drawn
        if (p.clear) {
            ctx.clearRect(x, y, w, h);
        } else {
            ctx.fillStyle = base;
            ctx.fillRect(x, y, w, h);
        }
        const noise = p.clear ? 0 : (p.n === undefined ? 0.05 : p.n);
        if (noise > 0) {
            for (let i = 0; i < w; i++) {
                for (let j = 0; j < h; j++) {
                    const f = 1 + (rand() - 0.5) * 2 * noise;
                    ctx.fillStyle = shade(base, f);
                    ctx.fillRect(x + i, y + j, 1, 1);
                }
            }
        }
        if (p.edge !== false && !p.clear && w >= 3 && h >= 3) {
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
    function pack(cubes, startRes) {
        // A mirrored cube whose partner is not part of the model gets its own area.
        cubes.filter(c => c.share && !cubes.includes(c.share)).forEach(c => { delete c.share; });
        let res = startRes;
        let used = new Uint8Array(res * res);
        // late parts take the area their paint really covers (sizes rounded up, as the paint does)
        const size = c => c.late ? [2 * (Math.ceil(c.s[0]) + Math.ceil(c.s[2])), Math.ceil(c.s[2]) + Math.ceil(c.s[1])]
                : [Math.ceil(2 * (c.s[0] + c.s[2])), Math.ceil(c.s[2] + c.s[1])];
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
        const place = c => {
            const [w, h] = size(c);
            for (let v = 0; v <= res - h; v++) {
                for (let u = 0; u <= res - w; u++) {
                    if (free(u, v, w, h)) {
                        mark(u, v, w, h);
                        c.uv = [u, v];
                        return true;
                    }
                }
            }
            return false;
        };
        // The parts of the accepted models first, exactly as they were always packed; the parts added later (late: true,
        // the gadgets of 1.5.0) only into what is left, so that nothing already painted moves.
        for (const c of [...cubes].filter(c => !c.late).sort((a, b) => area(b) - area(a))) {
            if (c.uv || c.share) {
                continue;
            }
            if (!place(c)) {
                throw new Error('No texture space for a cube of size ' + c.s);
            }
        }
        // the paint of an older part with a fractional size spills a little past its packed area: keep the late parts off it
        for (const c of cubes.filter(c => !c.late && c.uv)) {
            const w = 2 * (Math.ceil(c.s[0]) + Math.ceil(c.s[2])), h = Math.ceil(c.s[2]) + Math.ceil(c.s[1]);
            for (let y = c.uv[1]; y < Math.min(res, c.uv[1] + h); y++) {
                for (let x = c.uv[0]; x < Math.min(res, c.uv[0] + w); x++) {
                    used[y * res + x] = 1;
                }
            }
        }
        // the parts of 1.6.0 (latest) after those of 1.5.0, so that the older ones keep their places
        for (const c of [...cubes].filter(c => c.late).sort((a, b) => (a.latest ? 1 : 0) - (b.latest ? 1 : 0) || area(b) - area(a))) {
            if (c.uv || c.share) {
                continue;
            }
            // the texture may not grow: the first-person arms of bodies (vanilla player arm, 64) and armors (128) read it by size
            if (!place(c)) {
                throw new Error('No texture space for a late cube of size ' + c.s + ': make the new parts smaller');
            }
        }
        cubes.filter(c => c.share).forEach(c => { c.uv = c.share.uv; });
        return res;
    }

    function paintTexture(spec, cubes) {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = spec.texRes || spec.res;
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

    const STEP_TIME = 0.05; // Blockbench's animation snapping
    const THIGH = 6, SHIN = 6, HIP_HEIGHT = 12; // Bodies: the knee is at height 6.
    const DEG = 180 / Math.PI;

    /** Thigh and shin angles that put the foot at (forward, below) from the hip. The knee always bends forward. */
    function legAngles(forward, below) {
        const reach = Math.min(Math.hypot(forward, below), THIGH + SHIN - 0.02);
        const aim = Math.atan2(forward, below);
        const hip = Math.acos((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2 * THIGH * reach));
        const knee = Math.PI - Math.acos((THIGH * THIGH + SHIN * SHIN - reach * reach) / (2 * THIGH * SHIN));
        return [-(aim + hip) * DEG, knee * DEG];
    }

    /** One channel of a loop: fn gets the phase 0..1 of the loop and returns the three values. */
    function looped(length, step, fn) {
        const count = Math.round(length / step);
        const out = {};
        for (let i = 0; i <= count; i++) {
            out[(length * i / count).toFixed(3)] = fn((i % count) / count).map(v => Math.round(v * 100) / 100);
        }
        return out;
    }

    /**
     * The movement of every body: the feet follow a path and the legs are solved from it, so that the same code gives
     * a robot's stomp and a person's walk. What differs between the classes is only the data:
     * hold: part of each step the machine stands still (0 for a smooth movement); soft: its motors start and stop softly;
     * square: the foot goes up, across and down instead of along an arc; stance: part of the cycle a foot is on the ground;
     * stride, lift, shift: the foot path in pixels; hip: height of the hips; bob, bobAt: how much and when they drop;
     * lean, roll, twist, pelvis, sway: the body (twist and pelvis turn shoulders and hips against each other);
     * arm, lag: arm swing and how far it trails the legs; elbow: [bend, added bend when forward]; elbowLag: forearm trailing;
     * headRoll: part of the body's roll the head takes back; antenna: its spring after a footfall.
     */
    function bodyGait(c) {
        const S = c.stride, H = c.lift, st = c.stance || 0.5, hold = c.hold || 0, shift = c.shift || 0;
        const warp = t => {
            if (!hold) {
                return t;
            }
            const half = (((t % 1) + 1) % 1) * 2, k = Math.floor(half);
            let v = Math.min((half - k) / (1 - hold), 1);
            if (c.soft) {
                v = v * v * (3 - 2 * v);
            }
            return (k + v) / 2;
        };
        const foot = f => {
            f = ((f % 1) + 1) % 1;
            if (f < st) {
                return [shift + S * (1 - 2 * f / st), 0];
            }
            const u = (f - st) / (1 - st);
            if (c.square) {
                const across = Math.max(0, Math.min(1, (u - 0.15) / 0.7));
                return [shift - S + 2 * S * across, H * Math.min(1, Math.min(u, 1 - u) / 0.15)];
            }
            return [shift - S + 2 * S * u * u * (3 - 2 * u), H * Math.sin(Math.PI * Math.pow(u, 0.8))];
        };
        const hip = t => c.hip - c.bob * (1 + Math.cos(4 * Math.PI * (warp(t) - (c.bobAt || 0)))) / 2;
        const leg = (t, side) => {
            const [x, y] = foot(warp(t) + (side > 0 ? 0 : 0.5));
            return legAngles(x + side * (c.stagger || 0), hip(t) - y);
        };
        const arm = t => (c.arm || 0) * Math.cos(2 * Math.PI * (t - (c.lag || 0)));
        const forward = t => (c.arm ? 0.5 - arm(t) / (2 * c.arm) : 0);
        const armPitch = t => (c.armBase || 0) + arm(warp(t)) - c.lean * (c.armHang === undefined ? 0.8 : c.armHang);
        const elbow = t => -(c.elbow[0] + c.elbow[1] * forward(warp(t) - (c.elbowLag || 0)));
        const splay = c.splay || 0, out = c.armOut || 0;
        const bones = {
            root: {
                p: looped(c.length, STEP_TIME, t => [0, hip(t) - HIP_HEIGHT, 0]),
                r: looped(c.length, STEP_TIME, t => [0, -(c.pelvis || 0) * Math.cos(2 * Math.PI * warp(t)), (c.sway || 0) * Math.sin(2 * Math.PI * warp(t))])
            },
            right_leg: {r: looped(c.length, STEP_TIME, t => [leg(t, 1)[0], 0, splay])},
            left_leg: {r: looped(c.length, STEP_TIME, t => [leg(t, -1)[0], 0, -splay])},
            right_shin: {r: looped(c.length, STEP_TIME, t => [leg(t, 1)[1], 0, 0])},
            left_shin: {r: looped(c.length, STEP_TIME, t => [leg(t, -1)[1], 0, 0])},
            right_arm: {r: looped(c.length, STEP_TIME, t => [armPitch(t), 0, out])},
            left_arm: {r: looped(c.length, STEP_TIME, t => [armPitch(t + 0.5), 0, -out])},
            right_forearm: {r: looped(c.length, STEP_TIME, t => [elbow(t), 0, 0])},
            left_forearm: {r: looped(c.length, STEP_TIME, t => [elbow(t + 0.5), 0, 0])},
            torso: {r: looped(c.length, STEP_TIME, t => [c.lean, ((c.twist || 0) + (c.pelvis || 0)) * Math.cos(2 * Math.PI * warp(t)), (c.roll || 0) * Math.sin(2 * Math.PI * warp(t))])},
            head: {r: looped(c.length, STEP_TIME, t => [0, 0, -(c.headRoll || 0) * ((c.roll || 0) + (c.sway || 0)) * Math.sin(2 * Math.PI * warp(t))])}
        };
        if (c.antenna) {
            bones.antenna = {r: looped(c.length, STEP_TIME, t => {
                const half = t * 2, u = half - Math.floor(half);
                return [0, 0, (Math.floor(half) ? -1 : 1) * c.antenna * Math.exp(-4 * u) * Math.sin(5 * Math.PI * u)];
            })};
        }
        return {length: c.length, loop: 'loop', bones};
    }

    /** A machine standing still: it looks around in separate moves and its spring antenna reacts to each of them. */
    function machineIdle(c) {
        const L = c.length, m = c.move, Y = c.scan, [a, b, d] = c.moves, [ax, az] = c.arm;
        const keys = (...pairs) => Object.fromEntries(pairs.map(([t, v]) => [t.toFixed(3), v]));
        const still = v => keys([0, v], [L, v]);
        const armKeys = side => keys([0, [ax, 0, side * az]], [b, [ax, 0, side * az]],
            [b + 0.1, [ax - (side > 0 ? c.twitch : 0), 0, side * az]], [b + 0.35, [ax, 0, side * az]], [L, [ax, 0, side * az]]);
        const bones = {
            head: {r: keys([0, [0, 0, 0]], [a, [0, 0, 0]], [a + m, [0, Y, 0]], [b, [0, Y, 0]], [b + m, [0, -Y, 0]], [d, [0, -Y, 0]], [d + m, [0, 0, 0]], [L, [0, 0, 0]])},
            torso: {p: keys([0, [0, 0, 0]], [L / 2, [0, -c.settle, 0]], [L, [0, 0, 0]])},
            right_arm: {r: armKeys(1)},
            left_arm: {r: armKeys(-1)},
            right_leg: {r: still([0, 0, c.splay])},
            left_leg: {r: still([0, 0, -c.splay])}
        };
        const spring = {'0.000': [0, 0, 0]};
        [a, b, d].forEach(t => [[0.1, 1], [0.2, -0.6], [0.3, 0.3], [0.4, 0]].forEach(([dt, k]) => {
            spring[(t + dt).toFixed(3)] = [0, 0, k * c.antenna];
        }));
        spring[L.toFixed(3)] = [0, 0, 0];
        bones.antenna = {r: spring};
        return {length: L, loop: 'loop', ease: c.ease, bones};
    }

    /**
     * A living body standing still: breathing, the weight slowly going from leg to leg, looking around.
     * breaths and glances: how many in one loop; weight: side lean; knee: how much the free leg gives way.
     */
    function livingIdle(c) {
        const STEP = 0.25, turn = (t, n) => Math.sin(2 * Math.PI * n * t);
        const leg = (t, side) => legAngles(side * 0.5, 11.85 + side * c.knee * turn(t, 1));
        const armOut = (t, side) => side * (c.armOut + 0.4 * turn(t, c.breaths));
        return {length: c.length, loop: 'loop', bones: {
            root: {
                p: looped(c.length, STEP, () => [0, -0.15, 0]),
                r: looped(c.length, STEP, t => [0, 0, c.weight * turn(t, 1)])
            },
            torso: {
                p: looped(c.length, STEP, t => [0, c.breath * (1 + turn(t, c.breaths)) / 2, 0]),
                r: looped(c.length, STEP, t => [-c.chest * turn(t, c.breaths), 0, -c.weight * 0.6 * turn(t, 1)])
            },
            head: {r: looped(c.length, STEP, t => [c.nod * turn(t, c.breaths), c.look * turn(t, c.glances), 0])},
            right_leg: {r: looped(c.length, STEP, t => [leg(t, 1)[0], 0, 0])},
            left_leg: {r: looped(c.length, STEP, t => [leg(t, -1)[0], 0, 0])},
            right_shin: {r: looped(c.length, STEP, t => [leg(t, 1)[1], 0, 0])},
            left_shin: {r: looped(c.length, STEP, t => [leg(t, -1)[1], 0, 0])},
            right_arm: {r: looped(c.length, STEP, t => [c.armBase, 0, armOut(t, 1)])},
            left_arm: {r: looped(c.length, STEP, t => [c.armBase, 0, armOut(t, -1)])},
            right_forearm: {r: looped(c.length, STEP, t => [-c.elbow - 1.5 * turn(t, c.breaths), 0, 0])},
            left_forearm: {r: looped(c.length, STEP, t => [-c.elbow - 1.5 * turn(t, c.breaths), 0, 0])}
        }};
    }

    /** idle and interact as given; style holds the character of the class, walk, run and sneak what differs from it. */
    function bodyAnimations(cfg) {
        const of = part => bodyGait(Object.assign({}, cfg.style, part));
        return {
            'animation.body.idle': cfg.idle,
            'animation.body.walk': of(cfg.walk),
            'animation.body.run': of(cfg.run),
            'animation.body.sneak': of(cfg.sneak),
            // The same bent pose, without steps, and only barely moving.
            'animation.body.crouch': of(Object.assign({}, cfg.sneak, {length: 3, hold: 0, stride: 0, lift: 0, stagger: 1.5, arm: 0, bob: 0.12, roll: 0, sway: 0, twist: 0, pelvis: 0})),
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
            joints: {knee: 6, elbow: 17},
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
                // Old servos: abrupt starts and stops, a pause on every step, stiff arms, feet set down like a machine's.
                idle: machineIdle({length: 6, moves: [1.2, 2.8, 4.4], move: 0.15, scan: 24, settle: 0.1, arm: [-1, 3], twitch: 5, splay: 2, antenna: 22}),
                style: {square: true, hip: 11.8, splay: 2, armOut: 3, elbow: [5, 0], antenna: 18},
                walk: {length: 1.2, hold: 0.3, stride: 4.5, lift: 1, bob: 0.7, lean: 0, roll: 3, arm: 13},
                run: {length: 0.8, hold: 0.15, stride: 6.5, lift: 3.5, hip: 11.4, bob: 0.9, lean: 7, roll: 3, arm: 26, elbow: [28, 0]},
                sneak: {length: 1.5, hold: 0.3, stride: 3, lift: 1.5, hip: 10.4, bob: 0.3, lean: 8, roll: 1.5, arm: 5, elbow: [25, 0], splay: 3},
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
            joints: {knee: 6, elbow: 17},
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
                // Good motors: still step by step and perfectly even, but soft, and without the rattle.
                idle: machineIdle({length: 7, moves: [1.5, 3.2, 5], move: 0.5, scan: 16, settle: 0.15, arm: [-2, 2], twitch: 2, splay: 1, antenna: 8, ease: 'easeInOutSine'}),
                style: {soft: true, hip: 11.7, splay: 1, armOut: 1.5, elbow: [18, 0], antenna: 7},
                walk: {length: 1, hold: 0.1, stride: 5.5, lift: 1.3, bob: 0.45, lean: 1, roll: 1.2, twist: 1.5, arm: 20},
                run: {length: 0.7, hold: 0.05, stride: 7.5, lift: 4, hip: 11.2, bob: 0.7, lean: 9, roll: 1.2, twist: 2.5, arm: 38, elbow: [55, 0]},
                sneak: {length: 1.4, hold: 0.15, stride: 3.5, lift: 1.5, hip: 10.2, bob: 0.25, lean: 12, roll: 0.8, arm: 7, elbow: [28, 0]},
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
            joints: {knee: 6, elbow: 17},
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
                // A mannequin learning to walk: already a human gait, but careful and a little too even.
                idle: livingIdle({length: 6, breaths: 2, glances: 1, breath: 0.1, chest: 0.5, nod: 0.5, look: 10, weight: 0.3, knee: 0.1, armBase: -2, armOut: 2, elbow: 5}),
                style: {stance: 0.56, hip: 11.5, armOut: 1, headRoll: 0.5},
                walk: {length: 1.05, stride: 6, lift: 1.7, bob: 0.55, lean: 1.5, roll: 1, twist: 4, pelvis: 1, sway: 0.3, arm: 24, lag: 0.02, elbow: [10, 14], elbowLag: 0.03},
                run: {length: 0.7, stance: 0.42, bobAt: 0.21, stride: 8, lift: 5, hip: 11, bob: 0.9, lean: 9, roll: 1, twist: 7, pelvis: 2, sway: 0.5, arm: 44, lag: 0.02, elbow: [60, 25], elbowLag: 0.03},
                sneak: {length: 1.4, stride: 3.5, lift: 1.5, hip: 10, bob: 0.25, lean: 18, roll: 1, twist: 3, arm: 8, elbow: [25, 12]},
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
            joints: {knee: 6, elbow: 17},
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
                // An athlete: collected and leaning into the movement, long steps, a real sprinter's run.
                idle: livingIdle({length: 6, breaths: 2, glances: 2, breath: 0.12, chest: 0.6, nod: 0.5, look: 12, weight: 0.5, knee: 0.15, armBase: -4, armOut: 3, elbow: 12}),
                style: {stance: 0.58, hip: 11.3, armOut: 1.5, headRoll: 0.6},
                walk: {length: 0.95, stride: 7, lift: 2.1, bob: 0.6, lean: 3.5, roll: 1.2, twist: 6, pelvis: 2, sway: 0.6, arm: 26, lag: 0.03, elbow: [14, 20], elbowLag: 0.05},
                run: {length: 0.6, stance: 0.38, bobAt: 0.19, stride: 9, lift: 6.5, shift: -1, hip: 10.8, bob: 1, lean: 14, roll: 1.2, twist: 10, pelvis: 3, sway: 0.7, arm: 52, lag: 0.02, elbow: [78, 22], elbowLag: 0.04},
                sneak: {length: 1.3, stride: 4, lift: 1.8, hip: 9.8, bob: 0.3, lean: 22, roll: 1.2, twist: 4, pelvis: 1, arm: 10, elbow: [35, 15], elbowLag: 0.04},
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
            joints: {knee: 6, elbow: 17},
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
                // A person: relaxed, the weight goes from foot to foot, hips and shoulders turn against each other, arms trail.
                idle: livingIdle({length: 8, breaths: 3, glances: 2, breath: 0.14, chest: 0.7, nod: 0.6, look: 14, weight: 1.2, knee: 0.25, armBase: -2.5, armOut: 2.5, elbow: 8}),
                style: {stance: 0.6, hip: 11.4, armOut: 0.5, headRoll: 0.7},
                walk: {length: 1.05, stride: 6.5, lift: 2, bob: 0.7, lean: 1, roll: 1.8, twist: 7, pelvis: 3, sway: 0.9, arm: 30, lag: 0.05, elbow: [8, 22], elbowLag: 0.08},
                run: {length: 0.65, stance: 0.4, bobAt: 0.2, stride: 8.5, lift: 6, shift: -0.7, hip: 10.9, bob: 1.1, lean: 10, roll: 2, twist: 12, pelvis: 4, sway: 1.2, arm: 55, lag: 0.03, elbow: [72, 26], elbowLag: 0.05},
                sneak: {length: 1.3, stride: 4, lift: 1.8, hip: 9.8, bob: 0.3, lean: 24, roll: 1.5, twist: 5, pelvis: 2, sway: 0.5, arm: 10, lag: 0.05, elbow: [30, 18], elbowLag: 0.08},
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
    // cap and guard (1.5.2): the shoulder cap and the forearm plate the first-person arm also draws, shared by both arms
    const ARM_UV = {shoulderR: [0, 100], armR: [40, 100], fistR: [70, 100], shoulderL: [0, 70], armL: [40, 70], fistL: [70, 70], plate: [110, 100],
        cap: [140, 100], guard: [190, 100]};
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

    /**
     * The hatch of an armor that opens to let its pilot in and out (1.5.2): each moving part swings from shut to open with a
     * short unlatching jolt first, holds open, and swings back. open: {bone: [x, y, z] rotation when open}.
     */
    const CHEST_OPEN = 95, VISOR_OPEN = -105;
    function hatchAnimations(open) {
        const openBones = {}, closeBones = {};
        for (const [bone, v] of Object.entries(open)) {
            const part = f => v.map(a => Math.round(a * f * 100) / 100);
            openBones[bone] = {r: {'0.000': [0, 0, 0], '0.150': part(0.06), '0.300': part(0.1), '1.200': part(1)}};
            closeBones[bone] = {r: {'0.000': part(1), '0.850': part(0.04), '1.000': [0, 0, 0]}};
        }
        return {
            'animation.power_armor.open': {length: 1.2, loop: 'hold', ease: 'easeInOutSine', bones: openBones},
            'animation.power_armor.close': {length: 1.0, loop: 'once', ease: 'easeInOutSine', bones: closeBones}
        };
    }

    /**
     * Gears and valve wheels for the detailed armors (1.5.2), in the given colours (edge, light, dark, deep, amber). Both
     * stand a tenth off the centre given, so that their edges never meet the edges of the hub under them.
     */
    function detailKit({edge, light, dark, deep, amber}) {
        /**
         * A box standing out of a face: axis 0 (x) or 2 (z) is the way the face looks, face its plane, dir -1 or +1 the way
         * out; u (the other horizontal axis) and v (y) span it; a from aLo to aHi outward. turn: degrees about the axis.
         */
        const outBox = (axis, face, dir, uLo, uHi, vLo, vHi, aLo, aHi, paint, turn) => {
            const u = axis === 0 ? 2 : 0;
            const lo = [0, 0, 0], hi = [0, 0, 0];
            lo[u] = uLo; hi[u] = uHi; lo[1] = vLo; hi[1] = vHi;
            lo[axis] = Math.min(face + dir * aLo, face + dir * aHi); hi[axis] = Math.max(face + dir * aLo, face + dir * aHi);
            const size = [0, 1, 2].map(k => Math.round((hi[k] - lo[k]) * 1000) / 1000);
            if (!turn) return C(lo, size, paint);
            const rot = axis === 0 ? [turn, 0, 0] : [0, 0, turn];
            return C(lo, size, paint, {rot, pivot: [0, 1, 2].map(k => (lo[k] + hi[k]) / 2)});
        };
        /** A gear on a face: a toothed disc (a square and the same square turned 45 degrees) with an amber hub cap. */
        const gear = (axis, face, dir, cu0, cv0, s) => {
            // a tenth off the centre, so that its edges never meet the edges of the hub under it
            const cu = cu0 + 0.1, cv = cv0 + 0.1;
            const look = axis === 0 ? 'sd' : (dir < 0 ? 'f' : 'k');
            const disc = {c: edge, n: 0.04, edge: false, all: [[0, 0, s, 1, light]], [look]: [[0, 0, s, s, edge], [1, 1, s - 2, s - 2, dark],
                [Math.floor((s - 1) / 2), Math.floor((s - 1) / 2), s % 2 ? 1 : 2, s % 2 ? 1 : 2, deep]]};
            const teeth = {c: shade(edge, 0.85), n: 0.04, edge: false, all: [[0, 0, s, 1, shade(light, 0.9)]]};
            return [
                outBox(axis, face, dir, cu - s / 2, cu + s / 2, cv - s / 2, cv + s / 2, -0.25, 0.75, disc),
                outBox(axis, face, dir, cu - s / 2, cu + s / 2, cv - s / 2, cv + s / 2, -0.35, 0.65, teeth, 45),
                outBox(axis, face, dir, cu - 0.5, cu + 0.5, cv - 0.5, cv + 0.5, 0.5, 1.5, {c: amber, n: 0.04, edge: false, all: [[0, 0, 1, 1, shade(amber, 1.25)]]})
            ];
        };
        /** A cross-shaped valve wheel standing out of a hub of size s: two steel spokes and an amber cap. */
        const valve = (axis, face, dir, cu0, cv0, s) => {
            const cu = cu0 + 0.1, cv = cv0 + 0.1;
            const l = s + 1, spoke = {c: light, n: 0.03, all: [[0, 0, 1, 1, edge]], edge: false};
            return [
                outBox(axis, face, dir, cu - 0.5, cu + 0.5, cv - l / 2, cv + l / 2, -0.15, 0.85, spoke),
                outBox(axis, face, dir, cu - l / 2, cu + l / 2, cv - 0.5, cv + 0.5, -0.3, 0.7, spoke),
                outBox(axis, face, dir, cu - 1, cu + 1, cv - 1, cv + 1, 0.45, 1.45, {c: amber, n: 0.04, all: [[0, 0, 2, 1, shade(amber, 1.25)]], edge: false})
            ];
        };
        return {outBox, gear, valve};
    }

    /**
     * The hollow trunk of a suit the pilot stands in (1.5.2): a back wall, two side walls, and inside a padded back with two
     * harness straps, shoulder pads and a grip on each side wall. x: the outer half-width, y0 and h: bottom and height, z0:
     * the front, back: the inner face of the back wall. Seen when the chest plate is open; the pilot stands in it.
     */
    function hollowTrunk({x, y0, h, z0, back, colors: {dark, deep, edge, light, glow, pad}}) {
        const w = 2 * x;
        const padded = [[0, 0, w, h, pad], [1, 1, w - 2, 1, dark], [Math.floor(w / 2) - 1, h - 4, 2, 1, glow]];
        for (let i = 2; i < h - 2; i += 2) padded.push([2, i, w - 4, 1, shade(pad, 0.82)]);
        const side = C([-x - 0.3, y0 - 0.1, z0 - 0.3], [1, h, Math.round(back - z0 + 1)], {c: dark, n: 0.05, sd: [[0, 0, Math.round(back - z0 + 1), 1, edge],
            [1, 2, Math.round(back - z0 - 1), h - 4, pad], [2, Math.floor(h / 2), 2, 1, glow]]});
        const strap = C([-x + 3, y0 + 1.5, back - 0.6], [1, h - 3, 1], {c: '#3b3428', n: 0.06, edge: false, f: [[0, 0, 1, 1, light], [0, Math.floor(h / 2), 1, 1, light]]});
        const shoulderPad = C([-x + 2, y0 + h - 4.5, back - 0.45], [4, 2, 1], {c: shade(pad, 1.15), n: 0.05, f: [[0, 0, 4, 1, shade(pad, 1.35)]]});
        const grip = C([-x + 0.6, y0 + Math.floor(h / 2), z0 + 2], [1, 1, 3], {c: light, n: 0.03, edge: false, all: [[0, 0, 3, 1, edge]]});
        return [
            C([-x, y0, back], [w, h, 1], {c: dark, n: 0.05, f: padded, k: [[0, 0, w, 1, edge]], sd: [[0, 0, 1, 1, edge]]}),
            side, mirror(side),
            strap, mirror(strap),
            shoulderPad, mirror(shoulderPad),
            grip, mirror(grip)
        ];
    }

    // Combat (1.5.2, second design): a heavy front-opening suit after the owner's references. Every size is whole, so one
    // texture pixel is one model pixel; parts sink into each other instead of touching face to face, so nothing flickers
    // (checked: noCoplanarFaces). Plates stand out over a dark undersuit; the chest, knees, shoulders and helmet have
    // raised plates, hubs and grilles; the face is symmetric. The chest plate and the visor open (chest_door, visor).
    (function () {
        const plate = '#5d646b', light = '#a7afb5', edge = '#7f878e', dark = '#2a2e33', deep = '#16181b', olive = '#58623e',
            rust = '#6e4a30', amber = '#e0a020', lens = '#8ff4ff', glow = '#ff6a3a', tan = '#8d8371';
        const P = (o, s, paint, opts) => C(o, s, Object.assign({c: plate, n: 0.045}, paint), opts);
        const U = (o, s, paint, opts) => C(o, s, Object.assign({c: dark, n: 0.05}, paint), opts);
        const rivets = (w, h) => [[1, 1, 1, 1, light], [w - 2, 1, 1, 1, light], [1, h - 2, 1, 1, light], [w - 2, h - 2, 1, 1, light]];
        const lines = (w, rows) => rows.map(y => [0, y, w, 1, shade(plate, 0.72)]);
        /** A hub seen from its face: a dark disc, a light ring, a centre; s is the face size (square). */
        const hub = s => [[0, 0, s, s, deep], [1, 0, s - 2, 1, light], [1, s - 1, s - 2, 1, edge], [0, 1, 1, s - 2, light], [s - 1, 1, 1, s - 2, edge],
            [Math.floor((s - 1) / 2), Math.floor((s - 1) / 2), s % 2 ? 1 : 2, s % 2 ? 1 : 2, amber]];
        const grille = (x, y, w, h) => {
            const ops = [[x, y, w, h, deep]];
            for (let i = 0; i < h; i += 2) ops.push([x, y + i, w, 1, edge]);
            return ops;
        };
        const sideHub = U([0, 0, 0], [1, 4, 4], {sd: hub(4), edge: false});
        /**
         * A cross-shaped valve wheel standing out of a hub: two steel spokes and an amber cap. axis 0 (x) or 2 (z) is the way
         * the hub faces; face is the hub's outer plane, dir -1 or +1 the way out; cu, cv the hub's centre (u = the other
         * horizontal axis, v = y); s the hub's size. The spokes sink 0.15 and 0.3 into the hub and the cap into the spokes, so
         * no two faces meet in one plane.
         */
        const valve = (axis, face, dir, cu, cv, s) => {
            const u = axis === 0 ? 2 : 0;
            const length = s + 1;
            const box = (uLo, uHi, vLo, vHi, aLo, aHi, paint) => {
                const lo = [0, 0, 0], hi = [0, 0, 0];
                lo[u] = uLo; hi[u] = uHi; lo[1] = vLo; hi[1] = vHi;
                lo[axis] = Math.min(face + dir * aLo, face + dir * aHi); hi[axis] = Math.max(face + dir * aLo, face + dir * aHi);
                return C(lo, [0, 1, 2].map(k => Math.round((hi[k] - lo[k]) * 1000) / 1000), paint);
            };
            const spoke = {c: light, n: 0.03, all: [[0, 0, 1, 1, edge]], edge: false};
            return [
                box(cu - 0.5, cu + 0.5, cv - length / 2, cv + length / 2, -0.15, 0.85, spoke),
                box(cu - length / 2, cu + length / 2, cv - 0.5, cv + 0.5, -0.3, 0.7, spoke),
                box(cu - 1, cu + 1, cv - 1, cv + 1, 0.45, 1.45, {c: amber, n: 0.04, all: [[0, 0, 2, 1, shade(amber, 1.25)]], tp: [[0, 0, 2, 1, shade(amber, 1.25)]], edge: false})
            ];
        };
        const at = (cube, o) => Object.assign({}, cube, {o});

        // ---------------------------------------------------------------- the legs (right; the left is mirrored)
        const leg = [
            U([-6.5, 2.5, -2.5], [5, 13, 5], {all: [[0, 4, 5, 1, deep], [0, 9, 5, 1, deep]]}),
            P([-7, 9.5, -3], [6, 5, 6], {f: [[0, 0, 6, 1, light], ...lines(6, [2]), [0, 4, 6, 1, dark]], sd: [[0, 0, 6, 1, light], [1, 2, 4, 1, dark]],
                k: [[0, 0, 6, 1, light], [2, 1, 2, 3, dark]]}),
            P([-6.5, 10.3, -3.8], [5, 4, 1], {f: [[0, 0, 5, 1, light], [1, 1, 3, 2, shade(plate, 0.85)], [2, 3, 1, 1, rust]]}),
            C([-7.6, 10, -2], [1, 4, 4], {c: olive, n: 0.05, sd: [[1, 1, 2, 2, shade(olive, 0.8)], [0, 0, 4, 1, shade(olive, 1.25)]]}),
            P([-7.5, 6, -4], [7, 4, 2], {f: [[0, 0, 7, 1, light], [1, 1, 5, 2, shade(plate, 0.82)], [0, 3, 7, 1, dark]], tp: [[0, 0, 7, 1, light]]}),
            U([-5, 7, -4.6], [2, 2, 1], {f: [[0, 0, 2, 2, deep], [0, 0, 1, 1, light]], edge: false}),
            at(sideHub, [-8.1, 6.5, -1.5]),
            ...valve(0, -8.1, -1, 0.5, 8.4, 4),
            C([-5, 4, 2.3], [2, 6, 1], {c: light, n: 0.04, k: [[0, 0, 1, 6, edge]], edge: false}),
            P([-7, 2.2, -3], [6, 4, 6], {f: [[0, 0, 6, 1, light], [0, 3, 6, 1, dark]], sd: [[0, 0, 6, 1, light], [2, 1, 2, 2, dark]], k: [[0, 0, 6, 1, light]]}),
            P([-6.5, 2.5, -3.8], [5, 3, 1], {f: [[0, 0, 5, 1, light], ...hazard(0, 2, 5, 1, amber, dark, 1)]}),
            U([-7.5, 0, -5], [7, 2, 8], {tp: [[1, 1, 5, 6, shade(dark, 1.3)]], f: [[0, 0, 7, 1, edge]], sd: [[0, 0, 8, 1, edge], [2, 1, 1, 1, light], [5, 1, 1, 1, light]]}),
            P([-7, 0.5, -6], [6, 2, 2], {f: [[0, 0, 6, 1, light], [1, 1, 1, 1, dark], [4, 1, 1, 1, dark]], tp: [[0, 0, 6, 1, light]]}),
            P([-6.5, 0.4, 2], [5, 2, 2], {k: [[0, 0, 5, 1, light]]}),
            at(U([0, 0, 0], [1, 2, 2], {sd: [[0, 0, 2, 2, deep], [0, 0, 1, 1, light]], edge: false}), [-8.1, 0.5, -1]),
            U([-6, 14.8, -2], [4, 2, 4], {sd: [[0, 0, 4, 1, edge]]}),
            P([-4.5, 10.6, -4.3], [1, 3, 1], {f: [[0, 0, 1, 1, light]], edge: false}),
            P([-7.4, 13.9, -3.4], [7, 1, 7], {all: [[0, 0, 7, 1, light]], tp: [[0, 0, 7, 1, light]]}),
            U([-7.5, 3.4, -3.5], [7, 1, 7], {all: [[0, 0, 7, 1, edge]]}),
            P([-6, 2.3, -5.9], [1, 1, 2], {tp: [[0, 0, 1, 2, light]], edge: false}),
            P([-3, 2.3, -5.9], [1, 1, 2], {tp: [[0, 0, 1, 2, light]], edge: false})
        ];

        // ---------------------------------------------------------------- the torso
        const torso = [
            // the hollow trunk the pilot stands in, seen when the chest plate is open (1.5.2)
            ...hollowTrunk({x: 8, y0: 18, h: 15, z0: -4, back: 4, colors: {dark, deep, edge, light, glow, pad: '#2f3329'}}),
            P([-7, 15, -3.5], [14, 4, 7], {f: [...grille(1, 1, 12, 2)], k: [...grille(1, 1, 12, 2)], sd: [[0, 0, 7, 1, light]]}),
            P([-5, 15.5, -4.3], [10, 3, 1], {f: [[0, 0, 10, 1, light], [4, 1, 2, 1, amber], [1, 2, 8, 1, dark]]}),
            U([-6, 18.7, -4.6], [12, 1, 1], {f: [[0, 0, 12, 1, edge]], edge: false}),
            U([-6, 19.9, -4.6], [12, 1, 1], {f: [[0, 0, 12, 1, edge]], edge: false}),
            // the shoulder yoke
            P([-10, 32.5, -4.7], [20, 2, 10], {tp: [[1, 1, 18, 1, light], [1, 8, 18, 1, dark], [9, 2, 2, 6, dark]], f: [[0, 0, 20, 1, light]], k: [[0, 0, 20, 1, light]]}),
            // the power pack: a housing with a recessed grille, a core port, two exhaust canisters, two hoses
            P([-7, 20, 4.5], [14, 11, 5], {k: [[0, 0, 14, 1, light], ...grille(4, 6, 6, 4), [1, 1, 2, 9, dark], [11, 1, 2, 9, dark], ...rivets(14, 11)],
                sd: [[0, 0, 5, 1, light], [1, 2, 3, 6, olive]], tp: [[1, 1, 12, 3, dark]]}),
            U([-1.5, 23, 9.2], [3, 3, 1], {k: hub(3), edge: false}),
            ...valve(2, 10.2, 1, 0, 24.5, 3),
            C([-6.5, 21.8, 8.8], [3, 9, 3], {c: tan, n: 0.07, all: [[0, 1, 3, 1, dark], [0, 4, 3, 1, rust], [0, 7, 3, 1, dark]], tp: [[1, 1, 1, 1, deep]]}),
            mirror(C([-6.5, 21.8, 8.8], [3, 9, 3], {c: tan, n: 0.07, all: [[0, 1, 3, 1, dark], [0, 4, 3, 1, rust], [0, 7, 3, 1, dark]], tp: [[1, 1, 1, 1, deep]]})),
            C([-5.5, 31.3, 5.5], [2, 2, 4], {c: rust, n: 0.08, edge: false, all: [[0, 1, 4, 1, shade(rust, 0.7)]]}),
            mirror(C([-5.5, 31.3, 5.5], [2, 2, 4], {c: rust, n: 0.08, edge: false, all: [[0, 1, 4, 1, shade(rust, 0.7)]]})),
            // side armour plates and cooling fins on the pack
            P([-8.6, 19, -3.2], [1, 6, 7], {sd: [[0, 0, 7, 1, light], [1, 2, 5, 1, dark], [1, 4, 5, 1, dark]]}),
            mirror(P([-8.6, 19, -3.2], [1, 6, 7], {sd: [[0, 0, 7, 1, light], [1, 2, 5, 1, dark], [1, 4, 5, 1, dark]]})),
            P([-2.5, 30.8, 5.2], [1, 1, 4], {tp: [[0, 0, 1, 4, light]], edge: false}),
            P([-0.5, 30.8, 5.2], [1, 1, 4], {tp: [[0, 0, 1, 4, light]], edge: false}),
            P([1.5, 30.8, 5.2], [1, 1, 4], {tp: [[0, 0, 1, 4, light]], edge: false}),
            // a hub on each side, under the shoulders
            at(sideHub, [-8.6, 26, -1.5]),
            mirror(at(sideHub, [-8.6, 26, -1.5]))
        ];

        // ---------------------------------------------------------------- the chest plate (opens forward and down)
        const chestPanel = P([-8, 21.5, -6.6], [6, 5, 1], {f: [[0, 0, 6, 1, light], ...lines(6, [2, 4]), [1, 1, 1, 1, light]]});
        const chestHub = U([-9.5, 28, -7.6], [3, 3, 1], {f: hub(3), edge: false});
        const door = [
            P([-9, 21, -6.2], [18, 12, 2], {f: [[0, 0, 18, 1, light], ...rivets(18, 12), [0, 11, 18, 1, dark]],
                k: [[0, 0, 18, 12, deep], [2, 2, 3, 8, dark], [13, 2, 3, 8, dark], [8, 3, 2, 2, glow]], sd: [[0, 0, 2, 1, light]], tp: [[0, 0, 18, 1, light]]}),
            P([-8, 27, -7], [16, 5, 1], {f: [[0, 0, 16, 1, light], [1, 1, 14, 1, edge], [3, 3, 3, 1, dark], [10, 3, 3, 1, dark], [7, 2, 2, 2, dark]],
                tp: [[0, 0, 16, 1, light]], sd: [[0, 0, 1, 5, light]]}),
            P([-1, 21.5, -7], [2, 5, 1], {f: [[0, 0, 2, 1, light], [0, 2, 2, 1, dark], [0, 4, 2, 1, dark]]}),
            chestPanel, mirror(chestPanel),
            chestHub, mirror(chestHub),
            ...valve(2, -7.6, -1, -8, 29.5, 3),
            ...valve(2, -7.6, -1, -8, 29.5, 3).map(c => mirror(c)),
            P([-9.4, 21.4, -6.9], [1, 11, 1], {f: [[0, 0, 1, 11, edge], [0, 0, 1, 1, light]], edge: false}),
            mirror(P([-9.4, 21.4, -6.9], [1, 11, 1], {f: [[0, 0, 1, 11, edge], [0, 0, 1, 1, light]], edge: false})),
            P([-7, 28.5, -7.4], [5, 1, 1], {f: [[0, 0, 5, 1, light]], edge: false}),
            mirror(P([-7, 28.5, -7.4], [5, 1, 1], {f: [[0, 0, 5, 1, light]], edge: false})),
            U([-7, 32.9, -5.9], [14, 2, 2], {f: [[0, 0, 14, 1, edge]], tp: [[1, 0, 12, 1, light]]})
        ];

        // ---------------------------------------------------------------- the helmet and its visor (lifts)
        const head = [
            // the helmet is hollow (1.5.2): the pilot's head is in it, seen when the visor lifts
            P([-5, 34.8, 4.1], [10, 8, 1], {f: [[0, 0, 10, 8, '#2f3329'], [1, 1, 8, 1, dark], [2, 3, 6, 1, shade('#2f3329', 0.82)], [2, 5, 6, 1, shade('#2f3329', 0.82)]],
                k: [[0, 0, 10, 1, light], ...grille(2, 2, 6, 4), [0, 7, 10, 1, dark]]}),
            P([-5.1, 34.7, -5.3], [1, 8, 10], {sd: [[0, 0, 10, 1, light], ...grille(6, 2, 3, 4), [0, 7, 10, 1, dark]]}),
            mirror(P([-5.1, 34.7, -5.3], [1, 8, 10], {sd: [[0, 0, 10, 1, light], ...grille(6, 2, 3, 4), [0, 7, 10, 1, dark]]})),
            P([-4.4, 42, -5.1], [9, 1, 10], {tp: [[4, 0, 1, 10, olive], [0, 0, 9, 1, light]], bt: [[0, 0, 9, 10, '#2f3329']], all: [[0, 0, 9, 1, light]]}),
            C([-1, 42.5, -4.2], [2, 1, 9], {c: olive, n: 0.05, tp: [[0, 0, 2, 1, shade(olive, 1.3)]]}),
            at(sideHub, [-5.6, 36, -2]),
            mirror(at(sideHub, [-5.6, 36, -2])),
            ...valve(0, -5.6, -1, 0, 38, 4),
            ...valve(0, -5.6, -1, 0, 38, 4).map(c => mirror(c)),
            C([3.5, 42.6, 2], [1, 3, 1], {c: light, n: 0.03, tp: [[0, 0, 1, 1, '#ff3b2f']], edge: false}),
            U([-4, 33.6, -3.8], [8, 1, 8], {}),
            P([-4, 42.4, -4], [8, 1, 8], {tp: [[0, 0, 8, 1, light], [3, 1, 2, 6, shade(plate, 0.85)]], all: [[0, 0, 8, 1, light]]}),
            P([-5.4, 35.2, -4.6], [1, 4, 6], {sd: [[0, 0, 6, 1, light], [1, 2, 4, 1, dark]]}),
            mirror(P([-5.4, 35.2, -4.6], [1, 4, 6], {sd: [[0, 0, 6, 1, light], [1, 2, 4, 1, dark]]})),
            P([-4, 35.5, 4.6], [8, 5, 1], {k: [[0, 0, 8, 1, light], ...grille(1, 1, 6, 3)]})
        ];
        const sideFilter = [
            C([-5.6, 34.3, -8.2], [2, 2, 3], {c: olive, n: 0.05, all: [[0, 0, 3, 1, shade(olive, 1.25)]], f: [[0, 0, 2, 2, shade(olive, 0.75)], [0, 0, 1, 1, shade(olive, 1.3)]]},
                {rot: [10, -28, 0], pivot: [-4, 35.3, -6.6]}),
            C([-5.4, 34.5, -8.6], [2, 2, 1], {c: light, n: 0.03, f: [[0, 0, 2, 2, edge], [0, 0, 1, 1, light]], edge: false},
                {rot: [10, -28, 0], pivot: [-4, 35.3, -6.6]}),
            C([-4.9, 35.6, -6.4], [1, 1, 3], {c: rust, n: 0.08, all: [[0, 0, 3, 1, shade(rust, 0.7)]], edge: false}, {rot: [0, -12, 0], pivot: [-4.4, 36.1, -5]})
        ];
        const eye = C([-3.5, 38.5, -6.5], [2, 1, 1], {c: lens, n: 0.02, edge: false, f: [[0, 0, 2, 1, lens]]});
        const visor = [
            P([-4.5, 35, -6.2], [9, 7, 1], {f: [[0, 0, 9, 1, light], [1, 2, 3, 2, deep], [5, 2, 3, 2, deep], [4, 1, 1, 3, edge], [0, 6, 9, 1, dark]],
                k: [[0, 0, 9, 7, deep]]}),
            eye, mirror(eye),
            P([-5, 40.5, -7.4], [10, 1, 2], {f: [[0, 0, 10, 1, light]], tp: [[0, 0, 10, 1, light]], edge: false}),
            // the mask: a base over mouth and chin, a snout with a grille, a round filter of two crossed blocks and a cap
            U([-3, 34.9, -6.9], [6, 3, 1], {f: [[0, 0, 6, 1, edge], [0, 2, 6, 1, deep]], tp: [[0, 0, 6, 1, edge]]}),
            P([-2.5, 35.2, -7.8], [5, 2, 1], {f: [[0, 0, 5, 1, light], [0, 1, 5, 1, deep], [1, 1, 1, 1, edge], [3, 1, 1, 1, edge]], tp: [[0, 0, 5, 1, light]]}),
            U([-1.5, 34.4, -9.3], [3, 3, 2], {all: [[0, 0, 3, 1, edge]], f: [[0, 0, 3, 3, dark], [1, 1, 1, 1, deep]]}),
            P([-2, 34.9, -9.1], [4, 2, 2], {all: [[0, 0, 4, 1, light]], f: [[0, 0, 4, 2, plate], [0, 0, 4, 1, light]]}),
            C([-1, 35.3, -9.8], [2, 2, 1], {c: amber, n: 0.04, f: [[0, 0, 2, 2, amber], [0, 0, 1, 1, shade(amber, 1.3)]], edge: false}),
            ...sideFilter, ...sideFilter.map(c => mirror(c))
        ];

        // ---------------------------------------------------------------- the arms (right; the left is mirrored)
        const arm = [
            P([-16.5, 28, -5.5], [8, 7, 11], {f: [[0, 0, 8, 1, light], ...hazard(0, 6, 8, 1, amber, dark, 1)], k: [[0, 0, 8, 1, light]],
                sd: [[0, 0, 11, 1, light], [1, 2, 9, 1, edge], [1, 5, 9, 1, dark], ...rivets(11, 7)], tp: [[1, 1, 6, 9, edge]]}, {uv: ARM_UV.shoulderR}),
            U([-14, 12, -3], [5, 17, 6], {all: [[0, 4, 6, 1, deep], [0, 8, 6, 1, deep], [0, 13, 6, 1, deep]]}, {uv: ARM_UV.armR}),
            U([-14.5, 7.5, -3.5], [6, 5, 7], {f: [[0, 0, 6, 1, edge], [1, 2, 1, 2, edge], [3, 2, 1, 2, edge], [5, 2, 1, 2, edge]], all: [[0, 0, 6, 1, edge]]}, {uv: ARM_UV.fistR}),
            P([-17, 34.6, -6], [9, 2, 12], {tp: [[1, 1, 7, 10, edge], [4, 1, 1, 10, dark]], sd: [[0, 0, 12, 1, light]], f: [[0, 0, 9, 1, light]], k: [[0, 0, 9, 1, light]]}, {uv: ARM_UV.cap}),
            P([-15.2, 12.8, -4], [7, 7, 8], {f: [[0, 0, 7, 1, light], [3, 1, 1, 5, dark], [1, 5, 2, 1, rust]], sd: [[0, 0, 8, 1, light], [1, 2, 6, 1, dark], [1, 4, 6, 1, dark]],
                bt: [[1, 1, 5, 6, dark]], k: [[0, 0, 7, 1, light]]}, {uv: ARM_UV.guard}),
            U([-14.5, 19.5, -3.5], [6, 2, 7], {all: [[0, 0, 6, 1, edge]]}),
            C([-14.6, 22, -2.5], [1, 5, 5], {c: olive, n: 0.05, sd: [[1, 1, 3, 3, shade(olive, 0.8)], [0, 0, 5, 1, shade(olive, 1.25)]]}),
            U([-14.5, 29.5, -6.2], [4, 4, 1], {f: hub(4), edge: false}),
            ...valve(2, -6.2, -1, -12.5, 31.5, 4),
            at(sideHub, [-17.2, 29.5, -2]),
            ...valve(0, -17.2, -1, 0, 31.5, 4),
            P([-14, 8, -4.3], [5, 2, 1], {f: [[0, 0, 5, 1, light], [1, 1, 1, 1, dark], [3, 1, 1, 1, dark]]}),
            C([-12.5, 13.5, 3.6], [2, 6, 1], {c: light, n: 0.04, k: [[0, 0, 1, 6, edge]], edge: false}),
            P([-16.8, 32.5, -6.4], [8, 2, 1], {f: [[0, 0, 8, 1, light], [0, 1, 8, 1, dark]]}),
            P([-13, 36.4, -5], [1, 1, 10], {tp: [[0, 0, 1, 10, light]], edge: false}),
            U([-15.6, 15, -4.5], [8, 1, 9], {all: [[0, 0, 8, 1, edge]]}),
            U([-15.6, 16.8, -4.5], [8, 1, 9], {all: [[0, 0, 8, 1, edge]]})
        ];

        SPECS.power_armor = {
            res: 256, seed: 606, wrecked: true, noCoplanarFaces: true, cutMainOnly: true,
            joints: {knee: 8, elbow: 20},
            armTable: 'combat',
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...limbs('leg', 'root', [-4, 16, 0], leg),
                {name: 'torso', parent: 'root', pivot: [0, 16, 0], cubes: torso},
                {name: 'chest_door', parent: 'torso', pivot: [0, 21, -6.2], cubes: door},
                {name: 'head', parent: 'torso', pivot: [0, 34, 0], cubes: head},
                {name: 'visor', parent: 'head', pivot: [0, 42, -6.2], cubes: visor},
                ...limbs('arm', 'torso', [-11, 31, 0], arm, ARM_LEFT_UV),
                {name: 'right_hand', parent: 'right_arm', pivot: [-11.5, 11, 0], cubes: []},
                {name: 'left_hand', parent: 'left_arm', pivot: [11.5, 11, 0], cubes: []}
            ],
            animations: Object.assign(armorAnimations({
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
            }), hatchAnimations({chest_door: [CHEST_OPEN, 0, 0], visor: [VISOR_OPEN, 0, 0]}))
        };
    })();

    // Mobile (1.5.2, second design): a fast scout on raptor legs. Each leg has three bones: a thick thigh, a long shin that
    // runs back from a forward knee, and a foot with two walking toes, a raised sickle claw and a heel spur. The body is carried
    // leaning forward and balanced by a tail of two bones; the long helmet ends in a muzzle. The chest plate and the muzzle
    // open for the pilot (chest_door, visor). Graphite and olive plates over a dark undersuit, small amber and red marks.
    // Whole sizes, and parts sink into each other instead of touching face to face (noCoplanarFaces). The legs walk by
    // inverse kinematics: the toes stay on the ground while the body moves over them.
    (function () {
        const plate = '#4d5249', light = '#868c7f', edge = '#676d62', dark = '#232628', deep = '#131516', olive = '#525836',
            tan = '#77705c', amber = '#bf8420', red = '#c4391f', claw = '#34383a', steel = '#979d98';
        const P = (o, s, paint, opts) => C(o, s, Object.assign({c: plate, n: 0.045}, paint), opts);
        const U = (o, s, paint, opts) => C(o, s, Object.assign({c: dark, n: 0.05}, paint), opts);
        const O = (o, s, paint, opts) => C(o, s, Object.assign({c: olive, n: 0.05}, paint), opts);
        const hub = s => [[0, 0, s, s, deep], [1, 0, s - 2, 1, light], [1, s - 1, s - 2, 1, edge], [0, 1, 1, s - 2, light], [s - 1, 1, 1, s - 2, edge],
            [Math.floor((s - 1) / 2), Math.floor((s - 1) / 2), s % 2 ? 1 : 2, s % 2 ? 1 : 2, amber]];
        const grille = (x, y, w, h) => {
            const ops = [[x, y, w, h, deep]];
            for (let i = 0; i < h; i += 2) ops.push([x, y + i, w, 1, edge]);
            return ops;
        };
        const DEG = 180 / Math.PI;
        /**
         * A part along a bone segment seen from the side, from p (the upper end) to q, as [y, z] points: x0 and w across, d
         * deep, a whole length (plus extra). Tilted about x so that it lies along the segment; shift moves it off the line,
         * positive toward the back of the segment.
         */
        const seg = (x0, w, d, p, q, paint, extra, shift) => {
            const dy = q[0] - p[0], dz = q[1] - p[1], l = Math.hypot(dy, dz);
            const n = shift ? [dz / l * shift, -dy / l * shift] : [0, 0];
            const length = Math.round(l) + (extra || 0);
            const my = (p[0] + q[0]) / 2 + n[0], mz = (p[1] + q[1]) / 2 + n[1];
            const tilt = Math.round(Math.atan2(p[1] - q[1], p[0] - q[0]) * DEG * 10) / 10;
            return C([x0, my - length / 2, mz - d / 2], [w, length, d], paint, {rot: [tilt, 0, 0], pivot: [x0 + w / 2, my, mz]});
        };

        const {gear, valve} = detailKit({edge, light, dark, deep, amber});

        // the joints of the right leg in the side view, [y, z]
        const HIP = [18, 0], KNEE = [12.5, -3.5], ANKLE = [6.5, 3], BALL = [1, 0];

        // ---------------------------------------------------------------- the legs (right; the left is mirrored)
        const thigh = [
            seg(-6.9, 5, 5, HIP, KNEE, {c: plate, n: 0.045, f: [[0, 0, 5, 1, light], [1, 2, 3, 4, shade(plate, 0.85)], [0, 8, 5, 1, dark]],
                sd: [[0, 0, 5, 1, light], [1, 3, 3, 1, dark], [1, 5, 3, 1, dark]], k: [[0, 0, 5, 1, light], [1, 2, 3, 5, dark]]}, 2),
            seg(-7.8, 1, 6, [19, 0.5], [13, -3], {c: olive, n: 0.05, sd: [[0, 0, 6, 1, shade(olive, 1.25)], [1, 2, 4, 3, shade(olive, 0.82)], [2, 3, 2, 1, tan]]}),
            U([-7.4, 16, -2.5], [6, 4, 5], {sd: [[0, 0, 5, 1, edge]], f: [[0, 0, 6, 1, edge]]}),
            ...gear(0, -7.8, -1, 0, 18, 4),
            seg(-4.8, 1, 1, [17, -2], [12.5, -4.8], {c: light, n: 0.03, all: [[0, 0, 1, 1, edge]], edge: false}, 0, -2.4)
        ];
        const shin = [
            U([-6.6, 10.5, -5.5], [4, 4, 4], {sd: hub(4), f: [[0, 0, 4, 1, edge]]}),
            ...valve(0, -6.6, -1, -3.5, 12.5, 4),
            P([-6, 10.8, -6.3], [3, 3, 1], {f: [[0, 0, 3, 1, light], [1, 1, 1, 1, amber]], edge: false}),
            seg(-5.9, 3, 3, KNEE, ANKLE, {c: dark, n: 0.05, all: [[0, 3, 3, 1, deep], [0, 7, 3, 1, deep]]}, 1),
            seg(-6.5, 4, 2, [11.5, -2.5], [7.5, 1.8], {c: plate, n: 0.045, k: [[0, 0, 4, 1, light], [1, 2, 2, 3, shade(plate, 0.82)]],
                f: [[0, 0, 4, 1, light]], sd: [[0, 0, 2, 1, light]]}, 0, 1.8),
            seg(-5, 1, 1, KNEE, ANKLE, {c: steel, n: 0.03, all: [[0, 0, 1, 1, edge]], edge: false}, -2, -1.9)
        ];
        const toeTip = (x, z) => C([x + 0.15, 0.4, z - 0.9], [1, 2, 1], {c: claw, n: 0.03, all: [[0, 1, 1, 1, steel]], edge: false},
            {rot: [32, 0, 0], pivot: [x + 0.65, 1.6, z - 0.4]});
        const foot = [
            U([-6.2, 5, 1.5], [3, 3, 3], {sd: hub(3), edge: false}),
            ...gear(0, -6.2, -1, 3, 6.5, 3),
            seg(-6, 3, 3, ANKLE, BALL, {c: plate, n: 0.045, f: [[0, 0, 3, 1, light], [1, 2, 1, 3, shade(plate, 0.8)]], sd: [[0, 0, 3, 1, light]]}, 1),
            seg(-5.5, 2, 1, ANKLE, BALL, {c: olive, n: 0.05, f: [[0, 0, 2, 1, shade(olive, 1.25)]]}, -1, -1.8),
            U([-6.5, 0, -1.5], [4, 2, 3], {tp: [[1, 1, 2, 1, edge]], sd: [[0, 0, 3, 1, edge]]}),
            P([-6.3, 0.3, -5.3], [1, 2, 4], {tp: [[0, 0, 1, 1, light]], all: [[0, 0, 4, 1, light]]}),
            P([-5, 0.3, -6.3], [1, 2, 5], {tp: [[0, 0, 1, 1, light]], all: [[0, 0, 5, 1, light]]}),
            toeTip(-6.3, -5.3), toeTip(-5, -6.3),
            // the raised inner toe with the sickle claw
            P([-3.7, 1.3, -3.3], [1, 2, 2], {all: [[0, 0, 2, 1, light]]}),
            C([-3.55, 2.9, -3.7], [1, 4, 1], {c: claw, n: 0.03, all: [[0, 0, 1, 1, steel]], edge: false}, {rot: [-38, 0, 0], pivot: [-3.05, 3, -2.9]}),
            U([-5.1, 0.5, 1.3], [2, 2, 2], {k: [[0, 0, 2, 1, edge]]})
        ];
        const legChain = [['leg', 'root', [HIP[0], HIP[1]], thigh], ['shin', 'leg', KNEE, shin], ['foot', 'shin', ANKLE, foot]];
        const legBones = [];
        for (const [name, parent, [y, z], cubes] of legChain) {
            legBones.push({name: 'right_' + name, parent: parent === 'root' ? 'root' : 'right_' + parent, pivot: [-4.5, y, z], cubes});
            legBones.push({name: 'left_' + name, parent: parent === 'root' ? 'root' : 'left_' + parent, pivot: [4.5, y, z], cubes: cubes.map(c => mirror(c))});
        }

        // ---------------------------------------------------------------- the torso and the chest plate
        const sidePlate = P([-7.8, 22, -3], [1, 8, 7], {sd: [[0, 0, 7, 1, light], [1, 2, 5, 1, dark], [1, 4, 5, 1, dark], [1, 6, 5, 1, dark]]});
        const skirt = P([-7.2, 16.3, -3.4], [1, 4, 7], {sd: [[0, 0, 7, 1, light], [1, 2, 5, 1, shade(plate, 0.8)]]});
        const torso = [
            U([-6, 15.8, -3.5], [12, 5, 8], {f: [[0, 0, 12, 1, edge], [4, 1, 4, 3, deep]], sd: [[0, 0, 8, 1, edge]], k: [[0, 0, 12, 1, edge]]}),
            // the hollow trunk the pilot stands in, seen when the chest plate is open (1.5.2), with a roof under the collar
            ...hollowTrunk({x: 7, y0: 20.5, h: 11, z0: -4, back: 4.2, colors: {dark, deep, edge, light, glow: red, pad: '#2c2f27'}}),
            U([-6, 31.2, -4.2], [12, 1, 8], {bt: [[0, 0, 12, 8, deep], [5, 3, 2, 2, red]], edge: false}),
            P([-5, 21.5, 4.8], [10, 9, 3], {k: [[0, 0, 10, 1, light], ...grille(1, 2, 8, 5), [1, 8, 2, 1, amber], [7, 8, 2, 1, deep]], sd: [[0, 0, 3, 1, light]], tp: [[0, 0, 10, 1, light]]}),
            sidePlate, mirror(sidePlate),
            U([-5, 31.3, -3], [10, 2, 7], {all: [[0, 0, 10, 1, edge]]}),
            skirt, mirror(skirt),
            P([-5, 17, -4.3], [10, 3, 1], {f: [[0, 0, 10, 1, light], [1, 2, 8, 1, dark]]}),
            U([-1.5, 24.5, 7.6], [3, 3, 1], {k: hub(3), edge: false}),
            ...valve(2, 8.6, 1, 0, 26, 3),
            ...gear(2, 7.8, 1, -3.4, 28.5, 3), ...gear(2, 7.8, 1, -3.4, 28.5, 3).map(c => mirror(c)),
            ...gear(0, -7.8, -1, 0.5, 26, 4), ...gear(0, -7.8, -1, 0.5, 26, 4).map(c => mirror(c))
        ];
        const doorHub = U([-5, 22.3, -6.3], [3, 3, 1], {f: hub(3), edge: false});
        const door = [
            P([-6, 21, -5.8], [12, 10, 2], {f: [[0, 0, 12, 1, light], [1, 6, 10, 1, shade(plate, 0.8)], [0, 9, 12, 1, dark]], k: [[0, 0, 12, 10, deep]],
                sd: [[0, 0, 2, 1, light]]}),
            P([-5, 26, -6.6], [10, 4, 1], {f: [[0, 0, 10, 1, light], [1, 1, 3, 2, shade(plate, 0.85)], [6, 1, 3, 2, shade(plate, 0.85)], [0, 3, 10, 1, dark]]}),
            P([-0.5, 22.5, -6.7], [1, 8, 1], {f: [[0, 0, 1, 8, edge], [0, 0, 1, 1, light], [0, 6, 1, 1, red]], edge: false}),
            doorHub, mirror(doorHub),
            ...valve(2, -6.3, -1, -3.5, 23.8, 3), ...valve(2, -6.3, -1, -3.5, 23.8, 3).map(c => mirror(c))
        ];

        // ---------------------------------------------------------------- the head, the muzzle and the tail
        const cheek = P([-4.7, 33.8, -3.5], [1, 3, 5], {sd: [[0, 0, 5, 1, light], [1, 2, 3, 1, dark]]});
        const headSide = P([-4.1, 33.4, -4.7], [1, 7, 8], {sd: [[0, 0, 8, 1, light], [5, 2, 2, 2, deep], [0, 6, 8, 1, dark]]});
        const headFin = C([-4.2, 38.6, 1], [1, 2, 6], {c: plate, n: 0.045, sd: [[0, 0, 6, 1, light]], all: [[0, 0, 1, 1, light]]}, {rot: [-22, 0, 0], pivot: [-3.7, 39, 1]});
        const head = [
            U([-2.5, 31, -3.4], [5, 2, 5], {all: [[0, 1, 5, 1, deep]]}),
            // the helmet is hollow (1.5.2): the pilot's head is in it, seen when the faceplate with the muzzle lifts
            P([-4, 33.5, 2.5], [8, 7, 1], {f: [[0, 0, 8, 7, '#2c2f27'], [1, 1, 6, 1, dark], [2, 3, 4, 1, shade('#2c2f27', 0.82)]], k: [[0, 0, 8, 1, light], ...grille(1, 2, 6, 3)]}),
            headSide, mirror(headSide),
            P([-3.5, 39.6, -4.4], [7, 1, 8], {tp: [[3, 0, 1, 8, dark]], bt: [[0, 0, 7, 8, '#2c2f27']], all: [[0, 0, 7, 1, light]]}),
            P([-0.5, 40.4, -3.9], [1, 1, 7], {tp: [[0, 0, 1, 7, light]], edge: false}),
            cheek, mirror(cheek),
            headFin, mirror(headFin),
            ...gear(0, -4.1, -1, 1.5, 37, 3), ...gear(0, -4.1, -1, 1.5, 37, 3).map(c => mirror(c))
        ];
        const eye = C([-2.8, 36, -8], [1, 1, 3], {c: red, n: 0.03, sd: [[0, 0, 3, 1, red], [0, 0, 1, 1, shade(red, 1.4)]], edge: false});
        const muzzle = [
            // the faceplate over the whole front of the helmet; the muzzle stands out of it
            P([-4, 33.3, -5.6], [8, 7, 1], {f: [[0, 0, 8, 1, light], ...vents(1, 1, 6, 2, dark), [0, 6, 8, 1, dark]], k: [[0, 0, 8, 7, deep]], sd: [[0, 0, 1, 7, light]]}),
            P([-2.5, 33.8, -10.3], [5, 4, 6], {f: [[0, 0, 5, 1, light], [0, 3, 5, 1, dark]], sd: [[0, 0, 6, 1, light], [1, 2, 4, 1, dark]], tp: [[2, 0, 1, 6, dark]],
                k: [[0, 0, 5, 4, deep]]}),
            P([-3, 37.3, -9.2], [6, 1, 5], {f: [[0, 0, 6, 1, light]], tp: [[0, 0, 6, 1, light]], edge: false}),
            eye, mirror(eye),
            U([-2, 33, -9.7], [4, 1, 5], {f: [[0, 0, 4, 1, edge]], sd: [[0, 0, 5, 1, edge], [1, 0, 1, 1, deep], [3, 0, 1, 1, deep]]}),
            U([-1.5, 34.3, -10.6], [3, 2, 1], {f: [[0, 0, 3, 2, deep], [0, 0, 3, 1, edge]], edge: false}),
            P([-0.5, 37.6, -10], [1, 1, 6], {tp: [[0, 0, 1, 6, light]], edge: false})
        ];
        // the curve of the tail in the side view, [y, z]: down a little from the hips, then sweeping up
        const T0 = [19, 4.5], T1 = [17.6, 10], T2 = [18.2, 15.2], T3 = [20.6, 19.4], T4 = [24.2, 21.8];
        const tailSeg = (w, d, p, q, extra) => seg(-w / 2, w, d, p, q, {c: dark, n: 0.05, all: [[0, 0, w, 1, edge], [0, 2, w, 1, deep]], sd: [[0, 0, d, 1, edge]]}, extra);
        const tailPlate = (w, p, q, shift) => seg(-w / 2, w, 1, p, q, {c: plate, n: 0.045, all: [[0, 0, w, 1, light]], sd: [[0, 0, 1, 1, light]],
            f: [[0, 0, w, 1, light], [Math.floor(w / 2), 1, 1, 3, shade(plate, 0.8)]], k: [[0, 0, w, 1, light], [Math.floor(w / 2), 1, 1, 3, shade(plate, 0.8)]]}, 0, shift);
        const tailJoint = (w, at) => U([-w / 2, at[0] - 1.5, at[1] - 1.5], [w, 3, 3], {sd: hub(3), all: [[0, 0, w, 1, edge]]});
        const tail = [
            tailSeg(5, 4, T0, T1, 2),
            tailPlate(3, T0, T1, 2.2),
            tailJoint(6, T1), ...gear(0, -3, -1, T1[1], T1[0], 3), ...gear(0, -3, -1, T1[1], T1[0], 3).map(c => mirror(c)),
            tailSeg(4, 3, T1, T2, 1),
            tailPlate(2, T1, T2, 1.8)
        ];
        const tailTip = [
            tailJoint(5, T2), ...gear(0, -2.5, -1, T2[1], T2[0], 3), ...gear(0, -2.5, -1, T2[1], T2[0], 3).map(c => mirror(c)),
            tailSeg(3, 3, T2, T3, 1),
            seg(-0.5, 1, 1, T2, T3, {c: plate, n: 0.045, all: [[0, 0, 1, 1, light]], edge: false}, 0, 1.9),
            tailSeg(2, 2, T3, T4, 1),
            // the end: a flat blade across the tip, a fin on top and a red lamp
            seg(-3, 6, 1, [T3[0] + 1.2, T3[1] + 0.9], [T4[0] + 0.6, T4[1] + 0.4], {c: plate, n: 0.045, all: [[0, 0, 6, 1, light], [0, 1, 1, 1, red], [5, 1, 1, 1, red]],
                edge: false}),
            seg(-0.4, 1, 2, [T3[0] + 0.5, T3[1] + 0.2], T4, {c: plate, n: 0.045, all: [[0, 0, 2, 1, light]], edge: false}, 0, 1.6),
            seg(-0.6, 1, 1, T4, [T4[0] + 1.6, T4[1] + 0.9], {c: red, n: 0.03, edge: false}, 0)
        ];

        // ---------------------------------------------------------------- the arms (right; the left is mirrored)
        const armClaw = x => C([x, 5.5, -2.4], [1, 4, 1], {c: claw, n: 0.03, all: [[0, 0, 1, 1, steel]], edge: false}, {rot: [35, 0, 0], pivot: [x + 0.5, 9.3, -1.9]});
        const arm = [
            P([-12.5, 27, -3.5], [6, 6, 7], {f: [[0, 0, 6, 1, light], [0, 5, 6, 1, olive]], k: [[0, 0, 6, 1, light]],
                sd: [[0, 0, 7, 1, light], [1, 2, 5, 1, edge], [1, 4, 5, 1, dark]], tp: [[1, 1, 4, 5, edge]]}, {uv: ARM_UV.shoulderR}),
            U([-11.5, 13, -2], [4, 15, 4], {all: [[0, 4, 4, 1, deep], [0, 9, 4, 1, deep]]}, {uv: ARM_UV.armR}),
            U([-12, 9, -2.5], [5, 5, 5], {f: [[0, 0, 5, 1, edge], [1, 2, 1, 2, edge], [3, 2, 1, 2, edge]], all: [[0, 0, 5, 1, edge]]}, {uv: ARM_UV.fistR}),
            P([-12.8, 32.7, -3], [7, 1, 6], {tp: [[1, 1, 5, 4, edge]], all: [[0, 0, 7, 1, light]]}, {uv: ARM_UV.cap}),
            P([-12.1, 13.7, -3], [5, 5, 6], {f: [[0, 0, 5, 1, light], [2, 1, 1, 3, dark]], sd: [[0, 0, 6, 1, light], [1, 2, 4, 1, dark]], k: [[0, 0, 5, 1, light]],
                bt: [[1, 1, 3, 4, dark]]}, {uv: ARM_UV.guard}),
            O([-12.2, 20, -2.6], [1, 6, 5], {sd: [[0, 0, 5, 1, shade(olive, 1.25)], [1, 2, 3, 2, shade(olive, 0.82)]]}),
            armClaw(-11.7), armClaw(-10), armClaw(-8.3),
            ...gear(0, -12.5, -1, 0, 30, 4),
            ...gear(0, -12.1, -1, 0, 16, 3)
        ];

        // ---------------------------------------------------------------- walking on raptor legs
        const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
        const L1 = dist(HIP, KNEE), L2 = dist(KNEE, ANKLE), L3 = dist(ANKLE, BALL);
        /** The angle of a segment hanging from a to b, in radians: positive when b is in front (toward -z). */
        const swing = (a, b) => Math.atan2(a[1] - b[1], a[0] - b[0]);
        const REST = [swing(HIP, KNEE), swing(KNEE, ANKLE), swing(ANKLE, BALL)];
        /**
         * The thigh, shin and foot turns (degrees about x, as in the animation file: negative swings forward) that put the
         * ball of the foot at ball, with the hip at height hipY over the root and the foot at the angle footAngle.
         */
        const legPose = (hipY, ball, footAngle) => {
            const hip = [hipY, 0];
            const ankle = [ball[0] + L3 * Math.cos(footAngle), ball[1] + L3 * Math.sin(footAngle)];
            const reach = Math.min(dist(hip, ankle), L1 + L2 - 0.01);
            const bend = Math.acos(Math.max(-1, Math.min(1, (L1 * L1 + reach * reach - L2 * L2) / (2 * L1 * reach))));
            const thighAngle = swing(hip, ankle) + bend; // the knee bends forward
            const knee = [hip[0] - L1 * Math.cos(thighAngle), hip[1] - L1 * Math.sin(thighAngle)];
            const d = [thighAngle - REST[0], swing(knee, ankle) - REST[1], footAngle - REST[2]];
            return [-d[0] * DEG, -(d[1] - d[0]) * DEG, -(d[2] - d[1]) * DEG].map(v => Math.round(v * 100) / 100);
        };
        const legsAt = (pose, side) => ({
            [side + '_leg']: [pose[0], 0, 0], [side + '_shin']: [pose[1], 0, 0], [side + '_foot']: [pose[2], 0, 0]
        });
        const sampled = (length, samples, fn) => {
            const bones = {};
            for (let i = 0; i <= samples; i++) {
                const t = (i % samples) / samples, time = (length * i / samples).toFixed(3);
                for (const [bone, channels] of Object.entries(fn(t))) {
                    bones[bone] = bones[bone] || {};
                    for (const [channel, v] of Object.entries(channels)) {
                        bones[bone][channel] = bones[bone][channel] || {};
                        bones[bone][channel][time] = v.map(x => Math.round(x * 100) / 100);
                    }
                }
            }
            return bones;
        };
        /**
         * The raptor walk. Phase 0: the right foot lands in front. In stance the ball of the foot slides back along the ground
         * and the heel lifts at the end; in the swing the foot folds and comes forward high. The body dips after every
         * footfall, the tail swings against the hips, the head stays level and still.
         */
        const raptorWalk = c => {
            const footAt = t => {
                const p = ((t % 1) + 1) % 1;
                if (p < c.stance) {
                    const k = p / c.stance, roll = k > 0.65 ? (k - 0.65) / 0.35 : 0;
                    return [[BALL[0], -c.reach + (c.reach + c.push) * k], REST[2] - roll * roll * c.heel];
                }
                const k = (p - c.stance) / (1 - c.stance), e = (1 - Math.cos(Math.PI * k)) / 2;
                return [[BALL[0] + c.lift * Math.sin(Math.PI * k), c.push - (c.push + c.reach) * e], REST[2] - c.heel * (1 - e) - c.fold * Math.sin(Math.PI * k)];
            };
            const drop = t => c.bob * (0.5 + 0.5 * Math.cos(4 * Math.PI * (t - 0.08)));
            return {length: c.length, loop: 'loop', bones: sampled(c.length, 24, t => {
                const [rb, ra] = footAt(t), [lb, la] = footAt(t + 0.5);
                const hipY = HIP[0] - drop(t);
                const sway = Math.cos(2 * Math.PI * t), pitch = c.pitch * Math.cos(4 * Math.PI * (t - 0.08));
                const legs = Object.assign(legsAt(legPose(hipY, rb, ra), 'right'), legsAt(legPose(hipY, lb, la), 'left'));
                const out = {root: {p: [0, -drop(t), 0]}};
                for (const [bone, r] of Object.entries(legs)) out[bone] = {r};
                return Object.assign(out, {
                    torso: {r: [c.lean + pitch, c.twist * sway, c.roll * Math.sin(2 * Math.PI * t)]},
                    head: {r: [-c.lean * 0.85 - pitch * 1.2, -c.twist * sway * 0.9, -c.roll * Math.sin(2 * Math.PI * t) * 0.8]},
                    tail: {r: [c.tailLift - pitch * 1.5, -c.twist * 1.8 * sway, 0]},
                    tail_tip: {r: [2 - pitch, -c.twist * 2.2 * Math.cos(2 * Math.PI * (t - 0.12)), 0]},
                    right_arm: {r: [c.armX + c.arm * Math.cos(2 * Math.PI * (t + 0.5)), 0, c.armOut]},
                    left_arm: {r: [c.armX + c.arm * Math.cos(2 * Math.PI * t), 0, -c.armOut]},
                    right_forearm: {r: [c.elbow, 0, 0]},
                    left_forearm: {r: [c.elbow, 0, 0]}
                });
            })};
        };
        // the pose the armor holds when not walking: leaning forward, arms drawn up, the tail out behind
        const LEAN = 12, HEAD = -10, ARM = -20, ELBOW = -50, TAIL = -10;
        const pose = {torso: [LEAN, 0, 0], head: [HEAD, 0, 0], tail: [TAIL, 0, 0], arms: [ARM, 0, 5], elbow: [ELBOW, 0, 0]};
        /** A crouch: the hip down to hipY, the metatarsus laid back toward the ground. */
        const crouch = (hipY, ballZ, footDeg) => legPose(hipY, [BALL[0], ballZ], footDeg / DEG);
        const SIT = crouch(11.5, -1.5, 72);
        /**
         * The body leans in its stance; while the shell is open it straightens up (and the head with it), so that the pilot
         * standing upright inside fits the trunk; it leans again as the shell closes.
         */
        const uprightWhileOpen = (hatch, lean) => {
            for (const [bone, x] of Object.entries(lean)) {
                hatch['animation.power_armor.open'].bones[bone] = {r: {'0.000': [x, 0, 0], '0.600': [0, 0, 0], '1.200': [0, 0, 0]}};
                hatch['animation.power_armor.close'].bones[bone] = {r: {'0.000': [0, 0, 0], '0.400': [0, 0, 0], '1.000': [x, 0, 0]}};
            }
            return hatch;
        };

        SPECS.mobile_power_armor = {
            res: 256, seed: 707, wrecked: true, noCoplanarFaces: true, cutMainOnly: true,
            // the knee value places the leg upgrades on the thigh; the legs themselves are jointed by hand above
            joints: {knee: 12, elbow: 19},
            armTable: 'mobile',
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...legBones,
                {name: 'torso', parent: 'root', pivot: [0, 16, 0], cubes: torso},
                {name: 'chest_door', parent: 'torso', pivot: [0, 21, -5.8], cubes: door},
                {name: 'tail', parent: 'torso', pivot: [0, 19, 4.5], cubes: tail},
                {name: 'tail_tip', parent: 'tail', pivot: [0, T2[0], T2[1]], cubes: tailTip},
                {name: 'head', parent: 'torso', pivot: [0, 31.5, -1], cubes: head},
                {name: 'visor', parent: 'head', pivot: [0, 40.4, -5.6], cubes: muzzle},
                ...limbs('arm', 'torso', [-9, 30, 0], arm, ARM_LEFT_UV),
                {name: 'right_hand', parent: 'right_arm', pivot: [-9.5, 9, 0], cubes: []},
                {name: 'left_hand', parent: 'left_arm', pivot: [9.5, 9, 0], cubes: []}
            ],
            animations: Object.assign({
                // Standing: the body breathes, the tail sways slowly, the head looks about in short quick turns, like a bird's.
                'animation.power_armor.idle': {length: 4, loop: 'loop', bones: {
                    torso: {r: {'0.000': pose.torso, '1.000': [LEAN + 1.2, 0, 0], '2.000': pose.torso, '3.000': [LEAN + 1.2, 0, 0], '4.000': pose.torso}},
                    head: {r: {'0.000': pose.head, '0.700': pose.head, '0.800': [HEAD - 4, -24, 0], '1.700': [HEAD - 4, -24, 0], '1.800': [HEAD + 3, 10, 0],
                        '2.700': [HEAD + 3, 10, 0], '2.800': [HEAD, 0, 0], '4.000': pose.head}},
                    tail: {r: {'0.000': [TAIL, 0, 0], '1.000': [TAIL + 1, 7, 0], '2.000': [TAIL, 0, 0], '3.000': [TAIL + 1, -7, 0], '4.000': [TAIL, 0, 0]}},
                    tail_tip: {r: {'0.000': [0, -3, 0], '1.300': [1, 9, 0], '2.300': [0, 3, 0], '3.300': [1, -9, 0], '4.000': [0, -3, 0]}},
                    right_arm: {r: {'0.000': pose.arms, '2.000': [ARM - 3, 0, 6], '4.000': pose.arms}},
                    left_arm: {r: {'0.000': [ARM, 0, -5], '2.000': [ARM - 3, 0, -6], '4.000': [ARM, 0, -5]}},
                    right_forearm: {r: {'0.000': pose.elbow, '4.000': pose.elbow}},
                    left_forearm: {r: {'0.000': pose.elbow, '4.000': pose.elbow}}
                }},
                // Quick, springy steps with the body low and forward.
                'animation.power_armor.walk': raptorWalk({length: 0.62, stance: 0.56, reach: 5.5, push: 4.5, lift: 3.5, heel: 0.5, fold: 0.55,
                    bob: 0.9, lean: 16, pitch: 1.6, twist: 4, roll: 2, tailLift: -12, armX: -24, arm: 7, armOut: 5, elbow: -55}),
                // A lunging slash: the body rears back and winds up, then drives forward and down while the right claws rake
                // across; the tail whips to the other side to balance.
                'animation.power_armor.attack': flowing(0.65, {
                    torso: {r: {'0.000': pose.torso, '0.200': [2, 18, 0], '0.360': [30, -16, 0], '0.450': [28, -14, 0], '0.650': pose.torso},
                        p: {'0.000': [0, 0, 0], '0.200': [0, 0.3, 0.8], '0.360': [0, -0.8, -1.6], '0.450': [0, -0.8, -1.4], '0.650': [0, 0, 0]}},
                    head: {r: {'0.000': pose.head, '0.200': [-2, -14, 0], '0.360': [-20, 14, 0], '0.450': [-18, 12, 0], '0.650': pose.head}},
                    right_arm: {r: {'0.000': pose.arms, '0.200': [-140, 30, 12], '0.360': [-40, -40, -4], '0.450': [-36, -44, -4], '0.650': pose.arms}},
                    right_forearm: {r: {'0.000': pose.elbow, '0.200': [-40, 0, 0], '0.360': [-8, 0, 0], '0.450': [-14, 0, 0], '0.650': pose.elbow}},
                    left_arm: {r: {'0.000': [ARM, 0, -5], '0.200': [-30, 0, -12], '0.360': [-68, 10, -4], '0.650': [ARM, 0, -5]}},
                    left_forearm: {r: {'0.000': pose.elbow, '0.200': [-70, 0, 0], '0.360': [-30, 0, 0], '0.650': pose.elbow}},
                    tail: {r: {'0.000': [TAIL, 0, 0], '0.200': [TAIL - 2, -12, 0], '0.360': [TAIL + 8, 20, 0], '0.650': [TAIL, 0, 0]}},
                    tail_tip: {r: {'0.000': [0, 0, 0], '0.250': [0, -16, 0], '0.420': [5, 24, 0], '0.650': [0, 0, 0]}}
                }),
                // Power off: it sinks onto its folded legs, the body tips forward, the tail settles to the ground, the arms hang.
                'animation.power_armor.shutdown': {length: 1.5, loop: 'hold', ease: 'easeInOutSine', bones: Object.assign({
                    root: {p: {'0.000': [0, 0, 0], '1.200': [0, -(HIP[0] - 11.5), 0], '1.500': [0, -(HIP[0] - 11.5) + 0.2, 0]}},
                    torso: {r: {'0.000': pose.torso, '1.200': [30, 0, 0], '1.500': [28, 0, 0]}},
                    head: {r: {'0.000': pose.head, '1.500': [8, 0, 0]}},
                    tail: {r: {'0.000': [TAIL, 0, 0], '1.200': [-42, 0, 0], '1.500': [-40, 0, 0]}},
                    tail_tip: {r: {'0.000': [0, 0, 0], '1.500': [8, 0, 0]}},
                    right_arm: {r: {'0.000': pose.arms, '1.500': [-30, 0, 8]}},
                    left_arm: {r: {'0.000': [ARM, 0, -5], '1.500': [-30, 0, -8]}},
                    right_forearm: {r: {'0.000': pose.elbow, '1.500': [-10, 0, 0]}},
                    left_forearm: {r: {'0.000': pose.elbow, '1.500': [-10, 0, 0]}}
                }, ...['right', 'left'].map(side => Object.fromEntries(Object.entries(legsAt(SIT, side))
                    .map(([bone, r]) => [bone, {r: {'0.000': [0, 0, 0], '1.200': r, '1.500': r}}]))))},
                // Wrecked: the legs give way, it drops and rolls onto its side, head and tail limp.
                'animation.power_armor.wrecked': {length: 1, loop: 'hold', ease: 'easeOutBounce', bones: Object.assign({
                    root: {p: {'0.000': [0, 0, 0], '1.000': [0, -(HIP[0] - 11.5) - 1, 0]}},
                    torso: {r: {'0.000': pose.torso, '1.000': [26, 10, 34]}},
                    head: {r: {'0.000': pose.head, '1.000': [20, -18, -20]}},
                    tail: {r: {'0.000': [TAIL, 0, 0], '1.000': [-38, 18, -10]}},
                    tail_tip: {r: {'0.000': [0, 0, 0], '1.000': [10, 14, 0]}},
                    right_arm: {r: {'0.000': pose.arms, '1.000': [-8, 6, 30]}},
                    left_arm: {r: {'0.000': [ARM, 0, -5], '1.000': [-50, -6, -10]}},
                    right_forearm: {r: {'0.000': pose.elbow, '1.000': [-6, 0, 0]}},
                    left_forearm: {r: {'0.000': pose.elbow, '1.000': [-30, 0, 0]}}
                }, ...['right', 'left'].map((side, i) => Object.fromEntries(Object.entries(legsAt(crouch(10.5, i ? -3.5 : 1, i ? 60 : 80), side))
                    .map(([bone, r]) => [bone, {r: {'0.000': [0, 0, 0], '1.000': [r[0], 0, i ? -10 : 12]}}]))))}
            }, uprightWhileOpen(hatchAnimations({chest_door: [CHEST_OPEN, 0, 0], visor: [VISOR_OPEN, 0, 0]}), {torso: LEAN, head: HEAD}))
        };
    })();

    // Worker (1.5.2, second design): a walking excavator after the owner's concept art. The torso is a glass cab: the pilot
    // sits in it and is seen through the glass, which is clear in the texture apart from a pale frame and two light streaks.
    // Hazard-yellow plates over grey machinery, cyan work lamps; a circular saw on the right forearm (bone right_saw), a
    // clamp on the left fist. Strikes and mining are a jackhammer: the arms thrust at the target in quick short blows while
    // the saw spins. Whole sizes; no two faces in one plane (noCoplanarFaces). The cab does not lean or roll much in the
    // animations, because the pilot sits still in it.
    (function () {
        // the front window of the cab lifts up and forward
        const CAB_OPEN = -100;
        const yellow = '#d6a21e', yl = '#efc24a', yd = '#a47614', grey = '#5a5c5f', light = '#8a8d90', edge = '#6f7275', dark = '#37383b',
            deep = '#1c1d1f', amber = '#e0a020', lamp = '#9fe8ff', red = '#c4391f', frame = '#c9eef0', streak = '#e6fbfc', steel = '#a9adb0';
        const Y = (o, s, paint, opts) => C(o, s, Object.assign({c: yellow, n: 0.05}, paint), opts);
        const G = (o, s, paint, opts) => C(o, s, Object.assign({c: grey, n: 0.05}, paint), opts);
        const {gear, valve} = detailKit({edge, light, dark, deep, amber});
        const hub = s => [[0, 0, s, s, deep], [1, 0, s - 2, 1, light], [1, s - 1, s - 2, 1, edge], [0, 1, 1, s - 2, light], [s - 1, 1, 1, s - 2, edge],
            [Math.floor((s - 1) / 2), Math.floor((s - 1) / 2), s % 2 ? 1 : 2, s % 2 ? 1 : 2, amber]];
        const bolts = (w, h) => [[1, 1, 1, 1, yd], [w - 2, 1, 1, 1, yd], [1, h - 2, 1, 1, yd], [w - 2, h - 2, 1, 1, yd]];
        /** Clear glass: a pale frame line round the pane; the rest stays transparent. */
        const rim = (w, h) => [[0, 0, w, 1, frame], [0, h - 1, w, 1, frame], [0, 0, 1, h, frame], [w - 1, 0, 1, h, frame]];
        /** Two short light streaks in the upper corner, on the outer face only (on every face they read as noise), clear of the pilot's face. */
        const streaks = (w, h) => [0, 1, 2].flatMap(i => [[w - 3 - i, 1 + i, 1, 1, streak], [w - 3 - i, 4 + i, 1, 1, streak]]);
        const pane = (w, h, outer, inner) => ({c: frame, clear: true, edge: false, all: [[0, 0, 1, h, frame]],
            [outer]: outer ? [...rim(w, h), ...(outer === 'f' ? streaks(w, h) : [])] : [], [inner]: rim(w, h)});

        // ---------------------------------------------------------------- the legs (right; the left is mirrored)
        const leg = [
            G([-7.9, 2, -3], [6, 15, 6], {all: [[0, 4, 6, 1, dark], [0, 9, 6, 1, dark]]}),
            Y([-8.6, 9.8, -3.8], [7, 6, 7], {f: [[0, 0, 7, 1, yl], [1, 2, 5, 2, yd], ...bolts(7, 6)], sd: [[0, 0, 7, 1, yl], [1, 2, 5, 1, yd]], k: [[0, 0, 7, 1, yl]],
                tp: [[0, 0, 7, 1, yl]]}),
            G([-7.5, 6.2, -4.6], [5, 3, 2], {f: [[0, 0, 5, 1, light], [2, 1, 1, 1, amber]]}),
            Y([-8.6, 2.6, -3.6], [7, 4, 7], {f: [[0, 0, 7, 1, yl], ...hazard(0, 2, 7, 2, yellow, deep, 1)], sd: [[0, 0, 7, 1, yl], [2, 1, 3, 2, dark]], k: [[0, 0, 7, 1, yl]]}),
            G([-8.8, 0, -6], [8, 3, 10], {tp: [[1, 1, 6, 8, dark]], f: [[0, 0, 8, 1, light]], sd: [[0, 0, 10, 1, light], [2, 1, 1, 1, deep], [7, 1, 1, 1, deep]]}),
            G([-8.5, 0.2, -7.6], [3, 2, 2], {f: [[0, 0, 3, 1, light]], tp: [[0, 0, 3, 2, light]]}),
            G([-4.6, 0.2, -7.6], [3, 2, 2], {f: [[0, 0, 3, 1, light]], tp: [[0, 0, 3, 2, light]]}),
            G([-7, 0.3, 3.6], [4, 2, 2], {k: [[0, 0, 4, 1, light]]}),
            C([-5.5, 4, 2.8], [1, 9, 1], {c: light, n: 0.03, edge: false, k: [[0, 0, 1, 9, edge]]}),
            ...gear(0, -8.6, -1, 0, 12.8, 4)
        ];

        // ---------------------------------------------------------------- the cab
        const frontPost = Y([-8.2, 17.7, -10.2], [2, 22, 2], {f: [[0, 0, 2, 1, yl], [0, 10, 2, 1, yd]], sd: [[0, 0, 2, 1, yl]]});
        const rearPost = Y([-8.2, 17.7, 2.4], [2, 22, 2], {sd: [[0, 0, 2, 1, yl]], k: [[0, 0, 2, 1, yl]]});
        const sidePanel = Y([-8.4, 17.5, -8.4], [1, 6, 11], {sd: [[0, 0, 11, 1, yl], ...hazard(0, 4, 11, 2, yellow, deep, 2)]});
        const sideGlass = C([-7.9, 23.3, -8.3], [1, 13, 11], Object.assign(pane(11, 13, 'r', 'l'), {sd: rim(11, 13)}));
        const sideBar = Y([-8.4, 35.8, -8.4], [1, 3, 11], {sd: [[0, 0, 11, 1, yl], [0, 2, 11, 1, yd]]});
        const marker = C([-6, 18.2, -10.6], [1, 1, 1], {c: red, n: 0.03, edge: false});
        const lever = G([-5.5, 17.8, -6], [1, 5, 1], {tp: [[0, 0, 1, 1, red]], edge: false});
        const pipe = G([-5, 33, 6.6], [2, 8, 2], {tp: [[0, 0, 2, 2, deep]], all: [[0, 0, 2, 1, light], [0, 4, 2, 1, dark]]});
        const handlePost = G([-5, 40.8, -4], [1, 2, 1], {edge: false});
        const rail = G([-4, 20, 9.6], [1, 16, 1], {edge: false, k: [[0, 0, 1, 16, light]]});
        const torso = [
            // the back wall: inside a dark panel with gauges, outside yellow
            Y([-7.5, 17.8, 4.2], [15, 22, 2], {f: [[0, 0, 15, 22, dark], [2, 2, 11, 5, deep], [3, 3, 2, 2, lamp], [7, 3, 2, 2, amber], [10, 3, 2, 2, red],
                [1, 9, 13, 1, edge]], k: [[0, 0, 15, 1, yl], [0, 21, 15, 1, yd]]}),
            G([-8, 16, -10], [16, 2, 16], {tp: [[0, 0, 16, 16, dark], ...[1, 4, 7, 10, 13].map(x => [x, 1, 1, 14, edge])], f: [[0, 0, 16, 1, light]]}),
            Y([-8.5, 39, -10.5], [17, 2, 17], {tp: [[0, 0, 17, 1, yl], [1, 1, 15, 15, yd], [2, 2, 13, 13, yellow]], bt: [[0, 0, 17, 17, dark], [6, 6, 5, 5, lamp]],
                f: [[0, 0, 17, 1, yl], ...hazard(0, 1, 17, 1, yellow, deep, 2)]}),
            frontPost, mirror(frontPost), rearPost, mirror(rearPost),
            Y([-6.5, 17.5, -10.1], [13, 3, 2], {f: [[0, 0, 13, 1, yl], ...hazard(0, 1, 13, 2, yellow, deep, 2)], tp: [[0, 0, 13, 2, yd]]}),
            sidePanel, mirror(sidePanel), sideGlass, mirror(sideGlass), sideBar, mirror(sideBar),
            // under the cab, the hips; on the back the engine with a ladder, the exhausts and a valve
            G([-6, 13.5, -4], [12, 3, 8], {f: [[0, 0, 12, 1, light], [4, 1, 4, 1, deep]], all: [[0, 2, 12, 1, dark]]}),
            Y([-6, 21, 5.8], [12, 14, 4], {k: [[0, 0, 12, 1, yl], [3, 2, 6, 3, deep], [4, 3, 4, 1, lamp], ...vents(2, 8, 8, 3, yd)], sd: [[0, 0, 4, 1, yl], ...bolts(4, 14)],
                tp: [[0, 0, 12, 1, yl]]}),
            rail, mirror(rail),
            ...[22, 25, 28, 31].map(y => G([-3.5, y, 9.7], [7, 1, 1], {edge: false, all: [[0, 0, 7, 1, light]]})),
            pipe, mirror(pipe),
            ...gear(0, -6, -1, 7.8, 28, 4), ...gear(0, -6, -1, 7.8, 28, 4).map(c => mirror(c)),
            G([-1.5, 35.5, 5.8], [3, 3, 1], {k: hub(3), edge: false}),
            ...valve(2, 6.8, 1, 0, 37, 3),
            marker, mirror(marker),
            // inside: the seat and two control levers
            G([-4, 18.1, -1], [8, 1, 5], {tp: [[0, 0, 8, 5, dark], [1, 1, 6, 3, deep]]}),
            G([-3.5, 19, 3.3], [7, 9, 1], {f: [[0, 0, 7, 9, dark], [1, 1, 5, 7, deep]]}),
            lever, mirror(lever),
            // the hinges of the front window and the grab handle on the roof
            G([-6, 38.4, -10.8], [2, 1, 2], {edge: false, all: [[0, 0, 2, 1, light]]}), G([4, 38.4, -10.8], [2, 1, 2], {edge: false, all: [[0, 0, 2, 1, light]]}),
            handlePost, mirror(handlePost),
            Y([-5.5, 42.6, -4.4], [11, 1, 1], {edge: false, all: [[0, 0, 11, 1, yl]]})
        ];
        // the front window: the glass in its top bar, hinged under the front edge of the roof; it lifts up and forward to let
        // the pilot in and out (1.5.2), with two grab handles on the bar
        const window = [
            Y([-6.5, 35.9, -10], [13, 3, 2], {f: [[0, 0, 13, 1, yl], [0, 2, 13, 1, yd], [5, 1, 3, 1, lamp]], bt: [[0, 0, 13, 2, yd]]}),
            C([-7, 20.3, -9.6], [14, 16, 1], pane(14, 16, 'f', 'k')),
            G([-5.5, 36.2, -10.8], [1, 2, 1], {edge: false, all: [[0, 0, 1, 1, light]]}),
            G([4.5, 36.2, -10.8], [1, 2, 1], {edge: false, all: [[0, 0, 1, 1, light]]})
        ];
        const head = [
            G([-2, 40.8, 1.5], [4, 2, 3], {f: [[0, 0, 4, 2, deep], [1, 0, 2, 1, lamp]], tp: [[0, 0, 4, 1, light]]})
        ];

        // ---------------------------------------------------------------- the arms
        const armCommon = [
            Y([-16.8, 31, -5], [9, 9, 10], {f: [[0, 0, 9, 1, yl], [1, 6, 7, 2, yd], ...bolts(9, 9)], k: [[0, 0, 9, 1, yl], ...bolts(9, 9)],
                sd: [[0, 0, 10, 1, yl], ...hazard(0, 7, 10, 2, yellow, deep, 2)], tp: [[0, 0, 9, 10, yl], [1, 1, 7, 8, yellow]]}, {uv: ARM_UV.shoulderR}),
            G([-14, 20, -3], [6, 16, 6], {all: [[0, 3, 6, 1, dark], [0, 7, 6, 1, dark], [0, 12, 6, 1, dark]]}, {uv: ARM_UV.armR}),
            G([-15, 11.8, -4], [8, 6, 8], {f: [[0, 0, 8, 1, light], [1, 2, 2, 3, dark], [5, 2, 2, 3, dark]], all: [[0, 0, 8, 1, light]], bt: [[1, 1, 6, 6, dark]]},
                {uv: ARM_UV.fistR}),
            Y([-15.3, 17.6, -4.4], [8, 6, 9], {f: [[0, 0, 8, 1, yl], [1, 2, 6, 1, yd], [1, 4, 6, 1, yd]], sd: [[0, 0, 9, 1, yl], ...bolts(9, 6)], k: [[0, 0, 8, 1, yl]],
                tp: [[0, 0, 8, 9, yl]]}, {uv: ARM_UV.guard}),
            G([-14.5, 23.2, -3.5], [7, 3, 7], {all: [[0, 0, 7, 1, light], [0, 2, 7, 1, dark]]}, {uv: ARM_UV.cap}),
            G([-17.6, 33.5, -2], [1, 4, 4], {sd: [[0, 0, 4, 4, deep], [1, 1, 2, 2, lamp]], edge: false}),
            C([-12, 22, 2.8], [1, 8, 1], {c: light, n: 0.03, edge: false, k: [[0, 0, 1, 9, edge]]}),
            ...gear(2, -5, -1, -12.3, 35, 4)
        ];
        const leftArm = armCommon.map((c, i) => mirror(c, ARM_LEFT_UV[i]));
        // the clamp of the left fist: two jaws with teeth facing each other
        leftArm.push(
            G([8.5, 8, -3], [2, 4, 6], {f: [[0, 0, 2, 1, light]], sd: [[0, 0, 6, 1, light]]}),
            G([12.5, 8, -3], [2, 4, 6], {f: [[0, 0, 2, 1, light]], sd: [[0, 0, 6, 1, light]]}),
            G([10.3, 8.2, -2.5], [1, 1, 5], {edge: false, all: [[0, 0, 1, 1, steel]]}),
            G([11.7, 8.2, -2.5], [1, 1, 5], {edge: false, all: [[0, 0, 1, 1, steel]]})
        );
        // the circular saw on the outer side of the right forearm: four squares turned by 22.5 degrees make a toothed blade
        const SAW = [-17, 14.5, -1];
        const blade = (turn, x0) => C([x0, SAW[1] - 4, SAW[2] - 4], [1, 8, 8], {c: steel, n: 0.03, edge: false,
            sd: [[0, 0, 8, 8, '#9a9ea2'], [2, 2, 4, 4, '#6c7074'], [3, 3, 2, 2, deep], [0, 0, 1, 1, '#d6d9db'], [7, 7, 1, 1, '#d6d9db']], all: [[0, 0, 1, 1, '#d6d9db']]},
            turn ? {rot: [turn, 0, 0], pivot: [x0 + 0.5, SAW[1], SAW[2]]} : undefined);
        const saw = [
            blade(0, -17.5), blade(22.5, -17.45), blade(45, -17.55), blade(67.5, -17.4),
            G([-18.6, 13.5, -2], [4, 2, 2], {all: [[0, 0, 4, 1, light]], edge: false}),
            C([-19.4, 14, -1.5], [1, 1, 1], {c: amber, n: 0.03, edge: false})
        ];

        // ---------------------------------------------------------------- the jackhammer
        /**
         * Both arms aimed at the target, hammering in quick short blows: each blow drives the forearms forward and
         * straightens them a little, then they spring back; the cab shakes, the saw spins. raise: time to bring the arms up
         * (0 for the mining loop, which starts aimed); blows: how many in the given time.
         */
        const jackhammer = (length, raise, blows, loop) => {
            const bones = {right_arm: {r: {}, p: {}}, left_arm: {r: {}, p: {}}, right_forearm: {r: {}}, left_forearm: {r: {}}, torso: {p: {}, r: {}},
                head: {r: {}}, right_saw: {r: {}}};
            const AIM = [-62, 6, 4], ELBOW = -30;
            const key = (bone, ch, time, v) => { bones[bone][ch][time.toFixed(3)] = v.map(x => Math.round(x * 100) / 100); };
            const both = (time, aim, push, elbow, jolt) => {
                key('right_arm', 'r', time, aim);
                key('left_arm', 'r', time, [aim[0], -aim[1], -aim[2]]);
                key('right_arm', 'p', time, [0, 0, push]);
                key('left_arm', 'p', time, [0, 0, push]);
                key('right_forearm', 'r', time, [elbow, 0, 0]);
                key('left_forearm', 'r', time, [elbow, 0, 0]);
                key('torso', 'p', time, [0, jolt, 0]);
                key('torso', 'r', time, [jolt * 2, 0, 0]);
                key('head', 'r', time, [-jolt * 6, 0, 0]);
            };
            const end = loop ? length : length - 0.15;
            if (!loop) {
                both(0, [0, 0, 0], 0, 0, 0);
            }
            both(raise, AIM, 0, ELBOW, 0);
            const step = (end - raise) / blows;
            for (let i = 0; i < blows; i++) {
                const t0 = raise + i * step;
                both(t0 + step * 0.3, [AIM[0] - 3, AIM[1], AIM[2]], -2.2, ELBOW + 12, -0.35);
                both(t0 + step * 0.65, [AIM[0] + 1, AIM[1], AIM[2]], 0.4, ELBOW - 4, 0.2);
                both(t0 + step, AIM, 0, ELBOW, 0);
            }
            if (!loop) {
                both(length, [0, 0, 0], 0, 0, 0);
            }
            // the saw spins all the while, three turns a second
            const turns = Math.round(length * 3);
            key('right_saw', 'r', 0, [0, 0, 0]);
            key('right_saw', 'r', length, [-360 * Math.max(1, turns), 0, 0]);
            return {length, loop: loop ? 'loop' : 'once', bones};
        };

        SPECS.worker_power_armor = {
            res: 256, seed: 808, wrecked: true, noCoplanarFaces: true, cutMainOnly: true,
            joints: {knee: 8, elbow: 24},
            armTable: 'worker',
            // the chest upgrades sit low on the sill, below the window (the pilot must stay in sight)
            slotFrame: {hullY: 15.5, front: -10},
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...limbs('leg', 'root', [-5, 17, 0], leg),
                {name: 'torso', parent: 'root', pivot: [0, 17, 0], cubes: torso},
                {name: 'chest_door', parent: 'torso', pivot: [0, 38.9, -10], cubes: window},
                {name: 'head', parent: 'torso', pivot: [0, 41, 0], cubes: head},
                {name: 'right_arm', parent: 'torso', pivot: [-11.5, 35, 0], cubes: armCommon},
                {name: 'left_arm', parent: 'torso', pivot: [11.5, 35, 0], cubes: leftArm},
                {name: 'right_saw', parent: 'right_arm', pivot: SAW, cubes: saw, followsForearm: true},
                {name: 'right_hand', parent: 'right_arm', pivot: [-11, 14.8, 0], cubes: []},
                {name: 'left_hand', parent: 'left_arm', pivot: [11, 14.8, 0], cubes: []}
            ],
            animations: Object.assign(armorAnimations({
                idle: {length: 5, bob: -0.3, arm: -2, armZ: 1.5},
                // A slow, heavy walk with little sway: the cab with its pilot stays nearly level.
                walk: {length: 1.35, stride: 15, knee: [3, 8, 6, 32, 10], arm: 7, armLag: 0.1, armOut: 4, elbow: [6, 6], bob: 0.6, roll: 1.2, lean: 0, twist: 1.5, splay: 3},
                attack: jackhammer(0.75, 0.15, 4, false),
                // Power off: the arms drop, the cab settles a little, the roof sensor droops.
                shutdown: {length: 1.5, loop: 'hold', ease: 'easeInOutSine', bones: {
                    torso: {p: {'0.000': [0, 0, 0], '1.200': [0, -1.6, 0], '1.500': [0, -1.4, 0]}},
                    head: {r: {'0.000': [0, 0, 0], '1.500': [24, 0, 0]}},
                    right_arm: {r: {'0.000': [0, 0, 0], '1.200': [6, 0, 5], '1.500': [5, 0, 5]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '1.200': [6, 0, -5], '1.500': [5, 0, -5]}},
                    right_leg: {r: {'0.000': [0, 0, 0], '1.500': [-6, 0, 0]}},
                    left_leg: {r: {'0.000': [0, 0, 0], '1.500': [-6, 0, 0]}},
                    right_shin: {r: {'0.000': [0, 0, 0], '1.500': [10, 0, 0]}},
                    left_shin: {r: {'0.000': [0, 0, 0], '1.500': [10, 0, 0]}}
                }},
                wrecked: {length: 1, loop: 'hold', ease: 'easeOutBounce', bones: {
                    torso: {r: {'0.000': [0, 0, 0], '1.000': [5, 3, 6]}, p: {'0.000': [0, 0, 0], '1.000': [0, -3, 0]}},
                    head: {r: {'0.000': [0, 0, 0], '1.000': [30, -10, 10]}},
                    right_arm: {r: {'0.000': [0, 0, 0], '1.000': [-6, 8, 16]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '1.000': [-20, -6, -20]}},
                    right_leg: {r: {'0.000': [0, 0, 0], '1.000': [-18, 4, 4]}},
                    left_leg: {r: {'0.000': [0, 0, 0], '1.000': [-8, -4, -4]}},
                    right_shin: {r: {'0.000': [0, 0, 0], '1.000': [26, 0, 0]}},
                    left_shin: {r: {'0.000': [0, 0, 0], '1.000': [14, 0, 0]}}
                }}
            }), hatchAnimations({chest_door: [CAB_OPEN, 0, 0]}), {
                // mining: the jackhammer without the raise, again and again while the pilot breaks a block
                'animation.power_armor.mine': jackhammer(0.45, 0, 3, true)
            })
        };
        // the idle saw turns slowly
        SPECS.worker_power_armor.animations['animation.power_armor.idle'].bones.right_saw = {r: {'0.000': [0, 0, 0], '5.000': [-90, 0, 0]}};
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
                out.push(bone.name.endsWith('_hand') || bone.followsForearm
                    ? Object.assign({}, bone, {parent: bone.parent.replace('_arm', '_forearm')}) : bone);
                continue;
            }
            const [name, joint] = limb;
            // a limb whose lower bone the spec already has (the digitigrade legs of 1.5.2) is built by hand: no cut
            if (spec.bones.some(b => b.name === name)) {
                out.push(bone);
                continue;
            }
            const upper = [], lower = [];
            // with cutMainOnly (1.5.2) only the biggest cube across the joint is cut; the others go whole to the side of their
            // middle, so that no two cut faces lie in one plane and flicker when the joint bends
            const across = bone.cubes.filter(c => !c.rot && c.o[1] < joint && c.o[1] + c.s[1] > joint);
            const cutOnly = spec.cutMainOnly && across.length ? across.reduce((best, c) => (c.s[0] * c.s[1] * c.s[2] > best.s[0] * best.s[1] * best.s[2] ? c : best)) : null;
            for (const cube of bone.cubes) {
                const top = cube.o[1] + cube.s[1];
                if (cutOnly && across.includes(cube) && cube !== cutOnly) {
                    (cube.o[1] + cube.s[1] / 2 >= joint ? upper : lower).push(cube);
                    continue;
                }
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

    /**
     * Faces of two cubes that lie in the same plane and overlap: the game cannot tell which one is in front, and the texture
     * flickers there (1.5.2). Parts must sink into each other or keep apart instead of touching face to face. The upgrade
     * parts are left out (they show one at a time). Cubes turned about one axis are checked too (their faces are worked out
     * in space; a turn about x keeps the two side faces in planes along the axes); cubes turned about two or three axes are
     * left out. Overlap is judged by the faces' bounding rectangles in their plane, so it may report a near miss.
     */
    function coplanarFaces(spec) {
        const faces = [];
        const DEG = Math.PI / 180;
        for (const bone of spec.bones) {
            if (/^(slot_|upgrade_|jetpack_)/.test(bone.name)) {
                continue;
            }
            bone.cubes.forEach((c, i) => {
                const r = c.rot || [0, 0, 0];
                const axes = [0, 1, 2].filter(k => Math.abs(r[k]) > 1e-6);
                if (axes.length > 1) {
                    return;
                }
                // geo coordinates have x mirrored against Blockbench's, which turns the y and z rotations the other way
                const turn = axes.length ? axes[0] : -1;
                const angle = turn < 0 ? 0 : (turn === 0 ? r[0] : -r[turn]) * DEG;
                const pv = c.pivot || [0, 0, 0];
                const move = q => {
                    if (turn < 0) return q;
                    const [a, b] = [0, 1, 2].filter(k => k !== turn);
                    const x = q[a] - pv[a], y = q[b] - pv[b];
                    const out = q.slice();
                    // a right-handed turn about the axis; for y the pair is (x, z) and the sense flips
                    const s = turn === 1 ? -Math.sin(angle) : Math.sin(angle), co = Math.cos(angle);
                    out[a] = pv[a] + x * co - y * s;
                    out[b] = pv[b] + x * s + y * co;
                    return out;
                };
                const inf = c.inflate || 0; // a cube drawn bigger or smaller than its texture size
                const lo = c.o.map(v => v - inf), hi = [0, 1, 2].map(k => c.o[k] + c.s[k] + inf);
                for (let axis = 0; axis < 3; axis++) {
                    const [u, v] = [0, 1, 2].filter(k => k !== axis);
                    for (const side of [lo[axis], hi[axis]]) {
                        const corners = [[lo[u], lo[v]], [hi[u], lo[v]], [hi[u], hi[v]], [lo[u], hi[v]]].map(([a, b]) => {
                            const q = [0, 0, 0];
                            q[axis] = side; q[u] = a; q[v] = b;
                            return move(q);
                        });
                        const e1 = [0, 1, 2].map(k => corners[1][k] - corners[0][k]), e2 = [0, 1, 2].map(k => corners[3][k] - corners[0][k]);
                        let n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
                        const len = Math.hypot(...n);
                        if (len < 1e-9) continue;
                        n = n.map(x => x / len);
                        // the normal made to point into the positive half, so that both sides of a plane compare equal
                        const lead = [0, 1, 2].find(k => Math.abs(n[k]) > 1e-6);
                        if (n[lead] < 0) n = n.map(x => -x);
                        faces.push({name: bone.name + '#' + i, n, d: n[0] * corners[0][0] + n[1] * corners[0][1] + n[2] * corners[0][2], corners});
                    }
                }
            });
        }
        const out = [];
        const EPS = 0.001;
        for (let i = 0; i < faces.length; i++) {
            for (let j = i + 1; j < faces.length; j++) {
                const a = faces[i], b = faces[j];
                if (a.name === b.name || Math.abs(a.d - b.d) > EPS || Math.abs(a.n[0] * b.n[0] + a.n[1] * b.n[1] + a.n[2] * b.n[2]) < 1 - 1e-6) {
                    continue;
                }
                // a basis in the plane, and the bounding rectangles of both faces in it
                const ref = Math.abs(a.n[0]) < 0.9 ? [1, 0, 0] : [0, 1, 0];
                let e = [a.n[1] * ref[2] - a.n[2] * ref[1], a.n[2] * ref[0] - a.n[0] * ref[2], a.n[0] * ref[1] - a.n[1] * ref[0]];
                const el = Math.hypot(...e);
                e = e.map(x => x / el);
                const f = [a.n[1] * e[2] - a.n[2] * e[1], a.n[2] * e[0] - a.n[0] * e[2], a.n[0] * e[1] - a.n[1] * e[0]];
                const box = face => {
                    const us = face.corners.map(q => q[0] * e[0] + q[1] * e[1] + q[2] * e[2]), vs = face.corners.map(q => q[0] * f[0] + q[1] * f[1] + q[2] * f[2]);
                    return [Math.min(...us), Math.max(...us), Math.min(...vs), Math.max(...vs)];
                };
                const A = box(a), B = box(b);
                if (Math.min(A[1], B[1]) - Math.max(A[0], B[0]) > EPS && Math.min(A[3], B[3]) - Math.max(A[2], B[2]) > EPS) {
                    const axis = [0, 1, 2].find(k => Math.abs(a.n[k]) > 0.999);
                    out.push(a.name + ' / ' + b.name + ' at ' + (axis === undefined ? 'tilted plane d=' + a.d.toFixed(2) : 'xyz'[axis] + '=' + (Math.round(a.d * 1000) / 1000)));
                }
            }
        }
        return out;
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
        Project.geckolib_model_type = spec.weapon ? 'Item' : spec.block ? 'Block' : 'Entity';
        Project.box_uv = true;
        Project.visible_box = spec.res === 128 ? [3.5, 4, 2] : [2, 3, 1.5];

        if (spec.noCoplanarFaces) {
            const found = coplanarFaces(spec);
            if (found.length) {
                throw new Error(found.length + ' coplanar faces: ' + found.slice(0, 12).join('; '));
            }
        }
        const cubes = allCubes(spec);
        // the texture may have grown for the parts added later; everything else keeps its place
        spec.texRes = pack(cubes, spec.res);
        Project.texture_width = spec.texRes;
        Project.texture_height = spec.texRes;
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
        const size = spec.texRes || spec.res;
        source.width = source.height = size;
        source.getContext('2d').drawImage(data, 0, 0, size, size);
        write(ASSETS + (spec.weapon ? 'textures/item/weapon/' : spec.block ? 'textures/block/' : 'textures/entity/') + id + '.png', source);
        if (spec.wrecked) {
            write(ASSETS + 'textures/entity/' + id + '_wrecked.png', wreckedFrom(source, spec.seed + 1));
        }
        fs.mkdirSync(ROOT + 'blockbench', {recursive: true});
        fs.writeFileSync(ROOT + 'blockbench/' + id + '.bbmodel', Codecs.project.compile());
        return {saved: id};
    }

    // ------------------------------------------------------------------ upgrades

    // Bones of the upgrades, added after everything else so that the paint of the accepted models stays the same. The code
    // shows them by what is installed. All plates are light grey: the renderer draws them in the colour of their material.
    const PLATE = {c: '#dcdfe3', n: 0.03};
    const LAMP = {c: '#3a3f46', n: 0.03, f: [[0, 0, 3, 3, '#fff2b0']], edge: false};
    const TANK = {c: '#8a939c', n: 0.03, tp: [[0, 0, 5, 3, '#c9352b']], edge: false};
    const minOf = (cubes, axis) => Math.min(...cubes.map(c => c.o[axis]));
    const maxOf = (cubes, axis) => Math.max(...cubes.map(c => c.o[axis] + c.s[axis]));

    function bodyUpgradeBones(spec) {
        const bone = name => spec.bones.find(b => b.name === name);
        const torso = bone('torso'), arm = bone('right_arm'), leg = bone('right_leg'), head = bone('head');
        const faceHigh = head.cubes.filter(c => c.o[1] <= 27 && c.o[1] + c.s[1] >= 25);
        const knee = spec.joints.knee, elbow = spec.joints.elbow;
        const thigh = leg.cubes.filter(c => c.o[1] + c.s[1] > knee);
        const upperArm = arm.cubes.filter(c => c.o[1] < 21);
        const armPlate = C([minOf(upperArm, 0) - 1, elbow + 0.5, -2], [1, 3.5, 4], PLATE);
        const legPlate = C([-3.5, knee + 1, minOf(thigh, 2) - 1], [3, 4, 1], PLATE);
        const back = maxOf(torso.cubes, 2);
        const jets = {c: '#3a3f46', n: 0.03, bt: [[0, 0, 2, 2, '#ff8a24']], edge: false};
        const nozzle = C([-2.5, 12.5, back + 0.25], [2, 2, 1.5], jets);
        // The sealing module (1.3.0): a full face mask with two lenses, a valve and a yellow filter on each side.
        const face = minOf(faceHigh, 2);
        const lens = '#55c8ff';
        const mask = C([-3, 24.5, face - 1], [6, 5, 1], {c: '#2b3038', n: 0.03, edge: false,
            f: [[0, 0, 6, 1, '#4a525c'], [1, 1, 1, 1, lens], [4, 1, 1, 1, lens], [2, 3, 2, 2, '#0b0e12']]});
        const filter = C([-5, 24.5, face - 1.5], [2, 2.5, 2], {c: '#e0b33a', n: 0.04, edge: false, f: [[0, 1, 2, 1, '#6b5a1a']]});
        spec.bones.push(
            {name: 'upgrade_plating_torso', parent: 'torso', pivot: torso.pivot, cubes: [C([-3.5, 15, minOf(torso.cubes, 2) - 1], [7, 6, 1], PLATE)]},
            {name: 'upgrade_plating_right_arm', parent: 'right_arm', pivot: arm.pivot, cubes: [armPlate]},
            {name: 'upgrade_plating_left_arm', parent: 'left_arm', pivot: bone('left_arm').pivot, cubes: [mirror(armPlate)]},
            {name: 'upgrade_plating_right_leg', parent: 'right_leg', pivot: leg.pivot, cubes: [legPlate]},
            {name: 'upgrade_plating_left_leg', parent: 'left_leg', pivot: bone('left_leg').pivot, cubes: [mirror(legPlate)]},
            {name: 'upgrade_seal', parent: 'head', pivot: head.pivot, cubes: [mask, filter, mirror(filter)]},
            {name: 'upgrade_jetpack', parent: 'torso', pivot: torso.pivot, cubes: [
                C([-3, 14.5, back], [6, 7.5, 2], {c: '#59616b', n: 0.04, k: [[1, 1, 4, 1, '#ff8a24'], [1, 3, 4, 3, '#20262e']]}),
                nozzle, mirror(nozzle)]},
            {name: 'jetpack_nozzle_right', parent: 'upgrade_jetpack', pivot: [-1.5, 12.5, back + 1], cubes: []},
            {name: 'jetpack_nozzle_left', parent: 'upgrade_jetpack', pivot: [1.5, 12.5, back + 1], cubes: []}
        );
        // 1.5.0: the extra battery (a cell on the front of the belt, with a yellow band), the cooling module (finned radiators
        // on both sides of the jetpack's place on the back) and the electronics shielding (a copper mesh plate on the upper back).
        const front = minOf(torso.cubes, 2);
        const cell = C([-1.5, 12.5, front - 1], [3, 1.5, 1], {c: '#2b3038', n: 0.03, edge: false, f: [[0, 0, 3, 1, '#e0a800'], [1, 0, 1, 1, '#ffd54f']]}, {late: true});
        const fins = C([-4, 16, back], [1, 4.5, 0.5], {c: '#9aa5ad', n: 0.03, edge: false,
            k: [[0, 1, 1, 1, '#5d666d'], [0, 3, 1, 1, '#4fc3f7']]}, {late: true});
        const mesh = C([-2.5, 22.3, back], [5, 1.5, 1], {c: '#3a3f44', n: 0.03, edge: false,
            k: [[0, 0, 5, 1, '#d9894a'], [0, 0, 1, 1, '#d9894a'], [2, 0, 1, 1, '#d9894a'], [4, 0, 1, 1, '#d9894a']]}, {late: true});
        // 1.6.0, block F: the sensor module, a pod with a green lens on the right side of the head and a short antenna on it
        const headSide = minOf(head.cubes.filter(c => c.s[0] >= 4), 0);
        // small: the textures of the bodies (64 by 64) have little room left; the antenna shares the pod's texture
        const pod = C([headSide - 1, 26, -0.5], [1, 2, 1], {c: '#3a3f46', n: 0.03, edge: false,
            sd: [[0, 0, 1, 1, '#5fbf3a']], f: [[0, 0, 1, 1, '#b6f28a']]}, {late: true, latest: true});
        const rod = Object.assign(C([headSide - 1, 28, -0.5], [1, 2, 1], pod.p, {late: true, latest: true}), {share: pod});
        spec.bones.push(
            {name: 'upgrade_sensor', parent: 'head', pivot: head.pivot, cubes: [pod, rod]},
            {name: 'upgrade_battery', parent: 'torso', pivot: torso.pivot, cubes: [cell]},
            {name: 'upgrade_cooling', parent: 'torso', pivot: torso.pivot, cubes: [fins, mirror(fins)]},
            {name: 'upgrade_shielding', parent: 'torso', pivot: torso.pivot, cubes: [mesh]}
        );
    }

    function armorUpgradeBones(spec) {
        const bone = name => spec.bones.find(b => b.name === name);
        const torso = bone('torso'), head = bone('head'), arm = bone('right_arm'), leftArm = bone('left_arm'), leg = bone('right_leg'), leftLeg = bone('left_leg');
        const hull = torso.cubes.reduce((best, c) => (c.s[0] * c.s[1] > best.s[0] * best.s[1] ? c : best));
        // an armor with a chest hatch carries its chest upgrades on the hatch, so they open with it
        const door = bone('chest_door');
        // (the worker of 1.5.2 keeps them on the sill of its cab: its door is the window)
        const chest = door && !spec.slotFrame ? {parent: 'chest_door', pivot: door.pivot} : {parent: 'torso', pivot: torso.pivot};
        // a thin part on top (an antenna) does not count: the lamp stands on the helmet itself, sunk 0.2 into it
        const crown = spec.noCoplanarFaces ? head.cubes.filter(c => c.s[0] >= 2 && c.s[2] >= 2) : head.cubes;
        const front = spec.slotFrame ? spec.slotFrame.front : minOf(door ? door.cubes : torso.cubes, 2), back = maxOf(torso.cubes, 2),
            top = maxOf(crown, 1) - (spec.noCoplanarFaces ? 0.2 : 0);
        // the worker (1.5.2) is a glass cab: its upgrades sit low on the sill and the back, not on the window
        const hullY = spec.slotFrame ? spec.slotFrame.hullY : hull.o[1];
        const shoulder = arm.cubes[0], upper = arm.cubes[1];
        const elbow = spec.joints.elbow, knee = spec.joints.knee;
        const lowLeg = leg.cubes.filter(c => c.o[1] + c.s[1] > knee);
        const main = leg.cubes[0];
        const armPlate = C([upper.o[0] - 1, elbow + 1, upper.o[2]], [1, shoulder.o[1] - elbow - 2, upper.s[2]], PLATE, {uv: ARM_UV.plate});
        const armLamp = C([shoulder.o[0] + shoulder.s[0] / 2 - 1.5, shoulder.o[1] + shoulder.s[1], -1.5], [3, 2, 3], LAMP);
        const legPlate = C([main.o[0] + 1, knee + 1, minOf(lowLeg, 2) - 1], [main.s[0] - 2, 5, 1], PLATE);
        const legTank = C([main.o[0] + main.s[0] / 2 - 1.5, knee + 1, maxOf(lowLeg, 2)], [3, 4, 2], TANK);
        const part = (name, parent, pivot, cubes) => ({name, parent, pivot, cubes});
        spec.bones.push(
            part('slot_head_floodlight', 'head', head.pivot, [C([-1.5, top, -2.5], [3, 2, 3], LAMP)]),
            part('slot_chest_plating', chest.parent, chest.pivot, [C([-5, hullY + 3, front - 1], [10, 8, 1], PLATE)]),
            part('slot_chest_floodlight', chest.parent, chest.pivot, [C([-1.5, hullY + 8, front - 1.5], [3, 3, 1.5], LAMP)]),
            part('slot_back_air', 'torso', torso.pivot, [C([-2.5, hullY + 2, back], [5, 8, 3], TANK)]),
            // The mount of the banner: its pole stands on the back, and the banner rises 14 pixels above the armor.
            part('slot_back_banner', 'torso', [0, top + 14 - 32 * 2 / 3, back], []),
            part('slot_right_arm_plating', 'right_arm', arm.pivot, [armPlate]),
            part('slot_left_arm_plating', 'left_arm', leftArm.pivot, [mirror(armPlate)]),
            part('slot_right_arm_floodlight', 'right_arm', arm.pivot, [armLamp]),
            part('slot_left_arm_floodlight', 'left_arm', leftArm.pivot, [mirror(armLamp)]),
            part('slot_right_leg_plating', 'right_leg', leg.pivot, [legPlate]),
            part('slot_left_leg_plating', 'left_leg', leftLeg.pivot, [mirror(legPlate)]),
            part('slot_right_leg_air', 'right_leg', leg.pivot, [legTank]),
            part('slot_left_leg_air', 'left_leg', leftLeg.pivot, [mirror(legTank)])
        );
        // 1.5.0: the EMI screen, a dark plate with a violet grid, low on the chest or high on the back
        const screen = {c: '#2e3338', n: 0.03, edge: false,
            f: [[1, 0, 1, 2, '#a970ff'], [4, 0, 1, 2, '#a970ff'], [7, 0, 1, 2, '#a970ff']],
            k: [[1, 0, 1, 2, '#a970ff'], [4, 0, 1, 2, '#a970ff'], [7, 0, 1, 2, '#a970ff']]};
        spec.bones.push(
            part('slot_chest_emi', chest.parent, chest.pivot, [C([-4.5, hullY + 0.5, front - 1], [9, 2.5, 1], screen, {late: true})]),
            part('slot_back_emi', 'torso', torso.pivot, [C([-4.5, hullY + 10.5, back], [9, 2, 1], screen, {late: true})])
        );
        // 1.6.0, block F: the sensor unit, a box with two green lenses on the right side of the helmet and an antenna on top of it
        const helmetSide = minOf(crown, 0);
        const lenses = {c: '#343a42', n: 0.03, edge: false, f: [[0, 1, 1, 1, '#5fbf3a'], [2, 1, 1, 1, '#5fbf3a']], sd: [[1, 1, 1, 1, '#b6f28a']]};
        spec.bones.push(
            part('slot_head_sensor', 'head', head.pivot, [C([helmetSide - 1.75, top - 4, -1.5], [2, 3, 3], lenses, {late: true, latest: true}),
                C([helmetSide - 1.25, top - 1, -0.5], [1, 4, 1], {c: '#8c97a3', n: 0.02, edge: false, tp: [[0, 0, 1, 1, '#ff4a3d']]}, {late: true, latest: true})])
        );
    }

    for (const spec of Object.values(SPECS)) {
        (spec.armTable ? armorUpgradeBones : bodyUpgradeBones)(spec);
    }

    // ------------------------------------------------------------------ weapons

    // Sized for the fists of power armor. The barrel points at -Z, the grip is at the origin. See docs/MODEL_SPEC.md for the bones.
    const GUN = '#4c5359', GUN_DARK = '#25282c', GUN_STEEL = '#8b9299', GUN_OLIVE = '#5f6b3e', GUN_AMBER = '#e0a020', RUBBER = '#1b1d20';
    const FLASH = {c: '#ffe9a0', n: 0.02, edge: false, f: [[1, 1, 1, 1, '#ffffff']], k: [[1, 1, 1, 1, '#ffffff']], sd: [[1, 1, 2, 1, '#ffffff']]};

    function weaponSpec(seed, parts, animations) {
        const bones = [{name: 'gun', parent: undefined, pivot: [0, 0, 0], cubes: parts.body}];
        const child = (name, pivot, cubes) => bones.push({name, parent: 'gun', pivot, cubes: cubes || []});
        child('magazine', parts.magazine.pivot, parts.magazine.cubes);
        child('bolt', parts.bolt.pivot, parts.bolt.cubes);
        child('trigger', [0, -1, 1], [C([-0.5, -1.5, 0.5], [1, 1.5, 1], {c: GUN_STEEL, n: 0.03, edge: false})]);
        child('sight', parts.sight.pivot, parts.sight.cubes);
        child('muzzle', parts.muzzle);
        bones.push({name: 'muzzle_flash', parent: 'muzzle', pivot: parts.muzzle, cubes: [
            C([parts.muzzle[0] - 1.5, parts.muzzle[1] - 1.5, parts.muzzle[2] - 5], [3, 3, 5], FLASH),
            C([parts.muzzle[0] - 2.5, parts.muzzle[1] - 0.5, parts.muzzle[2] - 3], [5, 1, 1], FLASH),
            C([parts.muzzle[0] - 0.5, parts.muzzle[1] - 2.5, parts.muzzle[2] - 3], [1, 5, 1], FLASH)]});
        child('ejection_port', parts.ejection);
        return {res: 64, seed, weapon: true, bones, animations};
    }

    const still = (x, y, z) => [x, y, z];

    /** Reloads with a magazine: out with it, in with a new one, then the bolt. Length matches the default server time. */
    function magazineAnimations(reload, boltTravel, kick) {
        const out = -10;
        return {
            'animation.weapon.idle': {length: 4, loop: 'loop', bones: {gun: {r: steps(4, [still(0, 0, 0), still(0.5, 0, 0.3), still(0, 0, 0)])}}},
            'animation.weapon.aim': {length: 0.2, loop: 'hold', bones: {gun: {r: {'0.000': still(0, 0, 0), '0.200': still(0, 0, 0)}}}},
            'animation.weapon.shot': {length: 0.15, loop: 'once', bones: {
                gun: {p: {'0.000': still(0, 0, 0), '0.050': still(0, 0.1, kick), '0.150': still(0, 0, 0)}, r: {'0.000': still(0, 0, 0), '0.050': still(1.5, 0, 0), '0.150': still(0, 0, 0)}},
                bolt: {p: {'0.000': still(0, 0, 0), '0.050': still(0, 0, boltTravel), '0.150': still(0, 0, 0)}},
                trigger: {r: {'0.000': still(0, 0, 0), '0.050': still(-15, 0, 0), '0.150': still(0, 0, 0)}}}},
            'animation.weapon.reload': {length: reload, loop: 'hold', bones: {
                gun: {r: {'0.000': still(0, 0, 0), '0.300': still(6, 0, 18), [(reload - 0.4).toFixed(3)]: still(6, 0, 18), [reload.toFixed(3)]: still(0, 0, 0)}},
                magazine: {p: {'0.000': still(0, 0, 0), '0.300': still(0, -1, 0), '0.550': still(0, out, 0), [(reload - 1.1).toFixed(3)]: still(0, out, 0), [(reload - 0.8).toFixed(3)]: still(0, 0, 0), [reload.toFixed(3)]: still(0, 0, 0)}},
                bolt: {p: {'0.000': still(0, 0, 0), [(reload - 0.6).toFixed(3)]: still(0, 0, 0), [(reload - 0.4).toFixed(3)]: still(0, 0, boltTravel), [(reload - 0.25).toFixed(3)]: still(0, 0, 0), [reload.toFixed(3)]: still(0, 0, 0)}}}},
            'animation.weapon.empty': {length: 0.2, loop: 'once', bones: {trigger: {r: {'0.000': still(0, 0, 0), '0.060': still(-20, 0, 0), '0.200': still(0, 0, 0)}}}}
        };
    }

    SPECS.assault_rifle = weaponSpec(901, {
        body: [
            C([-1.5, 0, -6], [3, 4, 14], {c: GUN, f: [[0, 0, 3, 1, GUN_STEEL]], sd: [[1, 1, 3, 2, GUN_DARK], [7, 1, 5, 1, GUN_DARK]], tp: [[1, 1, 1, 12, GUN_DARK]]}),
            C([-1.5, 0.5, -14], [3, 3, 8], {c: GUN_OLIVE, sd: [[1, 1, 6, 1, GUN_DARK]], bt: [[1, 1, 1, 6, GUN_DARK]]}),
            C([-0.5, 1.5, -19], [1, 1, 5], {c: GUN_DARK, n: 0.04, edge: false}),
            C([-1, -4, 3], [2, 4, 2], {c: RUBBER, n: 0.06, sd: [[0, 1, 2, 1, GUN]]}),
            C([-1, 0.5, 8], [2, 3, 4], {c: GUN_DARK, n: 0.05}),
            C([-1.5, -0.5, 12], [3, 5, 1], {c: GUN_OLIVE})
        ],
        magazine: {pivot: [0, 0, -2], cubes: [C([-1, -6, -4], [2, 6, 3], {c: GUN_DARK, n: 0.05, sd: [[1, 1, 1, 4, GUN_AMBER]]})]},
        bolt: {pivot: [-2.5, 2.5, 0], cubes: [C([-2.5, 2, -1], [1, 1, 3], {c: GUN_STEEL, n: 0.03, edge: false})]},
        sight: {pivot: [0, 4, -2], cubes: [
            C([-1, 4, -4], [2, 1, 7], {c: GUN_DARK, n: 0.04, tp: [[0, 1, 2, 1, GUN_STEEL], [0, 3, 2, 1, GUN_STEEL], [0, 5, 2, 1, GUN_STEEL]]}),
            C([-0.5, 5, -3], [1, 1.5, 1], {c: GUN_AMBER, n: 0.02, edge: false})]},
        muzzle: [0, 2, -19],
        ejection: [-2, 3, 1]
    }, magazineAnimations(2.5, 2.5, 0.8));

    SPECS.battle_rifle = weaponSpec(902, {
        body: [
            C([-1.5, 0, -7], [3, 4, 16], {c: GUN_OLIVE, sd: [[1, 1, 5, 2, GUN_DARK], [9, 1, 6, 1, GUN_DARK]], tp: [[1, 1, 1, 14, GUN_DARK]]}),
            C([-1, 0.5, -16], [2, 3, 9], {c: GUN, sd: [[1, 1, 7, 1, GUN_DARK]]}),
            C([-0.5, 1.5, -26], [1, 1, 10], {c: GUN_DARK, n: 0.04, edge: false}),
            C([-1, 0.5, -27], [2, 3, 2], {c: GUN, n: 0.04, f: [[0, 1, 2, 1, RUBBER]]}),
            C([-1, -4, 3], [2, 4, 2], {c: RUBBER, n: 0.06, sd: [[0, 1, 2, 1, GUN_OLIVE]]}),
            C([-1, 0, 9], [2, 4, 5], {c: GUN_OLIVE, sd: [[1, 1, 3, 2, GUN_DARK]]}),
            C([-1.5, -1, 14], [3, 6, 1], {c: GUN_DARK, n: 0.05})
        ],
        magazine: {pivot: [0, 0, -2], cubes: [C([-1, -4, -4], [2, 4, 4], {c: GUN_DARK, n: 0.05, sd: [[1, 1, 2, 1, GUN_AMBER]]})]},
        bolt: {pivot: [-2.5, 2.5, 2], cubes: [C([-2.5, 2, 1], [1, 1, 3], {c: GUN_STEEL, n: 0.03, edge: false})]},
        sight: {pivot: [0, 4, -2], cubes: [
            C([-0.5, 4, -3], [1, 1, 1], {c: GUN_DARK, n: 0.04, edge: false}),
            C([-0.5, 4, 2], [1, 1, 1], {c: GUN_DARK, n: 0.04, edge: false}),
            C([-1, 5, -6], [2, 2, 11], {c: GUN_DARK, n: 0.04, f: [[0, 0, 2, 2, '#7fe8ff']], k: [[0, 0, 2, 2, '#3a7a8a']], sd: [[1, 0, 2, 1, GUN_AMBER]]})]},
        muzzle: [0, 2, -27],
        ejection: [-2, 3, 2]
    }, magazineAnimations(2.5, 3, 1.4));

    /** A shotgun loads shell by shell, and pumps after a shot. The reload plays once per shell. */
    SPECS.combat_shotgun = weaponSpec(903, {
        body: [
            C([-2, 0, -5], [4, 4, 12], {c: GUN_DARK, n: 0.05, sd: [[1, 1, 4, 2, GUN], [6, 1, 5, 1, GUN_AMBER]], tp: [[1, 1, 2, 10, GUN]]}),
            C([-1, 2, -20], [2, 2, 15], {c: GUN, n: 0.04, f: [[0, 0, 2, 2, RUBBER]], sd: [[1, 0, 13, 1, GUN_STEEL]]}),
            C([-1, 0, -17], [2, 2, 12], {c: GUN_DARK, n: 0.05, edge: false}),
            C([-1, -4, 3], [2, 4, 2], {c: RUBBER, n: 0.06}),
            C([-1, 0.5, 7], [2, 3, 3], {c: GUN, n: 0.05}),
            C([-1.5, -0.5, 10], [3, 5, 1], {c: RUBBER, n: 0.06})
        ],
        magazine: {pivot: [0, -0.5, -8], cubes: [C([-0.5, -1, -9], [1, 1, 2.5], {c: '#c9352b', n: 0.03, k: [[0, 0, 1, 1, GUN_AMBER]], edge: false})]},
        bolt: {pivot: [0, 0, -10], cubes: [C([-1.5, -0.5, -14], [3, 2, 6], {c: GUN_OLIVE, sd: [[1, 0, 1, 2, GUN_DARK], [3, 0, 1, 2, GUN_DARK]], bt: [[1, 1, 1, 4, GUN_DARK]]})]},
        sight: {pivot: [0, 4, -18], cubes: [C([-0.5, 4, -19], [1, 1, 1], {c: GUN_AMBER, n: 0.02, edge: false}), C([-1, 4, 3], [2, 1, 2], {c: GUN_DARK, n: 0.04, edge: false})]},
        muzzle: [0, 3, -20],
        ejection: [-2.5, 3, 1]
    }, {
        'animation.weapon.idle': {length: 4, loop: 'loop', bones: {gun: {r: steps(4, [still(0, 0, 0), still(0.5, 0, 0.3), still(0, 0, 0)])}, magazine: {s: {'0.000': still(0.01, 0.01, 0.01)}}}},
        'animation.weapon.aim': {length: 0.2, loop: 'hold', bones: {magazine: {s: {'0.000': still(0.01, 0.01, 0.01)}}}},
        'animation.weapon.shot': {length: 0.6, loop: 'once', bones: {
            gun: {p: {'0.000': still(0, 0, 0), '0.050': still(0, 0.3, 2), '0.200': still(0, 0, 0)}, r: {'0.000': still(0, 0, 0), '0.050': still(5, 0, 0), '0.250': still(0, 0, 0)}},
            bolt: {p: {'0.000': still(0, 0, 0), '0.250': still(0, 0, 0), '0.400': still(0, 0, 4), '0.550': still(0, 0, 0)}},
            trigger: {r: {'0.000': still(0, 0, 0), '0.050': still(-15, 0, 0), '0.150': still(0, 0, 0)}}}},
        'animation.weapon.reload': {length: 0.8, loop: 'loop', bones: {
            gun: {r: {'0.000': still(4, 0, -14), '0.800': still(4, 0, -14)}},
            magazine: {
                s: {'0.000': still(1, 1, 1), '0.700': still(1, 1, 1), '0.750': still(0.01, 0.01, 0.01), '0.800': still(0.01, 0.01, 0.01)},
                p: {'0.000': still(0, -7, 3), '0.450': still(0, -1, 1), '0.700': still(0, 0, -3), '0.800': still(0, 0, -3)}}}},
        'animation.weapon.empty': {length: 0.2, loop: 'once', bones: {trigger: {r: {'0.000': still(0, 0, 0), '0.060': still(-20, 0, 0), '0.200': still(0, 0, 0)}}}}
    });

    // ------------------------------------------------------------------ guard turret (1.6.0, part B3a)
    // The living version of the turret block: the base stands still, the head turns (bone "head", turned by the code to where
    // the turret looks) and the barrel tilts (bone "barrel"). Same light casing, dark frame and red lamps as the block turret,
    // painted with a coarse grain so it sits with Minecraft's own textures.
    (() => {
        const CASING = {c: '#a3a9b0', n: 0.09};
        const FRAME = {c: '#404044', n: 0.1};
        const RED = {c: '#d0352a', n: 0.04, edge: false};
        const panel = {c: '#a3a9b0', n: 0.09, sd: [[2, 2, 9, 1, '#7c8289']], f: [[1, 3, 10, 1, '#7c8289']]};
        const feet = [[-8, -8], [5, -8], [-8, 5], [5, 5]].map(([x, z]) => C([x, 0, z], [3, 3, 3], FRAME));
        // parts sink a pixel into each other instead of touching face to face, which would flicker
        const base = [...feet,
            C([-6, 1, -6], [12, 5, 12], CASING),
            C([-5, 5, -5], [10, 5, 10], CASING),
            C([-3, 9, -3], [6, 4, 6], CASING),
            C([-4, 12, -4], [8, 3, 8], FRAME),
            C([-2, 14, -2], [4, 7, 4], FRAME)];
        const head = [
            C([-3, 18, -3], [6, 2, 6], FRAME),
            C([-6, 19, -7], [12, 9, 13], panel),
            C([-2, 27, -4], [4, 3, 6], FRAME),
            C([-7, 21, 1], [2, 2, 4], RED),
            C([5, 21, 1], [2, 2, 4], RED),
            C([-3, 20, -9], [6, 6, 3], FRAME)];
        const barrel = [
            C([-2, 21, -23], [4, 4, 15], FRAME),
            // the muzzle: the bore in the middle of its front with a pixel of metal all round
            C([-3, 20, -25], [6, 6, 3], {c: '#404044', n: 0.1, f: [[1, 1, 4, 4, '#1c1c1e'], [2, 2, 2, 2, '#0a0a0b']]})];
        SPECS.guard_turret = {
            res: 128, seed: 1601, noCoplanarFaces: true,
            bones: [
                {name: 'base', parent: undefined, pivot: [0, 0, 0], cubes: base},
                {name: 'head', parent: 'base', pivot: [0, 19, 0], cubes: head},
                {name: 'barrel', parent: 'head', pivot: [0, 23, -8], cubes: barrel},
                {name: 'muzzle', parent: 'barrel', pivot: [0, 23, -25.2], cubes: []}
            ],
            animations: {
                'animation.guard_turret.idle': {length: 2, loop: 'loop', bones: {barrel: {p: {'0.000': [0, 0, 0], '2.000': [0, 0, 0]}}}},
                'animation.guard_turret.shot': {length: 0.2, loop: 'once', bones: {barrel: {p: {'0.000': [0, 0, 0], '0.050': [0, 0, 1.5], '0.200': [0, 0, 0]}}}}
            }
        };
    })();

    // ------------------------------------------------------------------ patrol robot and mech (1.6.0, part B3b)
    // Walking machines of the ruins. The robot: a gun for a right arm held forward, a heavy left fist, a head that turns and
    // tilts (bone "head", turned by the code). The mech: two heavy legs with knees, a torso that turns on the hips (bone
    // "torso", turned by the code), a twin machine gun on the right and a rocket pod on the left shoulder. Whole sizes; parts
    // sink a pixel into each other instead of touching face to face. Coarse grain, like the turret.
    (() => {
        const CASE = {c: '#8a9086', n: 0.09};
        const FRAME = {c: '#3c3e40', n: 0.1};
        const DARK = '#1c1d1f', EDGE = '#6a7068', AMBER = '#e0a030';
        const vent = (x, y, w, rows) => vents(x, y, w, rows, DARK, 2);
        const leg = [
            C([-6, 8, -2], [4, 9, 4], FRAME),
            C([-6.5, 2, -3], [5, 7, 6], Object.assign({}, CASE, {f: [[1, 1, 3, 1, EDGE], [1, 3, 3, 1, EDGE]]})),
            C([-7, 0, -5], [6, 3, 9], Object.assign({}, FRAME, {f: hazard(0, 0, 6, 3, AMBER, DARK, 2)}))];
        const body = [
            C([-5, 15, -3], [10, 4, 6], FRAME),
            C([-7, 18, -4], [14, 10, 8], Object.assign({}, CASE, {f: [[1, 1, 12, 1, EDGE], [5, 3, 4, 4, DARK], [6, 4, 2, 2, AMBER]], sd: vent(2, 2, 4, 3)})),
            C([-5, 19, 3], [10, 8, 3], Object.assign({}, FRAME, {k: vent(1, 1, 8, 3)}))];
        const head = [
            C([-2, 26, -2], [4, 3, 4], FRAME),
            C([-4, 27, -5], [8, 6, 8], Object.assign({}, CASE, {f: [[1, 2, 6, 2, DARK], [2, 2, 1, 2, AMBER], [5, 2, 1, 2, AMBER]], tp: [[1, 1, 6, 1, EDGE]]})),
            C([2, 32, 1], [1, 4, 1], Object.assign({}, FRAME, {edge: false}))];
        const gunArm = [
            C([-14, 23, -3], [8, 6, 6], Object.assign({}, CASE, {tp: hazard(0, 0, 8, 6, AMBER, DARK, 2)})),
            C([-13, 17, -2], [4, 7, 4], FRAME),
            C([-14, 14, -12], [6, 6, 13], Object.assign({}, CASE, {sd: [[2, 1, 9, 1, EDGE], [2, 3, 9, 1, DARK]]})),
            // the muzzle: the bore in the middle of its front with metal all round
            C([-13, 15, -17], [4, 4, 6], {c: '#3c3e40', n: 0.1, f: [[1, 1, 2, 2, '#0a0a0b']]})];
        const fistArm = [
            C([6, 23, -3], [8, 6, 6], Object.assign({}, CASE, {tp: hazard(0, 0, 8, 6, AMBER, DARK, 2)})),
            C([9, 15, -2], [3, 9, 4], FRAME),
            C([7, 8, -3], [6, 8, 6], Object.assign({}, CASE, {f: [[1, 1, 4, 1, EDGE]]})),
            C([7.5, 6, -2.5], [5, 3, 5], FRAME)];
        SPECS.patrol_robot = {
            res: 128, seed: 1602, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...limbs('leg', 'root', [-4, 16, 0], leg),
                {name: 'body', parent: 'root', pivot: [0, 16, 0], cubes: body},
                {name: 'head', parent: 'body', pivot: [0, 28, 0], cubes: head},
                {name: 'right_arm', parent: 'body', pivot: [-9, 26, 0], cubes: gunArm},
                {name: 'left_arm', parent: 'body', pivot: [9, 26, 0], cubes: fistArm}
            ],
            animations: {
                'animation.patrol_robot.idle': {length: 3, loop: 'loop', bones: {
                    body: {p: steps(3, [[0, 0, 0], [0, -0.3, 0], [0, 0, 0]])},
                    left_arm: {r: steps(3, [[0, 0, 0], [-3, 0, 0], [0, 0, 0]])}}},
                'animation.patrol_robot.walk': {length: 1.2, loop: 'loop', bones: {
                    right_leg: {r: steps(1.2, [[25, 0, 0], [-25, 0, 0], [25, 0, 0]])},
                    left_leg: {r: steps(1.2, [[-25, 0, 0], [25, 0, 0], [-25, 0, 0]])},
                    left_arm: {r: steps(1.2, [[18, 0, 0], [-18, 0, 0], [18, 0, 0]])},
                    right_arm: {r: steps(1.2, [[-3, 0, 0], [3, 0, 0], [-3, 0, 0]])},
                    body: {p: steps(1.2, [[0, 0, 0], [0, -0.6, 0], [0, 0, 0], [0, -0.6, 0], [0, 0, 0]])}}},
                'animation.patrol_robot.shot': {length: 0.15, loop: 'once', bones: {
                    right_arm: {p: {'0.000': [0, 0, 0], '0.050': [0, 0, 1.5], '0.150': [0, 0, 0]}}}},
                'animation.patrol_robot.melee': {length: 0.5, loop: 'once', bones: {
                    left_arm: {r: {'0.000': [0, 0, 0], '0.150': [-110, 0, 0], '0.250': [-30, 0, 0], '0.500': [0, 0, 0]}}}}
            }
        };
    })();

    (() => {
        const PLATE = {c: '#5f6957', n: 0.08};
        const FRAME = {c: '#34362f', n: 0.1};
        const DARK = '#151613', EDGE = '#495043', LIGHT = '#7b8670', AMBER = '#d89a2a', RED = '#d0352a';
        const P = (o, s, more) => C(o, s, Object.assign({}, PLATE, more));
        const F = (o, s, more) => C(o, s, Object.assign({}, FRAME, more));
        const thigh = [
            F([-15, 24, -4], [7, 6, 8]),
            P([-14, 15, -3], [5, 11, 6], {f: [[1, 1, 4, 1, LIGHT], [1, 4, 4, 1, EDGE]], sd: [[1, 2, 5, 1, EDGE]]})];
        const shin = [
            F([-15, 13, -5], [7, 4, 9]),
            P([-14, 4, -3], [5, 10, 6], {f: [[1, 1, 4, 1, LIGHT], [2, 3, 2, 5, EDGE]]}),
            F([-16, 0, -9], [10, 5, 15], {f: hazard(0, 0, 10, 5, AMBER, DARK, 2)})];
        const hips = [F([-9, 27, -6], [18, 6, 12])];
        const torso = [
            P([-12, 31, -9], [24, 15, 17], {f: [[1, 1, 22, 1, LIGHT], [2, 9, 20, 1, EDGE], [3, 11, 6, 2, AMBER], [15, 11, 6, 2, AMBER]],
                sd: [[2, 2, 13, 1, EDGE], ...vents(3, 5, 10, 4, DARK, 2)], k: vents(4, 3, 16, 5, DARK, 2)}),
            F([-6, 38, -11], [12, 6, 3], {f: [[1, 2, 10, 2, DARK], [2, 2, 3, 2, RED], [7, 2, 3, 2, RED]]}),
            P([-10, 45, -7], [20, 3, 13], {tp: [[1, 1, 18, 1, LIGHT], [1, 11, 18, 1, EDGE]]}),
            F([6, 47, 1], [1, 7, 1], {edge: false}),
            F([-7, 33, 7], [14, 11, 4], {k: vents(1, 1, 12, 5, DARK, 2)})];
        const gun = [
            F([-18, 36, -5], [7, 9, 10]),
            P([-19, 32, -16], [9, 7, 20], {sd: [[2, 1, 16, 1, LIGHT], [2, 4, 16, 1, EDGE]], tp: [[1, 2, 7, 1, EDGE]]}),
            F([-18, 34, -21], [3, 3, 6], {f: [[1, 1, 1, 1, '#050505']], edge: false}),
            F([-14, 34, -21], [3, 3, 6], {f: [[1, 1, 1, 1, '#050505']], edge: false}),
            F([-19, 33, -29], [9, 5, 9], {f: [[1, 1, 2, 2, '#050505'], [5, 1, 2, 2, '#050505']]})];
        const pod = [
            F([11, 39, -4], [5, 6, 8]),
            P([13, 44, -8], [11, 9, 14], {f: [[1, 1, 4, 3, DARK], [6, 1, 4, 3, DARK], [1, 5, 4, 3, DARK], [6, 5, 4, 3, DARK],
                [2, 2, 2, 1, RED], [7, 2, 2, 1, RED], [2, 6, 2, 1, RED], [7, 6, 2, 1, RED]], sd: hazard(1, 6, 12, 2, AMBER, DARK, 2)})];
        const legBones = side => {
            const s = side === 'right' ? 1 : -1;
            const flip = c => (s > 0 ? c : mirror(c));
            return [
                {name: side + '_leg', parent: 'hips', pivot: [-11.5 * s, 29, 0], cubes: thigh.map(flip)},
                {name: side + '_shin', parent: side + '_leg', pivot: [-11.5 * s, 15, 0], cubes: shin.map(flip)}];
        };
        SPECS.mech = {
            res: 256, seed: 1603, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'hips', parent: 'root', pivot: [0, 30, 0], cubes: hips},
                ...legBones('right'), ...legBones('left'),
                {name: 'torso', parent: 'hips', pivot: [0, 32, 0], cubes: torso},
                {name: 'right_gun', parent: 'torso', pivot: [-14, 40, 0], cubes: gun},
                {name: 'left_pod', parent: 'torso', pivot: [14, 44, 0], cubes: pod}
            ],
            animations: {
                'animation.mech.idle': {length: 3, loop: 'loop', bones: {
                    hips: {p: steps(3, [[0, 0, 0], [0, -0.4, 0], [0, 0, 0]])},
                    right_gun: {r: steps(3, [[0, 0, 0], [-2, 0, 0], [0, 0, 0]])}}},
                'animation.mech.walk': {length: 2, loop: 'loop', bones: {
                    right_leg: {r: steps(2, [[20, 0, 0], [-20, 0, 0], [20, 0, 0]])},
                    left_leg: {r: steps(2, [[-20, 0, 0], [20, 0, 0], [-20, 0, 0]])},
                    right_shin: {r: steps(2, [[0, 0, 0], [0, 0, 0], [25, 0, 0], [0, 0, 0], [0, 0, 0]])},
                    left_shin: {r: steps(2, [[25, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0], [25, 0, 0]])},
                    hips: {p: steps(2, [[0, 0, 0], [0, -1, 0], [0, 0, 0], [0, -1, 0], [0, 0, 0]]), r: steps(2, [[0, 0, 2], [0, 0, -2], [0, 0, 2]])}}},
                'animation.mech.shot': {length: 0.12, loop: 'once', bones: {
                    right_gun: {p: {'0.000': [0, 0, 0], '0.040': [0, 0, 2], '0.120': [0, 0, 0]}}}},
                'animation.mech.rockets': {length: 0.6, loop: 'once', bones: {
                    left_pod: {r: {'0.000': [0, 0, 0], '0.100': [-12, 0, 0], '0.600': [0, 0, 0]}, p: {'0.000': [0, 0, 0], '0.100': [0, -1, 1.5], '0.600': [0, 0, 0]}}}},
                'animation.mech.stomp': {length: 1, loop: 'once', bones: {
                    right_leg: {r: {'0.000': [0, 0, 0], '0.500': [-45, 0, 0], '0.600': [8, 0, 0], '1.000': [0, 0, 0]}},
                    right_shin: {r: {'0.000': [0, 0, 0], '0.500': [50, 0, 0], '0.600': [0, 0, 0], '1.000': [0, 0, 0]}},
                    hips: {p: {'0.000': [0, 0, 0], '0.500': [0, 1, 0], '0.600': [0, -2, 0], '1.000': [0, 0, 0]}}}}
            }
        };
        SPECS.mech_rocket = {
            res: 32, seed: 1604, noCoplanarFaces: true,
            bones: [{name: 'rocket', parent: undefined, pivot: [0, 2.5, 0], cubes: [
                C([-1, 1.5, -4], [2, 2, 8], {c: '#c8ccc4', n: 0.06, sd: [[2, 0, 1, 2, RED]], tp: [[2, 0, 1, 2, RED]]}),
                C([-0.5, 2, -5], [1, 1, 2], {c: RED, n: 0.05, edge: false}),
                C([-2, 2, 1.5], [4, 1, 2], {c: '#34362f', n: 0.08, edge: false}),
                C([-0.5, 0.5, 1], [1, 4, 2], {c: '#34362f', n: 0.08, edge: false})]}],
            animations: {'animation.mech_rocket.idle': {length: 1, loop: 'loop', bones: {rocket: {r: {'0.000': [0, 0, 0], '1.000': [0, 0, 360]}}}}}
        };
    })();

    // ------------------------------------------------------------------ mutants (1.6.0, part B3c)
    // Creatures of the contaminated lands: the mutated hound (low, bony, raw flesh), the glowing ghoul (thin, hunched, long arms,
    // green sores that glow: the glow mask keeps only the GLOW colour of its texture) and the brute (short legs, huge arms and
    // shoulders, a low head). The head bone turns to where they look (code). Whole sizes where they can be; parts sink into each
    // other instead of touching face to face.
    (() => {
        const FLESH = '#8a5a4a', RAW = '#b5655a', BONE = '#d8cfb5', DARK = '#3a2a24', SORE = '#9bc24a';
        const flesh = more => Object.assign({c: FLESH, n: 0.12}, more);
        const ribs = [[1, 1, 1, 4, BONE], [3, 1, 1, 4, BONE], [5, 1, 1, 4, BONE], [7, 1, 1, 4, BONE], [2, 4, 3, 1, RAW]];
        const leg = (x, z) => C([x, 0, z], [2, 7, 2], flesh({f: [[0, 5, 2, 2, DARK]], edge: false}));
        SPECS.mutated_hound = {
            res: 64, seed: 1631, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'body', parent: 'root', pivot: [0, 9, 0], cubes: [
                    C([-4, 6, -6], [8, 6, 11], flesh({sd: ribs, tp: [[1, 2, 2, 2, RAW], [5, 6, 2, 1, SORE]], bt: [[1, 1, 6, 9, DARK]]})),
                    C([-1, 11, -4], [2, 2, 8], {c: BONE, n: 0.08, edge: false, tp: [[0, 1, 2, 1, DARK], [0, 4, 2, 1, DARK]]})]},
                {name: 'head', parent: 'body', pivot: [0, 10, -6], cubes: [
                    C([-3, 7, -11], [6, 6, 6], flesh({f: [[1, 1, 1, 1, SORE], [4, 1, 1, 1, SORE], [0, 3, 6, 1, DARK]], tp: [[2, 1, 2, 3, RAW]]})),
                    C([-2, 6, -15], [4, 4, 5], flesh({f: [[0, 2, 4, 1, BONE], [1, 3, 2, 1, DARK]], sd: [[0, 2, 5, 1, BONE]], c: RAW})),
                    C([-2.5, 12, -8], [1, 2, 1], {c: BONE, n: 0.05, edge: false}),
                    C([1.5, 12, -8], [1, 2, 1], {c: BONE, n: 0.05, edge: false})]},
                {name: 'front_right_leg', parent: 'body', pivot: [-2, 7, -4], cubes: [leg(-3, -5)]},
                {name: 'front_left_leg', parent: 'body', pivot: [2, 7, -4], cubes: [leg(1, -5)]},
                {name: 'back_right_leg', parent: 'body', pivot: [-2, 7, 3], cubes: [leg(-3, 2)]},
                {name: 'back_left_leg', parent: 'body', pivot: [2, 7, 3], cubes: [leg(1, 2)]},
                {name: 'tail', parent: 'body', pivot: [0, 10, 4], cubes: [C([-0.5, 9.5, 4], [1, 1, 5], {c: RAW, n: 0.1, edge: false}, {rot: [-25, 0, 0], pivot: [0, 10, 4]})]}
            ],
            animations: {
                'animation.mutated_hound.idle': {length: 2, loop: 'loop', bones: {
                    body: {p: steps(2, [[0, 0, 0], [0, -0.3, 0], [0, 0, 0]])},
                    tail: {r: steps(2, [[0, -10, 0], [0, 10, 0], [0, -10, 0]])}}},
                'animation.mutated_hound.walk': {length: 0.5, loop: 'loop', bones: {
                    front_right_leg: {r: steps(0.5, [[35, 0, 0], [-35, 0, 0], [35, 0, 0]])},
                    back_left_leg: {r: steps(0.5, [[35, 0, 0], [-35, 0, 0], [35, 0, 0]])},
                    front_left_leg: {r: steps(0.5, [[-35, 0, 0], [35, 0, 0], [-35, 0, 0]])},
                    back_right_leg: {r: steps(0.5, [[-35, 0, 0], [35, 0, 0], [-35, 0, 0]])},
                    body: {p: steps(0.5, [[0, 0, 0], [0, 0.5, 0], [0, 0, 0], [0, 0.5, 0], [0, 0, 0]])},
                    tail: {r: steps(0.5, [[0, -15, 0], [0, 15, 0], [0, -15, 0]])}}},
                'animation.mutated_hound.attack': {length: 0.3, loop: 'once', bones: {
                    body: {r: {'0.000': [0, 0, 0], '0.100': [-10, 0, 0], '0.300': [0, 0, 0]}, p: {'0.000': [0, 0, 0], '0.100': [0, 1, -2], '0.300': [0, 0, 0]}}}}
            }
        };
    })();

    (() => {
        const SKIN = '#7a8a6a', DARK = '#3d4636', RAG = '#5a4a3a', GLOW = '#7cff4a', PALE = '#9aa88a';
        const skin = more => Object.assign({c: SKIN, n: 0.1}, more);
        SPECS.glowing_ghoul = {
            res: 64, seed: 1632, noCoplanarFaces: true, glow: GLOW,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...limbs('leg', 'root', [-2, 13, 0], [C([-3, 0, -1.5], [2.5, 13, 3], skin({f: [[0, 0, 3, 5, RAG], [1, 8, 1, 1, GLOW]], k: [[0, 0, 3, 5, RAG]], edge: false}))]),
                {name: 'body', parent: 'root', pivot: [0, 12, 0], cubes: [
                    C([-4, 12, -2], [8, 11, 4], skin({f: [[0, 0, 8, 3, RAG], [2, 5, 2, 2, GLOW], [5, 7, 1, 1, GLOW], [1, 4, 6, 1, DARK]],
                        k: [[3, 2, 2, 3, GLOW], [1, 7, 1, 1, GLOW], [0, 0, 8, 2, RAG]], sd: [[1, 6, 2, 1, GLOW]]}))]},
                {name: 'head', parent: 'body', pivot: [0, 22, -1], cubes: [
                    C([-3.5, 22, -4.5], [7, 7, 7], skin({f: [[1, 2, 2, 1, GLOW], [4, 2, 2, 1, GLOW], [2, 4, 3, 1, DARK], [1, 5, 5, 1, PALE]], tp: [[2, 2, 2, 2, GLOW]], sd: [[3, 3, 1, 1, GLOW]]}))]},
                ...limbs('arm', 'body', [-5, 21, 0], [C([-6.5, 8, -1.5], [3, 14, 3], skin({f: [[1, 4, 1, 2, GLOW], [0, 12, 3, 2, DARK]], sd: [[1, 9, 1, 1, GLOW]], edge: false}))])
            ],
            animations: {
                'animation.glowing_ghoul.idle': {length: 3, loop: 'loop', bones: {
                    body: {r: steps(3, [[12, 0, 0], [16, 0, 2], [12, 0, 0]])},
                    right_arm: {r: steps(3, [[-12, 0, 0], [-6, 0, 0], [-12, 0, 0]])},
                    left_arm: {r: steps(3, [[-8, 0, 0], [-14, 0, 0], [-8, 0, 0]])}}},
                'animation.glowing_ghoul.walk': {length: 1.4, loop: 'loop', bones: {
                    body: {r: steps(1.4, [[15, 0, -3], [15, 0, 3], [15, 0, -3]])},
                    right_leg: {r: steps(1.4, [[25, 0, 0], [-25, 0, 0], [25, 0, 0]])},
                    left_leg: {r: steps(1.4, [[-25, 0, 0], [25, 0, 0], [-25, 0, 0]])},
                    right_arm: {r: steps(1.4, [[-70, 0, 0], [-55, 0, 0], [-70, 0, 0]])},
                    left_arm: {r: steps(1.4, [[-55, 0, 0], [-70, 0, 0], [-55, 0, 0]])}}},
                'animation.glowing_ghoul.attack': {length: 0.5, loop: 'once', bones: {
                    right_arm: {r: {'0.000': [-60, 0, 0], '0.150': [-120, 0, 0], '0.300': [-30, 0, 0], '0.500': [-60, 0, 0]}},
                    left_arm: {r: {'0.000': [-60, 0, 0], '0.150': [-120, 0, 0], '0.300': [-30, 0, 0], '0.500': [-60, 0, 0]}}}}
            }
        };
    })();

    (() => {
        const SKIN = '#8a6a5a', SCAR = '#b07a6a', DARK = '#4a3028', BONE = '#d8cfb5', SORE = '#9bc24a';
        const skin = more => Object.assign({c: SKIN, n: 0.11}, more);
        SPECS.brute = {
            res: 128, seed: 1633, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                ...limbs('leg', 'root', [-3.5, 13, 0], [C([-6.5, 0, -3], [6, 13, 6], skin({f: [[0, 10, 6, 3, DARK], [2, 3, 2, 1, SCAR]], k: [[0, 10, 6, 3, DARK]]}))]),
                {name: 'body', parent: 'root', pivot: [0, 13, 0], cubes: [
                    C([-7, 12, -4], [14, 10, 9], skin({f: [[3, 2, 8, 1, SCAR], [5, 5, 3, 2, SORE]], k: [[0, 0, 14, 3, DARK]]})),
                    C([-9, 21, -5], [18, 12, 11], skin({f: [[2, 2, 5, 1, SCAR], [11, 3, 4, 1, SCAR], [8, 6, 2, 3, SORE]], k: [[3, 4, 3, 2, SORE]],
                        tp: [[2, 2, 3, 2, BONE], [8, 4, 2, 2, BONE], [13, 2, 3, 2, BONE]]})),
                    C([-2, 32, 0], [4, 3, 3], {c: BONE, n: 0.06, edge: false}),
                    C([-6, 32, 1], [3, 2, 2], {c: BONE, n: 0.06, edge: false}),
                    C([3, 32, 1], [3, 2, 2], {c: BONE, n: 0.06, edge: false})]},
                {name: 'head', parent: 'body', pivot: [0, 30, -5], cubes: [
                    C([-3.5, 27, -10], [7, 7, 6], skin({f: [[1, 2, 2, 1, '#e8d050'], [4, 2, 2, 1, '#e8d050'], [1, 4, 5, 1, DARK], [1, 5, 1, 1, BONE], [5, 5, 1, 1, BONE]], tp: [[2, 1, 3, 3, SCAR]]}))]},
                ...limbs('arm', 'body', [-10, 30, 0], [
                    C([-15.5, 18, -3.5], [7, 14, 7], skin({f: [[2, 4, 3, 1, SCAR]], sd: [[2, 6, 2, 2, SORE]]})),
                    C([-16, 4, -4.5], [8, 15, 9], skin({f: [[0, 12, 8, 3, DARK], [2, 3, 3, 2, SCAR]], bt: [[1, 1, 6, 7, DARK]]}))])
            ],
            animations: {
                'animation.brute.idle': {length: 3, loop: 'loop', bones: {
                    body: {r: steps(3, [[8, 0, 0], [11, 0, 0], [8, 0, 0]])},
                    right_arm: {r: steps(3, [[-8, 0, 0], [-4, 0, 0], [-8, 0, 0]])},
                    left_arm: {r: steps(3, [[-8, 0, 0], [-4, 0, 0], [-8, 0, 0]])}}},
                'animation.brute.walk': {length: 1.6, loop: 'loop', bones: {
                    body: {r: steps(1.6, [[10, 0, -4], [10, 0, 4], [10, 0, -4]]), p: steps(1.6, [[0, 0, 0], [0, -1, 0], [0, 0, 0], [0, -1, 0], [0, 0, 0]])},
                    right_leg: {r: steps(1.6, [[20, 0, 0], [-20, 0, 0], [20, 0, 0]])},
                    left_leg: {r: steps(1.6, [[-20, 0, 0], [20, 0, 0], [-20, 0, 0]])},
                    right_arm: {r: steps(1.6, [[-20, 0, 0], [10, 0, 0], [-20, 0, 0]])},
                    left_arm: {r: steps(1.6, [[10, 0, 0], [-20, 0, 0], [10, 0, 0]])}}},
                'animation.brute.attack': {length: 0.6, loop: 'once', bones: {
                    right_arm: {r: {'0.000': [0, 0, 0], '0.250': [-150, 0, 0], '0.350': [-20, 0, 0], '0.600': [0, 0, 0]}},
                    left_arm: {r: {'0.000': [0, 0, 0], '0.250': [-150, 0, 0], '0.350': [-20, 0, 0], '0.600': [0, 0, 0]}},
                    body: {r: {'0.000': [8, 0, 0], '0.250': [-5, 0, 0], '0.350': [20, 0, 0], '0.600': [8, 0, 0]}}}}
            }
        };
    })();

    /** Thigh and shin angles that put the ankle at (forward, below) from the hip, knee bending forward. */
    const solve = (thigh, shin, forward, below) => {
        const reach = Math.min(Math.hypot(forward, below), thigh + shin - 0.02);
        const aim = Math.atan2(forward, below);
        const hip = Math.acos((thigh * thigh + reach * reach - shin * shin) / (2 * thigh * reach));
        const knee = Math.PI - Math.acos((thigh * thigh + shin * shin - reach * reach) / (2 * thigh * shin));
        return [-(aim + hip) * DEG, knee * DEG];
    };
    /**
     * A two-legged walk by inverse kinematics. c: length, thigh, shin (pixels), rest (hip height over the ankle when standing),
     * hip (hip height while walking), stride, lift, stance (part of the cycle a foot is down), bob, sway (hips roll toward the
     * standing leg), lean, twist (shoulders against hips), feet (the sole stays level), arms: {bone: [swing, base, side, lag]},
     * forearms: {bone: [bend, extra when forward]}, hips/torso: bone names.
     */
    function ikGait(c) {
        const S = c.stride, st = c.stance || 0.55;
        const foot = f => {
            f = ((f % 1) + 1) % 1;
            if (f < st) {
                return [S * (1 - 2 * f / st), 0];
            }
            const u = (f - st) / (1 - st);
            return [-S + 2 * S * u * u * (3 - 2 * u), c.lift * Math.sin(Math.PI * Math.pow(u, 0.75))];
        };
        const hip = t => c.hip - c.bob * (1 + Math.cos(4 * Math.PI * (t - 0.08))) / 2;
        const leg = (t, side) => {
            const [x, y] = foot(t + (side > 0 ? 0 : 0.5));
            const [a, k] = solve(c.thigh, c.shin, x, hip(t) - y);
            const lifted = y > 0.2;
            return [a, k, -(a + k) + (lifted ? (c.toe || 12) * Math.sin(Math.PI * Math.min(1, y / c.lift)) : 0)];
        };
        const bones = {};
        bones[c.hips || 'hips'] = {
            p: looped(c.length, STEP_TIME, t => [0, hip(t) - c.rest, 0]),
            r: looped(c.length, STEP_TIME, t => [0, (c.pelvis || 0) * Math.cos(2 * Math.PI * t), (c.sway || 0) * Math.sin(2 * Math.PI * t)])
        };
        bones.right_leg = {r: looped(c.length, STEP_TIME, t => [leg(t, 1)[0], 0, c.splay || 0])};
        bones.left_leg = {r: looped(c.length, STEP_TIME, t => [leg(t, -1)[0], 0, -(c.splay || 0)])};
        bones.right_shin = {r: looped(c.length, STEP_TIME, t => [leg(t, 1)[1], 0, 0])};
        bones.left_shin = {r: looped(c.length, STEP_TIME, t => [leg(t, -1)[1], 0, 0])};
        if (c.feet) {
            bones.right_foot = {r: looped(c.length, STEP_TIME, t => [leg(t, 1)[2], 0, 0])};
            bones.left_foot = {r: looped(c.length, STEP_TIME, t => [leg(t, -1)[2], 0, 0])};
        }
        bones[c.torso || 'torso'] = {r: looped(c.length, STEP_TIME, t => [c.lean || 0, -((c.twist || 0) + (c.pelvis || 0)) * Math.cos(2 * Math.PI * t),
            -(c.sway || 0) * 0.6 * Math.sin(2 * Math.PI * t)])};
        for (const [bone, [swing, base, side, lag]] of Object.entries(c.arms || {})) {
            bones[bone] = {r: looped(c.length, STEP_TIME, t => [base + swing * Math.cos(2 * Math.PI * (t + (side > 0 ? 0 : 0.5) - (lag || 0.05))), 0, (c.armOut || 0) * side])};
        }
        for (const [bone, [bend, extra, side]] of Object.entries(c.forearms || {})) {
            bones[bone] = {r: looped(c.length, STEP_TIME, t => [-(bend + extra * (0.5 - 0.5 * Math.cos(2 * Math.PI * (t + (side > 0 ? 0 : 0.5) - 0.1)))), 0, 0])};
        }
        return {length: c.length, loop: 'loop', bones};
    }

    const GATLING_FOLDED = 105; // the mech's gatling folded down behind its back (part B3e)
    // ------------------------------------------------------------------ hostile machines and mutants, second version (1.6.0, part B3d)
    // Owner's notes: the walks and blows looked wooden; the mutants did not convince. The machines now have knees, ankles and
    // elbows, and walk by inverse kinematics (the foot follows a path, the leg is solved from it, the sole stays level on the
    // ground); the blows have a wind-up, a strike and a recovery, sampled smoothly. The mech sheds its armor plate and rocket pod
    // in a second phase. The mutants follow the look of the infection mods the owner named: a recognisable body overgrown with
    // raw flesh, exposed bone and teeth, lumps and tendrils, never symmetric; ours also glow with radiation.
    (() => {
        const R2 = v => Math.round(v * 100) / 100;
        const loopOf = (length, fn) => looped(length, STEP_TIME, fn);
        /** A one-off movement through key poses, sampled smoothly (see flowing); hold keeps the last pose. */
        const move = (length, bones, hold) => Object.assign(flowing(length, bones), hold ? {loop: 'hold'} : {});
        const K = (...pairs) => Object.fromEntries(pairs.map(([t, v]) => [t.toFixed(3), v]));

        // ---------------------------------------------------------------- the patrol robot
        const CASE = {c: '#8a9086', n: 0.09};
        const FRAME = {c: '#3c3e40', n: 0.1};
        const DARK = '#1c1d1f', EDGE = '#6a7068', AMBER = '#e0a030';
        const cs = more => Object.assign({}, CASE, more), fr = more => Object.assign({}, FRAME, more);
        const thighR = [C([-5.5, 8.5, -2], [4, 7.5, 4], fr({sd: [[1, 2, 2, 4, '#2a2c2e']]}))];
        const shinR = [C([-6, 7, -3], [5, 3, 4], cs({f: [[1, 1, 3, 1, EDGE]]})), C([-5.5, 1.5, -2.5], [4, 6.5, 5], cs({f: [[1, 1, 2, 4, DARK]], sd: [[1, 1, 3, 1, EDGE]]}))];
        const footR = [C([-6.5, 0, -5], [6, 3, 8], fr({f: hazard(0, 0, 6, 3, AMBER, DARK, 2)}))];
        SPECS.patrol_robot = {
            res: 128, seed: 1612, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'hips', parent: 'root', pivot: [0, 15, 0], cubes: [C([-5, 14, -3], [10, 4, 6], fr())]},
                {name: 'right_leg', parent: 'hips', pivot: [-3.5, 15, 0], cubes: thighR},
                {name: 'right_shin', parent: 'right_leg', pivot: [-3.5, 8.5, 0], cubes: shinR},
                {name: 'right_foot', parent: 'right_shin', pivot: [-3.5, 1.5, 0], cubes: footR},
                {name: 'left_leg', parent: 'hips', pivot: [3.5, 15, 0], cubes: thighR.map(c => mirror(c))},
                {name: 'left_shin', parent: 'left_leg', pivot: [3.5, 8.5, 0], cubes: shinR.map(c => mirror(c))},
                {name: 'left_foot', parent: 'left_shin', pivot: [3.5, 1.5, 0], cubes: footR.map(c => mirror(c))},
                {name: 'torso', parent: 'hips', pivot: [0, 17, 0], cubes: [
                    C([-4, 17, -2.5], [8, 3, 5], fr()),
                    C([-7, 19, -4], [14, 8, 8], cs({f: [[1, 1, 12, 1, EDGE], [5, 2, 4, 4, DARK], [6, 3, 2, 2, AMBER]], sd: vents(2, 2, 4, 3, DARK, 2)})),
                    C([-5, 20, 3], [10, 6, 3], fr({k: vents(1, 1, 8, 3, DARK, 2)}))]},
                {name: 'head', parent: 'torso', pivot: [0, 28, -1], cubes: [
                    C([-2, 26, -2], [4, 3, 4], fr()),
                    C([-4, 28, -5], [8, 6, 8], cs({f: [[1, 2, 6, 2, DARK], [2, 2, 1, 2, AMBER], [5, 2, 1, 2, AMBER]], tp: [[1, 1, 6, 1, EDGE]]})),
                    C([2, 33, 1], [1, 4, 1], fr({edge: false}))]},
                {name: 'right_arm', parent: 'torso', pivot: [-8.5, 26, 0], cubes: [
                    C([-14, 23, -3.5], [8, 6, 7], cs({tp: hazard(0, 0, 8, 7, AMBER, DARK, 2)})),
                    C([-12, 17, -2], [4, 7, 4], fr())]},
                // aiming (part B3j): the code turns this joint at the elbow toward the target; the animations never touch it
                {name: 'gun_mount', parent: 'right_arm', pivot: [-10, 18, 0], cubes: []},
                {name: 'right_forearm', parent: 'gun_mount', pivot: [-10, 18, 0], cubes: [
                    C([-13.5, 14, -11], [6, 6, 12], cs({sd: [[2, 1, 9, 1, EDGE], [2, 3, 9, 1, DARK]]})),
                    C([-12, 15, -16], [4, 4, 6], {c: '#3c3e40', n: 0.1, f: [[1, 1, 2, 2, '#0a0a0b']]}),
                    C([-11.5, 11, -6], [3, 4, 3], fr({f: [[1, 1, 1, 2, AMBER]]}))]},
                {name: 'muzzle', parent: 'right_forearm', pivot: [-11, 17, -16.2], cubes: []},
                {name: 'left_arm', parent: 'torso', pivot: [8.5, 26, 0], cubes: [
                    C([6, 23, -3.5], [8, 6, 7], cs({tp: hazard(0, 0, 8, 7, AMBER, DARK, 2)})),
                    C([8.5, 17, -2], [3, 7, 4], fr())]},
                {name: 'left_forearm', parent: 'left_arm', pivot: [10, 17.5, 0], cubes: [
                    C([7, 9, -3], [6, 9, 6], cs({f: [[1, 1, 4, 1, EDGE]], sd: [[1, 4, 4, 1, DARK]]})),
                    C([7.5, 6, -2.5], [5, 4, 5], fr({f: [[1, 1, 1, 2, DARK], [3, 1, 1, 2, DARK]]}))]}
            ],
            animations: {
                'animation.patrol_robot.idle': {length: 4, loop: 'loop', bones: {
                    hips: {p: loopOf(4, t => [0, R2(-0.25 + 0.25 * Math.cos(2 * Math.PI * t)), 0])},
                    torso: {r: loopOf(4, t => [R2(1 + Math.sin(2 * Math.PI * t)), R2(2 * Math.sin(2 * Math.PI * t)), 0])},
                    right_arm: {r: loopOf(4, t => [R2(-2 + Math.sin(2 * Math.PI * t)), 0, 0])},
                    left_arm: {r: loopOf(4, t => [R2(3 * Math.sin(2 * Math.PI * (t + 0.3))), 0, -3])},
                    left_forearm: {r: loopOf(4, t => [R2(-10 - 3 * Math.sin(2 * Math.PI * t)), 0, 0])}}},
                'animation.patrol_robot.walk': ikGait({length: 1.1, thigh: 6.5, shin: 7, rest: 13.5, hip: 12.9, stride: 3.8, lift: 2.6, stance: 0.58,
                    bob: 0.55, sway: 3, pelvis: 5, twist: 3, lean: 3, feet: true,
                    arms: {left_arm: [16, 2, -1, 0.05], right_arm: [3, -4, 1, 0.05]}, forearms: {left_forearm: [12, 18, -1], right_forearm: [0, 2, 1]}}),
                'animation.patrol_robot.shot': move(0.2, {
                    right_arm: {r: K([0, [0, 0, 0]], [0.04, [-5, 0, 0]], [0.2, [0, 0, 0]])},
                    right_forearm: {p: K([0, [0, 0, 0]], [0.04, [0, 0.2, 1.6]], [0.2, [0, 0, 0]])},
                    torso: {r: K([0, [0, 0, 0]], [0.05, [-1.5, 2, 0]], [0.2, [0, 0, 0]])}}),
                'animation.patrol_robot.melee': move(0.75, {
                    torso: {r: K([0, [0, 0, 0]], [0.25, [4, 28, 0]], [0.38, [-6, -24, 0]], [0.75, [0, 0, 0]])},
                    left_arm: {r: K([0, [0, 0, 0]], [0.25, [25, 0, -25]], [0.38, [-95, -10, -8]], [0.55, [-80, 0, -5]], [0.75, [0, 0, 0]])},
                    left_forearm: {r: K([0, [-10, 0, 0]], [0.25, [-85, 0, 0]], [0.38, [-5, 0, 0]], [0.75, [-10, 0, 0]])},
                    right_arm: {r: K([0, [0, 0, 0]], [0.25, [-10, 0, 8]], [0.38, [10, 0, -4]], [0.75, [0, 0, 0]])},
                    hips: {p: K([0, [0, 0, 0]], [0.25, [0, -0.8, 1]], [0.38, [0, -0.5, -1.5]], [0.75, [0, 0, 0]])},
                    left_leg: {r: K([0, [0, 0, 0]], [0.38, [-12, 0, 0]], [0.75, [0, 0, 0]])},
                    left_shin: {r: K([0, [0, 0, 0]], [0.38, [10, 0, 0]], [0.75, [0, 0, 0]])},
                    left_foot: {r: K([0, [0, 0, 0]], [0.38, [2, 0, 0]], [0.75, [0, 0, 0]])}}),
                'animation.patrol_robot.dead': move(1.2, {
                    root: {r: K([0, [0, 0, 0]], [0.5, [-25, 0, 6]], [1.0, [-88, 0, 10]], [1.2, [-86, 0, 10]]),
                        p: K([0, [0, 0, 0]], [1.0, [0, 4, -17]], [1.2, [0, 4, -17]])},
                    right_leg: {r: K([0, [0, 0, 0]], [1.0, [-30, 8, 0]], [1.2, [-30, 8, 0]])},
                    right_shin: {r: K([0, [0, 0, 0]], [1.0, [45, 0, 0]], [1.2, [45, 0, 0]])},
                    left_leg: {r: K([0, [0, 0, 0]], [1.0, [-8, -6, 0]], [1.2, [-8, -6, 0]])},
                    head: {r: K([0, [0, 0, 0]], [1.0, [20, -25, 0]], [1.2, [20, -25, 0]])},
                    right_arm: {r: K([0, [0, 0, 0]], [1.0, [-20, 0, 40]], [1.2, [-20, 0, 40]])},
                    left_arm: {r: K([0, [0, 0, 0]], [1.0, [-60, 0, -50]], [1.2, [-60, 0, -50]])},
                    left_forearm: {r: K([0, [0, 0, 0]], [1.0, [-40, 0, 0]], [1.2, [-40, 0, 0]])}}, true)
            }
        };
    })();

    (() => {
        // ---------------------------------------------------------------- the mech
        const R2 = v => Math.round(v * 100) / 100;
        const loopOf = (length, fn) => looped(length, STEP_TIME, fn);
        const K = (...pairs) => Object.fromEntries(pairs.map(([t, v]) => [t.toFixed(3), v]));
        const move = (length, bones, hold) => Object.assign(flowing(length, bones), hold ? {loop: 'hold'} : {});
        const PLATE = {c: '#5f6957', n: 0.08};
        const FRAME = {c: '#34362f', n: 0.1};
        const DARK = '#151613', EDGE = '#495043', LIGHT = '#7b8670', AMBER = '#d89a2a', RED = '#d0352a', CORE = '#ff8a1e';
        const P = (o, s, more) => C(o, s, Object.assign({}, PLATE, more));
        const F = (o, s, more) => C(o, s, Object.assign({}, FRAME, more));
        const thigh = [F([-15, 24, -4], [7, 6, 8]), P([-14, 15, -3], [5, 11, 6], {f: [[1, 1, 3, 1, LIGHT], [1, 4, 3, 1, EDGE]], sd: [[1, 2, 4, 1, EDGE]]})];
        const shin = [F([-15, 13, -5], [7, 4, 9]), P([-14, 3.5, -3], [5, 10.5, 6], {f: [[1, 1, 3, 1, LIGHT], [2, 3, 1, 5, EDGE]]}),
            F([-12.5, 6, 2.5], [2, 6.5, 2], {edge: false})];
        const foot = [F([-16, 0, -9], [10, 4, 15], {f: hazard(0, 0, 10, 4, AMBER, DARK, 2)}), F([-15, 0.5, 5.5], [8, 3, 3])];
        const flip = cubes => cubes.map(c => mirror(c));
        SPECS.mech = {
            res: 256, seed: 1613, noCoplanarFaces: true, glow: CORE,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'hips', parent: 'root', pivot: [0, 29, 0], cubes: [F([-9, 27, -6], [18, 6, 12])]},
                {name: 'right_leg', parent: 'hips', pivot: [-11.5, 29, 0], cubes: thigh},
                {name: 'right_shin', parent: 'right_leg', pivot: [-11.5, 16, 0], cubes: shin},
                {name: 'right_foot', parent: 'right_shin', pivot: [-11.5, 3, 0], cubes: foot},
                {name: 'left_leg', parent: 'hips', pivot: [11.5, 29, 0], cubes: flip(thigh)},
                {name: 'left_shin', parent: 'left_leg', pivot: [11.5, 16, 0], cubes: flip(shin)},
                {name: 'left_foot', parent: 'left_shin', pivot: [11.5, 3, 0], cubes: flip(foot)},
                {name: 'torso', parent: 'hips', pivot: [0, 32, 0], cubes: [
                    P([-12, 31, -9], [24, 15, 17], {f: [[1, 1, 22, 1, LIGHT], [3, 3, 18, 8, DARK], [2, 12, 20, 1, EDGE]],
                        sd: [[2, 2, 13, 1, EDGE], ...vents(3, 5, 10, 4, DARK, 2)], k: vents(4, 3, 16, 5, DARK, 2)}),
                    F([-6, 40, -11], [12, 5, 3], {f: [[1, 1, 10, 2, DARK], [2, 1, 3, 2, RED], [7, 1, 3, 2, RED]]}),
                    P([-10, 45, -7], [20, 3, 13], {tp: [[1, 1, 18, 1, LIGHT], [1, 11, 18, 1, EDGE]]}),
                    F([6, 47, 1], [1, 7, 1], {edge: false}),
                    F([-7, 33, 7], [14, 11, 4], {k: vents(1, 1, 12, 5, DARK, 2)})]},
                // the reactor core behind the chest plate: hidden at first, laid bare in the second phase
                {name: 'core', parent: 'torso', pivot: [0, 36, -9], cubes: [C([-4, 34, -10.5], [8, 4, 2], {c: CORE, n: 0.06, f: [[1, 1, 6, 2, '#ffd27a']], edge: false})]},
                {name: 'plate_front', parent: 'torso', pivot: [0, 36, -10], cubes: [P([-9, 32, -11], [18, 7, 3], {f: [[1, 1, 16, 1, LIGHT], [2, 3, 3, 2, AMBER], [13, 3, 3, 2, AMBER], [1, 5, 16, 1, EDGE]]})]},
                {name: 'gun_mount', parent: 'torso', pivot: [-14, 40, 0], cubes: []},
                {name: 'right_gun', parent: 'gun_mount', pivot: [-14, 40, 0], cubes: [
                    F([-18, 36, -5], [7, 9, 10]),
                    P([-19, 32, -16], [9, 7, 20], {sd: [[2, 1, 16, 1, LIGHT], [2, 4, 16, 1, EDGE]], tp: [[1, 2, 7, 1, EDGE]]}),
                    F([-18, 34, -21], [3, 3, 6], {f: [[1, 1, 1, 1, '#050505']], edge: false}),
                    F([-14, 34, -21], [3, 3, 6], {f: [[1, 1, 1, 1, '#050505']], edge: false}),
                    F([-19, 33, -29], [9, 5, 9], {f: [[1, 1, 2, 2, '#050505'], [5, 1, 2, 2, '#050505']]})]},
                {name: 'left_pod', parent: 'torso', pivot: [14, 44, 0], cubes: [
                    F([11, 39, -4], [5, 6, 8]),
                    P([13, 44, -8], [11, 9, 14], {f: [[1, 1, 4, 3, DARK], [6, 1, 4, 3, DARK], [1, 5, 4, 3, DARK], [6, 5, 4, 3, DARK],
                        [2, 2, 2, 1, RED], [7, 2, 2, 1, RED], [2, 6, 2, 1, RED], [7, 6, 2, 1, RED]], sd: hazard(1, 6, 12, 2, AMBER, DARK, 2)})]},
                // the jetpack: a frame on the back, two fuel tanks and two nozzles glowing inside
                {name: 'jetpack', parent: 'torso', pivot: [0, 38, 11], cubes: [
                    F([-7.5, 34, 10], [15, 9, 2]),
                    P([-8.5, 32, 11.5], [5, 12, 5], {sd: [[1, 2, 3, 1, LIGHT], [1, 8, 3, 1, EDGE]], k: hazard(0, 9, 5, 2, AMBER, DARK, 2)}),
                    P([3.5, 32, 11.5], [5, 12, 5], {sd: [[1, 2, 3, 1, LIGHT], [1, 8, 3, 1, EDGE]], k: hazard(0, 9, 5, 2, AMBER, DARK, 2)})]},
                {name: 'jet_right', parent: 'jetpack', pivot: [-6, 31, 13.75], cubes: [F([-7.5, 29.5, 12.25], [3, 3, 3], {bt: [[0, 0, 3, 3, CORE]], edge: false})]},
                {name: 'jet_left', parent: 'jetpack', pivot: [6, 31, 13.75], cubes: [F([4.5, 29.5, 12.25], [3, 3, 3], {bt: [[0, 0, 3, 3, CORE]], edge: false})]},
                // the gatling: on a swinging arm at the back of the left shoulder; folded down behind the back until the second phase
                {name: 'gatling_mount', parent: 'torso', pivot: [9.5, 48, 6], cubes: [C([8, 46.2, 3.75], [3, 5, 5], Object.assign({}, FRAME), {inflate: -0.25})]},
                {name: 'gatling_aim', parent: 'gatling_mount', pivot: [9.5, 53, 0], cubes: []},
                {name: 'gatling', parent: 'gatling_aim', pivot: [9.5, 53, 0], cubes: [
                    P([6.5, 50, -9], [6, 6, 12], {sd: [[1, 1, 10, 1, LIGHT], [1, 4, 10, 1, EDGE]], tp: hazard(0, 9, 6, 2, AMBER, DARK, 2), f: [[1, 1, 4, 4, DARK]]}),
                    C([7.5, 51.5, 2.6], [4, 3, 2], Object.assign({}, FRAME), {inflate: -0.2})]},
                // the barrels turn about the gun's axis while it fires; whole pixels on the texture (thin by inflate), so they keep their paint
                {name: 'gatling_barrels', parent: 'gatling', pivot: [9.5, 53, -9], cubes: [
                    ...[[8.4, 51.9], [10.6, 51.9], [8.4, 54.1], [10.6, 54.1]].map(([x, y]) => C([x - 1, y - 1, -21.6], [2, 2, 14], {c: '#2e302c', n: 0.08, edge: false,
                        tp: [[0, 0, 2, 14, '#4a4d47']], f: [[0, 0, 2, 2, '#050505']]}, {inflate: -0.4})),
                    C([8, 51.5, -16.3], [3, 3, 1], {c: '#3a3c37', n: 0.08, edge: false}, {inflate: -0.25}),
                    C([8, 51.5, -21.3], [3, 3, 1], {c: '#3a3c37', n: 0.08, edge: false}, {inflate: -0.3})]},
                // where the flashes of its guns are drawn
                {name: 'gun_muzzle', parent: 'right_gun', pivot: [-14.5, 35.5, -29.2], cubes: []},
                {name: 'gatling_muzzle', parent: 'gatling', pivot: [9.5, 53, -21.4], cubes: []}
            ],
            animations: (() => {
                const heavy = (length, stride, lean, bob) => ikGait({length, thigh: 13, shin: 13, rest: 26, hip: 24.6, stride, lift: 4.5, stance: 0.62,
                    bob, sway: 4, pelvis: 6, twist: 4, lean, feet: true, toe: 10, hips: 'hips', torso: 'torso',
                    arms: {right_gun: [3, -2, 1, 0.1]}});
                return {
                    'animation.mech.idle': {length: 4, loop: 'loop', bones: {
                        hips: {p: loopOf(4, t => [0, R2(-0.4 + 0.4 * Math.cos(2 * Math.PI * t)), 0])},
                        torso: {r: loopOf(4, t => [R2(Math.sin(2 * Math.PI * t)), 0, R2(0.8 * Math.sin(2 * Math.PI * (t + 0.25)))])},
                        right_gun: {r: loopOf(4, t => [R2(-2 + 1.5 * Math.sin(2 * Math.PI * t)), 0, 0])},
                        left_pod: {r: loopOf(4, t => [R2(-2 * Math.sin(2 * Math.PI * (t + 0.4))), 0, 0])},
                        core: {s: loopOf(4, t => { const k = R2(1 + 0.08 * Math.sin(8 * Math.PI * t)); return [k, k, 1]; })}}},
                    'animation.mech.walk': heavy(2.2, 7, 3, 1.2),
                    'animation.mech.run': (() => {
                        const g = heavy(1.3, 9, 9, 1.8);
                        g.bones.core = {s: loopOf(1.3, t => { const k = R2(1.1 + 0.15 * Math.sin(4 * Math.PI * t)); return [k, k, 1]; })};
                        return g;
                    })(),
                    'animation.mech.idle_berserk': {length: 1.6, loop: 'loop', bones: {
                        hips: {p: loopOf(1.6, t => [0, R2(-1.5 + 0.5 * Math.cos(2 * Math.PI * t)), 0])},
                        right_leg: {r: loopOf(1.6, () => [-10, 0, 0])}, left_leg: {r: loopOf(1.6, () => [-10, 0, 0])},
                        right_shin: {r: loopOf(1.6, () => [18, 0, 0])}, left_shin: {r: loopOf(1.6, () => [18, 0, 0])},
                        right_foot: {r: loopOf(1.6, () => [-8, 0, 0])}, left_foot: {r: loopOf(1.6, () => [-8, 0, 0])},
                        torso: {r: loopOf(1.6, t => [R2(8 + 2 * Math.sin(2 * Math.PI * t)), R2(4 * Math.sin(6 * Math.PI * t) * Math.sin(2 * Math.PI * t)), R2(1.5 * Math.sin(10 * Math.PI * t))])},
                        right_gun: {r: loopOf(1.6, t => [R2(-6 + 3 * Math.sin(4 * Math.PI * t)), 0, 0])},
                        core: {s: loopOf(1.6, t => { const k = R2(1.15 + 0.15 * Math.sin(6 * Math.PI * t)); return [k, k, 1]; })}}},
                    'animation.mech.shot': move(0.14, {
                        right_gun: {p: K([0, [0, 0, 0]], [0.035, [0, 0.3, 2.2]], [0.14, [0, 0, 0]]), r: K([0, [0, 0, 0]], [0.035, [-3, 0, 0]], [0.14, [0, 0, 0]])},
                        torso: {r: K([0, [0, 0, 0]], [0.04, [-0.6, 1, 0]], [0.14, [0, 0, 0]])}}),
                    'animation.mech.rockets': move(0.8, {
                        left_pod: {r: K([0, [0, 0, 0]], [0.12, [-22, 0, 0]], [0.45, [-10, 0, 0]], [0.8, [0, 0, 0]]), p: K([0, [0, 0, 0]], [0.08, [0, -1.2, 2]], [0.8, [0, 0, 0]])},
                        torso: {r: K([0, [0, 0, 0]], [0.1, [-3, 0, -2]], [0.8, [0, 0, 0]])},
                        hips: {p: K([0, [0, 0, 0]], [0.12, [0, -0.8, 0.6]], [0.8, [0, 0, 0]])}}),
                    'animation.mech.stomp': move(1.0, {
                        hips: {p: K([0, [0, 0, 0]], [0.35, [0, 0.8, 0]], [0.6, [0, -2.4, 0]], [1.0, [0, 0, 0]]), r: K([0, [0, 0, 0]], [0.35, [0, 0, -6]], [0.6, [0, 0, 2]], [1.0, [0, 0, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [0.45, [-48, 0, 4]], [0.6, [6, 0, 0]], [1.0, [0, 0, 0]])},
                        right_shin: {r: K([0, [0, 0, 0]], [0.45, [62, 0, 0]], [0.6, [4, 0, 0]], [1.0, [0, 0, 0]])},
                        right_foot: {r: K([0, [0, 0, 0]], [0.45, [-10, 0, 0]], [0.6, [-10, 0, 0]], [1.0, [0, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [0.6, [16, 0, 0]], [1.0, [0, 0, 0]])},
                        left_leg: {r: K([0, [0, 0, 0]], [0.6, [-8, 0, 0]], [1.0, [0, 0, 0]])},
                        left_foot: {r: K([0, [0, 0, 0]], [0.6, [-8, 0, 0]], [1.0, [0, 0, 0]])},
                        torso: {r: K([0, [0, 0, 0]], [0.45, [-6, 0, 4]], [0.62, [10, 0, -2]], [1.0, [0, 0, 0]])},
                        right_gun: {r: K([0, [0, 0, 0]], [0.45, [-15, 0, 0]], [0.62, [8, 0, 0]], [1.0, [0, 0, 0]])}}),
                    'animation.mech.transform': move(3.0, {
                        hips: {p: K([0, [0, 0, 0]], [0.7, [0, -6, 0]], [2.2, [0, -6, 0]], [3.0, [0, -1, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [0.7, [-28, 0, 0]], [2.2, [-28, 0, 0]], [3.0, [-8, 0, 0]])},
                        left_leg: {r: K([0, [0, 0, 0]], [0.7, [-28, 0, 0]], [2.2, [-28, 0, 0]], [3.0, [-8, 0, 0]])},
                        right_shin: {r: K([0, [0, 0, 0]], [0.7, [52, 0, 0]], [2.2, [52, 0, 0]], [3.0, [16, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [0.7, [52, 0, 0]], [2.2, [52, 0, 0]], [3.0, [16, 0, 0]])},
                        right_foot: {r: K([0, [0, 0, 0]], [0.7, [-24, 0, 0]], [2.2, [-24, 0, 0]], [3.0, [-8, 0, 0]])},
                        left_foot: {r: K([0, [0, 0, 0]], [0.7, [-24, 0, 0]], [2.2, [-24, 0, 0]], [3.0, [-8, 0, 0]])},
                        torso: {r: K([0, [0, 0, 0]], [0.7, [22, 0, 0]], [0.9, [22, 0, 3]], [1.1, [22, 0, -3]], [1.3, [22, 0, 3]], [1.5, [26, 0, 0]],
                            [1.6, [8, 0, 0]], [2.2, [6, 0, 0]], [2.6, [-14, 0, 0]], [3.0, [8, 0, 0]])},
                        right_gun: {r: K([0, [0, 0, 0]], [0.7, [30, 0, 10]], [1.5, [30, 0, 10]], [2.6, [-40, 0, -12]], [3.0, [-6, 0, 0]])},
                        plate_front: {p: K([0, [0, 0, 0]], [1.5, [0, 0, 0]], [1.7, [0, 2, -7]], [2.1, [0, -14, -12]]),
                            r: K([0, [0, 0, 0]], [1.5, [0, 0, 0]], [2.1, [-80, 20, 0]]), s: K([0, [1, 1, 1]], [1.95, [1, 1, 1]], [2.1, [0, 0, 0]], [3.0, [0, 0, 0]])},
                        left_pod: {p: K([0, [0, 0, 0]], [1.5, [0, 0, 0]], [1.65, [3, 5, 0]], [2.2, [8, -26, 2]]),
                            r: K([0, [0, 0, 0]], [1.5, [0, 0, 0]], [2.2, [20, 0, -70]]), s: K([0, [1, 1, 1]], [2.05, [1, 1, 1]], [2.2, [0, 0, 0]], [3.0, [0, 0, 0]])},
                        core: {s: K([0, [1, 1, 1]], [1.5, [1, 1, 1]], [1.7, [1.4, 1.4, 1]], [2.0, [1.1, 1.1, 1]], [2.6, [1.35, 1.35, 1]], [3.0, [1.15, 1.15, 1]])},
                        gatling_mount: {r: K([0, [GATLING_FOLDED, 0, 0]], [2.1, [GATLING_FOLDED, 0, 0]], [2.6, [-12, 0, 0]], [2.8, [4, 0, 0]], [3.0, [0, 0, 0]])}}),
                    'animation.mech.folded': {length: 1, loop: 'loop', bones: {gatling_mount: {r: {'0.000': [GATLING_FOLDED, 0, 0], '1.000': [GATLING_FOLDED, 0, 0]}}}},
                    'animation.mech.gatling': {length: 0.1, loop: 'once', bones: {
                        gatling: {p: {'0.000': [0, 0, 0], '0.030': [0, 0, 0.8], '0.100': [0, 0, 0]}}}},
                    'animation.mech.gatling_spin': {length: 0.25, loop: 'loop', bones: {
                        gatling_barrels: {r: {'0.000': [0, 0, 0], '0.125': [0, 0, 180], '0.250': [0, 0, 360]}}}},
                    'animation.mech.fly': {length: 1.2, loop: 'loop', bones: {
                        hips: {p: loopOf(1.2, t => [0, R2(1.2 * Math.sin(2 * Math.PI * t)), 0])},
                        right_leg: {r: loopOf(1.2, t => [R2(12 + 4 * Math.sin(2 * Math.PI * t)), 0, 6])},
                        left_leg: {r: loopOf(1.2, t => [R2(16 + 4 * Math.sin(2 * Math.PI * (t + 0.3))), 0, -6])},
                        right_shin: {r: loopOf(1.2, t => [R2(30 + 5 * Math.sin(2 * Math.PI * t)), 0, 0])},
                        left_shin: {r: loopOf(1.2, t => [R2(36 + 5 * Math.sin(2 * Math.PI * (t + 0.3))), 0, 0])},
                        right_foot: {r: loopOf(1.2, () => [28, 0, 0])}, left_foot: {r: loopOf(1.2, () => [30, 0, 0])},
                        torso: {r: loopOf(1.2, t => [R2(12 + 2 * Math.sin(2 * Math.PI * t)), 0, R2(2 * Math.sin(2 * Math.PI * (t + 0.25)))])},
                        jet_right: {r: loopOf(1.2, t => [R2(-10 + 4 * Math.sin(4 * Math.PI * t)), 0, 0])},
                        jet_left: {r: loopOf(1.2, t => [R2(-10 + 4 * Math.sin(4 * Math.PI * (t + 0.2))), 0, 0])},
                        core: {s: loopOf(1.2, t => { const k = R2(1.15 + 0.12 * Math.sin(4 * Math.PI * t)); return [k, k, 1]; })}}},
                    'animation.mech.dash': move(1.1, {
                        torso: {r: K([0, [0, 0, 0]], [0.15, [16, 0, 0]], [0.95, [16, 0, 0]], [1.1, [0, 0, 0]])},
                        hips: {p: K([0, [0, 0, 0]], [0.15, [0, -2, 0]], [0.95, [0, -2, 0]], [1.1, [0, 0, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [0.25, [-30, 0, 0]], [0.5, [24, 0, 0]], [0.75, [-30, 0, 0]], [1.0, [10, 0, 0]], [1.1, [0, 0, 0]])},
                        left_leg: {r: K([0, [0, 0, 0]], [0.25, [24, 0, 0]], [0.5, [-30, 0, 0]], [0.75, [24, 0, 0]], [1.0, [-10, 0, 0]], [1.1, [0, 0, 0]])},
                        right_shin: {r: K([0, [0, 0, 0]], [0.25, [40, 0, 0]], [0.5, [10, 0, 0]], [0.75, [40, 0, 0]], [1.1, [0, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [0.25, [10, 0, 0]], [0.5, [40, 0, 0]], [0.75, [10, 0, 0]], [1.1, [0, 0, 0]])},
                        right_gun: {r: K([0, [0, 0, 0]], [0.15, [25, 0, 12]], [0.95, [25, 0, 12]], [1.1, [0, 0, 0]])}}),
                    'animation.mech.dead': move(1.6, {
                        hips: {p: K([0, [0, 0, 0]], [0.5, [0, -4, 0]], [1.2, [0, -13, 0]], [1.6, [0, -13, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [1.2, [-72, 0, 6]], [1.6, [-72, 0, 6]])},
                        left_leg: {r: K([0, [0, 0, 0]], [1.2, [-60, 0, -8]], [1.6, [-60, 0, -8]])},
                        right_shin: {r: K([0, [0, 0, 0]], [1.2, [108, 0, 0]], [1.6, [108, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [1.2, [96, 0, 0]], [1.6, [96, 0, 0]])},
                        right_foot: {r: K([0, [0, 0, 0]], [1.2, [-30, 0, 0]], [1.6, [-30, 0, 0]])},
                        left_foot: {r: K([0, [0, 0, 0]], [1.2, [-30, 0, 0]], [1.6, [-30, 0, 0]])},
                        torso: {r: K([0, [0, 0, 0]], [0.6, [12, 0, 4]], [1.3, [38, 0, 9]], [1.6, [36, 0, 9]])},
                        right_gun: {r: K([0, [0, 0, 0]], [1.3, [52, 0, 18]], [1.6, [50, 0, 18]])},
                        core: {s: K([0, [1, 1, 1]], [1.6, [0.9, 0.9, 1]])}}, true)
                };
            })()
        };
    })();

    // ---------------------------------------------------------------- the mutants
    (() => {
        const R2 = v => Math.round(v * 100) / 100;
        const loopOf = (length, fn) => looped(length, STEP_TIME, fn);
        const K = (...pairs) => Object.fromEntries(pairs.map(([t, v]) => [t.toFixed(3), v]));
        const move = (length, bones, hold) => Object.assign(flowing(length, bones), hold ? {loop: 'hold'} : {});
        /**
         * Organic paint: blotches of darker and lighter tissue, veins, and now and then a sore, a tooth or a pustule; every face
         * of the cube gets its own, sized to it. pal: {base, dark, light, vein, spots: [colours of the rarer marks]}.
         */
        let seedCount = 5100;
        function organic(s, pal, extra) {
            const rand = random(seedCount++);
            const w = Math.ceil(s[0]), h = Math.ceil(s[1]), d = Math.ceil(s[2]);
            const marks = (fw, fh) => {
                const ops = [];
                const count = Math.max(1, Math.round(fw * fh / 10));
                for (let i = 0; i < count; i++) {
                    const bw = 1 + Math.floor(rand() * 2), bh = 1 + Math.floor(rand() * 2);
                    ops.push([Math.floor(rand() * fw), Math.floor(rand() * fh), bw, bh, rand() < 0.6 ? pal.dark : pal.light]);
                }
                if (pal.vein && fw * fh > 12) {
                    let x = Math.floor(rand() * fw), y = 0;
                    while (y < fh) { ops.push([x, y, 1, 1, pal.vein]); y++; x += Math.floor(rand() * 3) - 1; }
                }
                for (const spot of pal.spots || []) {
                    if (rand() < (pal.spotChance || 0.2)) {
                        ops.push([Math.floor(rand() * fw), Math.floor(rand() * fh), 1, 1, spot]);
                    }
                }
                return ops;
            };
            const paint = {c: pal.base, n: pal.n || 0.1, edge: false, faces: {top: shade(pal.base, 1.12), bottom: shade(pal.base, 0.78)},
                f: marks(w, h), k: marks(w, h), l: marks(d, h), r: marks(d, h), tp: marks(w, d), bt: marks(w, d)};
            for (const [key, ops] of Object.entries(extra || {})) {
                paint[key] = (paint[key] || []).concat(ops);
            }
            return paint;
        }
        const O = (o, s, pal, extra, opts) => C(o, s, organic(s, pal, extra), opts);
        const RAW = {base: '#8a2c28', dark: '#6c1f1d', light: '#a4423a', vein: '#4a1214', spots: ['#c25a4c']};
        const SKIN = {base: '#76584e', dark: '#5e443c', light: '#8e6c5e', vein: '#6a2420', spots: ['#8a2c28']};
        const BONE = {base: '#d8cfb5', dark: '#a89e84', light: '#efe8d4', n: 0.06};
        const TEETH = '#efe8d4', MAW = '#2a0808', EYE = '#e8d050';
        /**
         * A tooth: white enamel with a grain like the game's bone and quartz (part B3f). Whole pixels on the texture, made thin by a
         * negative inflate, so it never takes in the colour of its neighbours on the texture (that was the red line). cx, cz: its
         * middle; ya: its lower end; h: its visible length.
         */
        const FANG = {c: '#ece6d2', n: 0.11, edge: false};
        const fang = (cx, ya, h, cz) => C([cx - 0.5, ya - 0.25, cz - 0.5], [1, Math.max(1, Math.round(h + 0.5)), 1], FANG, {inflate: -0.25});

        // the hound: a skinned, starving dog; its jaw splits open; a fleshy growth over the right shoulder, bone spikes on the back
        SPECS.mutated_hound = {
            res: 64, seed: 1641, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'body', parent: 'root', pivot: [0, 10, 0], cubes: [
                    O([-3.5, 7, -6.5], [7, 6, 8], RAW, {sd: [[1, 1, 1, 4, '#d8cfb5'], [3, 1, 1, 4, '#d8cfb5'], [5, 1, 1, 4, '#d8cfb5']]}),
                    O([-3.25, 7.5, 1], [6.5, 5, 5], SKIN),
                    O([-5, 10, -5.5], [3, 4, 4], RAW, {f: [[1, 1, 1, 1, EYE]]}),
                    O([-0.5, 12.5, -5], [1, 2, 1], BONE), O([-0.5, 12.5, -2], [1, 3, 1], BONE), O([-0.5, 12, 2], [1, 2, 1], BONE)]},
                {name: 'head', parent: 'body', pivot: [0, 10, -6], cubes: [
                    O([-2.5, 8, -11], [5, 4.5, 5], SKIN, {f: [[0, 1, 1, 1, EYE], [4, 1, 1, 1, EYE], [2, 0, 1, 1, EYE]]}),
                    O([-2, 9, -15.5], [4, 2, 5], RAW, {bt: [[0, 0, 4, 5, MAW]]}),
                    ...[[-1.5, 1.5], [-0.6, 0.5], [0.3, 1.5], [1.2, 0.5]].map(([x, h]) => fang(x, 9.25 - h, h, -15.0)),
                    fang(-1.55, 7.75, 1.5, -13.35), fang(1.55, 7.75, 1.5, -13.15),
                    O([-3, 12, -9], [1, 3, 1], BONE), O([2, 11.5, -8.5], [1, 2, 1], BONE)]},
                {name: 'jaw', parent: 'head', pivot: [0, 8.5, -11], cubes: [O([-2, 6.9, -15], [4, 2, 5], RAW, {tp: [[0, 0, 4, 5, MAW]]}),
                    ...[[-1.15, 0.5], [-0.15, 1.5], [0.85, 0.5], [1.55, 1.5]].map(([x, h]) => fang(x, 8.65, h, -14.45))]},
                {name: 'front_right_leg', parent: 'body', pivot: [-2, 8, -4], cubes: [O([-3, 0, -5], [2, 8, 2], SKIN, {f: [[0, 6, 2, 2, '#2a1a16']]})]},
                {name: 'front_left_leg', parent: 'body', pivot: [2, 8, -4], cubes: [O([1, 0, -5], [2, 8, 2], RAW, {f: [[0, 6, 2, 2, '#2a1a16']]})]},
                {name: 'back_right_leg', parent: 'body', pivot: [-2, 8, 3], cubes: [O([-3, 0, 2], [2, 8, 2], SKIN, {f: [[0, 6, 2, 2, '#2a1a16']]})]},
                {name: 'back_left_leg', parent: 'body', pivot: [2, 8, 3], cubes: [O([1, 0, 2], [2, 8, 2], SKIN, {f: [[0, 6, 2, 2, '#2a1a16']]})]},
                {name: 'tendril_1', parent: 'body', pivot: [-1, 11, 5], cubes: [O([-1.5, 10.5, 5], [1, 1, 6], RAW, {}, {rot: [-30, 10, 0], pivot: [-1, 11, 5]})]},
                {name: 'tendril_2', parent: 'body', pivot: [1, 11, 5], cubes: [O([0.5, 10.5, 5], [1, 1, 5], RAW, {}, {rot: [-15, -20, 0], pivot: [1, 11, 5]})]}
            ],
            animations: {
                'animation.mutated_hound.idle': {length: 2, loop: 'loop', bones: {
                    body: {p: loopOf(2, t => [0, R2(-0.3 + 0.3 * Math.cos(2 * Math.PI * t)), 0])},
                    jaw: {r: loopOf(2, t => [R2(6 + 6 * Math.sin(2 * Math.PI * t)), 0, 0])},
                    tendril_1: {r: loopOf(2, t => [R2(8 * Math.sin(2 * Math.PI * t)), R2(12 * Math.sin(2 * Math.PI * (t + 0.2))), 0])},
                    tendril_2: {r: loopOf(2, t => [R2(8 * Math.sin(2 * Math.PI * (t + 0.5))), R2(-12 * Math.sin(2 * Math.PI * (t + 0.7))), 0])}}},
                'animation.mutated_hound.walk': {length: 0.5, loop: 'loop', bones: {
                    front_right_leg: {r: loopOf(0.5, t => [R2(40 * Math.cos(2 * Math.PI * t)), 0, 0])},
                    front_left_leg: {r: loopOf(0.5, t => [R2(40 * Math.cos(2 * Math.PI * (t + 0.12))), 0, 0])},
                    back_right_leg: {r: loopOf(0.5, t => [R2(-40 * Math.cos(2 * Math.PI * (t + 0.05))), 0, 0])},
                    back_left_leg: {r: loopOf(0.5, t => [R2(-40 * Math.cos(2 * Math.PI * (t + 0.17))), 0, 0])},
                    body: {r: loopOf(0.5, t => [R2(5 * Math.sin(2 * Math.PI * t)), 0, 0]), p: loopOf(0.5, t => [0, R2(0.8 * Math.abs(Math.sin(2 * Math.PI * t))), 0])},
                    head: {r: loopOf(0.5, t => [R2(-5 * Math.sin(2 * Math.PI * t)), 0, 0])},
                    jaw: {r: loopOf(0.5, t => [R2(14 + 10 * Math.sin(2 * Math.PI * t)), 0, 0])},
                    tendril_1: {r: loopOf(0.5, t => [R2(15 * Math.sin(2 * Math.PI * t)), 0, 0])},
                    tendril_2: {r: loopOf(0.5, t => [R2(15 * Math.sin(2 * Math.PI * (t + 0.3))), 0, 0])}}},
                'animation.mutated_hound.attack': move(0.4, {
                    body: {r: K([0, [0, 0, 0]], [0.12, [8, 0, 0]], [0.22, [-8, 0, 0]], [0.4, [0, 0, 0]]), p: K([0, [0, 0, 0]], [0.12, [0, -0.6, 1]], [0.22, [0, 0.6, -2.5]], [0.4, [0, 0, 0]])},
                    jaw: {r: K([0, [5, 0, 0]], [0.15, [55, 0, 0]], [0.24, [0, 0, 0]], [0.4, [5, 0, 0]])},
                    head: {r: K([0, [0, 0, 0]], [0.15, [-15, 0, 0]], [0.24, [10, 0, 0]], [0.4, [0, 0, 0]])}})
            }
        };

        // the glowing ghoul: a worker in the rags of a yellow hazmat suit, swollen with sores that glow; its left arm grew into a
        // heavy clawed mass, its head split open around a glowing maw
        const SUIT = {base: '#b08e2a', dark: '#8e7020', light: '#c8a63c', vein: '#6a5420', spots: ['#5a4a3a'], n: 0.08};
        const SICK = {base: '#768468', dark: '#5c6850', light: '#8c9a7c', vein: '#4e5a42', spots: ['#7cff4a'], spotChance: 0.45};
        const GLOW = '#7cff4a';
        SPECS.glowing_ghoul = {
            res: 64, seed: 1642, noCoplanarFaces: true, glow: GLOW,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'right_leg', parent: 'root', pivot: [-2, 13, 0], cubes: [O([-3, 0, -1.5], [2.5, 13, 3], SUIT, {f: [[0, 0, 3, 3, '#3a3020']]})]},
                {name: 'left_leg', parent: 'root', pivot: [2, 13, 0], cubes: [O([0.5, 0, -1.5], [2.25, 13, 3], SICK, {f: [[0, 0, 3, 5, '#7a6018'], [1, 8, 1, 1, GLOW]]})]},
                {name: 'body', parent: 'root', pivot: [0, 12, 0], cubes: [
                    O([-4, 12, -2], [8, 11, 4], SUIT, {f: [[4, 3, 3, 4, '#7a8a6a'], [5, 4, 1, 1, GLOW], [2, 7, 2, 2, GLOW]], k: [[1, 2, 5, 5, '#7a8a6a'], [2, 3, 2, 2, GLOW]]}),
                    O([1, 17, -3], [3.5, 4, 1.5], SICK, {f: [[1, 1, 1, 1, GLOW]]}),
                    O([-3, 19, 1.5], [4, 4.5, 3], SICK, {k: [[1, 1, 2, 2, GLOW]], tp: [[1, 1, 1, 1, GLOW]]})]},
                {name: 'head', parent: 'body', pivot: [0, 22, -1], cubes: [
                    // like the brute's (part B3h): a skull pushed forward with three glowing eyes and a dark maw in the lower face, a row of
                    // upper teeth before it, a growth on top; the jaw under it drops open
                    O([-3.5, 23.2, -5], [7, 6, 7], SICK, {f: [[1, 1, 1, 1, GLOW], [4, 0, 1, 1, GLOW], [5, 2, 1, 1, GLOW], [1, 4, 5, 2, MAW]], tp: [[3, 1, 1, 4, GLOW]]}),
                    ...[[-2.0, 1.5], [-0.9, 1.0], [0.2, 1.5], [1.3, 1.0], [2.2, 1.5]].map(([x, h]) => fang(x, 25.15 - h, h, -5.1)),
                    O([1, 28.6, -4], [3, 2, 3], SICK, {tp: [[1, 1, 1, 1, GLOW]], f: [[1, 0, 1, 1, GLOW]]})]},
                {name: 'jaw', parent: 'head', pivot: [0, 23.4, -1], cubes: [O([-3, 21.6, -5.5], [6, 2, 5], SICK, {tp: [[1, 0, 4, 3, MAW]], f: [[4, 1, 1, 1, GLOW]]}),
                    ...[[-1.9, 1.0], [-0.8, 0.5], [0.4, 1.0], [1.5, 0.5]].map(([x, h]) => fang(x, 23.45, h, -5.15))]},
                {name: 'right_arm', parent: 'body', pivot: [-5, 21, 0], cubes: [O([-6.5, 9, -1.5], [3, 13, 3], SUIT, {f: [[0, 9, 3, 4, '#7a8a6a'], [1, 11, 1, 1, GLOW]]})]},
                {name: 'left_arm', parent: 'body', pivot: [5, 21, 0], cubes: [O([3.5, 15, -2.5], [4, 7, 5], SICK, {f: [[1, 2, 1, 1, GLOW]], sd: [[2, 1, 1, 1, GLOW]]})]},
                {name: 'left_forearm', parent: 'left_arm', pivot: [5.5, 15.5, 0], cubes: [
                    O([3, 6, -3], [6, 10, 6], SICK, {f: [[1, 2, 2, 2, GLOW], [4, 6, 1, 1, GLOW]], sd: [[2, 3, 2, 2, GLOW]], k: [[3, 1, 2, 2, GLOW]]}),
                    O([4, 3, -2.5], [1, 3.5, 1], BONE), O([6, 2.5, -2.5], [1, 4.5, 1], BONE), O([7.5, 3, -2.5], [1, 3.5, 1], BONE)]}
            ],
            animations: {
                'animation.glowing_ghoul.idle': {length: 3, loop: 'loop', bones: {
                    body: {r: loopOf(3, t => [R2(14 + 3 * Math.sin(2 * Math.PI * t)), 0, R2(3 * Math.sin(2 * Math.PI * (t + 0.3)))])},
                    right_arm: {r: loopOf(3, t => [R2(-10 + 5 * Math.sin(2 * Math.PI * t)), 0, 4])},
                    left_arm: {r: loopOf(3, t => [R2(-6 + 4 * Math.sin(2 * Math.PI * (t + 0.5))), 0, -10])},
                    left_forearm: {r: loopOf(3, t => [R2(-14 - 4 * Math.sin(2 * Math.PI * t)), 0, 0])},
                    jaw: {r: loopOf(3, t => [R2(8 + 8 * Math.sin(4 * Math.PI * t)), 0, 0])}}},
                // a limping shamble: the healthy leg takes long steps, the sick one drags
                'animation.glowing_ghoul.walk': {length: 1.4, loop: 'loop', bones: {
                    right_leg: {r: loopOf(1.4, t => [R2(30 * Math.cos(2 * Math.PI * t)), 0, 0])},
                    left_leg: {r: loopOf(1.4, t => [R2(-14 * Math.cos(2 * Math.PI * t)), 0, R2(-4 + 3 * Math.sin(2 * Math.PI * t))])},
                    body: {r: loopOf(1.4, t => [R2(18 + 4 * Math.sin(4 * Math.PI * t)), R2(6 * Math.cos(2 * Math.PI * t)), R2(-7 * Math.cos(2 * Math.PI * t))]),
                        p: loopOf(1.4, t => [0, R2(-0.9 * Math.abs(Math.sin(2 * Math.PI * t))), 0])},
                    right_arm: {r: loopOf(1.4, t => [R2(-60 + 15 * Math.cos(2 * Math.PI * (t + 0.5))), 0, 5])},
                    left_arm: {r: loopOf(1.4, t => [R2(10 + 12 * Math.cos(2 * Math.PI * t)), 0, -12])},
                    left_forearm: {r: loopOf(1.4, t => [R2(-20 - 8 * Math.cos(2 * Math.PI * t)), 0, 0])},
                    jaw: {r: loopOf(1.4, t => [R2(10 + 10 * Math.sin(2 * Math.PI * t)), 0, 0])}}},
                'animation.glowing_ghoul.attack': move(0.7, {
                    body: {r: K([0, [14, 0, 0]], [0.3, [6, -25, 0]], [0.45, [26, 25, 0]], [0.7, [14, 0, 0]])},
                    left_arm: {r: K([0, [0, 0, -10]], [0.3, [-120, 10, -30]], [0.45, [-30, -20, 10]], [0.7, [0, 0, -10]])},
                    left_forearm: {r: K([0, [-15, 0, 0]], [0.3, [-50, 0, 0]], [0.45, [-5, 0, 0]], [0.7, [-15, 0, 0]])},
                    jaw: {r: K([0, [5, 0, 0]], [0.3, [35, 0, 0]], [0.7, [5, 0, 0]])}}),
                'animation.glowing_ghoul.spit': move(0.8, {
                    body: {r: K([0, [14, 0, 0]], [0.45, [-12, 0, 0]], [0.6, [28, 0, 0]], [0.8, [14, 0, 0]])},
                    head: {r: K([0, [0, 0, 0]], [0.45, [-25, 0, 0]], [0.6, [15, 0, 0]], [0.8, [0, 0, 0]])},
                    jaw: {r: K([0, [5, 0, 0]], [0.45, [20, 0, 0]], [0.6, [60, 0, 0]], [0.8, [5, 0, 0]])}})
            }
        };

        // the brute (second version, part B3e): no golem but a heap of sick flesh. Pale dead skin over raw muscle, a hunch of
        // pulsing tumours, a ridge of vertebrae through the skin, ribs breaking out of its left side, two tendrils from the back;
        // the right arm swelled into a club with bone shards and a clawed fist, the left one long and thin with claws; a low skull
        // with three eyes and a jaw full of separate teeth. Every growth moves on its own.
        const PALE = {base: '#857068', dark: '#6a544c', light: '#9c8880', vein: '#5a1e22', spots: ['#8a2c28', '#6e2c26']};
        const MUSCLE = {base: '#8a2c28', dark: '#6c1f1d', light: '#a4423a', vein: '#4a1214', spots: ['#c25a4c']};
        const TUMOR = {base: '#9a4a3e', dark: '#74342c', light: '#b86a58', vein: '#5a1e22', spots: ['#c8b84a', '#d8c86a'], spotChance: 0.5};
        const tooth = (x, y, z, h) => fang(x + 0.3, y, Math.round(h + 0.5) - 0.5, z + 0.3);
        /** A bone, claw or tooth-like growth: whole pixels on the texture (part B3i), kept where it was by its middle. */
        const bone = (o, s, rot, pivot) => {
            const size = s.map(v => Math.max(1, Math.round(v)));
            const origin = o.map((v, k) => v + (s[k] - size[k]) / 2);
            return C(origin, size, {c: '#d8cfb5', n: 0.09, edge: false}, rot ? {rot, pivot} : undefined);
        };
        SPECS.brute = {
            res: 128, seed: 1653, noCoplanarFaces: true,
            bones: [
                {name: 'root', pivot: [0, 0, 0], cubes: []},
                {name: 'hips', parent: 'root', pivot: [0, 13, 0], cubes: [O([-6, 10, -3.5], [12, 6, 8], PALE)]},
                {name: 'belly', parent: 'hips', pivot: [0, 15, -3], cubes: [O([-4.5, 11, -6], [9, 7, 4], MUSCLE, {f: [[3, 2, 2, 1, '#4a1214'], [5, 4, 1, 2, '#4a1214']]})]},
                {name: 'right_leg', parent: 'hips', pivot: [-4, 12, 0], cubes: [O([-7, 5.5, -2.5], [5.5, 7, 5.5], PALE)]},
                {name: 'right_shin', parent: 'right_leg', pivot: [-4.25, 6, 0], cubes: [O([-6.5, 1, -2.25], [4.5, 5.5, 5], PALE)]},
                {name: 'right_foot', parent: 'right_shin', pivot: [-4.25, 1.5, 0], cubes: [O([-7, 0.2, -4.5], [5.5, 2, 6.5], PALE),
                    bone([-6.8, 0, -5.6], [1, 1.2, 1.5]), bone([-4.75, 0, -5.8], [1, 1.2, 1.7]), bone([-2.7, 0, -5.6], [1, 1.2, 1.5])]},
                {name: 'left_leg', parent: 'hips', pivot: [4, 12, 0], cubes: [O([1.5, 5.5, -2.5], [5.5, 7, 5.5], MUSCLE)]},
                {name: 'left_shin', parent: 'left_leg', pivot: [4.25, 6, 0], cubes: [O([2, 1, -2.25], [4.5, 5.5, 5], PALE)]},
                {name: 'left_foot', parent: 'left_shin', pivot: [4.25, 1.5, 0], cubes: [O([1.5, 0.2, -4.5], [5.5, 2, 6.5], PALE),
                    bone([1.8, 0, -5.6], [1, 1.2, 1.5]), bone([3.75, 0, -5.8], [1, 1.2, 1.7]), bone([5.8, 0, -5.6], [1, 1.2, 1.5])]},
                {name: 'torso', parent: 'hips', pivot: [0, 15, 0], cubes: [
                    O([-8, 15, -5], [16, 13, 10], PALE, {f: [[4, 3, 2, 1, '#a4423a'], [9, 6, 3, 1, '#6c1f1d']], k: [[3, 4, 1, 3, '#a4423a'], [11, 7, 2, 1, '#a4423a']]}),
                    O([-7, 26, -2], [14, 6, 9], MUSCLE),
                    // the ribs breaking out of the left side, and the vertebrae through the back
                    bone([7.6, 18, -3.6], [1.2, 6, 0.8]), bone([7.6, 18.5, -1.4], [1.2, 5.5, 0.8]), bone([7.6, 19, 0.8], [1.2, 5, 0.8]),
                    bone([-0.75, 31.6, 0.5], [1.5, 2.2, 1.5]), bone([-0.75, 31.4, 3.2], [1.5, 2.4, 1.5]), bone([-0.75, 30.6, 5.6], [1.5, 2, 1.5]),
                    bone([-0.75, 24.3, 4.6], [1.5, 1.5, 1.6]), bone([-0.75, 21.5, 4.6], [1.5, 1.5, 1.6]), bone([-0.75, 18.5, 4.6], [1.5, 1.5, 1.6])]},
                {name: 'tumor_1', parent: 'torso', pivot: [4.5, 24, 5], cubes: [O([2.5, 22, 4.5], [4, 3.8, 3.5], TUMOR)]},
                {name: 'tumor_2', parent: 'torso', pivot: [-5.25, 20, 5], cubes: [O([-7, 18.5, 4.3], [3.5, 3.5, 3], TUMOR)]},
                {name: 'tumor_3', parent: 'torso', pivot: [-7.5, 30, -1], cubes: [O([-9.5, 28.5, -3.2], [4, 3.3, 4], TUMOR)]},
                {name: 'tendril_1', parent: 'torso', pivot: [-2.5, 29, 6], cubes: [O([-3, 28.5, 6.2], [1, 1, 6], MUSCLE, {}, {rot: [-35, 10, 0], pivot: [-2.5, 29, 6]})]},
                {name: 'tendril_2', parent: 'torso', pivot: [3, 27, 6], cubes: [O([2.5, 26.5, 6.2], [1, 1, 5], MUSCLE, {}, {rot: [-20, -15, 0], pivot: [3, 27, 6]})]},
                {name: 'head', parent: 'torso', pivot: [0, 27, -5], cubes: [
                    // whole pixels (part B3k): a head of 6.5 left a row of its texture empty, seen in game as a hole
                    O([-3.5, 24, -11], [7, 7, 7], PALE, {f: [[1, 1, 1, 1, EYE], [4, 0, 1, 1, EYE], [5, 2, 1, 1, EYE]], tp: [[2, 1, 2, 2, '#a4423a']]}),
                    O([1.5, 29.5, -9.5], [3, 2.5, 3], TUMOR),
                    tooth(-2.8, 23.7, -11.4, 1.5), tooth(-1.6, 23.9, -11.4, 1.1), tooth(-0.3, 23.5, -11.4, 1.7), tooth(1.0, 23.8, -11.4, 1.2), tooth(2.2, 23.6, -11.4, 1.5)]},
                {name: 'jaw', parent: 'head', pivot: [0, 25, -6], cubes: [
                    O([-3, 22.1, -11.5], [6, 2, 6], MUSCLE),
                    tooth(-2.05, 24.05, -11.2, 1.2), tooth(-0.75, 24.05, -11.2, 0.9), tooth(0.55, 24.05, -11.2, 1.3), tooth(1.65, 24.05, -11.2, 1.0)]},
                {name: 'right_arm', parent: 'torso', pivot: [-9, 26, 0], cubes: [
                    O([-16.5, 16, -4], [9, 11.8, 8], PALE, {f: [[2, 3, 3, 2, '#a4423a']], sd: [[3, 5, 2, 3, '#8a2c28']]}),
                    bone([-15.5, 17, 3.6], [1.5, 1.5, 3], [25, 0, 0], [-14.75, 17.75, 4]), bone([-17, 21, -1], [2.5, 1.5, 1.5])]},
                {name: 'right_forearm', parent: 'right_arm', pivot: [-12, 17, 0], cubes: [
                    O([-18.5, 4, -5.5], [10.8, 14, 11], MUSCLE, {f: [[2, 3, 3, 2, '#857068'], [6, 8, 2, 3, '#857068']]}),
                    bone([-17.5, 13, -6.4], [1.5, 3, 1.5], [-30, 0, 0], [-16.75, 14.5, -5.6]), bone([-13, 15, -6.6], [1.5, 2.5, 1.5], [-25, 0, 0], [-12.25, 16, -5.8]),
                    bone([-19.3, 9, -1], [1.5, 1.5, 3], [0, 0, 30], [-18.5, 9.75, 0.5])]},
                {name: 'right_hand', parent: 'right_forearm', pivot: [-13, 4.5, -1], cubes: [
                    O([-18, 1, -6], [10, 3.5, 10], PALE, {bt: [[2, 2, 2, 2, '#6a544c'], [6, 5, 2, 2, '#6a544c']]}),
                    bone([-17.6, 0.1, -7.6], [1.6, 2.5, 2]), bone([-15, -0.2, -7.9], [1.6, 2.8, 2.2]), bone([-12.4, -0.1, -7.8], [1.6, 2.7, 2.1]), bone([-9.9, 0.2, -7.4], [1.5, 2.4, 1.9]),
                    bone([-17.4, 3.8, -6.8], [1.2, 1.4, 1.2]), bone([-14.6, 4.1, -6.9], [1.2, 1.4, 1.2]), bone([-11.8, 4.0, -6.8], [1.2, 1.4, 1.2])]},
                {name: 'left_arm', parent: 'torso', pivot: [8.5, 26, 0], cubes: [O([7.5, 15.2, -2.5], [4.5, 11.8, 5], MUSCLE)]},
                {name: 'left_forearm', parent: 'left_arm', pivot: [9.75, 15.5, 0], cubes: [O([7.75, 4, -2.25], [4, 12, 4.5], PALE)]},
                {name: 'left_hand', parent: 'left_forearm', pivot: [9.75, 4.5, -1], cubes: [
                    bone([7.9, 0.4, -2.8], [1, 4, 1], [-15, 0, 0], [8.4, 4.4, -2.3]), bone([9.3, 0, -3.1], [1, 4.4, 1], [-15, 0, 0], [9.8, 4.4, -2.6]),
                    bone([10.7, 0.4, -2.8], [1, 4, 1], [-15, 0, 0], [11.2, 4.4, -2.3])]}
            ],
            animations: (() => {
                const T = 'torso';
                const pulse = (length, phase, amount) => loopOf(length, t => { const k = R2(1 + amount * (0.5 + 0.5 * Math.sin(2 * Math.PI * (t + phase)))); return [k, k, k]; });
                const living = length => ({
                    tumor_1: {s: pulse(length, 0, 0.14)}, tumor_2: {s: pulse(length, 0.33, 0.16)}, tumor_3: {s: pulse(length, 0.66, 0.12)},
                    belly: {s: loopOf(length, t => [1, 1, R2(1 + 0.08 * Math.sin(2 * Math.PI * t))])},
                    tendril_1: {r: loopOf(length, t => [R2(12 * Math.sin(2 * Math.PI * t)), R2(18 * Math.sin(2 * Math.PI * (t + 0.2))), 0])},
                    tendril_2: {r: loopOf(length, t => [R2(10 * Math.sin(2 * Math.PI * (t + 0.5))), R2(-15 * Math.sin(2 * Math.PI * (t + 0.7))), 0])}});
                return {
                    'animation.brute.idle': {length: 3, loop: 'loop', bones: Object.assign(living(3), {
                        [T]: {r: loopOf(3, t => [R2(24 + 2 * Math.sin(2 * Math.PI * t)), 0, R2(1.5 * Math.sin(2 * Math.PI * (t + 0.25)))]), p: loopOf(3, t => [0, R2(-0.4 + 0.4 * Math.cos(2 * Math.PI * t)), 0])},
                        // a sudden twitch of the head now and then
                        head: {r: K([0, [-18, 0, 0]], [1.6, [-18, 0, 0]], [1.7, [-14, 18, 10]], [1.85, [-18, -6, -4]], [2.1, [-18, 0, 0]], [3, [-18, 0, 0]])},
                        jaw: {r: loopOf(3, t => [R2(8 + 8 * Math.sin(4 * Math.PI * t)), 0, 0])},
                        right_arm: {r: loopOf(3, t => [R2(-22 + 3 * Math.sin(2 * Math.PI * t)), 0, 5])},
                        right_forearm: {r: loopOf(3, t => [R2(-6 - 3 * Math.sin(2 * Math.PI * t)), 0, 0])},
                        right_hand: {r: loopOf(3, t => [R2(4 + 6 * Math.sin(2 * Math.PI * (t + 0.3))), 0, 0])},
                        left_arm: {r: loopOf(3, t => [R2(-18 + 3 * Math.sin(2 * Math.PI * (t + 0.5))), 0, -6])},
                        left_hand: {r: loopOf(3, t => [R2(-10 * (0.5 + 0.5 * Math.sin(6 * Math.PI * t))), 0, 0])}})},
                    'animation.brute.walk': (() => {
                        const g = ikGait({length: 1.6, thigh: 6.5, shin: 5, rest: 11.5, hip: 10.9, stride: 3.2, lift: 2.2, stance: 0.6, bob: 0.9,
                            sway: 6, pelvis: 6, twist: 5, lean: 26, hips: 'hips', torso: T, feet: true,
                            arms: {right_arm: [24, -16, 1, 0.08], left_arm: [18, -14, -1, 0.08]},
                            forearms: {right_forearm: [8, 14, 1], left_forearm: [12, 14, -1]}});
                        Object.assign(g.bones, living(1.6), {
                            head: {r: loopOf(1.6, t => [R2(-20 + 4 * Math.sin(4 * Math.PI * t)), R2(6 * Math.sin(2 * Math.PI * t)), 0])},
                            jaw: {r: loopOf(1.6, t => [R2(10 + 8 * Math.sin(2 * Math.PI * t)), 0, 0])}});
                        return g;
                    })(),
                    'animation.brute.attack': move(0.9, {
                        [T]: {r: K([0, [24, 0, 0]], [0.35, [-4, 18, 0]], [0.5, [40, -12, 0]], [0.9, [24, 0, 0]])},
                        right_arm: {r: K([0, [-22, 0, 5]], [0.35, [-170, 0, 15]], [0.5, [-35, 0, 0]], [0.9, [-22, 0, 5]])},
                        right_forearm: {r: K([0, [-6, 0, 0]], [0.35, [-35, 0, 0]], [0.5, [0, 0, 0]], [0.9, [-6, 0, 0]])},
                        right_hand: {r: K([0, [4, 0, 0]], [0.35, [-30, 0, 0]], [0.5, [25, 0, 0]], [0.9, [4, 0, 0]])},
                        hips: {p: K([0, [0, 0, 0]], [0.35, [0, 0.5, 1]], [0.5, [0, -1.5, -1.5]], [0.9, [0, 0, 0]])},
                        head: {r: K([0, [-18, 0, 0]], [0.35, [-30, 0, 0]], [0.5, [-5, 0, 0]], [0.9, [-18, 0, 0]])},
                        jaw: {r: K([0, [6, 0, 0]], [0.35, [45, 0, 0]], [0.9, [6, 0, 0]])}}),
                    'animation.brute.roar': move(0.85, {
                        [T]: {r: K([0, [24, 0, 0]], [0.3, [-14, 0, 0]], [0.65, [-16, 0, 0]], [0.85, [26, 0, 0]])},
                        head: {r: K([0, [-18, 0, 0]], [0.3, [-45, 0, 0]], [0.45, [-45, 8, 6]], [0.65, [-45, -8, -6]], [0.85, [-18, 0, 0]])},
                        jaw: {r: K([0, [6, 0, 0]], [0.3, [65, 0, 0]], [0.65, [65, 0, 0]], [0.85, [6, 0, 0]])},
                        right_arm: {r: K([0, [-22, 0, 5]], [0.3, [-45, 0, 55]], [0.65, [-45, 0, 55]], [0.85, [20, 0, 10]])},
                        left_arm: {r: K([0, [-18, 0, -6]], [0.3, [-45, 0, -60]], [0.65, [-45, 0, -60]], [0.85, [20, 0, -10]])},
                        tumor_1: {s: K([0, [1, 1, 1]], [0.4, [1.3, 1.3, 1.3]], [0.85, [1, 1, 1]])},
                        tumor_2: {s: K([0, [1, 1, 1]], [0.45, [1.3, 1.3, 1.3]], [0.85, [1, 1, 1]])},
                        tumor_3: {s: K([0, [1, 1, 1]], [0.5, [1.25, 1.25, 1.25]], [0.85, [1, 1, 1]])},
                        tendril_1: {r: K([0, [0, 0, 0]], [0.4, [-40, 30, 0]], [0.85, [0, 0, 0]])},
                        tendril_2: {r: K([0, [0, 0, 0]], [0.4, [-40, -30, 0]], [0.85, [0, 0, 0]])}}),
                    'animation.brute.charge': {length: 0.5, loop: 'loop', bones: Object.assign(ikGait({length: 0.5, thigh: 6.5, shin: 5, rest: 11.5, hip: 10.3,
                        stride: 5, lift: 3.2, stance: 0.45, bob: 1.6, sway: 3, pelvis: 4, twist: 2, lean: 45, hips: 'hips', torso: T, feet: true,
                        arms: {right_arm: [10, 25, 1, 0], left_arm: [10, 25, -1, 0]}, forearms: {right_forearm: [5, 5, 1], left_forearm: [5, 5, -1]}}).bones,
                        living(0.5), {head: {r: loopOf(0.5, () => [-40, 0, 0])}, jaw: {r: loopOf(0.5, t => [R2(35 + 10 * Math.sin(2 * Math.PI * t)), 0, 0])}})},
                    'animation.brute.leap': move(0.5, {
                        hips: {p: K([0, [0, 0, 0]], [0.15, [0, -3, 0]], [0.3, [0, 1, 0]], [0.5, [0, 1, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [0.15, [-40, 0, 0]], [0.3, [15, 0, 0]], [0.5, [-30, 0, 0]])},
                        left_leg: {r: K([0, [0, 0, 0]], [0.15, [-40, 0, 0]], [0.3, [15, 0, 0]], [0.5, [-30, 0, 0]])},
                        right_shin: {r: K([0, [0, 0, 0]], [0.15, [70, 0, 0]], [0.3, [5, 0, 0]], [0.5, [50, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [0.15, [70, 0, 0]], [0.3, [5, 0, 0]], [0.5, [50, 0, 0]])},
                        [T]: {r: K([0, [24, 0, 0]], [0.15, [45, 0, 0]], [0.3, [-5, 0, 0]], [0.5, [10, 0, 0]])},
                        right_arm: {r: K([0, [-22, 0, 5]], [0.15, [30, 0, 10]], [0.3, [-150, 0, 20]], [0.5, [-150, 0, 20]])},
                        left_arm: {r: K([0, [-18, 0, -6]], [0.15, [30, 0, -10]], [0.3, [-150, 0, -20]], [0.5, [-150, 0, -20]])}}, true),
                    'animation.brute.land': move(0.7, {
                        hips: {p: K([0, [0, 1, 0]], [0.1, [0, -3.5, 0]], [0.7, [0, 0, 0]])},
                        right_leg: {r: K([0, [-30, 0, 0]], [0.1, [-45, 0, 0]], [0.7, [0, 0, 0]])},
                        left_leg: {r: K([0, [-30, 0, 0]], [0.1, [-45, 0, 0]], [0.7, [0, 0, 0]])},
                        right_shin: {r: K([0, [50, 0, 0]], [0.1, [80, 0, 0]], [0.7, [0, 0, 0]])},
                        left_shin: {r: K([0, [50, 0, 0]], [0.1, [80, 0, 0]], [0.7, [0, 0, 0]])},
                        [T]: {r: K([0, [10, 0, 0]], [0.1, [55, 0, 0]], [0.7, [24, 0, 0]])},
                        right_arm: {r: K([0, [-150, 0, 20]], [0.1, [-25, 0, 25]], [0.7, [-22, 0, 5]])},
                        left_arm: {r: K([0, [-150, 0, -20]], [0.1, [-25, 0, -25]], [0.7, [-18, 0, -6]])}}),
                    'animation.brute.stun': move(2.0, {
                        [T]: {r: K([0, [24, 0, 0]], [0.2, [0, 0, 0]], [0.6, [28, 0, 12]], [1.0, [28, 0, -12]], [1.4, [28, 0, 10]], [1.8, [26, 0, -4]], [2.0, [24, 0, 0]])},
                        head: {r: K([0, [-18, 0, 0]], [0.6, [0, 20, 0]], [1.0, [0, -20, 0]], [1.4, [0, 15, 0]], [2.0, [-18, 0, 0]])},
                        right_arm: {r: K([0, [-22, 0, 5]], [0.6, [5, 0, 20]], [1.4, [5, 0, 20]], [2.0, [-22, 0, 5]])},
                        left_arm: {r: K([0, [-18, 0, -6]], [0.6, [5, 0, -20]], [1.4, [5, 0, -20]], [2.0, [-18, 0, -6]])}}),
                    // death: it reels back, drops to its knees and falls forward onto its belly, arms sprawled; the growths go limp
                    'animation.brute.dead': move(2.2, {
                        root: {r: K([0, [0, 0, 0]], [0.35, [-8, 0, 4]], [0.9, [6, 0, -2]], [1.6, [84, 0, -4]], [1.8, [80, 0, -4]], [2.2, [82, 0, -4]]),
                            p: K([0, [0, 0, 0]], [0.9, [0, -2, 0]], [1.6, [0, 4.5, 15]], [2.2, [0, 4.5, 15]])},
                        hips: {p: K([0, [0, 0, 0]], [0.9, [0, -5, 0]], [2.2, [0, -2, 0]])},
                        right_leg: {r: K([0, [0, 0, 0]], [0.9, [-55, 0, 4]], [1.6, [-10, 0, 8]], [2.2, [-12, 0, 8]])},
                        left_leg: {r: K([0, [0, 0, 0]], [0.9, [-45, 0, -4]], [1.6, [-4, 0, -10]], [2.2, [-6, 0, -10]])},
                        right_shin: {r: K([0, [0, 0, 0]], [0.9, [95, 0, 0]], [1.6, [40, 0, 0]], [2.2, [45, 0, 0]])},
                        left_shin: {r: K([0, [0, 0, 0]], [0.9, [85, 0, 0]], [1.6, [25, 0, 0]], [2.2, [30, 0, 0]])},
                        [T]: {r: K([0, [24, 0, 0]], [0.35, [-6, 0, 0]], [0.9, [30, 0, 0]], [1.6, [0, 0, 0]], [2.2, [2, 0, 0]])},
                        head: {r: K([0, [-18, 0, 0]], [0.35, [-40, 0, 0]], [1.6, [-30, 30, 10]], [2.2, [-32, 34, 12]])},
                        jaw: {r: K([0, [6, 0, 0]], [0.35, [55, 0, 0]], [2.2, [30, 0, 0]])},
                        right_arm: {r: K([0, [-22, 0, 5]], [0.35, [10, 0, 30]], [1.6, [-150, 0, 20]], [2.2, [-155, 0, 22]])},
                        left_arm: {r: K([0, [-18, 0, -6]], [0.35, [10, 0, -35]], [1.6, [-120, 0, -40]], [2.2, [-124, 0, -42]])},
                        right_hand: {r: K([0, [0, 0, 0]], [2.2, [30, 0, 0]])},
                        tendril_1: {r: K([0, [0, 0, 0]], [2.2, [35, 20, 0]])},
                        tendril_2: {r: K([0, [0, 0, 0]], [2.2, [40, -20, 0]])},
                        tumor_1: {s: K([0, [1, 1, 1]], [2.2, [0.9, 0.9, 0.9]])}}, true)
                };
            })()
        };
    })();

    // ------------------------------------------------------------------ pilot weapons, second version (1.6.0, part B3f)
    // After the owner's reference: dark gunmetal parts set apart from each other, the sides banded lighter and darker in rows,
    // a top rail with notches, vents in the handguards, a skeleton stock, real sights, a glowing ammo counter on the shotgun.
    // Whole pixels everywhere (fractional sizes take in their neighbours' colours on the texture). Same bones as before, so the
    // animations and the code keep working.
    (() => {
        const GM = '#5a5e64', GL = '#8d9299', GD = '#35383c', GS = '#a6acb3', BORE = '#0a0a0b', GLOW = '#dff4ff', LENS = '#7fd8ff', RED = '#d23a2e';
        const M = (base, more) => Object.assign({c: base, n: 0.07}, more || {});
        /** Rows of light and dark along a side, as on the reference: the top lit, a dark seam, a lighter band. */
        const bands = (len, h) => {
            const ops = [[0, 0, len, 1, GL]];
            if (h >= 3) ops.push([0, h - 1, len, 1, GD]);
            if (h >= 4) ops.push([0, Math.floor(h / 2), len, 1, '#70747a']);
            return ops;
        };
        const notches = (len, w) => Array.from({length: Math.floor(len / 2)}, (_, i) => [0, i * 2, w, 1, GL]);
        const vent = (x0, count, y) => Array.from({length: count}, (_, i) => [x0 + i * 2, y, 1, 1, GD]);

        SPECS.assault_rifle = Object.assign(weaponSpec(911, {
            body: [
                C([-1.5, 0, -6], [3, 4, 13], M(GM, {sd: [...bands(13, 4), [6, 1, 3, 1, GD], [7, 2, 1, 1, GS]], tp: [[1, 0, 1, 13, GD]]})),
                C([-1, 3, -15], [2, 2, 20], M(GD, {tp: notches(20, 2), sd: [[0, 0, 20, 1, GL]]})),
                C([-2, 0.5, -16], [4, 3, 11], M(GM, {sd: [...bands(11, 3), ...vent(1, 5, 1)], bt: [[1, 1, 2, 9, GD]]})),
                C([-0.5, 1.5, -22], [1, 1, 7], M(GD, {edge: false})),
                C([-1.5, 0.5, -25], [3, 3, 4], M(GM, {f: [[1, 1, 1, 1, BORE]], sd: [[1, 1, 2, 1, GD]], edge: false})),
                C([-1, -5, 1], [2, 5, 3], M(GD, {sd: [[0, 1, 3, 1, '#45484d'], [0, 3, 3, 1, '#45484d']]}), {rot: [15, 0, 0], pivot: [0, 0, 2.5]}),
                C([-1.5, -2, -2], [3, 1, 4], M(GD, {edge: false})),
                C([-1, -4, -13], [2, 5, 2], M(GD, {sd: [[0, 1, 2, 1, '#45484d'], [0, 3, 2, 1, '#45484d']]})),
                C([-1, 1, 6], [2, 2, 5], M(GM, {sd: bands(5, 2)})),
                C([-0.5, 2.2, 10], [1, 1, 7], M(GD)),
                C([-0.5, -2.2, 10], [1, 1, 7], M(GD)),
                C([-1, -1, 15.5], [2, 3, 1], M(GM)),
                C([-1.5, -3, 16], [3, 6, 2], M(GD, {k: [[1, 1, 1, 4, '#45484d']]}))
            ],
            magazine: {pivot: [0, 0, -3.5], cubes: [C([-1, -7, -5], [2, 7, 3], M(GD, {sd: [[0, 0, 3, 1, GL], [0, 3, 3, 1, '#45484d'], [0, 5, 3, 1, '#45484d']]}), {rot: [-15, 0, 0], pivot: [0, 0, -3.5], inflate: -0.1})]},
            bolt: {pivot: [-2.5, 2.5, 0], cubes: [C([-2.4, 2, -1], [1, 1, 2], M(GS, {edge: false}))]},
            sight: {pivot: [0, 5, -2], cubes: [
                C([-1.5, 4.5, -4], [3, 3, 4], M(GM, {f: [[1, 1, 1, 1, LENS]], k: [[1, 1, 1, 1, LENS]], tp: [[1, 1, 1, 2, GD]]})),
                C([-0.5, 4.5, -13], [1, 2, 1], M(GL, {edge: false}))]},
            muzzle: [0, 2, -25],
            ejection: [-2, 3, 1]
        }, magazineAnimations(2.5, 2.5, 0.8)), {res: 128});

        SPECS.battle_rifle = Object.assign(weaponSpec(912, {
            body: [
                C([-1.5, 0, -8], [3, 4, 16], M(GM, {sd: [...bands(16, 4), [8, 1, 3, 1, GD]], tp: [[1, 0, 1, 16, GD]]})),
                C([-1, 3, -16], [2, 2, 20], M(GD, {tp: notches(20, 2), sd: [[0, 0, 20, 1, GL]]})),
                C([-2, 0.5, -20], [4, 3, 13], M(GM, {sd: [...bands(13, 3), ...vent(1, 6, 1)], bt: [[1, 1, 2, 11, GD]]})),
                C([-0.5, 1.5, -30], [1, 1, 11], M(GD, {edge: false})),
                C([-1.5, 0.5, -35], [3, 3, 6], M(GM, {f: [[1, 1, 1, 1, BORE]], sd: [[1, 1, 1, 1, GD], [3, 1, 1, 1, GD]], edge: false})),
                C([-1, -5, 2], [2, 5, 3], M(GD, {sd: [[0, 1, 3, 1, '#45484d'], [0, 3, 3, 1, '#45484d']]}), {rot: [15, 0, 0], pivot: [0, 0, 3.5]}),
                C([-1.5, -2, -0.8], [3, 1, 4], M(GD, {edge: false})),
                // the folded bipod under the handguard
                C([-2.5, -1, -19], [1, 1, 8], M(GD, {edge: false})),
                C([1.5, -1, -19], [1, 1, 8], M(GD, {edge: false})),
                C([-1, -0.7, -20], [2, 1, 2], M(GM, {edge: false})),
                // the solid stock with a cheek rest
                C([-1.5, -1, 7], [3, 4, 10], M(GM, {sd: bands(10, 4)}), {inflate: -0.1}),
                C([-1, 2.5, 9], [2, 2, 5], M(GD, {edge: false})),
                C([-1.5, -4, 16], [3, 8, 2], M(GD, {k: [[1, 1, 1, 6, '#45484d']]}))
            ],
            magazine: {pivot: [0, 0, -2], cubes: [C([-1, -5, -5], [2, 6, 4], M(GD, {sd: [[0, 0, 4, 1, GL], [0, 3, 4, 1, '#45484d']]}))]},
            bolt: {pivot: [-2.5, 2.5, 2], cubes: [C([-2.4, 2, 1], [1, 1, 3], M(GS, {edge: false}))]},
            sight: {pivot: [0, 6, -2], cubes: [
                C([-1.5, 6, -8.5], [3, 3, 12], M(GD, {sd: [[0, 0, 12, 1, GL]], tp: [[1, 3, 1, 2, GM], [1, 7, 1, 2, GM]]})),
                C([-2, 5.5, -11], [4, 4, 3], M(GM, {f: [[1, 1, 2, 2, LENS], [1, 1, 1, 1, '#c8f6ff']]})),
                C([-2, 5.5, 3], [4, 4, 2], M(GM, {k: [[1, 1, 2, 2, '#2a5a6a']]})),
                C([-0.5, 4.2, -5], [1, 3, 1], M(GM, {edge: false})),
                C([-0.5, 4.2, 1], [1, 3, 1], M(GM, {edge: false}))]},
            muzzle: [0, 2, -35],
            ejection: [-2, 3, 0]
        }, magazineAnimations(2.5, 3, 1.4)), {res: 128});

        // the shotgun: a box with a glowing ammo counter on each side and a red shell light, a pump under the barrel
        const counter = [[3, 1, 6, 3, '#0c0e10'], [4, 2, 1, 1, GLOW], [6, 2, 1, 1, GLOW], [4, 1, 1, 1, '#9fb4c0'], [6, 1, 1, 1, '#9fb4c0'], [11, 1, 1, 1, RED]];
        const oldShotgun = SPECS.combat_shotgun.animations;
        SPECS.combat_shotgun = Object.assign(weaponSpec(913, {
            body: [
                C([-2, 0, -7], [4, 5, 13], M(GM, {sd: [...bands(13, 5), ...counter], tp: [[1, 0, 2, 13, GD]]})),
                C([-1, 4, -10], [2, 2, 14], M(GD, {tp: notches(14, 2), sd: [[0, 0, 14, 1, GL]]})),
                C([-1, 1.8, -18], [2, 2, 12], M(GD, {sd: [[0, 0, 12, 1, GL]]})),
                C([-1, -1, -16], [2, 2, 10], M(GM, {sd: [[0, 1, 10, 1, GD]]})),
                C([-1.5, 1.5, -20], [3, 3, 3], M(GM, {f: [[1, 1, 1, 1, BORE]], edge: false})),
                C([-1, -5, 1], [2, 5, 3], M(GD, {sd: [[0, 1, 3, 1, '#45484d'], [0, 3, 3, 1, '#45484d']]}), {rot: [15, 0, 0], pivot: [0, 0, 2.5]}),
                C([-1.5, -2, -1], [3, 1, 4], M(GD, {edge: false})),
                C([-1.5, 0.3, 5], [3, 3, 10], M(GM, {sd: bands(10, 3)})),
                C([-2, -2, 14], [4, 6, 2], M(GD, {k: [[1, 1, 2, 4, '#45484d']]}))
            ],
            magazine: {pivot: [0, -0.5, -8], cubes: [C([-0.5, -0.8, -9.3], [1, 1, 2], {c: '#c9352b', n: 0.03, k: [[0, 0, 1, 1, GL]], edge: false})]},
            bolt: {pivot: [0, 0, -11], cubes: [C([-1.5, -1.5, -14], [3, 3, 6], M(GM, {sd: [[1, 0, 1, 3, GD], [3, 0, 1, 3, GD], [5, 0, 1, 3, GD]], bt: [[1, 1, 1, 4, GD]]}))]},
            sight: {pivot: [0, 6, -9], cubes: [C([-0.5, 5.8, -9], [1, 1, 1], M(GL, {edge: false})), C([-1.5, 5.8, 2], [3, 1, 1], M(GD, {edge: false}))]},
            muzzle: [0, 3, -20],
            ejection: [-2.5, 3, 1]
        }, oldShotgun), {res: 128});
    })();

    // ------------------------------------------------------------------ pilot weapons, third version (1.6.0, part B3g)
    // The owner: bigger, so they feel big and heavy; the assault rifle's stock must be one piece with the gun. About half as big
    // again as before, thicker everywhere; the sides banded from the size of each part. Same bones as before.
    (() => {
        const GM = '#5a5e64', GL = '#8d9299', GD = '#35383c', GS = '#a6acb3', BORE = '#0a0a0b', GLOW = '#dff4ff', LENS = '#7fd8ff', RED = '#d23a2e';
        const SEAM = '#45484d', MID = '#70747a';
        const bands = (len, h) => {
            const ops = [[0, 0, len, 1, GL]];
            if (h >= 3) ops.push([0, h - 1, len, 1, GD]);
            if (h >= 4) ops.push([0, Math.floor(h / 2), len, 1, MID]);
            if (h >= 6) ops.push([0, Math.floor(h / 2) + 1, len, 1, SEAM]);
            return ops;
        };
        /** A part whose sides are banded to its own size; more: extra paint (its sd is added to the bands); opts: rot, pivot, inflate. */
        const part = (o, s, base, more, opts) => {
            const p = Object.assign({c: base, n: 0.07}, more || {});
            p.sd = bands(s[2], s[1]).concat((more && more.sd) || []);
            return C(o, s, p, opts);
        };
        const plain = (o, s, base, more, opts) => C(o, s, Object.assign({c: base, n: 0.07}, more || {}), opts);
        const notches = (len, w) => Array.from({length: Math.floor(len / 2)}, (_, i) => [0, i * 2, w, 1, GL]);
        const vents = (x0, count, y, h) => Array.from({length: count}, (_, i) => [x0 + i * 2, y, 1, h || 2, GD]);
        const grip = (z, pivotZ) => plain([-1.5, -7, z], [3, 7, 4], GD, {sd: [[0, 1, 4, 1, SEAM], [0, 3, 4, 1, SEAM], [0, 5, 4, 1, SEAM]]}, {rot: [15, 0, 0], pivot: [0, 0, pivotZ]});

        SPECS.assault_rifle = Object.assign(weaponSpec(921, {
            body: [
                part([-2.5, 0, -9], [5, 6, 20], GM, {sd: [[9, 1, 4, 2, GD], [10, 2, 2, 1, GS]], tp: [[2, 0, 1, 20, GD]]}),
                plain([-1.5, 4.8, -22], [3, 2, 29], GD, {tp: notches(29, 3), sd: [[0, 0, 29, 1, GL]]}),
                part([-3, 1, -24], [6, 4, 16], GM, {sd: vents(2, 6, 1), bt: [[2, 1, 2, 14, GD]]}),
                plain([-1, 2, -33], [2, 2, 10], GD, {edge: false}),
                plain([-2, 1, -37], [4, 4, 5], GM, {f: [[1, 1, 2, 2, BORE]], sd: [[1, 1, 3, 1, GD], [1, 2, 3, 1, GD]]}),
                grip(2, 4),
                plain([-2, -3, -2], [4, 1, 6], GD, {edge: false}),
                plain([-1.5, -6, -20], [3, 8, 3], GD, {sd: [[0, 1, 3, 1, SEAM], [0, 3, 3, 1, SEAM], [0, 5, 3, 1, SEAM]]}),
                // the stock, one piece with the receiver: a neck out of its back, the body, a cheek riser and a rubber butt
                part([-2, 0.5, 10], [4, 5, 5], GM),
                part([-2.5, -2.5, 14], [5, 7, 8], GM, {tp: [[1, 1, 3, 6, GD]]}),
                plain([-1.5, 4, 15.3], [3, 1, 5], GD, {edge: false}),
                plain([-3, -4.5, 21], [6, 10, 2], GD, {k: [[1, 1, 4, 8, SEAM]]})
            ],
            magazine: {pivot: [0, 0, -5], cubes: [plain([-1.5, -10, -7], [3, 10, 4], GD, {sd: [[0, 0, 4, 1, GL], [0, 3, 4, 1, SEAM], [0, 6, 4, 1, SEAM]]}, {rot: [-15, 0, 0], pivot: [0, 0, -5], inflate: -0.1})]},
            bolt: {pivot: [-3.5, 3.5, 0], cubes: [plain([-3.4, 3, -1], [1, 1, 3], GS, {edge: false})]},
            sight: {pivot: [0, 7, -3], cubes: [
                plain([-2, 6.6, -6], [4, 4, 5], GM, {f: [[1, 1, 2, 2, LENS]], k: [[1, 1, 2, 2, LENS]], tp: [[1, 1, 2, 3, GD]]}),
                plain([-0.5, 6.6, -20], [1, 2, 1], GL, {edge: false})]},
            muzzle: [0, 3, -37],
            ejection: [-3, 4.5, 1]
        }, magazineAnimations(2.5, 3.5, 1.2)), {res: 128});

        SPECS.battle_rifle = Object.assign(weaponSpec(922, {
            body: [
                part([-2.5, 0, -12], [5, 6, 24], GM, {sd: [[12, 1, 4, 2, GD]], tp: [[2, 0, 1, 24, GD]]}),
                plain([-1.5, 4.8, -24], [3, 2, 30], GD, {tp: notches(30, 3), sd: [[0, 0, 30, 1, GL]]}),
                part([-3, 1, -30], [6, 4, 19], GM, {sd: vents(2, 8, 1), bt: [[2, 1, 2, 17, GD]]}),
                plain([-1, 2, -45], [2, 2, 16], GD, {edge: false}),
                plain([-2, 1, -52], [4, 4, 8], GM, {f: [[1, 1, 2, 2, BORE]], sd: [[1, 1, 1, 2, GD], [3, 1, 1, 2, GD], [5, 1, 1, 2, GD]]}),
                grip(3, 5),
                plain([-2, -3, -1], [4, 1, 6], GD, {edge: false}),
                // the folded bipod under the handguard
                plain([-4, -1.2, -29], [2, 2, 12], GD, {edge: false}),
                plain([2, -1.2, -29], [2, 2, 12], GD, {edge: false}),
                plain([-1.5, -1.2, -31], [3, 2, 3], GM, {edge: false}),
                // the solid stock with a cheek rest
                part([-2.5, -2, 11], [5, 6, 15], GM, {}, {inflate: -0.1}),
                plain([-1.5, 3.5, 13], [3, 3, 8], GD, {edge: false}),
                plain([-2.5, -6, 25], [5, 12, 3], GD, {k: [[1, 1, 3, 10, SEAM]]})
            ],
            magazine: {pivot: [0, 0, -5], cubes: [plain([-1.5, -8, -8], [3, 9, 6], GD, {sd: [[0, 0, 6, 1, GL], [0, 3, 6, 1, SEAM], [0, 6, 6, 1, SEAM]]})]},
            bolt: {pivot: [-3.5, 3.5, 3], cubes: [plain([-3.4, 3, 1], [1, 1, 4], GS, {edge: false})]},
            sight: {pivot: [0, 9, -3], cubes: [
                plain([-2, 9, -11.5], [4, 4, 18], GD, {sd: [[0, 0, 18, 1, GL]], tp: [[1, 4, 2, 3, GM], [1, 11, 2, 3, GM]]}),
                plain([-3, 8, -16], [6, 6, 4], GM, {f: [[1, 1, 4, 4, LENS], [1, 1, 2, 2, '#c8f6ff']]}),
                plain([-3, 8, 5], [6, 6, 3], GM, {k: [[1, 1, 4, 4, '#2a5a6a']]}),
                plain([-1, 6.6, -7], [2, 3, 2], GM, {edge: false}),
                plain([-1, 6.6, 1], [2, 3, 2], GM, {edge: false})]},
            muzzle: [0, 3, -52],
            ejection: [-3, 4.5, 0]
        }, magazineAnimations(2.5, 4, 1.8)), {res: 128});

        // the shotgun: a heavy box with a glowing ammo counter on each side and a red shell light, a big pump under the barrel
        const counter = [[4, 2, 9, 4, '#0c0e10'], [5, 3, 3, 1, GLOW], [5, 4, 1, 1, GLOW], [7, 4, 1, 1, GLOW], [9, 3, 3, 1, GLOW], [9, 4, 1, 1, GLOW], [11, 4, 1, 1, GLOW],
            [5, 5, 3, 1, '#9fb4c0'], [9, 5, 3, 1, '#9fb4c0'], [15, 2, 2, 1, RED]];
        const shotgunAnims = SPECS.combat_shotgun.animations;
        SPECS.combat_shotgun = Object.assign(weaponSpec(923, {
            body: [
                part([-3, 0, -10], [6, 8, 19], GM, {sd: counter, tp: [[2, 0, 2, 19, GD]]}),
                plain([-1.5, 7, -15], [3, 2, 21], GD, {tp: notches(21, 3), sd: [[0, 0, 21, 1, GL]]}),
                plain([-1.5, 3, -27], [3, 3, 18], GD, {sd: [[0, 0, 18, 1, GL]]}),
                plain([-1.5, -1.5, -24], [3, 3, 15], GM, {sd: [[0, 2, 15, 1, GD]]}),
                plain([-2, 2.5, -30], [4, 4, 4], GM, {f: [[1, 1, 2, 2, BORE]], edge: false}),
                grip(1, 3),
                plain([-2, -3, -2], [4, 1, 6], GD, {edge: false}),
                part([-2, 0.3, 8], [4, 5, 14], GM),
                plain([-3, -3, 21], [6, 9, 3], GD, {k: [[1, 1, 4, 7, SEAM]]})
            ],
            magazine: {pivot: [0, -0.5, -12], cubes: [C([-1, -1.3, -14.3], [2, 2, 3], {c: '#c9352b', n: 0.03, k: [[0, 0, 2, 2, GL]], edge: false})]},
            bolt: {pivot: [0, 0, -16], cubes: [plain([-2.5, -2.5, -21], [5, 5, 9], GM, {sd: [[1, 0, 1, 5, GD], [3, 0, 1, 5, GD], [5, 0, 1, 5, GD], [7, 0, 1, 5, GD]], bt: [[1, 1, 3, 7, GD]]})]},
            sight: {pivot: [0, 9, -14], cubes: [plain([-0.5, 8.8, -14], [1, 2, 1], GL, {edge: false}), plain([-2, 8.8, 3], [4, 1, 2], GD, {edge: false})]},
            muzzle: [0, 4.5, -30],
            ejection: [-3.5, 5, 1]
        }, shotgunAnims), {res: 128});
    })();

    // ------------------------------------------------------------------ manipulator (1.7.0, block B)
    // A robot arm of the multiblock workshops, a GeckoLib block: a round base on the floor, a turret that turns, an upright
    // lower arm, a forearm reaching forward (-Z, toward the machine under assembly) and a welding head pointing down. The
    // code turns the block to face the assembly zone; "work" swings the arm and shakes the head, "idle" sways the turret.
    (() => {
        const CASE = {c: '#d9a62e', n: 0.07, tp: [[1, 1, 2, 2, '#2a2d31']]};
        const FRAME = {c: '#4a4f56', n: 0.08};
        const DARK = {c: '#2a2d31', n: 0.06, edge: false};
        const TIP = {c: '#9fd8ff', n: 0.02, edge: false};
        SPECS.manipulator = {
            res: 64, seed: 1701, block: true, noCoplanarFaces: true,
            bones: [
                {name: 'base', parent: undefined, pivot: [0, 0, 0], cubes: [
                    C([-6, 0, -6], [12, 3, 12], Object.assign({}, FRAME, {tp: [[0, 0, 12, 1, '#d9a62e'], [0, 11, 12, 1, '#d9a62e']]})),
                    C([-4, 2, -4], [8, 3, 8], DARK)]},
                {name: 'turret', parent: 'base', pivot: [0, 4, 0], cubes: [
                    C([-3.5, 4, -3.5], [7, 4, 7], CASE)]},
                {name: 'lower_arm', parent: 'turret', pivot: [0, 7, 0], cubes: [
                    C([-2.5, 6, -2], [5, 3, 4], FRAME),
                    C([-1.5, 7, -1.5], [3, 10, 3], CASE)]},
                {name: 'forearm', parent: 'lower_arm', pivot: [0, 16, 0], cubes: [
                    C([-2, 15, -2], [4, 3, 4], FRAME),
                    C([-1, 15.5, -11], [2, 2, 10], CASE)]},
                {name: 'tool', parent: 'forearm', pivot: [0, 16.5, -10], cubes: [
                    C([-1.5, 13, -11.5], [3, 3.5, 3], FRAME),
                    C([-0.5, 11, -10.5], [1, 2.5, 1], TIP)]}
            ],
            animations: {
                'animation.manipulator.idle': {length: 4, loop: 'loop', bones: {
                    turret: {r: {'0.000': [0, 0, 0], '2.000': [0, 8, 0], '4.000': [0, 0, 0]}},
                    lower_arm: {r: {'0.000': [-5, 0, 0], '2.000': [-8, 0, 0], '4.000': [-5, 0, 0]}}}},
                'animation.manipulator.work': {length: 2, loop: 'loop', bones: {
                    turret: {r: {'0.000': [0, -20, 0], '0.500': [0, 0, 0], '1.000': [0, 20, 0], '1.500': [0, 0, 0], '2.000': [0, -20, 0]}},
                    lower_arm: {r: {'0.000': [-10, 0, 0], '0.500': [-25, 0, 0], '1.000': [-10, 0, 0], '1.500': [-25, 0, 0], '2.000': [-10, 0, 0]}},
                    forearm: {r: {'0.000': [10, 0, 0], '0.500': [25, 0, 0], '1.000': [10, 0, 0], '1.500': [25, 0, 0], '2.000': [10, 0, 0]}},
                    tool: {p: {'0.000': [0, 0, 0], '0.250': [0, -0.5, 0], '0.500': [0, 0, 0], '0.750': [0, -0.5, 0], '1.000': [0, 0, 0],
                        '1.250': [0, -0.5, 0], '1.500': [0, 0, 0], '1.750': [0, -0.5, 0], '2.000': [0, 0, 0]}}}}
            }
        };
    })();

    window.RebirthModels = {build, save, SPECS, coplanarFaces};
})();
