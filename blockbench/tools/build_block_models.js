// Builds the 3D models of the functional blocks (vanilla block model JSON) and their block states.
// Usage in Blockbench's console or through the MCP risky_eval tool:
//   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/build_block_models.js', 'utf8'));
//   RebirthBlockModels.saveAll();                         // writes models and block states into the mod's resources
//   RebirthBlockModels.open('assembly_station_on');       // opens a written model in Blockbench to look at it
//   RebirthBlockModels.saveProjects();                    // saves every model as blockbench/blocks/<id>.bbmodel
// Models look north. Parts stay inside the block (saveAll checks it, and that no faces overlap). Textures come from build_textures.js.
(function () {
    const fs = require('fs');
    const ROOT = 'E:/Projects/Project-Rebirth-mod/';
    const ASSETS = ROOT + 'src/main/resources/assets/projectrebirth/';
    const DIRS = ['north', 'south', 'west', 'east', 'up', 'down'];

    // Colours of block/machine_parts: 4x4 squares, the middle 2x2 of each is used.
    const SWATCHES = ['steel', 'mid', 'gun', 'dark', 'copper', 'brass', 'rubber', 'rust', 'cyan', 'green', 'red', 'yellow', 'orange', 'olive', 'white', 'hazard'];

    function sw(name) {
        const i = SWATCHES.indexOf(name);
        if (i < 0) {
            throw new Error('No swatch ' + name);
        }
        const x = (i % 4) * 4 + 1, y = Math.floor(i / 4) * 4 + 1;
        return {texture: '#parts', uv: [x, y, x + 2, y + 2]};
    }

    function tex(key, uv) {
        return {texture: '#' + key, uv: uv || [0, 0, 16, 16]};
    }

    /** A cube: the colour goes on all sides, the map replaces single sides with a colour name or a texture face. */
    function box(from, to, color, faces, extra) {
        const out = {from, to, faces: {}};
        for (const dir of DIRS) {
            const face = faces && faces[dir];
            out.faces[dir] = face ? (typeof face === 'string' ? sw(face) : face) : sw(color);
        }
        return Object.assign(out, extra || {});
    }

    const GLOW = {neoforge_data: {block_light: 15}};

    /** Removes faces that nothing can see, so that they cannot flicker against each other. */
    function without(element, ...dirs) {
        dirs.forEach(dir => delete element.faces[dir]);
        return element;
    }

    /** Body shared by the stations and the generator: plinth, frame, side and back panels, top plate. */
    function machine(accent) {
        return [
            box([0, 0, 0], [16, 2, 16], 'mid', {up: 'gun'}),
            without(box([1, 2, 1], [15, 13, 15], 'dark'), 'up', 'down'),
            box([0, 13, 0], [16, 14, 16], 'mid', {up: tex('top')}),
            // Frame posts.
            without(box([0, 2, 0], [2, 13, 2], 'steel'), 'up', 'down'),
            without(box([14, 2, 0], [16, 13, 2], 'steel'), 'up', 'down'),
            without(box([0, 2, 14], [2, 13, 16], 'steel'), 'up', 'down'),
            without(box([14, 2, 14], [16, 13, 16], 'steel'), 'up', 'down'),
            // Side panels and the back with the machine's colour.
            without(box([0.5, 3, 3], [1.5, 13, 13], 'gun', {west: tex('side', [3, 3, 13, 13])}), 'up'),
            without(box([14.5, 3, 3], [15.5, 13, 13], 'gun', {east: tex('side', [3, 3, 13, 13])}), 'up'),
            without(box([3, 3, 14.5], [13, 13, 15.5], 'gun', {south: tex('side', [3, 3, 13, 13])}), 'up'),
            box([2, 2, 15], [14, 3, 16], accent),
            // Copper pipe along the back.
            without(box([12, 3, 15.5], [13, 13, 16], 'copper'), 'up')
        ];
    }

    /** The recessed screen with a frame around it; the ledge is the frame's lower bar. */
    function screen(on, ledge, uv) {
        return [
            box([3, 3, 0.5], [13, 13, 1.5], 'dark', {north: tex('front', uv || [3, 3, 13, 13])}, on ? GLOW : undefined),
            box([2, 2, 0], [14, 3, 1], ledge || 'steel', ledge === 'dark' ? {up: 'gun'} : undefined),
            box([2, 3, 0], [3, 13, 1], 'steel'),
            box([13, 3, 0], [14, 13, 1], 'steel')
        ];
    }

    const MODELS = {};

    function textures(id, on, list, particle) {
        const map = {};
        for (const key of list) {
            const name = id + '_' + key;
            map[key] = 'projectrebirth:block/' + (on ? name + '_on' : name);
        }
        map.parts = 'projectrebirth:block/machine_parts';
        map.particle = 'projectrebirth:block/' + (particle || id + '_side');
        return map;
    }

    function station(id, accent, ledge, extras) {
        [false, true].forEach(on => {
            MODELS[id + (on ? '_on' : '')] = {
                parent: 'minecraft:block/block',
                textures: textures(id, on, ['front', 'side', 'top']),
                elements: [...machine(accent), ...screen(on, ledge), ...extras(on)]
            };
        });
    }

    // Everything on the top plate stays below y = 16.
    station('electronics_fabricator', 'green', 'dark', on => [
        // Capacitors, antenna, memory bar.
        box([2, 14, 2], [5, 16, 5], 'gun', {up: 'brass'}),
        box([2, 14, 6], [4, 15.5, 8], 'dark', {up: 'copper'}),
        box([12, 14, 2], [13, 15.5, 3], 'steel'),
        box([11.5, 15.5, 1.5], [13.5, 16, 3.5], on ? 'green' : 'mid', undefined, on ? GLOW : undefined),
        box([3, 14, 11], [13, 15, 14], 'dark', {up: 'rubber'})
    ]);

    station('assembly_station', 'orange', 'hazard', on => [
        // A small robot arm lying on the top plate.
        box([6, 14, 6], [10, 15, 10], 'gun', {up: 'steel'}),
        box([7, 15, 7], [9, 16, 12], 'orange'),
        box([6.5, 15, 3.5], [9.5, 16, 7], 'steel', {up: on ? 'yellow' : 'dark'}, on ? GLOW : undefined),
        box([2, 14, 11], [5, 15, 14], 'hazard')
    ]);

    station('armor_workstation', 'olive', 'steel', on => [
        // Armor plates on the sides and a low helmet on the top plate.
        box([0, 5, 4], [0.5, 11, 12], 'olive'),
        box([15.5, 5, 4], [16, 11, 12], 'olive'),
        box([5, 14, 5], [11, 16, 11], 'olive'),
        box([6, 14.5, 4.5], [10, 15.5, 5], on ? 'red' : 'dark', undefined, on ? GLOW : undefined),
        box([2, 14, 2], [4, 15, 4], 'steel'),
        box([12, 14, 12], [14, 15, 14], 'steel')
    ]);

    station('maintenance_station', 'yellow', 'hazard', on => [
        // Warning lamp, tool box, gauge.
        box([12, 14, 2], [14, 14.5, 4], 'gun'),
        box([12.25, 14.5, 2.25], [13.75, 16, 3.75], on ? 'yellow' : 'rust', undefined, on ? GLOW : undefined),
        box([2, 14, 3], [8, 15.5, 7], 'red', {up: 'rust'}),
        box([4, 15.5, 4.5], [6, 16, 5.5], 'steel'),
        box([0, 8, 2], [0.5, 11, 3], 'white')
    ]);

    [false, true].forEach(on => {
        MODELS['combustion_generator' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: textures('combustion_generator', on, ['front', 'side', 'top']),
            elements: [
                ...machine('rust'),
                ...screen(on, 'steel', [3, 4, 13, 14]),
                // Chimney, fuel hatch, cooling fins.
                box([9, 14, 9], [13, 15, 13], 'gun', {up: 'dark'}),
                box([9.5, 15, 9.5], [12.5, 16, 12.5], 'rust', {up: on ? 'orange' : 'dark'}, on ? GLOW : undefined),
                box([3, 14, 3], [8, 15, 8], 'steel', {up: 'rust'}),
                box([5, 15, 5], [6, 15.5, 6], 'brass'),
                box([0, 4, 5], [0.5, 12, 6], 'copper'),
                box([0, 4, 8], [0.5, 12, 9], 'copper'),
                box([0, 4, 11], [0.5, 12, 12], 'copper'),
                box([15.5, 4, 5], [16, 12, 6], 'copper'),
                box([15.5, 4, 8], [16, 12, 9], 'copper'),
                box([15.5, 4, 11], [16, 12, 12], 'copper')
            ]
        };
    });

    [false, true].forEach(on => {
        const side = tex('side', [2, 2, 14, 14]);
        MODELS['battery' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: textures('battery', on, ['front', 'side']),
            elements: [
                // The output side at the front, the cell behind it.
                box([0, 0, 0], [16, 16, 2], 'brass', {north: tex('front')}, on ? GLOW : undefined),
                box([2, 2, 2], [14, 14, 14], 'dark', {west: side, east: side, up: side, down: side}),
                box([0, 0, 14], [16, 16, 16], 'steel', {south: 'gun'}),
                box([0, 0, 2], [2, 2, 14], 'steel'),
                box([14, 0, 2], [16, 2, 14], 'steel'),
                box([0, 14, 2], [2, 16, 14], 'steel'),
                box([14, 14, 2], [16, 16, 14], 'steel'),
                // Terminals.
                box([4, 14, 7], [6, 15, 9], 'red'),
                box([10, 14, 7], [12, 15, 9], 'dark')
            ]
        };
    });

    [false, true].forEach(on => {
        MODELS['solar_panel' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: {
                top: 'projectrebirth:block/solar_panel_top' + (on ? '_on' : ''),
                parts: 'projectrebirth:block/machine_parts',
                particle: 'projectrebirth:block/solar_panel_top'
            },
            elements: [
                box([1, 0, 1], [15, 1, 15], 'mid', {up: 'gun'}),
                box([6, 1, 6], [10, 3, 10], 'gun'),
                box([3, 1, 3], [5, 2, 13], 'steel'),
                box([11, 1, 3], [13, 2, 13], 'steel'),
                // The panel leans south, towards the sun's path.
                box([0, 3, 0], [16, 4, 16], 'steel', {up: tex('top'), down: 'gun'},
                    {rotation: {origin: [8, 3.5, 8], axis: 'x', angle: 22.5}})
            ]
        };
    });

    // The cable: a junction box, and for each connection an insulated tube with a copper band and a coupling at the block's edge.
    const CABLE_TEXTURES = {
        cable: 'projectrebirth:block/cable',
        node: 'projectrebirth:block/cable_node',
        parts: 'projectrebirth:block/machine_parts',
        particle: 'projectrebirth:block/cable'
    };
    const NODE = tex('node', [5, 5, 11, 11]);
    const cableCore = () => box([5, 5, 5], [11, 11, 11], 'gun', {north: NODE, south: NODE, west: NODE, east: NODE, up: NODE, down: NODE});
    const TUBE = tex('cable', [4, 4, 12, 12]);
    const cableArm = () => [
        without(box([6, 6, 1], [10, 10, 5], 'rubber', {west: TUBE, east: TUBE, up: TUBE, down: TUBE}), 'north', 'south'),
        box([5.5, 5.5, 2.5], [10.5, 10.5, 3.5], 'copper'),
        Object.assign(box([5, 5, 0], [11, 11, 1], 'steel', {north: 'dark'}))
    ];
    const opposite = element => {
        const out = JSON.parse(JSON.stringify(element));
        out.from[2] = 16 - element.to[2];
        out.to[2] = 16 - element.from[2];
        out.faces.north = element.faces.south;
        out.faces.south = element.faces.north;
        Object.keys(out.faces).forEach(dir => { if (!out.faces[dir]) { delete out.faces[dir]; } });
        return out;
    };
    MODELS.cable_core = {textures: CABLE_TEXTURES, elements: [cableCore()]};
    MODELS.cable_arm = {textures: CABLE_TEXTURES, elements: cableArm()};
    MODELS.cable_arm.elements[2].faces.north.cullface = 'north';
    // In the inventory: a straight piece.
    MODELS.cable_item = {parent: 'minecraft:block/block', textures: CABLE_TEXTURES,
        elements: [cableCore(), ...cableArm(), ...cableArm().map(opposite)]};

    MODELS.faction_core = {
        parent: 'minecraft:block/block',
        textures: {
            side: 'projectrebirth:block/faction_core_side',
            top: 'projectrebirth:block/faction_core_top',
            parts: 'projectrebirth:block/machine_parts',
            particle: 'projectrebirth:block/faction_core_side'
        },
        elements: [
            box([0, 0, 0], [16, 3, 16], 'gun', {up: tex('top')}),
            box([0, 13, 0], [16, 16, 16], 'gun', {up: tex('top'), down: tex('top')}),
            box([0, 3, 0], [2, 13, 2], 'dark'),
            box([14, 3, 0], [16, 13, 2], 'dark'),
            box([0, 3, 14], [2, 13, 16], 'dark'),
            box([14, 3, 14], [16, 13, 16], 'dark'),
            // The crystal, turned 45 degrees, in an open cage.
            box([5, 3, 5], [11, 13, 11], 'cyan', {
                north: tex('side', [5, 3, 11, 13]), south: tex('side', [5, 3, 11, 13]),
                west: tex('side', [5, 3, 11, 13]), east: tex('side', [5, 3, 11, 13])
            }, {rotation: {origin: [8, 8, 8], axis: 'y', angle: 45}, neoforge_data: {block_light: 15}})
        ]
    };

    // The capsule is 2.25 blocks tall inside so that a player with a hat fits: floor 2/16, roof underside 34/16.
    const FRAME = 'transfer_capsule_frame';
    const capsuleTextures = {
        particle: 'projectrebirth:block/' + FRAME,
        frame: 'projectrebirth:block/' + FRAME,
        back: 'projectrebirth:block/transfer_capsule_back',
        floor: 'projectrebirth:block/transfer_capsule_floor',
        parts: 'projectrebirth:block/machine_parts'
    };
    const frame = (from, to, faces) => box(from, to, 'gun', Object.assign({
        north: tex('frame'), south: tex('frame'), west: tex('frame'), east: tex('frame')
    }, faces || {}));

    MODELS.transfer_capsule_lower = {
        parent: 'minecraft:block/block',
        ambientocclusion: false,
        textures: capsuleTextures,
        elements: [
            frame([0, 0, 0], [16, 2, 16], {up: tex('floor')}),
            frame([0, 2, 14], [16, 16, 16], {north: tex('back')}),
            frame([0, 2, 0], [2, 16, 2]),
            frame([14, 2, 0], [16, 16, 2]),
            // Side ribs, a lit edge on the floor, pipes on the back.
            box([0, 2, 11], [1, 16, 14], 'gun', {east: 'mid'}),
            box([15, 2, 11], [16, 16, 14], 'gun', {west: 'mid'}),
            box([2, 2, 0], [14, 2.5, 1], 'cyan', undefined, GLOW),
            box([3, 2, 15.5], [4, 16, 16.5], 'copper'),
            box([12, 2, 15.5], [13, 16, 16.5], 'copper')
        ]
    };
    MODELS.transfer_capsule_upper = {
        parent: 'minecraft:block/block',
        ambientocclusion: false,
        textures: capsuleTextures,
        elements: [
            frame([0, 0, 14], [16, 18, 16], {north: tex('back')}),
            frame([0, 0, 0], [2, 18, 2]),
            frame([14, 0, 0], [16, 18, 2]),
            box([0, 0, 11], [1, 18, 14], 'gun', {east: 'mid'}),
            box([15, 0, 11], [16, 18, 14], 'gun', {west: 'mid'}),
            box([3, 0, 15.5], [4, 18, 16.5], 'copper'),
            box([12, 0, 15.5], [13, 18, 16.5], 'copper'),
            // Roof with a lamp above the head, and a cap.
            frame([0, 18, 0], [16, 20, 16], {up: 'gun', down: 'dark'}),
            box([5, 17.5, 5], [11, 18, 11], 'cyan', undefined, GLOW),
            box([3, 20, 3], [13, 21, 13], 'mid', {up: 'gun'}),
            box([6, 21, 6], [10, 21.5, 10], 'cyan', undefined, GLOW)
        ]
    };

    // ---------------------------------------------------------------- block states

    const ROTATIONS = {north: 0, east: 90, south: 180, west: 270};

    function variant(id, extra) {
        return Object.assign({model: 'projectrebirth:block/' + id}, extra);
    }

    function rotation(y) {
        return y ? {y} : {};
    }

    const STATES = {};
    ['electronics_fabricator', 'assembly_station', 'armor_workstation', 'maintenance_station'].forEach(id => {
        const variants = {};
        Object.entries(ROTATIONS).forEach(([facing, y]) => {
            variants['facing=' + facing + ',working=false'] = variant(id, rotation(y));
            variants['facing=' + facing + ',working=true'] = variant(id + '_on', rotation(y));
        });
        STATES[id] = {variants};
    });
    STATES.combustion_generator = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        STATES.combustion_generator.variants['facing=' + facing + ',lit=false'] = variant('combustion_generator', rotation(y));
        STATES.combustion_generator.variants['facing=' + facing + ',lit=true'] = variant('combustion_generator_on', rotation(y));
    });
    STATES.battery = {variants: {}};
    [false, true].forEach(working => {
        const id = working ? 'battery_on' : 'battery';
        Object.entries(ROTATIONS).forEach(([facing, y]) => {
            STATES.battery.variants['facing=' + facing + ',working=' + working] = variant(id, rotation(y));
        });
        STATES.battery.variants['facing=up,working=' + working] = variant(id, {x: 270});
        STATES.battery.variants['facing=down,working=' + working] = variant(id, {x: 90});
    });
    STATES.solar_panel = {variants: {}};
    [false, true].forEach(working => {
        Object.entries(ROTATIONS).forEach(([facing, y]) => {
            STATES.solar_panel.variants['facing=' + facing + ',working=' + working] = variant(working ? 'solar_panel_on' : 'solar_panel', rotation(y));
        });
    });

    // ---------------------------------------------------------------- output

    function json(value) {
        return JSON.stringify(value, null, 2) + '\n';
    }

    /**
     * Problems of a model: parts outside the block, and faces of two parts that look the same way in the same plane
     * and overlap: the game cannot decide which to draw, and such a face flickers. The capsule is taller on purpose.
     */
    function problems(id, model) {
        const out = [];
        const axes = {west: [0, 1, 2, 0], east: [0, 1, 2, 1], down: [1, 0, 2, 0], up: [1, 0, 2, 1], north: [2, 0, 1, 0], south: [2, 0, 1, 1]};
        const capsule = id.startsWith('transfer_capsule');
        model.elements.forEach((e, i) => {
            if (!capsule && [...e.from, ...e.to].some(v => v < 0 || v > 16)) {
                out.push(id + ': part ' + i + ' is outside the block');
            }
            model.elements.forEach((o, j) => {
                if (j <= i || e.rotation || o.rotation) {
                    return;
                }
                for (const [dir, [n, u, v, far]] of Object.entries(axes)) {
                    const plane = x => (far ? x.to : x.from)[n];
                    if (plane(e) === plane(o) && e.faces[dir] && o.faces[dir]
                        && Math.min(e.to[u], o.to[u]) > Math.max(e.from[u], o.from[u])
                        && Math.min(e.to[v], o.to[v]) > Math.max(e.from[v], o.from[v])) {
                        out.push(id + ': parts ' + i + ' and ' + j + ' overlap on ' + dir);
                    }
                }
            });
        });
        return out;
    }

    function saveAll() {
        const found = Object.entries(MODELS).flatMap(([id, model]) => problems(id, model));
        if (found.some(text => !text.startsWith('transfer_capsule'))) {
            throw new Error(found.join('; '));
        }
        Object.entries(MODELS).forEach(([id, model]) => fs.writeFileSync(ASSETS + 'models/block/' + id + '.json', json(model)));
        Object.entries(STATES).forEach(([id, state]) => fs.writeFileSync(ASSETS + 'blockstates/' + id + '.json', json(state)));
        return {models: Object.keys(MODELS).length, states: Object.keys(STATES).length};
    }

    /** Opens a written model; Blockbench finds the textures from the assets path. */
    function open(id) {
        const path = ASSETS + 'models/block/' + id + '.json';
        loadModelFile({name: id + '.json', path, content: fs.readFileSync(path, 'utf8')});
        return Project.name;
    }

    function saveProjects() {
        const dir = ROOT + 'blockbench/blocks/';
        fs.mkdirSync(dir, {recursive: true});
        const ids = Object.keys(MODELS);
        ids.forEach(id => {
            open(id);
            const compiled = Codecs.project.compile();
            fs.writeFileSync(dir + id + '.bbmodel', typeof compiled === 'string' ? compiled : JSON.stringify(compiled));
            Project.close(true);
        });
        return ids.length;
    }

    window.RebirthBlockModels = {MODELS, STATES, saveAll, open, saveProjects};
})();
