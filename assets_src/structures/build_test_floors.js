// 1.8.0, block H: the larger floors of the game tests (the small ones, floor_3x3 to floor_9x9, are older and not made here).
// floor_24x24: 24 x 14 x 24, stone in the six lowest rows (y 0-5), so that tests can dig pits and pools into it; machines stand
// at y 6. floor_48x48 (the load measurement, group projectrebirth_bench): 48 x 8 x 48, one row of stone.
// Run: node assets_src/structures/build_test_floors.js
const fs = require('fs');
const path = require('path');
const {nbtFile, Build} = require('./structure_lib');

const DATA = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'data');
const OUT = path.join(DATA, 'projectrebirth_interface', 'structure');

function floor(size, height, thickness) {
    const b = new Build(size, height, size, 1);
    b.box(0, 0, 0, size - 1, thickness - 1, size - 1, 'minecraft:stone');
    return b;
}

fs.writeFileSync(path.join(OUT, 'floor_24x24.nbt'), nbtFile(floor(24, 14, 6).toNbt()));
fs.writeFileSync(path.join(DATA, 'projectrebirth_bench', 'structure', 'floor_48x48.nbt'), nbtFile(floor(48, 8, 1).toNbt()));
console.log('floor_24x24.nbt, floor_48x48.nbt written');
