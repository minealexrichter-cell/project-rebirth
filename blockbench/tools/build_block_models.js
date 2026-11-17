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

    /**
     * Gives every textured face without a uv the uv vanilla would give it from the element's place, so one pixel of the texture is
     * one sixteenth of a block, as in Minecraft's own models. Coordinates outside the block are wrapped into it first.
     */
    function placeUv(elements) {
        // an interval outside the block is moved by a whole block into it
        const into = (a, b) => (a < 0 ? [a + 16, b + 16] : b > 16 ? [a - 16, b - 16] : [a, b]);
        for (const e of elements) {
            const [x1, x2] = into(e.from[0], e.to[0]), [y1, y2] = into(e.from[1], e.to[1]), [z1, z2] = into(e.from[2], e.to[2]);
            const uv = {down: [x1, 16 - z2, x2, 16 - z1], up: [x1, z1, x2, z2], north: [16 - x2, 16 - y2, 16 - x1, 16 - y1],
                south: [x1, 16 - y2, x2, 16 - y1], west: [z1, 16 - y2, z2, 16 - y1], east: [16 - z2, 16 - y2, 16 - z1, 16 - y1]};
            for (const [dir, face] of Object.entries(e.faces)) if (!face.uv) face.uv = uv[dir];
        }
        return elements;
    }

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

    station('implant_fabricator', 'cyan', 'steel', on => [
        // A tray of implants under a small lit hood.
        box([2, 14, 2], [14, 15, 8], 'gun', {up: 'dark'}),
        box([4, 15, 3], [6, 15.5, 7], on ? 'cyan' : 'steel', undefined, on ? GLOW : undefined),
        box([7, 15, 3], [9, 15.5, 7], 'copper'),
        box([10, 15, 3], [12, 15.5, 7], on ? 'cyan' : 'steel', undefined, on ? GLOW : undefined),
        box([6, 14, 10], [10, 16, 14], 'steel', {up: 'gun'})
    ]);

    station('armor_upgrade_fabricator', 'red', 'hazard', on => [
        // A stack of plates and a press above it.
        box([3, 14, 3], [8, 15, 8], 'steel', {up: 'olive'}),
        box([4, 15, 4], [7, 15.5, 7], 'olive'),
        box([9, 14, 4], [13, 15, 12], 'gun', {up: 'dark'}),
        box([10, 15, 6], [12, 16, 10], on ? 'orange' : 'steel', undefined, on ? GLOW : undefined)
    ]);

    station('weapon_workshop', 'olive', 'hazard', on => [
        // A rifle in a vice, and a tray of rounds.
        box([2, 14, 4], [4, 15, 12], 'gun', {up: 'dark'}),
        box([12, 14, 4], [14, 15, 12], 'gun', {up: 'dark'}),
        box([3, 15, 7], [13, 15.5, 9], 'steel', {up: 'dark'}),
        box([5, 14, 12.5], [11, 15, 14.5], 'brass', {up: 'copper'}),
        box([9, 15.5, 7.5], [10, 16, 8.5], on ? 'orange' : 'olive', undefined, on ? GLOW : undefined)
    ]);

    station('body_modification_station', 'white', 'dark', on => [
        // A padded table with an arm above it.
        box([3, 14, 3], [13, 15, 13], 'white', {up: 'rubber'}),
        box([2, 14, 13], [4, 16, 15], 'gun'),
        box([3, 15.5, 7], [13, 16, 9], on ? 'cyan' : 'mid', undefined, on ? GLOW : undefined)
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

    // ---------------------------------------------------------------- shelter (version 1.4.0)

    /** Texture map where only some keys change while the block works. */
    function shelterTextures(id, on, keys, changing, particle) {
        const map = {};
        for (const key of keys) {
            map[key] = 'projectrebirth:block/' + id + '_' + key + (on && changing.includes(key) ? '_on' : '');
        }
        map.parts = 'projectrebirth:block/machine_parts';
        map.particle = 'projectrebirth:block/' + id + '_' + (particle || keys[0]);
        return map;
    }

    // Air filtration unit: the machine body with a big fan behind the front grille, vents and a duct on top.
    [false, true].forEach(on => {
        MODELS['air_filtration_unit' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('air_filtration_unit', on, ['front', 'side', 'top'], ['front'], 'side'),
            elements: [...machine('yellow'), ...screen(on, 'steel'),
                box([2, 14, 2], [5, 16, 5], 'steel', {up: 'dark'}),
                box([11, 14, 10], [14, 15, 14], 'gun', {up: 'yellow'}),
                box([6, 14, 3], [10, 15, 6], 'mid', {up: on ? 'green' : 'dark'}, on ? GLOW : undefined)
            ]
        };
    });

    // Water purifier (1.6.0, block C): the machine body with its two tanks behind glass, a hatch and a pipe on top.
    [false, true].forEach(on => {
        MODELS['water_purifier' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('water_purifier', on, ['front', 'side', 'top'], ['front'], 'side'),
            elements: [...machine('cyan'), ...screen(on, 'steel'),
                box([10, 14, 10], [14, 16, 14], 'steel', {up: 'dark'}),
                box([2, 14, 11], [10, 15, 13], 'copper', {up: 'copper'})
            ]
        };
    });

    // Environmental monitoring station (1.6.0, block E): the machine body with a map screen, an air sampler and a sensor dish on top.
    [false, true].forEach(on => {
        MODELS['monitoring_station' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('monitoring_station', on, ['front', 'side', 'top'], ['front'], 'side'),
            elements: [...machine('green'), ...screen(on, 'steel'),
                // the air sampler: a column with an intake cap
                box([2, 14, 10], [5, 15, 13], 'steel', {up: 'dark'}),
                box([1, 15, 9], [6, 16, 14], 'gun', {up: 'green'}),
                // the sensor dish on a short post
                box([10, 14, 4], [12, 15, 6], 'gun'),
                box([8, 15, 2], [14, 16, 8], 'white', {up: 'steel'}),
                box([11, 14, 10], [13, 15, 12], on ? 'green' : 'dark', {}, on ? GLOW : undefined)
            ]
        };
    });

    // Weather station (1.6.0, block D): the machine body with a radar screen, a louvred instrument box on top and a mast with a cup
    // anemometer and a wind vane, reaching up into the open air the station needs.
    [false, true].forEach(on => {
        MODELS['weather_station' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            // the mast stands above the block: shading by the blocks around the station's foot would darken it in patches
            ambientocclusion: false,
            textures: shelterTextures('weather_station', on, ['front', 'side', 'top'], ['front'], 'side'),
            elements: [...machine('cyan'), ...screen(on, 'steel'),
                // the instrument box
                box([2, 14, 9], [7, 19, 14], 'white', {up: 'white'}),
                box([2, 19, 8], [7, 20, 15], 'steel'),
                box([3, 15, 8], [6, 16, 9], 'gun'),
                box([3, 17, 8], [6, 18, 9], 'gun'),
                // the mast
                box([10, 15, 3], [12, 29, 5], 'steel', {up: 'dark'}),
                box([9, 14, 2], [13, 15, 6], 'gun'),
                // the anemometer: two crossed arms round the mast with a cup at each end
                box([6, 26, 3], [10, 27, 5], 'gun'),
                box([12, 26, 3], [14, 27, 5], 'gun'),
                box([10, 27, 1], [12, 28, 3], 'gun'),
                box([10, 27, 5], [12, 28, 7], 'gun'),
                box([4, 25, 2], [6, 27, 4], 'white'),
                box([14, 25, 4], [16, 27, 6], 'white'),
                box([8, 26, 0], [10, 28, 2], 'white'),
                box([12, 26, 6], [14, 28, 8], 'white'),
                // the wind vane on top and the lamp of the mast
                box([10, 29, 1], [12, 30, 7], 'steel'),
                box([10, 30, 5], [12, 31, 7], 'red'),
                box([10, 22, 2], [12, 23, 3], on ? 'green' : 'dark', {}, on ? GLOW : undefined)
            ]
        };
    });

    // Backup battery: a cell with a charge window on every side, posts, a plinth and a top plate with terminals.
    [false, true].forEach(on => {
        const side = tex('side', [2, 2, 14, 14]);
        MODELS['backup_battery' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('backup_battery', on, ['side', 'top'], ['side']),
            elements: [
                box([0, 0, 0], [16, 2, 16], 'mid', {up: 'gun'}),
                without(box([2, 2, 2], [14, 14, 14], 'dark', {north: side, south: side, west: side, east: side}, on ? GLOW : undefined), 'up', 'down'),
                without(box([0, 2, 0], [2, 14, 2], 'steel'), 'up', 'down'),
                without(box([14, 2, 0], [16, 14, 2], 'steel'), 'up', 'down'),
                without(box([0, 2, 14], [2, 14, 16], 'steel'), 'up', 'down'),
                without(box([14, 2, 14], [16, 14, 16], 'steel'), 'up', 'down'),
                box([0, 14, 0], [16, 16, 16], 'mid', {up: tex('top')})
            ]
        };
    });

    // Decontamination chamber: a booth open to the front, tiles inside, a drain below, nozzles above; water while it washes.
    const TILES = tex('tiles');
    const WATER = {north: tex('water'), south: tex('water'), west: tex('water'), east: tex('water')};
    [false, true].forEach(on => {
        ['lower', 'upper'].forEach(half => {
            const lower = half === 'lower';
            const y0 = lower ? 1 : 0, y1 = lower ? 16 : 14;
            const elements = [
                box([0, lower ? 1 : 0, 14], [16, 16, 16], 'steel', {north: TILES, south: tex('side')}),
                box([0, lower ? 1 : 0, 0], [2, 16, 14], 'steel', {east: TILES, west: tex('side'), north: 'cyan'}),
                box([14, lower ? 1 : 0, 0], [16, 16, 14], 'steel', {west: TILES, east: tex('side'), north: 'cyan'})
            ];
            if (lower) {
                elements.push(box([0, 0, 0], [16, 1, 16], 'mid', {up: tex('grate')}));
                elements.push(box([3, 4, 13], [5, 9, 14], 'steel', {north: 'yellow'}));
            } else {
                elements.push(box([2, 14, 0], [14, 16, 14], 'steel', {down: tex('ceiling')}));
                elements.push(box([6, 13, 5], [10, 14, 9], 'gun', {down: on ? 'cyan' : 'dark'}, on ? GLOW : undefined));
            }
            if (on) {
                elements.push(Object.assign({from: [3, y0, 4], to: [13, lower ? y1 : 13, 12], faces: WATER}));
            }
            const model = {
                parent: 'minecraft:block/block',
                textures: {tiles: 'projectrebirth:block/decontamination_chamber_tiles', grate: 'projectrebirth:block/decontamination_chamber_grate',
                    ceiling: 'projectrebirth:block/decontamination_chamber_ceiling', side: 'projectrebirth:block/decontamination_chamber_side',
                    water: 'projectrebirth:block/decontamination_chamber_water', parts: 'projectrebirth:block/machine_parts',
                    particle: 'projectrebirth:block/decontamination_chamber_side'},
                elements
            };
            if (on) {
                model.render_type = 'minecraft:translucent';
            }
            MODELS['decontamination_chamber_' + half + (on ? '_on' : '')] = model;
        });
    });

    // Radiation purge station: a platform with an emitter ring and four emitter posts at the corners.
    [false, true].forEach(on => {
        const sides = tex('side', [0, 12, 16, 16]);
        const post = (x, z) => without(box([x, 4, z], [x + 2, 6, z + 2], 'gun', {up: on ? 'green' : 'dark'}, on ? GLOW : undefined), 'down');
        MODELS['radiation_purge_station' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('radiation_purge_station', on, ['top', 'side'], ['top'], 'side'),
            elements: [
                box([0, 0, 0], [16, 4, 16], 'gun', {up: tex('top'), north: sides, south: sides, west: sides, east: sides}, on ? GLOW : undefined),
                post(0, 0), post(14, 0), post(0, 14), post(14, 14)
            ]
        };
    });

    // Charging pad (1.5.0): a low plate (the collision shape is 3 px, set in ChargingPadBlock) with a lamp post at each corner
    // that glows yellow while it charges.
    [false, true].forEach(on => {
        const sides = tex('side', [0, 13, 16, 16]);
        const lamp = (x, z) => without(box([x, 3, z], [x + 2, 5, z + 2], 'gun', {up: on ? 'yellow' : 'dark'}, on ? GLOW : undefined), 'down');
        MODELS['charging_pad' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('charging_pad', on, ['top', 'side'], ['top'], 'side'),
            elements: [
                box([0, 0, 0], [16, 3, 16], 'gun', {up: tex('top'), north: sides, south: sides, west: sides, east: sides}, on ? GLOW : undefined),
                lamp(0, 0), lamp(14, 0), lamp(0, 14), lamp(14, 14)
            ]
        };
    });

    // Heater (1.5.0): a box with a glowing grille on every side, corner posts, a plinth and a top plate.
    [false, true].forEach(on => {
        const side = tex('side', [2, 2, 14, 14]);
        MODELS['heater' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('heater', on, ['side', 'top'], ['side']),
            elements: [
                box([0, 0, 0], [16, 2, 16], 'mid', {up: 'gun'}),
                without(box([2, 2, 2], [14, 14, 14], 'dark', {north: side, south: side, west: side, east: side}, on ? GLOW : undefined), 'up', 'down'),
                without(box([0, 2, 0], [2, 14, 2], 'steel'), 'up', 'down'),
                without(box([14, 2, 0], [16, 14, 2], 'steel'), 'up', 'down'),
                without(box([0, 2, 14], [2, 14, 16], 'steel'), 'up', 'down'),
                without(box([14, 2, 14], [16, 14, 16], 'steel'), 'up', 'down'),
                box([0, 14, 0], [16, 16, 16], 'mid', {up: tex('top')})
            ]
        };
    });

    // Shelter control panel: a console with a status screen, an antenna and an alarm lamp that glows red while it sounds.
    [false, true].forEach(on => {
        MODELS['shelter_control_panel' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: shelterTextures('shelter_control_panel', on, ['front', 'side', 'top'], ['front'], 'side'),
            elements: [...machine('cyan'), ...screen(true, 'steel'),
                box([12, 14, 2], [13, 16, 3], 'steel'),
                box([3, 14, 3], [6, 16, 6], 'gun', {up: on ? 'red' : 'dark', north: on ? 'red' : 'gun'}, on ? GLOW : undefined),
                box([8, 14, 10], [13, 15, 13], 'dark', {up: 'rubber'})
            ]
        };
    });

    // ---------------------------------------------------------------- uranium and radiation sources (1.6.0)

    // Radioisotope generator: the machine body, a shielded core with an emitter cap and cooling fins on the top plate.
    [false, true].forEach(on => {
        MODELS['radioisotope_generator' + (on ? '_on' : '')] = {
            parent: 'minecraft:block/block',
            textures: textures('radioisotope_generator', on, ['front', 'side', 'top']),
            elements: [...machine('yellow'), ...screen(on, 'hazard'),
                // Core drum with a lit cap, a cooling fin on each side, a terminal block behind.
                box([5, 14, 5], [11, 15.5, 11], 'gun', {up: 'steel'}),
                box([6, 15.5, 6], [10, 16, 10], on ? 'yellow' : 'olive', undefined, on ? GLOW : undefined),
                box([3, 14, 4], [4, 15.5, 12], 'steel'),
                box([12, 14, 4], [13, 15.5, 12], 'steel'),
                box([5, 14, 12], [11, 15, 14], 'dark', {up: 'rubber'}),
                box([7, 15, 12.5], [9, 15.5, 13.5], on ? 'green' : 'mid', undefined, on ? GLOW : undefined)
            ]
        };
    });

    // Leaking waste barrel: stays inside [2,0,2]-[14,16,14], which is also its collision shape.
    (() => {
        const side = tex('side', [2, 0, 14, 16]);
        const lid = tex('top', [2, 2, 14, 14]);
        MODELS.leaking_waste_barrel = {
            parent: 'minecraft:block/block',
            textures: {side: 'projectrebirth:block/leaking_waste_barrel_side', top: 'projectrebirth:block/leaking_waste_barrel_top',
                parts: 'projectrebirth:block/machine_parts', particle: 'projectrebirth:block/leaking_waste_barrel_side'},
            elements: [
                // Body, slightly narrower than the rims and bands.
                without(box([2.5, 1, 2.5], [13.5, 15, 13.5], 'rust', {north: side, south: side, west: side, east: side}), 'up', 'down'),
                box([2, 0, 2], [14, 1, 14], 'dark', {down: 'dark'}),
                box([2, 15, 2], [14, 16, 14], 'rust', {up: lid, down: 'dark'}),
                without(box([2, 3.5, 2], [14, 5, 14], 'rust'), 'up', 'down'),
                without(box([2, 10.5, 2], [14, 12, 14], 'rust'), 'up', 'down'),
                // Toxic seep running down the front.
                box([9.5, 5, 13.5], [12.5, 10.5, 13.9], 'green', {north: 'green', south: 'green', west: 'green', east: 'green', up: 'green', down: 'green'}, GLOW)
            ]
        };
    })();

    // ---------------------------------------------------------------- structure decoration (1.6.0, large structures)

    const decorTextures = (map, particle) => Object.assign({parts: 'projectrebirth:block/machine_parts', particle: 'projectrebirth:block/' + particle},
        Object.fromEntries(Object.entries(map).map(([k, v]) => [k, 'projectrebirth:block/' + v])));

    // Wall monitor: a framed screen hanging on the wall behind it (the south side); the screen faces north and glows.
    MODELS.wall_monitor = {
        parent: 'minecraft:block/block',
        textures: decorTextures({screen: 'wall_monitor_screen_on'}, 'wall_monitor_screen_on'),
        elements: [
            box([1, 2, 14], [15, 14, 16], 'gun', {north: 'dark', south: 'dark'}),
            box([2, 3, 13.5], [14, 13, 14], 'dark', {north: tex('screen', [2, 3, 14, 13])}, GLOW),
            box([6, 1, 14.5], [10, 2, 15.5], 'steel'),
            box([12, 13, 13.5], [13, 13.5, 14], 'green', undefined, GLOW)
        ]
    };

    // Server rack: a dark cabinet with a front of drive bays and blinking lights.
    MODELS.server_rack = {
        parent: 'minecraft:block/block',
        textures: decorTextures({front: 'server_rack_front', side: 'server_rack_side'}, 'server_rack_side'),
        elements: [
            box([1, 0, 1], [15, 16, 15], 'dark', {west: tex('side'), east: tex('side'), south: tex('side'), up: 'gun', down: 'gun'}),
            without(box([1.5, 0.5, 0.5], [14.5, 15.5, 1], 'gun', {north: tex('front', [1, 0, 15, 16])}, GLOW), 'south'),
            without(box([0.5, 0, 0.5], [1.5, 16, 1.5], 'steel'), 'up', 'down'),
            without(box([14.5, 0, 0.5], [15.5, 16, 1.5], 'steel'), 'up', 'down')
        ]
    };

    // Specimen tank: a steel base and cap with a status panel, four posts, a column of translucent blue liquid, and inside it a
    // pale specimen curled up as a 3D body (head, torso, arms, folded legs) on a feeding tube. Drawn in the translucent layer.
    (() => {
        const liquid = tex('liquid', [2, 2, 14, 14]);
        const skin = (u, v, w, h) => tex('specimen', [u, v, u + w, v + h]);
        const body = (from, to, uv, extra) => {
            const f = skin(...uv);
            return Object.assign({from, to, faces: {north: f, south: f, west: f, east: f, up: f, down: f}}, extra || {});
        };
        const tilt = (angle, axis, origin) => ({rotation: {angle, axis, origin}});
        const metal = tex('metal');
        MODELS.specimen_tank = {
            parent: 'minecraft:block/block',
            render_type: 'minecraft:translucent',
            textures: decorTextures({liquid: 'specimen_tank_liquid', specimen: 'specimen', metal: 'tank_metal'}, 'tank_metal'),
            elements: [
                box([1, 0, 1], [15, 2, 15], 'gun', {north: tex('metal', [0, 6, 16, 8]), south: tex('metal', [0, 6, 16, 8]), west: tex('metal', [0, 6, 16, 8]), east: tex('metal', [0, 6, 16, 8]), up: 'steel'}),
                box([1, 14, 1], [15, 16, 15], 'gun', {north: tex('metal', [0, 0, 16, 2]), south: tex('metal', [0, 0, 16, 2]), west: tex('metal', [0, 0, 16, 2]), east: tex('metal', [0, 0, 16, 2]), up: metal, down: 'steel'}),
                without(box([2, 2, 2], [14, 14, 14], 'cyan', {north: liquid, south: liquid, west: liquid, east: liquid}, GLOW), 'up', 'down'),
                without(box([1, 2, 1], [2, 14, 2], 'steel'), 'up', 'down'),
                without(box([14, 2, 1], [15, 14, 2], 'steel'), 'up', 'down'),
                without(box([1, 2, 14], [2, 14, 15], 'steel'), 'up', 'down'),
                without(box([14, 2, 14], [15, 14, 15], 'steel'), 'up', 'down'),
                // the specimen, a little turned: head bowed, knees drawn up, arms folded
                body([6.5, 8.5, 6.5], [9.5, 11.5, 9.5], [0, 0, 6, 6], tilt(22.5, 'x', [8, 8.5, 8])),
                body([6.75, 5, 7], [9.25, 8.75, 9], [8, 0, 5, 7], tilt(-22.5, 'y', [8, 6, 8])),
                body([5.75, 6, 7.25], [6.75, 8.5, 8.25], [12, 8, 2, 5], tilt(22.5, 'z', [6.25, 8.5, 7.75])),
                body([9.25, 6, 7.25], [10.25, 8.5, 8.25], [12, 8, 2, 5], tilt(-22.5, 'z', [9.75, 8.5, 7.75])),
                body([6.75, 3.5, 6.25], [7.75, 5.5, 8.75], [0, 8, 2, 5], tilt(22.5, 'x', [7.25, 5, 7.5])),
                body([8.25, 3.5, 6.25], [9.25, 5.5, 8.75], [0, 8, 2, 5], tilt(22.5, 'x', [8.75, 5, 7.5])),
                box([7.75, 11.5, 7.75], [8.25, 14, 8.25], 'rubber', {up: 'rubber', down: 'rubber'})
            ]
        };
    })();

    // Military ammo crate: an olive box with a stencil band and steel latches.
    MODELS.ammo_crate = {
        parent: 'minecraft:block/block',
        textures: decorTextures({side: 'ammo_crate_side', top: 'ammo_crate_top'}, 'ammo_crate_side'),
        elements: [
            box([1, 0, 2], [15, 10, 14], 'olive', {north: tex('side', [1, 3, 15, 13]), south: tex('side', [1, 3, 15, 13]),
                west: tex('side', [2, 3, 14, 13]), east: tex('side', [2, 3, 14, 13]), up: tex('top', [1, 2, 15, 14]), down: 'dark'}),
            box([0.5, 2, 5], [1, 8, 11], 'gun'),
            box([15, 2, 5], [15.5, 8, 11], 'gun')
        ]
    };

    // 1.7.1: the crate a turret rolls out of the heavy dock in. An olive box in a steel frame with corner posts, rails top and
    // bottom, a hazard band on the front and back, a steel lid. Swatches only; never placed, only drawn by the crate entity.
    MODELS.turret_crate = (() => {
        const parts = [];
        [[0, 0], [14, 0], [0, 14], [14, 14]].forEach(([x, z]) => parts.push(box([x, 0, z], [x + 2, 14, z + 2], 'gun')));
        [0, 12].forEach(y => {
            parts.push(box([2, y, 0], [14, y + 2, 2], 'steel'));
            parts.push(box([2, y, 14], [14, y + 2, 16], 'steel'));
            parts.push(box([0, y, 2], [2, y + 2, 14], 'steel'));
            parts.push(box([14, y, 2], [16, y + 2, 14], 'steel'));
        });
        parts.push(box([2, 0, 2], [14, 14, 14], 'olive', {down: 'dark'}));
        parts.push(box([4, 6, 1], [12, 8, 2], 'hazard'));
        parts.push(box([4, 6, 14], [12, 8, 15], 'hazard'));
        parts.push(box([0, 14, 0], [16, 16, 16], 'gun', {up: 'mid'}));
        return {parent: 'minecraft:block/block', textures: {parts: 'projectrebirth:block/machine_parts', particle: 'projectrebirth:block/machine_parts'},
            elements: parts};
    })();

    // 1.7.1, block G: the turret mount and the service post take textures with grain (build_textures.js, garrison_*), one pixel to
    // a sixteenth of a block: the faces name only the texture and the game takes the uv from where the face is. The tiny lamps
    // stay swatches of machine_parts.
    const GARRISON_TEXTURES = {
        steel: 'projectrebirth:block/garrison_steel', mid: 'projectrebirth:block/garrison_mid', gun: 'projectrebirth:block/garrison_gun',
        dark: 'projectrebirth:block/garrison_dark', hazard: 'projectrebirth:block/garrison_hazard', cyan: 'projectrebirth:block/service_post_screen',
        deck: 'projectrebirth:block/service_post_deck', parts: 'projectrebirth:block/machine_parts'
    };
    /** A cube in the garrison textures: a name of GARRISON_TEXTURES on all sides, the map replaces single sides; 'green' is a swatch. */
    function gbox(from, to, color, faces, extra) {
        const out = {from, to, faces: {}};
        for (const dir of DIRS) {
            const name = (faces && faces[dir]) || color;
            out.faces[dir] = name === 'green' ? sw('green') : {texture: '#' + name};
        }
        return Object.assign(out, extra || {});
    }
    const garrisonTextures = (particle, names) => {
        const map = {particle: GARRISON_TEXTURES[particle]};
        names.forEach(name => { map[name] = GARRISON_TEXTURES[name]; });
        return map;
    };

    // 1.7.0, block G: the turret mount. A heavy steel plinth with a ring on top the turret's feet stand on, an ammunition hatch
    // with a hazard band on each side and a green lamp.
    MODELS.turret_mount = {
        parent: 'minecraft:block/block',
        textures: garrisonTextures('steel', ['steel', 'mid', 'gun', 'dark', 'hazard', 'parts']),
        elements: [
            gbox([0, 0, 0], [16, 3, 16], 'dark'),
            gbox([1, 3, 1], [15, 13, 15], 'steel', {up: 'mid', down: 'dark'}),
            gbox([0, 13, 0], [16, 16, 16], 'gun', {up: 'dark'}),
            gbox([4, 5, 0.5], [12, 11, 1.5], 'mid'),
            gbox([4, 5, 14.5], [12, 11, 15.5], 'mid'),
            gbox([0.5, 5, 4], [1.5, 11, 12], 'mid'),
            gbox([14.5, 5, 4], [15.5, 11, 12], 'mid'),
            gbox([5, 9, 0], [11, 10, 1], 'hazard'),
            gbox([5, 9, 15], [11, 10, 16], 'hazard'),
            gbox([0, 9, 5], [1, 10, 11], 'hazard'),
            gbox([15, 9, 5], [16, 10, 11], 'hazard'),
            gbox([7, 11.5, 0], [9, 12.5, 1.5], 'green', null, GLOW)
        ]
    };

    // 1.7.0, block H: the service post. A plate: a low steel deck with a hazard rim and a grid, a machine stands on four of them.
    // The post console: a squat cabinet with a screen and a green lamp.
    MODELS.service_post_plate = {
        parent: 'minecraft:block/block',
        textures: garrisonTextures('deck', ['deck', 'steel', 'mid', 'dark', 'hazard']),
        elements: [
            // 1.7.2: a half block (the top at 8 pixels, as the collision box), the machine stands on its top
            gbox([0, 0, 0], [16, 6, 16], 'dark', {up: 'deck', north: 'hazard', south: 'hazard', west: 'hazard', east: 'hazard'}),
            gbox([1, 6, 1], [15, 7, 15], 'steel', {up: 'deck'}),
            gbox([3, 7, 3], [13, 8, 13], 'mid', {up: 'deck'})
        ]
    };
    MODELS.service_post = {
        parent: 'minecraft:block/block',
        textures: garrisonTextures('steel', ['steel', 'mid', 'gun', 'dark', 'hazard', 'cyan', 'parts']),
        elements: [
            gbox([1, 0, 1], [15, 2, 15], 'dark'),
            gbox([2, 2, 2], [14, 12, 14], 'steel', {up: 'mid'}),
            gbox([3, 6, 1], [13, 11, 2.5], 'dark'),
            gbox([4, 7, 0.5], [12, 10, 1.5], 'cyan', null, GLOW),
            gbox([11, 3, 1.5], [13, 5, 2.5], 'green', null, GLOW),
            gbox([3, 3, 1.5], [9, 4, 2.5], 'hazard'),
            gbox([5, 11, 5], [11, 14, 11], 'gun')
        ]
    };

    // Irradiated sludge: a glowing green layer two pixels deep over the ground.
    (() => {
        const ooze = tex('ooze'), edge = tex('ooze', [0, 14, 16, 16]);
        MODELS.irradiated_sludge = {
            parent: 'minecraft:block/block',
            textures: decorTextures({ooze: 'irradiated_sludge'}, 'irradiated_sludge'),
            elements: [
                box([0, 0, 0], [16, 2, 16], 'green', {up: ooze, north: edge, south: edge, west: edge, east: edge, down: Object.assign(tex('ooze'), {cullface: 'down'})}, GLOW)
            ]
        };
    })();

    // Alarm lamp (after the owner's drawing, 14 wide and 16 tall): a dark housing with vents and a red slot in front, a ring, a
    // cage of four posts and middle bars round a red glass column with a bulb glowing inside, a top ring, a cap and a knob.
    // Standing it is this model; hanging, it is turned over; on a wall it lies with its housing against the wall; on the side of a
    // post (a fence or a stone wall) it has a bracket reaching across to the post. Drawn in the translucent layer.
    (() => {
        const tex = key => ({texture: '#' + key}); // placed by position: one pixel per sixteenth of a block
        const housing = tex('housing'), vents = tex('vents'), glass = tex('glass'), bulb = tex('bulb'), metal = tex('metal');
        const all = t => ({north: {...t}, south: {...t}, west: {...t}, east: {...t}, up: {...t}, down: {...t}});
        const el = (from, to, faces, extra) => Object.assign({from, to, faces: faces.texture ? all(faces) : faces}, extra || {});
        const lampTextures = decorTextures({housing: 'lamp_housing', vents: 'lamp_vents', glass: 'lamp_glass', bulb: 'lamp_bulb', metal: 'lamp_metal'}, 'lamp_housing');
        const lamp = [
            el([1, 0, 1], [15, 5, 15], {north: tex('housing'), south: tex('vents'), west: tex('vents'), east: tex('vents'), up: tex('housing'), down: tex('housing')}),
            el([5, 1.5, 0.75], [11, 3, 1], {north: tex('glass'), west: tex('glass'), east: tex('glass'), up: tex('glass'), down: tex('glass')}, GLOW),
            el([2, 5, 2], [14, 6, 14], metal),
            el([2, 6, 2], [3.5, 13, 3.5], metal), el([12.5, 6, 2], [14, 13, 3.5], metal),
            el([2, 6, 12.5], [3.5, 13, 14], metal), el([12.5, 6, 12.5], [14, 13, 14], metal),
            without(el([3.5, 6, 3.5], [12.5, 13, 12.5], glass, GLOW), 'up', 'down'),
            el([6, 6.5, 6], [10, 12, 10], bulb, GLOW),
            el([7.5, 6, 3], [8.5, 13, 3.5], metal), el([7.5, 6, 12.5], [8.5, 13, 13], metal),
            el([3, 6, 7.5], [3.5, 13, 8.5], metal), el([12.5, 6, 7.5], [13, 13, 8.5], metal),
            el([2, 13, 2], [14, 14, 14], metal),
            el([4, 14, 4], [12, 15, 12], housing),
            el([6, 15, 6], [10, 16, 10], metal)
        ];
        placeUv(lamp);
        MODELS.emergency_lamp = {parent: 'minecraft:block/block', render_type: 'minecraft:translucent', textures: lampTextures, elements: lamp};
        MODELS.emergency_lamp_post = {parent: 'minecraft:block/block', render_type: 'minecraft:translucent', textures: lampTextures,
            elements: [...lamp, ...placeUv([without(el([6, -6, 6], [10, 0, 10], tex('metal')), 'up'), el([5, -6.5, 5], [11, -5.5, 11], tex('metal'))])]};
    })();

    // Turret (after the owner's drawing), three blocks, looking north. The base: four dark feet, a stepped light casing, a dark
    // collar and the foot of the column. The tower: the column, a turntable, the light housing with a red lamp on each side, a sight
    // on top and the sleeve of the barrel at the front. The barrel: a long dark barrel ending in a muzzle with its bore. The broken
    // turret: the same parts gone to rust, rubble round the base, the housing slumped forward and the barrel hanging down.
    (() => {
        const all = t => ({north: {...t}, south: {...t}, west: {...t}, east: {...t}, up: {...t}, down: {...t}});
        const tex = key => ({texture: '#' + key}); // placed by position: one pixel per sixteenth of a block
        const parts = (broken) => {
            const casing = tex('casing'), frame = tex('frame'), bore = tex('bore');
            const el = (from, to, t, extra) => Object.assign({from, to, faces: t.texture ? all(t) : t}, extra || {});
            const light = broken ? 'rust' : 'red';
            const slump = broken ? {rotation: {angle: -22.5, axis: 'x', origin: [8, 3, 8]}} : {};
            const hang = broken ? {rotation: {angle: -45, axis: 'x', origin: [8, 7.5, 16]}} : {};
            const base = [
                el([0, 0, 0], [3.5, 3, 3.5], frame), el([12.5, 0, 0], [16, 3, 3.5], frame),
                el([0, 0, 12.5], [3.5, 3, 16], frame), el([12.5, 0, 12.5], [16, 3, 16], frame),
                without(el([1.5, 0, 1.5], [14.5, 6, 14.5], casing), 'down'),
                el([3, 6, 3], [13, 10, 13], casing),
                el([4.5, 10, 4.5], [11.5, 13, 11.5], casing),
                el([4, 13, 4], [12, 14.5, 12], frame),
                without(el([6, 14.5, 6], [10, 16, 10], frame), 'up')
            ];
            if (broken) base.push(el([0, 0, 6.5], [1.25, 1.5, 8], frame), el([14.75, 0, 9], [16, 1, 10.5], casing), el([9, 0, 0], [10.5, 1, 1.25], casing), el([5, 0, 14.75], [6, 1, 16], frame));
            const tower = [
                without(el([6, 0, 6], [10, 2, 10], frame), 'down'),
                el([5, 2, 5], [11, 3, 11], frame),
                el([1.5, 3, 1.5], [14.5, 12, 15], casing, slump),
                el([6, 12, 4], [10, 13.5, 11], frame, slump),
                box([14.5, 5.5, 8], [15, 8, 12], light, {west: light}, broken ? slump : Object.assign({}, GLOW)),
                box([1, 5.5, 8], [1.5, 8, 12], light, {east: light}, broken ? slump : Object.assign({}, GLOW)),
                el([5, 5, 0], [11, 10, 1.5], frame, slump)
            ];
            const barrel = [
                el([5.5, 5, 6], [10.5, 9, 16], frame, hang),
                // the muzzle: 6 x 6 pixels on its face, the bore in the middle with a pixel of metal all round
                el([5, 4, 4], [11, 10, 6], Object.assign(all(frame), {north: bore}), hang)
            ];
            return {base: placeUv(base), tower: placeUv(tower), barrel: placeUv(barrel)};
        };
        for (const [id, broken] of [['turret', false], ['wrecked_turret', true]]) {
            const p = parts(broken);
            const t = broken ? {casing: 'turret_casing_broken', frame: 'turret_frame_broken', bore: 'turret_bore'} : {casing: 'turret_casing', frame: 'turret_frame', bore: 'turret_bore'};
            const textures = decorTextures(t, t.casing);
            for (const part of ['base', 'tower', 'barrel']) MODELS[id + '_' + part] = {parent: 'minecraft:block/block', textures, elements: p[part]};
            // the item: the three parts put together, shrunk to fit
            const shift = (elements, dy, dz) => elements.map(e => {
                const c = JSON.parse(JSON.stringify(e));
                c.from = [c.from[0], c.from[1] + dy, c.from[2] + dz]; c.to = [c.to[0], c.to[1] + dy, c.to[2] + dz];
                if (c.rotation) c.rotation.origin = [c.rotation.origin[0], c.rotation.origin[1] + dy, c.rotation.origin[2] + dz];
                return c;
            });
            MODELS[id + '_item'] = {parent: 'minecraft:block/block', textures,
                elements: [...shift(p.base, -8, 8), ...shift(p.tower, 8, 8), ...shift(p.barrel, 8, -8)],
                display: {
                    gui: {rotation: [30, 225, 0], translation: [0, -1, 0], scale: [0.36, 0.36, 0.36]},
                    ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.2, 0.2, 0.2]},
                    fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4]},
                    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25]},
                    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3]},
                    firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3]}
                }};
        }
    })();

    // Broken monitor: the same frame, the screen dead and cracked, no light.
    MODELS.broken_wall_monitor = {
        parent: 'minecraft:block/block',
        textures: decorTextures({screen: 'wall_monitor_screen_broken'}, 'wall_monitor_screen_broken'),
        elements: [
            box([1, 2, 14], [15, 14, 16], 'gun', {north: 'dark', south: 'dark'}),
            box([2, 3, 13.5], [14, 13, 14], 'dark', {north: tex('screen', [2, 3, 14, 13])}),
            box([6, 1, 14.5], [10, 2, 15.5], 'steel')
        ]
    };

    // Broken server rack: dead lights, two bays pulled out, loose wires hanging from the front.
    MODELS.broken_server_rack = {
        parent: 'minecraft:block/block',
        textures: decorTextures({front: 'server_rack_front_broken', side: 'server_rack_side_rusted'}, 'server_rack_side_rusted'),
        elements: [
            box([1, 0, 1], [15, 16, 15], 'dark', {west: tex('side'), east: tex('side'), south: tex('side'), up: 'gun', down: 'gun'}),
            without(box([1.5, 0.5, 0.5], [14.5, 15.5, 1], 'gun', {north: tex('front', [1, 0, 15, 16])}), 'south'),
            without(box([0.5, 0, 0.5], [1.5, 16, 1.5], 'steel'), 'up', 'down'),
            without(box([14.5, 0, 0.5], [15.5, 16, 1.5], 'steel'), 'up', 'down'),
            box([4, 3, 0.1], [4.5, 9, 0.4], 'copper', {north: 'copper'}, {rotation: {angle: 22.5, axis: 'z', origin: [4.25, 9, 0.25]}}),
            box([6, 2, 0.1], [6.5, 8, 0.4], 'cyan', {north: 'cyan'}),
            box([9, 1, 0], [13, 2.5, 0.5], 'gun', {north: 'dark'}, {rotation: {angle: 22.5, axis: 'z', origin: [9, 2.5, 0.25]}})
        ]
    };

    // Broken specimen tank: the glass smashed down to jagged stumps, the liquid drained to a puddle, the specimen slumped on the floor.
    (() => {
        const shard = tex('shard'), metal = tex('metal');
        const skin = (u, v, w, h) => tex('specimen', [u, v, u + w, v + h]);
        const body = (from, to, uv, extra) => { const f = skin(...uv); return Object.assign({from, to, faces: {north: f, south: f, west: f, east: f, up: f, down: f}}, extra || {}); };
        MODELS.broken_specimen_tank = {
            parent: 'minecraft:block/block', render_type: 'minecraft:translucent',
            textures: decorTextures({shard: 'tank_glass_broken', specimen: 'specimen_dead', metal: 'tank_metal', liquid: 'specimen_tank_liquid'}, 'tank_metal'),
            elements: [
                box([1, 0, 1], [15, 2, 15], 'gun', {north: tex('metal', [0, 6, 16, 8]), south: tex('metal', [0, 6, 16, 8]), west: tex('metal', [0, 6, 16, 8]), east: tex('metal', [0, 6, 16, 8]), up: 'steel'}),
                box([1, 14, 1], [15, 16, 15], 'gun', {north: tex('metal', [0, 0, 16, 2]), south: tex('metal', [0, 0, 16, 2]), west: tex('metal', [0, 0, 16, 2]), east: tex('metal', [0, 0, 16, 2]), up: metal, down: 'steel'}),
                without(box([1, 2, 1], [2, 14, 2], 'steel'), 'up', 'down'),
                without(box([14, 2, 1], [15, 14, 2], 'steel'), 'up', 'down'),
                without(box([1, 2, 14], [2, 14, 15], 'steel'), 'up', 'down'),
                without(box([14, 2, 14], [15, 14, 15], 'steel'), 'up', 'down'),
                // jagged stumps of glass on three sides, the back pane cracked but whole
                {from: [2, 2, 2], to: [14, 8, 2.25], faces: {north: tex('shard', [0, 8, 16, 16]), south: tex('shard', [0, 8, 16, 16])}},
                {from: [2, 2, 2.25], to: [2.25, 10, 13.75], faces: {west: tex('shard', [0, 6, 16, 16]), east: tex('shard', [0, 6, 16, 16])}},
                {from: [13.75, 2, 2.25], to: [14, 6, 13.75], faces: {west: tex('shard', [0, 10, 16, 16]), east: tex('shard', [0, 10, 16, 16])}},
                {from: [2, 2, 13.75], to: [14, 14, 14], faces: {north: tex('shard', [0, 4, 16, 16]), south: tex('shard', [0, 4, 16, 16])}},
                {from: [2.5, 2, 2.5], to: [13.5, 2.3, 13.5], faces: {up: tex('liquid', [2, 2, 14, 14])}},
                // the specimen: lying curled on its side, its feeding tube torn and hanging
                body([4, 2.3, 7], [7.5, 4.8, 10], [8, 0, 5, 7], {rotation: {angle: 22.5, axis: 'y', origin: [5.75, 3, 8.5]}}),
                body([7.5, 2.3, 7.5], [10.5, 5.3, 10.5], [0, 0, 6, 6], {rotation: {angle: -22.5, axis: 'z', origin: [9, 2.3, 9]}}),
                body([3, 2.3, 9.5], [5, 3.3, 12], [0, 8, 2, 5]),
                body([5, 2.3, 4.5], [6, 3.3, 7], [12, 8, 2, 5]),
                box([7.75, 9, 7.75], [8.25, 14, 8.25], 'rubber', {up: 'rubber', down: 'rubber'})
            ]
        };
    })();

    // Rusted machines: the machine's own model (switched off) with every texture swapped for its rusted copy and no light.
    const RUSTED = ['electronics_fabricator', 'assembly_station', 'armor_workstation', 'maintenance_station', 'implant_fabricator', 'body_modification_station',
        'armor_upgrade_fabricator', 'weapon_workshop', 'combustion_generator', 'battery', 'solar_panel', 'air_filtration_unit', 'shelter_control_panel', 'heater', 'backup_battery'];
    RUSTED.forEach(id => {
        const base = JSON.parse(JSON.stringify(MODELS[id]));
        Object.keys(base.textures).forEach(k => { base.textures[k] = base.textures[k] + '_rusted'; });
        base.elements.forEach(e => { delete e.neoforge_data; });
        MODELS['rusted_' + id] = base;
    });

    // ---------------------------------------------------------------- block states

    const ROTATIONS = {north: 0, east: 90, south: 180, west: 270};

    function variant(id, extra) {
        return Object.assign({model: 'projectrebirth:block/' + id}, extra);
    }

    function rotation(y) {
        return y ? {y} : {};
    }

    const STATES = {};
    ['electronics_fabricator', 'assembly_station', 'armor_workstation', 'maintenance_station', 'implant_fabricator', 'body_modification_station', 'armor_upgrade_fabricator', 'weapon_workshop'].forEach(id => {
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

    // Shelter blocks of version 1.4.0.
    STATES.air_filtration_unit = {variants: {}};
    STATES.shelter_control_panel = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(working => {
            STATES.air_filtration_unit.variants['facing=' + facing + ',working=' + working] = variant('air_filtration_unit' + (working ? '_on' : ''), rotation(y));
            STATES.shelter_control_panel.variants['facing=' + facing + ',working=' + working] = variant('shelter_control_panel' + (working ? '_on' : ''), rotation(y));
        });
    });
    STATES.water_purifier = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(working => {
            STATES.water_purifier.variants['facing=' + facing + ',working=' + working] = variant('water_purifier' + (working ? '_on' : ''), rotation(y));
        });
    });
    STATES.monitoring_station = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(working => {
            STATES.monitoring_station.variants['facing=' + facing + ',working=' + working] = variant('monitoring_station' + (working ? '_on' : ''), rotation(y));
        });
    });
    STATES.weather_station = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(working => {
            STATES.weather_station.variants['facing=' + facing + ',working=' + working] = variant('weather_station' + (working ? '_on' : ''), rotation(y));
        });
    });
    STATES.backup_battery = {variants: {'working=false': variant('backup_battery'), 'working=true': variant('backup_battery_on')}};
    STATES.radiation_purge_station = {variants: {'working=false': variant('radiation_purge_station'), 'working=true': variant('radiation_purge_station_on')}};
    STATES.charging_pad = {variants: {'working=false': variant('charging_pad'), 'working=true': variant('charging_pad_on')}};
    STATES.heater = {variants: {'working=false': variant('heater'), 'working=true': variant('heater_on')}};
    STATES.decontamination_chamber = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        ['lower', 'upper'].forEach(half => {
            [false, true].forEach(working => {
                STATES.decontamination_chamber.variants['facing=' + facing + ',half=' + half + ',working=' + working] =
                    variant('decontamination_chamber_' + half + (working ? '_on' : ''), rotation(y));
            });
        });
    });

    STATES.radioisotope_generator = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(working => {
            STATES.radioisotope_generator.variants['facing=' + facing + ',working=' + working] =
                variant('radioisotope_generator' + (working ? '_on' : ''), rotation(y));
        });
    });
    STATES.leaking_waste_barrel = {variants: {'': variant('leaking_waste_barrel')}};
    STATES.turret_mount = {variants: {'': variant('turret_mount')}};
    STATES.service_post_plate = {variants: {'': variant('service_post_plate')}};
    STATES.service_post = {variants: {'': variant('service_post')}};

    // Structure decoration (1.6.0): facing blocks look the way the model looks at north; the tank and the sludge have no facing.
    ['wall_monitor', 'server_rack', 'ammo_crate', 'broken_wall_monitor', 'broken_server_rack', ...RUSTED.map(id => 'rusted_' + id)].forEach(id => {
        STATES[id] = {variants: {}};
        Object.entries(ROTATIONS).forEach(([facing, y]) => { STATES[id].variants['facing=' + facing] = variant(id, rotation(y)); });
    });
    STATES.specimen_tank = {variants: {'': variant('specimen_tank')}};
    STATES.broken_specimen_tank = {variants: {'': variant('broken_specimen_tank')}};
    STATES.emergency_lamp = {variants: {}};
    Object.entries(ROTATIONS).forEach(([facing, y]) => {
        [false, true].forEach(post => {
            const key = f => 'face=' + f + ',facing=' + facing + ',post=' + post;
            STATES.emergency_lamp.variants[key('floor')] = variant('emergency_lamp', rotation(y));
            STATES.emergency_lamp.variants[key('ceiling')] = variant('emergency_lamp', Object.assign({x: 180}, rotation(y)));
            // on a wall the lamp lies with its housing against the wall, as an end rod does
            STATES.emergency_lamp.variants[key('wall')] = variant(post ? 'emergency_lamp_post' : 'emergency_lamp', Object.assign({x: 90}, rotation(y)));
        });
    });
    ['turret', 'wrecked_turret'].forEach(id => {
        STATES[id] = {variants: {}};
        Object.entries(ROTATIONS).forEach(([facing, y]) => ['base', 'tower', 'barrel'].forEach(part => {
            STATES[id].variants['facing=' + facing + ',part=' + part] = variant(id + '_' + part, rotation(y));
        }));
    });
    STATES.irradiated_sludge = {variants: {'': variant('irradiated_sludge')}};
    STATES.turret_crate = {variants: {'': variant('turret_crate')}};

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
        const capsule = id.startsWith('transfer_capsule') || id === 'emergency_lamp_post' || id.endsWith('turret_barrel') || id.endsWith('turret_item')
            || id.startsWith('weather_station');
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
