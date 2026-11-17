// The contents of the Rebirth Codex in one place: categories, entries, their map places and pages, with the text in
// English and Russian. Writes assets/projectrebirth/codex/ (index.json, entries/*.json) and the codex strings of the
// language files (codex.projectrebirth.*; the strings of the screen itself are kept).
// Usage: node assets_src/codex/build_codex.js
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '../..') + '/src/main/resources/assets/projectrebirth/';
const P = 'projectrebirth:';

// Each category: id, icon, en/ru title, entries. Each entry: id, icon, x, y, parents, en/ru title, pages.
// A page: [type, value or null, en text or null, ru text or null]. Text pages: ['text', null, en, ru].
const CATEGORIES = [];
const category = (id, icon, en, ru, entries) => CATEGORIES.push({id, icon, en, ru, entries});
const entry = (id, icon, x, y, parents, en, ru, pages) => ({id, icon, x, y, parents, en, ru, pages});
const text = (en, ru) => ['text', null, en, ru];
const note = (en, ru) => ['note', null, en, ru];
const item = (id, en, ru) => ['item', id, en, ru];
const block = (id, en, ru) => ['block', id, en, ru];
const entity = (id, en, ru) => ['entity', id, en, ru];
const recipe = (id, en, ru) => ['recipe', id, en || null, ru || null];
// 1.7.0, block A: the plan of a multiblock, a turning model with a layer chosen by the mouse wheel
const multiblock = (id, en, ru) => ['multiblock', id, en || null, ru || null];

// ================================================================ basics

category('basics', P + 'rebirth_codex', 'Basics', 'Основы', [
    entry('welcome', P + 'rebirth_codex', 0, 0, [], 'The Rebirth Codex', 'Кодекс перерождения', [
        text('This book follows you through Project: Rebirth: from the first ores and machines to artificial bodies, power armor, factions and survival in a hostile world.\n\nEach tab on the left is a part of the mod. A map shows its entries; a line leads from what you need first to what follows.',
            'Эта книга ведёт вас через Project: Rebirth: от первых руд и машин до искусственных тел, силовой брони, фракций и выживания во враждебном мире.\n\nКаждая вкладка слева — часть мода. На её карте — записи; линия ведёт от того, что нужно сначала, к тому, что идёт следом.'),
        item(P + 'rebirth_codex', 'Every player gets the Codex once, on first entering the world. A lost one is made again on a crafting table.',
            'Каждый игрок получает Кодекс один раз, при первом входе в мир. Потерянный делается снова на верстаке.'),
        note('Click a medallion to open its entry. Arrows, the mouse wheel or A and D turn the pages; right-click, Escape or the red button return to the map. Hover over an item to see its name.',
            'Щёлкните медальон, чтобы открыть запись. Стрелки, колесо мыши или A и D листают страницы; ПКМ, Escape или красная кнопка возвращают к карте. Наведите на предмет, чтобы увидеть его название.'),
        recipe(P + 'rebirth_codex', 'A book and an iron ingot.', 'Книга и железный слиток.')
    ]),
    entry('path', P + 'circuit_board', 2, 0, ['welcome'], 'The Way Ahead', 'Путь развития', [
        text('The mod grows as a chain: every new station and every body needs what the previous ones made.\n\n1. Mine the new ores: tin, aluminum, silver, lithium, titanium. Copper and tin make bronze.\n2. The electronics fabricator (crafting table) makes circuit boards, processors and power cells.\n3. The assembly station makes actuators, synthetic fiber, the transfer capsule and the first artificial bodies.',
            'Мод развивается цепочкой: каждая следующая станция и каждое тело требуют того, что сделано на предыдущих.\n\n1. Добудьте новые руды: олово, алюминий, серебро, литий, титан. Из меди и олова — бронза.\n2. Цех электроники (верстак) делает платы, процессоры и энергоячейки.\n3. Сборочная станция — приводы, синтетическую ткань, капсулу переноса и первые искусственные тела.'),
        text('4. The armor workstation makes armor plates and the parts of power armor; then come the implant fabricator, the armor upgrade fabricator and the weapon workshop.\n5. Stations need energy: a combustion generator, solar panels, a battery, cable.\n6. The world is hostile from the first day: a respirator, filters and a roof at first, a sealed shelter with air filtration later.',
            '4. Станция брони — бронепластины и детали силовой брони; дальше цех имплантов, цех модулей брони и оружейная мастерская.\n5. Станциям нужна энергия: генератор на угле, солнечные панели, аккумулятор, кабель.\n6. Мир враждебен с первого дня: сначала респиратор, фильтры и крыша, позже — герметичное убежище с фильтрацией воздуха.'),
        text('7. Catastrophes leave contaminated land and ruins full of loot, machines and mutants. Before an expedition, learn the way: a weather station foresees the next catastrophe, a monitoring station maps the contamination around and prints it on paper, and in the field the analyzer, the sensor module of a body or the sensor unit of armor show what is near. Clean water comes from the water purifier.',
            '7. Катастрофы оставляют заражённые земли и руины с добычей, машинами и мутантами. Перед вылазкой разведайте путь: метеостанция предскажет следующую катастрофу, станция мониторинга составит карту заражения вокруг и напечатает её, а в поле покажут, что рядом, анализатор, сенсорный модуль тела или сенсорный блок брони. Чистую воду даёт водоочиститель.'),
        text('8. Multiblocks: big machines built of blocks. The body workshop assembles synthetic bodies and androids, the heavy dock power armor, turrets and the mech.\n\n9. The Garrison: an AI core (electronics fabricator) puts a machine to work for you. First a combat or work android on a simple body and a command console with a tactical tablet; then defence turrets around the shelter and a service post; at the top the guard mech.',
            '8. Мультиблоки: большие машины из блоков. Сборочный цех тел собирает синтетические тела и андроидов, тяжёлый док — силовую броню, турели и меха.\n\n9. Гарнизон: ядро ИИ (цех электроники) заставляет машину работать на вас. Сначала боевой или рабочий андроид на простом теле и пульт с тактическим планшетом; потом турели вокруг убежища и пост обслуживания; на вершине — мех охраны.'),
        note('Each tab of this book is one branch of that chain.', 'Каждая вкладка этой книги — одна ветка этой цепочки.')
    ]),
    entry('keys', 'minecraft:compass', 2, 2, ['welcome'], 'Keys and Settings', 'Клавиши и настройки', [
        text('Keys of the mod (change them in Controls, section "Project: Rebirth"):\n\nZ — return to the human body\nV (hold) — emergency release of the hatch of a shut-down armor\n9 in armor — the ability slot\nB — worker armor: mine 3×3 or one block\nN — floodlight of the armor\nR — reload a weapon\nG — the faction window\nH — HUD and transfer animation settings',
            'Клавиши мода (меняются в настройках управления, раздел «Project: Rebirth»):\n\nZ — вернуться в человеческое тело\nV (удерживать) — аварийный сброс люка отключённой брони\n9 в броне — слот способности\nB — рабочая броня: добыча 3×3 или 1 блок\nN — прожектор брони\nR — перезарядка оружия\nG — окно фракции\nH — настройки HUD и анимации переноса'),
        text('H opens the HUD settings: turn it on or off, its size and where the panels stand, the transfer animation, and the HUD of the garrison: bars over your machines, the console alarm mark, the tablet lines. They are kept for each player.\n\nThe balance of the mod is in config/projectrebirth-server.toml.',
            'H открывает настройки HUD: включение, размер, где стоят панели, анимацию переноса и HUD гарнизона: полосы над вашими машинами, значок тревоги пульта, строки планшета. Они хранятся у каждого игрока.\n\nБаланс всего мода — время, прочность, урон, стоимость — в файле config/projectrebirth-server.toml на сервере (в одиночной игре — в папке игры).')
    ])
]);

// ================================================================ technology

category('technology', P + 'electronics_fabricator', 'Technology', 'Технологии', [
    entry('ores', P + 'titanium_ore', -4, 0, [], 'Ores and Metals', 'Руды и металлы', [
        text('Five new ores lie in the world, in stone and in deepslate:\n\nTin — common, from y −16 to 112.\nAluminum — common, from 0 to 112.\nSilver — from −64 to 32.\nLithium — rare, from −48 to 16.\nTitanium — the rarest and deepest, from −80 to 0.\n\nMined ore gives raw metal; a furnace or a blast furnace makes ingots of it.',
            'В мире пять новых руд, в камне и в глубинном сланце:\n\nОлово — часто, от y −16 до 112.\nАлюминий — часто, от 0 до 112.\nСеребро — от −64 до 32.\nЛитий — редко, от −48 до 16.\nТитан — реже всего и глубже всего, от −80 до 0.\n\nДобытая руда даёт сырой металл; печь или плавильная печь делают из него слиток.'),
        block(P + 'tin_ore', 'Tin ore: needed for bronze from the very start.', 'Оловянная руда: нужна для бронзы с самого начала.'),
        block(P + 'deepslate_titanium_ore', 'Deepslate titanium ore: for the armor workstation, armor and the best bodies.', 'Титановая руда в глубинном сланце: для станции брони, брони и лучших тел.'),
        recipe(P + 'titanium_ingot_from_blasting', 'A blast furnace smelts raw metal twice as fast.', 'Плавильная печь плавит сырой металл вдвое быстрее.'),
        note('A sixth, radioactive ore, uranium, lies even deeper: see the entry Uranium in the Hostile Environment tab.', 'Шестая, радиоактивная руда — уран — лежит ещё глубже: смотрите запись «Уран» во вкладке «Враждебная среда».')
    ]),
    entry('bronze', P + 'bronze_ingot', -2, 0, ['ores'], 'Bronze', 'Бронза', [
        recipe(P + 'bronze_ingot', 'Three copper ingots and one tin ingot give four of bronze.', 'Три медных слитка и один оловянный дают четыре бронзовых.'),
        text('Bronze goes into the electronics fabricator itself and into actuators — the motors of bodies, armor and machines.',
            'Бронза идёт в сам цех электроники и в приводы — моторы тел, брони и машин.')
    ]),
    entry('stations', P + 'assembly_station', 0, 2, ['electronics_fabricator'], 'How Stations Work', 'Как работают станции', [
        text('Every production station works the same way. In its window pick a recipe from the list; put the materials into the six slots (the needed amounts are shown); the station makes the result by itself and goes on while there are materials and room in the output slot.',
            'Все станции производства работают одинаково. В окне выберите рецепт из списка; положите материалы в шесть слотов (нужное количество показано); станция делает результат сама и продолжает, пока есть материалы и место в выходном слоте.'),
        text('A station uses 20 FE of energy per tick while it works and holds 20 000 FE. Without energy it waits and keeps the progress made so far.\n\nHoppers and pipes of other mods put materials in and take the results out. With JEI installed, all station recipes are in it.',
            'Пока станция работает, она тратит 20 FE энергии за тик и держит запас 20 000 FE. Без энергии она ждёт и не теряет сделанного.\n\nВоронки и трубы других модов кладут материалы и забирают готовое. Если установлен JEI, все рецепты станций есть в нём.'),
        note('A server may let stations work without energy: [energy] stationsNeedEnergy = false.', 'Сервер может разрешить станциям работать без энергии: [energy] stationsNeedEnergy = false.')
    ]),
    entry('electronics_fabricator', P + 'electronics_fabricator', 0, 0, ['bronze'], 'Electronics Fabricator', 'Цех электроники', [
        block(P + 'electronics_fabricator', 'The first station: circuit boards, processors, power cells and the dosimeter.', 'Первая станция: печатные платы, процессоры, энергоячейки и дозиметр.'),
        recipe(P + 'electronics_fabricator', 'Made on a crafting table from iron, glass, bronze and redstone.', 'Делается на верстаке из железа, стекла, бронзы и красной пыли.'),
        recipe(P + 'circuit_board', 'Circuit boards: copper, silver and redstone. They are in almost everything.', 'Печатные платы: медь, серебро и красная пыль. Они почти во всём.'),
        recipe(P + 'processor', 'Processors, for advanced bodies and machines: boards, silver, quartz and gold.', 'Процессоры — для продвинутых тел и машин: платы, серебро, кварц и золото.'),
        recipe(P + 'power_cell', 'Power cells: lithium, aluminum, copper and redstone. They store energy in bodies, armor and batteries.', 'Энергоячейки: литий, алюминий, медь и красная пыль. Хранят энергию в телах, броне и аккумуляторах.')
    ]),
    entry('assembly_station', P + 'assembly_station', 2, 0, ['electronics_fabricator'], 'Assembly Station', 'Сборочная станция', [
        block(P + 'assembly_station', 'Actuators, synthetic fiber, the transfer capsule, robot bodies, the heavy actuator and the manipulators. Synthetic bodies are assembled in the body workshop (Garrison tab).', 'Приводы, синтетическая ткань, капсула переноса, тела-роботы, тяжёлый привод и манипуляторы. Синтетические тела собираются в сборочном цехе тел (вкладка «Гарнизон»).'),
        recipe(P + 'assembly_station', 'Aluminum, circuit boards, a crafting table and iron.', 'Алюминий, платы, верстак и железо.'),
        recipe(P + 'actuator', 'Actuators: bronze, iron and redstone.', 'Приводы: бронза, железо и красная пыль.'),
        recipe(P + 'synthetic_fiber', 'Synthetic fiber, the skin of synthetic bodies: string and a slime ball.', 'Синтетическая ткань — кожа синтетических тел: нитки и слизь.')
    ]),
    entry('armor_workstation', P + 'armor_workstation', 4, -1, ['assembly_station'], 'Armor Workstation', 'Станция брони', [
        block(P + 'armor_workstation', 'Armor plates, the heavy protective suit and the parts of power armor: the power frame and the hulls of three kinds. The armor itself is assembled in the heavy dock (Garrison tab).', 'Бронепластины, тяжёлый защитный костюм и детали силовой брони: силовой каркас и корпуса трёх видов. Саму броню собирают в тяжёлом сборочном доке (вкладка «Гарнизон»).'),
        recipe(P + 'armor_workstation', 'Titanium, actuators, an anvil and iron.', 'Титан, приводы, наковальня и железо.'),
        recipe(P + 'armor_plate', 'Armor plates: the shell of power armor and a part of later stations.', 'Бронепластины: корпус силовой брони и часть поздних станций.')
    ]),
    entry('implant_fabricator', P + 'implant_fabricator', 4, 1, ['assembly_station'], 'Implant Fabricator', 'Цех имплантов', [
        block(P + 'implant_fabricator', 'Gadgets for artificial bodies: armor plates of copper, iron or titanium, the sealing module, the jetpack.',
            'Гаджеты для искусственных тел: бронепластины из меди, железа или титана, модуль герметизации, джетпак.'),
        recipe(P + 'implant_fabricator', 'Iron, a circuit board, actuators and a smithing table.', 'Железо, плата, приводы и стол кузнеца.'),
        text('More in the tab of bodies.', 'Подробнее — во вкладке тел.')
    ]),
    entry('armor_upgrade_fabricator', P + 'armor_upgrade_fabricator', 5, -2, ['armor_workstation'], 'Armor Upgrade Fabricator', 'Цех модулей брони', [
        block(P + 'armor_upgrade_fabricator', 'Upgrades of power armor: plating of copper, iron or titanium, the compressed air module, the floodlight.',
            'Улучшения силовой брони: обшивка из меди, железа или титана, модуль сжатого воздуха, прожектор.'),
        recipe(P + 'armor_upgrade_fabricator', 'Iron, an armor plate, actuators and a smithing table.', 'Железо, бронепластина, приводы и стол кузнеца.'),
        text('They are put on with the mounting tool, made on a crafting table. More in the tab of armor.', 'Ставятся устройством монтажа, оно делается на верстаке. Подробнее — во вкладке брони.')
    ]),
    entry('weapon_workshop', P + 'weapon_workshop', 5, 0, ['armor_workstation'], 'Weapon Workshop', 'Оружейная мастерская', [
        block(P + 'weapon_workshop', 'Guns of power armor and their rounds: two rifles and a combat shotgun.', 'Оружие силовой брони и патроны к нему: две винтовки и боевой дробовик.'),
        recipe(P + 'weapon_workshop', 'Iron, an armor plate, circuit boards and a smithing table.', 'Железо, бронепластина, платы и стол кузнеца.'),
        text('More in the tab of armor.', 'Подробнее — во вкладке брони.')
    ]),
    entry('maintenance_station', P + 'maintenance_station', 2, 2, ['assembly_station'], 'Maintenance Station', 'Станция обслуживания', [
        block(P + 'maintenance_station', 'Repairs packed armor and bodies. The critical integrity of armor is mended only here.',
            'Чинит упакованную броню и тела. Критическая целостность брони чинится только здесь.'),
        recipe(P + 'maintenance_station', 'Iron, an actuator, circuit boards and an anvil.', 'Железо, привод, платы и наковальня.'),
        text('Put the packed armor or body in and add the materials: every two seconds the station uses one of them and 20 FE per tick.\n\nArmor: armor plates mend the critical integrity, iron ingots the structural one, up to the maximum with the plating of its upgrades. Robot bodies take iron ingots, synthetic bodies synthetic fiber. Since 1.5 the station also takes wear off bodies: actuators for robots, circuit boards for synthetics.',
            'Положите упакованную броню или тело и материалы: каждые две секунды станция тратит один предмет и 20 FE за тик.\n\nБроня: бронепластины чинят критическую целостность, железные слитки — структурную, до максимума с учётом обшивки улучшений. Роботизированные тела чинятся железом, синтетические — синтетической тканью. С 1.5 станция снимает и износ тел: у роботов приводами, у синтетических — платами.')
    ])
]);

// ================================================================ energy

category('energy', P + 'battery', 'Energy', 'Энергия', [
    entry('energy_basics', P + 'cable', 0, 0, [], 'Energy', 'Энергия', [
        text('Machines of the mod run on energy in FE (NeoForge Energy); it works with the machines and cables of other mods.\n\nStations use 20 FE per tick while they work; the air filtration unit, the decontamination chamber and the purge station use their own amounts. Every machine keeps a store of its own.',
            'Машины мода работают на энергии в FE (NeoForge Energy); она совместима с машинами и кабелями других модов.\n\nСтанции тратят 20 FE за тик, пока работают; у установки фильтрации, камеры и станции очистки — свой расход. У каждой машины свой запас.'),
        note('A generator gives energy to the blocks right next to it and into cables; the cable carries it to everything joined to it.', 'Генератор отдаёт энергию соседним блокам и в кабели; кабель несёт её ко всему, что к нему подключено.')
    ]),
    entry('combustion_generator', P + 'combustion_generator', -2, 1, ['energy_basics'], 'Combustion Generator', 'Топливный генератор', [
        block(P + 'combustion_generator', 'Burns anything a furnace burns, for as long as a furnace would, and makes 40 FE per tick meanwhile.', 'Жжёт всё, что жжёт печь, столько же времени, и делает при этом 40 FE за тик.'),
        recipe(P + 'combustion_generator', 'Iron, a furnace, copper and redstone — the first energy of the game.', 'Железо, печь, медь и красная пыль — первая энергия в игре.'),
        text('It stops burning while its store is full, so no fuel is wasted, and gives up to 200 FE per tick to its neighbours and cables. A hopper can bring fuel; an empty bucket after lava comes out.',
            'Пока запас полон, он не жжёт топливо — ничего не пропадает — и отдаёт до 200 FE за тик соседям и в кабели. Топливо может подавать воронка; пустое ведро после лавы она забирает.')
    ]),
    entry('solar_panel', P + 'solar_panel', 2, 1, ['energy_basics'], 'Solar Panel', 'Солнечная панель', [
        block(P + 'solar_panel', '8 FE per tick in daylight under the open sky; nothing at night and in rain, a quarter in a dust storm.', '8 FE за тик днём под открытым небом; ночью и в дождь — ничего, в пыльную бурю — четверть.'),
        recipe(P + 'solar_panel', 'Glass, aluminum, a circuit board and iron.', 'Стекло, алюминий, плата и железо.'),
        note('A panel gives energy only downwards: put the cable or the machine under it. It is placed like stairs, its high edge away from you.', 'Панель отдаёт энергию только вниз: кабель или машину ставьте под неё. Ставится как ступенька, высоким краем от вас.')
    ]),
    entry('radioisotope_generator', P + 'radioisotope_generator', -2, 3, ['combustion_generator'], 'Radioisotope Generator', 'Радиоизотопный генератор', [
        block(P + 'radioisotope_generator', 'Makes 24 FE per tick all the time, with no fuel and in any weather; holds 40 000 FE. It gives energy to its neighbours and cables, and a comparator reads its charge.',
            'Вырабатывает 24 FE за тик всегда, без топлива и в любую погоду; хранит 40 000 FE. Отдаёт энергию соседям и в кабели, компаратор читает заряд.'),
        recipe(P + 'radioisotope_generator', 'Titanium, uranium, processors and a power cell: a late machine.', 'Титан, уран, процессоры и энергоячейка: поздняя машина.'),
        note('It radiates around itself, 3 units next to it and up to 6 blocks. Put it behind walls, away from where you live and work: each solid block halves it.',
            'Он фонит вокруг: 3 единицы вплотную, до 6 блоков. Поставьте его за стенами, подальше от жилья и работы: каждый сплошной блок ослабляет излучение вдвое.')
    ]),
    entry('cable', P + 'cable', 0, 2, ['energy_basics'], 'Energy Cable', 'Энергокабель', [
        item(P + 'cable', 'Joins generators, batteries and machines, of this mod and of others. A cable network can be up to 4096 cables long.', 'Соединяет генераторы, аккумуляторы и машины — этого мода и других. Сеть кабеля — до 4096 кусков.'),
        recipe(P + 'cable', 'Copper, redstone, copper: six cables.', 'Медь, красная пыль, медь: шесть кабелей.'),
        text('Energy put into a cable goes to every machine on it that takes energy: an equal share each, then what is left in turn.\n\nSince 1.4.0 life support comes first: while the air filtration unit and the other life support blocks are short, production stations on the same cable wait. The cable also passes through the wall of a sealed room without letting the air out.',
            'Энергия, отданная в кабель, идёт ко всем машинам на нём, которые её принимают: сначала поровну, остаток по очереди.\n\nС 1.4.0 первым идёт жизнеобеспечение: пока установке фильтрации и другим блокам жизнеобеспечения не хватает, станции производства на том же кабеле ждут. Кабель проходит сквозь стену герметичного помещения, не выпуская воздух.')
    ]),
    entry('battery', P + 'battery', 2, 3, ['cable'], 'Battery', 'Аккумулятор', [
        block(P + 'battery', 'Stores 500 000 FE. It takes energy on every side and gives it only through its front, marked with an arrow.', 'Хранит 500 000 FE. Принимает энергию со всех сторон и отдаёт только через переднюю сторону со стрелкой.'),
        recipe(P + 'battery', 'Lithium, redstone, a power cell and iron.', 'Литий, красная пыль, энергоячейка и железо.'),
        text('Put it between generators and the machines, front towards the machines: it never sends energy back the way it came. Up to 1000 FE per tick in and out. A comparator reads its charge; its screen glows while the charge changes.',
            'Ставьте его между генераторами и машинами передом к машинам: обратно он энергию не отдаёт. До 1000 FE за тик на вход и выход. Компаратор читает заряд; экран светится, пока заряд меняется.')
    ])
]);

// ================================================================ bodies

const bodyClass = (id, x, y, parents, en, ru, captionEn, captionRu, recipeEn, recipeRu) => entry(id, P + id, x, y, parents, en, ru, [
    entity(P + id, captionEn, captionRu),
    recipe(P + id, recipeEn, recipeRu)
]);

category('bodies', P + 'basic_robot_body', 'Bodies', 'Тела', [
    entry('consciousness', P + 'transfer_capsule', -4, 0, [], 'Transfer of Consciousness', 'Перенос сознания', [
        text('Your mind can leave the human body and live in an artificial one. The human body stays behind in the transfer capsule; there it is safe from hunger but not from attack.',
            'Сознание может покинуть человеческое тело и жить в искусственном. Человеческое тело остаётся в капсуле переноса: там оно не голодает, но уязвимо для ударов.'),
        block(P + 'transfer_capsule', 'The transfer capsule, two blocks high, made at the assembly station.', 'Капсула переноса в два блока высотой, делается на сборочной станции.'),
        recipe(P + 'transfer_capsule'),
        text('Place a body from its item. Right-click the capsule: it lists all your bodies — class, integrity, distance, state — with a button "Transfer". A body must stand within 16 blocks of the capsule.\n\nZ returns you to the human body. If the artificial body is destroyed, the mind comes back by itself; without a human body it goes to a spare body, and with no bodies left an ordinary death follows.',
            'Поставьте тело из предмета. ПКМ по капсуле — список всех ваших тел (класс, прочность, расстояние, состояние) с кнопкой «Перенестись». Тело должно стоять не дальше 16 блоков от капсулы.\n\nZ возвращает в человеческое тело. Если искусственное тело уничтожено, сознание возвращается само; без человеческого тела — переходит в запасное, а если тел не осталось, наступает обычная смерть.'),
        note('Bodies and their inventories survive leaving the game and a server restart.', 'Тела и их инвентарь переживают выход из игры и перезапуск сервера.')
    ]),
    bodyClass('basic_robot_body', -2, 0, ['consciousness'], 'Basic Robot', 'Базовый робот',
        'Class 1, robotic. Integrity 20, armor 2, speed ×0.85. One gadget slot.', 'Класс 1, роботизированный. Прочность 20, броня 2, скорость ×0,85. Один слот гаджета.',
        'The first body, made at the assembly station.', 'Первое тело, делается на сборочной станции.'),
    bodyClass('improved_robot_body', 0, 0, ['basic_robot_body'], 'Improved Robot', 'Улучшенный робот',
        'Class 2, robotic. Integrity 26, armor 4, speed ×0.95, damage +0.5. Two gadget slots.', 'Класс 2, роботизированный. Прочность 26, броня 4, скорость ×0,95, урон +0,5. Два слота гаджетов.',
        'A better body is made from the one before it; its gadgets go over to the new one.', 'Лучшее тело делается из предыдущего; его гаджеты переходят в новое.'),
    bodyClass('advanced_synthetic_body', 2, 0, ['improved_robot_body'], 'Advanced Synthetic', 'Продвинутый синтетик',
        'Class 3, synthetic. Integrity 30, armor 6, speed ×1.0, damage +1. Mends itself slowly. Three slots.', 'Класс 3, синтетический. Прочность 30, броня 6, скорость ×1,0, урон +1. Понемногу чинится сам. Три слота.',
        'Synthetic bodies need synthetic fiber.', 'Синтетическим телам нужна синтетическая ткань.'),
    bodyClass('hightech_synthetic_body', 4, 0, ['advanced_synthetic_body'], 'High-Tech Synthetic', 'Высокотехнологичный синтетик',
        'Class 4, synthetic. Integrity 36, armor 8, speed ×1.1, damage +2. Four slots.', 'Класс 4, синтетический. Прочность 36, броня 8, скорость ×1,1, урон +2. Четыре слота.',
        'Titanium, processors and a power cell.', 'Титан, процессоры и энергоячейка.'),
    bodyClass('premium_synthetic_body', 4, 2, ['hightech_synthetic_body'], 'Premium Synthetic', 'Премиальный синтетик',
        'Class 5, synthetic. Integrity 40, armor 10, speed ×1.2, damage +3, the fastest self-repair. Five slots.',
        'Класс 5, синтетический. Прочность 40, броня 10, скорость ×1,2, урон +3, быстрее всех чинится сам. Пять слотов.',
        'The best body of the mod: it also needs netherite and an echo shard. It is the only one that eats golden apples and drinks potions.', 'Лучшее тело мода: ему нужны ещё незерит и эхо-осколок. Единственное ест золотые яблоки и пьёт зелья.'),
    entry('body_life', P + 'repair_kit', -2, 2, ['basic_robot_body'], 'Life in a Body', 'Жизнь в теле', [
        text('Instead of hearts a body has an integrity bar; it has no hunger. Bodies of classes 1–4 neither eat nor drink, and potions do not work on them.\n\nBodies do not wear ordinary armor, elytra, pumpkins or heads. Their inventory is their own: in place of armor slots it has a panel with the class, integrity and gadget slots.',
            'Вместо сердец у тела полоса прочности, голода нет. Тела классов 1–4 не едят и не пьют, зелья на них не действуют.\n\nОбычную броню, элитры, тыкву и головы тела не носят. Инвентарь у тела свой: вместо слотов брони — панель с классом, прочностью и слотами гаджетов.'),
        text('Synthetic bodies (classes 3–5) mend themselves a few seconds after damage, while they have charge; it costs charge.\n\nAn empty body stands like a solid thing: you can stand on it and nothing pushes it away. Since 1.5 every body runs on charge — see Body Charge.',
            'Синтетические тела (классы 3–5) понемногу чинятся сами через несколько секунд после урона, пока есть заряд; на это уходит заряд.\n\nПустое тело стоит как твёрдый предмет: на него можно встать, его ничто не сдвигает. С 1.5 каждое тело работает на заряде — см. «Заряд тела».'),
        item(P + 'repair_kit', 'The repair kit mends 35% of integrity in 4 seconds; damage breaks the repair off.', 'Ремкомплект за 4 секунды чинит 35% прочности; урон прерывает ремонт.'),
        recipe(P + 'repair_kit'),
        note('A packed body is mended at the maintenance station: robots with iron, synthetics with synthetic fiber.', 'Упакованное тело чинит станция обслуживания: роботов — железом, синтетиков — синтетической тканью.')
    ]),
    entry('body_modification', P + 'body_modification_station', 2, -2, ['advanced_synthetic_body'], 'Body Modification', 'Модификация тел', [
        block(P + 'body_modification_station', 'Puts gadgets into a packed body and takes them out.', 'Ставит гаджеты в упакованное тело и снимает их.'),
        recipe(P + 'body_modification_station'),
        text('Put the packed body in and pick a gadget: installing takes 5 seconds and energy, removing is instant. A body has as many gadget slots as its class — from one to five.\n\nWhen a body is made into a better one at the assembly station, its gadgets go over; those that do not fit come back as items.',
            'Положите упакованное тело и выберите гаджет: установка занимает 5 секунд и энергию, снятие мгновенно. Слотов гаджетов у тела столько, каков его класс, — от одного до пяти.\n\nКогда тело улучшают на сборочной станции, гаджеты переходят в новое; лишние возвращаются предметами.')
    ]),
    entry('gadgets', P + 'jetpack', 4, -2, ['body_modification'], 'Gadgets', 'Гаджеты', [
        text('Gadgets are made at the implant fabricator.\n\nArmor plates add armor: copper +1, iron +2, titanium +3 and +1 toughness, for each plate. Different materials do not mix on one body.',
            'Гаджеты делаются в цехе имплантов.\n\nБронепластины добавляют броню: медь +1, железо +2, титан +3 и +1 к стойкости — за каждую пластину. Разные материалы на одном теле не смешиваются.'),
        recipe(P + 'titanium_plating'),
        item(P + 'sealing_module', 'The sealing module keeps the air under water and, since 1.3, keeps acid and dust off the body. It does not save from being stuck in blocks.',
            'Модуль герметизации сохраняет воздух под водой, а с 1.3 защищает тело от кислоты и пыли. От удушья в блоках не спасает.'),
        recipe(P + 'sealing_module'),
        item(P + 'jetpack', 'The jetpack: hold jump in the air to rise up to 8 blocks above the ground under the body. 6 seconds of thrust, refilled only on the ground; with none left the body falls as usual.',
            'Джетпак: удерживайте прыжок в воздухе — тело поднимается до 8 блоков над землёй под собой. 6 секунд тяги, пополняется только на земле; без тяги тело падает как обычно.'),
        recipe(P + 'jetpack'),
        item(P + 'extra_battery', 'The extra battery adds 40 000 FE to the charge of a body and takes one slot.', 'Дополнительный аккумулятор добавляет телу 40 000 FE заряда и занимает один слот.'),
        recipe(P + 'extra_battery'),
        item(P + 'cooling_module', 'The cooling module takes heat away all the time: a body with it overheats much later.', 'Модуль охлаждения постоянно отводит тепло: тело с ним перегревается гораздо позже.'),
        recipe(P + 'cooling_module'),
        item(P + 'emi_shielding', 'The electronics shielding takes away 90% of the interference of an EMI storm; the recipe is in the entry EMI Storm.',
            'Экранирование электроники гасит 90% помех ЭМИ-бури; рецепт — в записи «ЭМИ-буря».')
    ]),
    entry('body_wear', P + 'actuator', -4, 2, ['body_life'], 'Wear', 'Износ', [
        text('Since 1.5 a body has a second scale beside integrity: wear, from 0 to 100%. Integrity is lost to blows, falls, fire and weapons. Wear is the tiredness of the mechanisms: it grows slowly with work, fast while the body is overheated, and from acid rain, dust and faults in an EMI storm.',
            'С 1.5 у тела вторая шкала рядом с прочностью — износ, от 0 до 100%. Прочность отнимают удары, падения, огонь и оружие. Износ — усталость механизмов: медленно копится от работы, быстро — пока тело перегрето, а ещё от кислотного дождя, пыли и сбоев в ЭМИ-бурю.'),
        text('25%: more charge used. 50%: slower, half the self-repair, faster heating. 75%: weaker blows. 100%: critical — half the integrity, much slower, no self-repair.\n\nA worn body is never destroyed by wear and stays under control.',
            '25%: заряд тратится быстрее. 50%: медленнее, самовосстановление вдвое слабее, нагрев быстрее. 75%: удары слабее. 100%: критический — прочность вдвое ниже, тело гораздо медленнее, самовосстановления нет.\n\nИзнос никогда не уничтожает тело и не отнимает управление.'),
        text('Wear does not mend by itself and not with a repair kit. Pack the body and service it at the maintenance station: actuators take wear off robots, circuit boards off synthetic bodies, 10% each. Better bodies wear more slowly.',
            'Износ не проходит сам и не снимается ремкомплектом. Упакуйте тело и обслужите его на станции обслуживания: у роботов износ снимают приводы, у синтетических — платы, по 10% за штуку. Лучшие тела изнашиваются медленнее.'),
        recipe(P + 'actuator', 'An actuator: wear of robot bodies.', 'Привод — от износа роботов.'),
        recipe(P + 'circuit_board', 'A circuit board: wear of synthetic bodies.', 'Плата — от износа синтетиков.')
    ]),
    entry('body_heat', P + 'cooling_module', 2, 2, ['body_charge'], 'Overheating', 'Перегрев', [
        text('Since 1.5 a body heats up. Running, blows, mining and above all the jetpack heat it, and so do deserts, badlands, the Nether, fire, lava and magma close by.\n\nRest, shade, rain, cold lands and most of all water cool it. Better bodies cool faster.',
            'С 1.5 тело нагревается. Его греют бег, удары, добыча и сильнее всего джетпак, а ещё пустыни, столовые горы, Незер, огонь, лава и магма рядом.\n\nОстужают покой, тень, дождь, холодные земли и сильнее всего вода. Лучшие тела остывают быстрее.'),
        text('From 70 the body warns. Above 85 it throttles: slower, weaker blows, no jetpack. At 100 it has overheated: it stays throttled and wears fast until it cools below 85.\n\nOverheating never destroys a body and never takes control: lead it into water or shade.',
            'С 70 тело предупреждает. Выше 85 сбрасывает мощность: медленнее, удары слабее, джетпак не работает. На 100 тело перегрелось: остаётся слабым и быстро изнашивается, пока не остынет ниже 85.\n\nПерегрев никогда не уничтожает тело и не отнимает управление: отведите его в воду или тень.'),
        note('A power armor does not heat up: a body inside one only cools. An empty body standing in the world cools down too.',
            'Силовая броня не нагревается: тело в ней только остывает. Пустое тело, стоящее в мире, тоже остывает.')
    ]),
    entry('body_charge', P + 'body_battery', 0, 2, ['body_life'], 'Body Charge', 'Заряд тела', [
        text('Since 1.5 every artificial body runs on charge (FE). Walking, running, blows, breaking blocks, self-repair and above all the jetpack spend it. An empty body standing in the world keeps its charge; it goes with the body when you enter it, leave it or pack it.',
            'С 1.5 каждое искусственное тело работает на заряде (FE). Его тратят шаг, бег, удары, добыча, самовосстановление и сильнее всего джетпак. Пустое тело, стоящее в мире, заряд не тратит; заряд переходит вместе с телом, когда вы в него входите, выходите или упаковываете его.'),
        text('A full basic body lasts about a game day of work, the premium one about four.\n\nBelow 20% the body warns and slows down. At 0% it is in emergency mode: slow walking, no running, jumping, jetpack or self-repair, slow mining. You keep control; Z always returns you home.',
            'Полного базового тела хватает примерно на игровой день работы, премиального — на четыре.\n\nНиже 20% тело предупреждает и замедляется. На 0% — аварийный режим: медленный шаг, без бега, прыжков, джетпака и самовосстановления, добыча медленнее. Управление остаётся, Z всегда возвращает домой.'),
        block(P + 'charging_pad', 'A body standing on the pad, empty or yours, is charged from the cable.', 'Тело на платформе, пустое или ваше, заряжается от кабеля.'),
        recipe(P + 'charging_pad'),
        item(P + 'body_battery', 'Hold right-click in a body: in 3 seconds it hands over its charge. Damage breaks it off.', 'Удерживайте ПКМ в теле: за 3 секунды он отдаст заряд. Урон прерывает.'),
        recipe(P + 'body_battery'),
        note('Charge the body battery in the pad: right-click the pad with it, right-click with an empty hand to take it back. Chargers of other mods work too.',
            'Аккумулятор тела заряжается в платформе: ПКМ по ней с аккумулятором, ПКМ пустой рукой — забрать. Подходят и зарядники других модов.'),
        note('In the HUD, above the integrity bar: thin bars of heat, charge and wear, each with its mark, and the numbers in the panel. Marks right of the integrity bar warn: no charge, overheating, critical wear, interference.',
            'В HUD над полосой прочности — тонкие полосы нагрева, заряда и износа, у каждой свой значок, а цифры — в панели. Значки справа от полосы прочности предупреждают: нет заряда, перегрев, критический износ, помехи.')
    ])
]);

// ================================================================ power armor

category('armor', P + 'power_armor', 'Power Armor', 'Силовая броня', [
    entry('power_armor', P + 'power_armor', -3, 0, [], 'Power Armor', 'Силовая броня', [
        entity(P + 'power_armor', 'A walking suit of armor for a human or a body inside it.', 'Шагающая броня для человека или тела внутри.'),
        recipe(P + 'dock_power_armor', 'Assembled in the heavy dock (see the Garrison tab).', 'Собирается в тяжёлом сборочном доке (вкладка «Гарнизон»).'),
        text('Place it from its item. Right-click to climb in, Shift to get out; the combat armor opens first: the chest plate swings down, the visor lifts. The pilot is protected: all damage goes to the armor.\n\nArmor strikes with both hands for more damage, reaches farther and jumps higher; items in its fists keep their poses — bow, crossbow, trident, shield.',
            'Ставится из предмета. ПКМ — сесть, Shift — выйти; боевая броня сначала раскрывается: грудная плита откидывается, забрало поднимается. Пилот защищён: весь урон получает броня.\n\nБроня бьёт обеими руками сильнее, дотягивается дальше и прыгает выше; предметы в её кулаках держатся как надо — лук, арбалет, трезубец, щит.'),
        note('In armor the 9th hotbar slot becomes the ability slot; what was in it comes back when you get out.', 'В броне 9-й слот хотбара становится слотом способности; то, что в нём лежало, вернётся после выхода.')
    ]),
    entry('integrity', P + 'repair_wrench', -1, -1, ['power_armor'], 'Integrity and Repair', 'Целостность и ремонт', [
        text('Armor has two bars. Structural integrity takes ordinary hits and comes back by itself after a while. Critical integrity is worn by explosions and fire.\n\nAt zero structural integrity the armor shuts down. At zero critical it becomes a wreck that can only be taken apart.',
            'У брони две шкалы. Структурная целостность принимает обычные удары и сама восстанавливается через время. Критическая изнашивается от взрывов и огня.\n\nПри нулевой структурной броня отключается. При нулевой критической становится развалиной, которую можно только разобрать.'),
        item(P + 'repair_wrench', 'The repair wrench mends structural integrity in the field with iron. Shift with the wrench packs the armor into its item.', 'Ремонтный ключ в поле чинит структурную целостность железом. Shift с ключом упаковывает броню в предмет.'),
        recipe(P + 'repair_wrench'),
        note('Critical integrity is mended only at the maintenance station, with armor plates.', 'Критическая целостность чинится только на станции обслуживания — бронепластинами.')
    ]),
    entry('emergency', P + 'rescue_tool', 1, -1, ['integrity'], 'Emergency Exit', 'Аварийный выход', [
        text('A shut-down armor holds its pilot. Hold V inside to blow the hatch and climb out.',
            'Отключённая броня держит пилота. Удерживайте V внутри, чтобы сбросить люк и выбраться.'),
        item(P + 'rescue_tool', 'The rescue tool pulls a pilot out of armor from outside.', 'Аварийный инструмент вытаскивает пилота из брони снаружи.'),
        recipe(P + 'rescue_tool')
    ]),
    entry('variants', P + 'mobile_power_armor', -1, 1, ['power_armor'], 'Three Kinds of Armor', 'Три вида брони', [
        text('Combat armor: structural 100, critical 50, the strongest blows; with the ability slot chosen it hits harder.\n\nMobile armor: 70 and 35, faster, jumps higher and falls up to 15 blocks without harm; with the slot chosen it runs faster.\n\nWorker armor: 60 and 30, mines like a diamond tool; B switches between 3×3 and one block; with the slot chosen it mines.',
            'Боевая: структурная 100, критическая 50, самые сильные удары; с выбранным слотом способности бьёт ещё сильнее.\n\nМобильная: 70 и 35, быстрее, прыгает выше и падает до 15 блоков без урона; с выбранным слотом бежит быстрее.\n\nРабочая: 60 и 30, добывает как алмазный инструмент; B переключает 3×3 и один блок; с выбранным слотом добывает.'),
        recipe(P + 'dock_mobile_power_armor', 'Mobile armor, in the heavy dock.', 'Мобильная броня, в тяжёлом доке.'),
        recipe(P + 'dock_worker_power_armor', 'Worker armor, in the heavy dock.', 'Рабочая броня, в тяжёлом доке.')
    ]),
    entry('armor_upgrades', P + 'upgrade_mounting_tool', 1, 1, ['variants'], 'Armor Upgrades', 'Улучшения брони', [
        text('With the mounting tool, Shift + right-click your own empty armor: a window with 7 slots of hull parts opens. Putting an upgrade into a slot installs it, taking it out removes it. A banner goes on the back.',
            'Устройство монтажа: Shift + ПКМ по своей пустой броне открывает окно с 7 слотами частей корпуса. Положить улучшение в слот — установить, забрать — снять. Знамя ставится на спину.'),
        recipe(P + 'upgrade_mounting_tool'),
        text('Plating per part: copper +10, iron +20, titanium +30 structural (+5 critical). Compressed air: higher jump, safer fall. Floodlight (N): light where you look.',
            'Обшивка поднимает максимум структурной целостности за каждую часть: медь +10, железо +20, титан +30 и +5 критической. Модуль сжатого воздуха повышает прыжок и безопасную высоту падения. Прожектор (N) освещает то, куда смотрит пилот.'),
        recipe(P + 'armor_titanium_plating'),
        recipe(P + 'compressed_air_module'),
        recipe(P + 'floodlight')
    ]),
    entry('weapons', P + 'assault_rifle', 1, 3, ['variants'], 'Weapons', 'Оружие', [
        text('Guns fire only from working power armor: left click fires, right click aims, R reloads. Without armor a gun can be carried, but it is a heavy load and does not fire. Each gun has its own rounds, made at the weapon workshop.',
            'Оружие стреляет только из работающей силовой брони: ЛКМ — огонь, ПКМ — прицеливание, R — перезарядка. Без брони его можно носить, но это тяжёлый груз, и оно не стреляет. У каждого оружия свои патроны, они делаются в оружейной мастерской.'),
        item(P + 'assault_rifle', 'Assault rifle: automatic, 30 rounds, 6 damage a bullet, 64 blocks.', 'Автоматическая винтовка: очередями, 30 патронов, 6 урона за пулю, 64 блока.'),
        recipe(P + 'assault_rifle'),
        recipe(P + 'assault_rifle_ammo'),
        item(P + 'battle_rifle', 'Battle rifle: one shot at a time with a scope, 10 rounds, 18 damage, 128 blocks.', 'Полуавтоматическая винтовка: по одному выстрелу, с прицелом, 10 патронов, 18 урона, 128 блоков.'),
        recipe(P + 'battle_rifle'),
        recipe(P + 'battle_rifle_ammo'),
        item(P + 'combat_shotgun', 'Combat shotgun: 6 shells, 8 pellets of 4 damage, 24 blocks; reloaded a shell at a time.', 'Боевой дробовик: 6 патронов, 8 дробин по 4 урона, 24 блока; заряжается по одному патрону.'),
        recipe(P + 'combat_shotgun'),
        recipe(P + 'shotgun_shell')
    ])
]);

// ================================================================ factions

category('factions', P + 'faction_core', 'Factions', 'Фракции', [
    entry('factions', 'minecraft:white_banner', -3, 0, [], 'Factions', 'Фракции', [
        text('A faction is a group of players who share a name, a color and land. Press G to open the faction window: create a faction, pick its color, invite players and answer invitations.\n\nMembers carry the tag "[Faction] Name" above the head, in chat and in the player list.',
            'Фракция — это группа игроков с общим названием, цветом и землёй. Клавиша G открывает окно фракции: там создают фракцию, выбирают цвет, приглашают игроков и отвечают на приглашения.\n\nУ участников над головой, в чате и в списке игроков виден тег «[Фракция] Имя».'),
        note('Friendly fire between members and allies is off by default. The leader turns it on in the window, if the server allows it.',
            'Дружественный огонь между своими и союзниками по умолчанию выключен. Лидер включает его в окне, если сервер это разрешает.')
    ]),
    entry('faction_roles', 'minecraft:golden_helmet', -1, -1, ['factions'], 'Roles', 'Роли', [
        text('Leader: everything — color, friendly fire, alliances and wars, any role below leader; may hand leadership over.\n\nOfficer: invites, places the core, removes members and recruits, moves people between member and recruit.\n\nMember: uses chests, stations and capsules on the faction land.\n\nRecruit: only doors there.',
            'Лидер: всё — цвет, дружественный огонь, союзы и войны, любая роль ниже лидера; может передать лидерство.\n\nОфицер: приглашает, ставит ядро, исключает участников и новичков, переводит между участником и новичком.\n\nУчастник: пользуется сундуками, станциями и капсулами на земле фракции.\n\nНовичок: там только двери.')
    ]),
    entry('faction_territory', P + 'faction_core', -1, 1, ['factions'], 'Territory', 'Территория', [
        block(P + 'faction_core', 'The core guards a square of 24 blocks each way, full height.', 'Ядро охраняет квадрат на 24 блока во все стороны, на всю высоту.'),
        recipe(P + 'faction_core'),
        text('A leader or an officer places the core; a faction has one, and its square may not touch another faction\'s. There strangers do not open chests, stations or doors; allies use only doors.\n\nThe faction may break its own core at any time; the land goes with it.',
            'Ядро ставит лидер или офицер; у фракции оно одно, и его квадрат не может касаться чужого. На этой земле чужаки не открывают сундуки, станции и двери, а союзники пользуются только дверьми.\n\nСвоё ядро фракция может сломать в любой момент — земля пропадёт вместе с ним.')
    ]),
    entry('faction_diplomacy', 'minecraft:iron_sword', 1, 0, ['faction_roles', 'faction_territory'], 'Alliances and Wars', 'Союзы и войны', [
        text('Leaders make an alliance when both agree. Allies do not hurt each other while friendly fire is off and may use each other\'s doors.\n\nA leader may declare war. For 10 minutes it is only preparation; then, while someone of the defenders is online, the enemy may open their doors and break their core.',
            'Союз заключают лидеры по согласию обоих. Союзники не ранят друг друга, пока выключен дружественный огонь, и пользуются дверьми друг друга.\n\nЛидер может объявить войну. Первые 10 минут — только подготовка; затем, пока кто-то из защитников в сети, враги могут открывать их двери и ломать ядро.'),
        note('Peace is made when both leaders agree. A war ends by itself after 72 hours.', 'Мир заключается по согласию обоих лидеров. Война заканчивается сама через 72 часа.')
    ])
]);

// ================================================================ hostile environment

category('environment', P + 'respirator', 'Hostile Environment', 'Враждебная среда', [
    entry('hostile_world', 'minecraft:dead_bush', -3, 0, [], 'A Poisoned World', 'Отравленный мир', [
        text('The air of the world is polluted and radiation is everywhere. An unprotected person slowly gathers a toxic load and a radiation dose: hours in calm weather, minutes in a catastrophe. The body clears both by itself, slowly.\n\nA roof helps a little (a quarter less pollution). Full protection without gear is only a sealed shelter.',
            'Воздух мира загрязнён, радиация есть везде. Человек без защиты понемногу набирает токсическую нагрузку и дозу облучения: в спокойную погоду за часы, в катастрофу за минуты. Тело само понемногу выводит и то и другое.\n\nКрыша немного помогает (загрязнения на четверть меньше). Полная защита без снаряжения — только герметичное убежище.'),
        text('Toxins (of 100): from 30 nausea, from 60 weakness, from 90 damage.\n\nDose (of 500): from 50 weakness, from 100 slower healing, from 200 less health, from 300 damage.\n\nAfter death both start from zero.',
            'Токсины (из 100): с 30 тошнота, с 60 слабость, с 90 урон.\n\nДоза (из 500): с 50 слабость, со 100 хуже лечится, с 200 меньше здоровья, с 300 урон.\n\nПосле смерти обе начинаются с нуля.'),
        note('The HUD shows the toxins, the filter and the hazards around you; the dose — only with a dosimeter.', 'HUD показывает токсины, фильтр и опасности вокруг; дозу — только с дозиметром.')
    ]),
    entry('catastrophes', 'minecraft:clock', -1, -1, ['hostile_world'], 'Catastrophes', 'Катастрофы', [
        text('Catastrophes come by themselves, the first one no sooner than 40 minutes into a new world. Two minutes before, a message, a siren and a HUD line warn you.\n\nAcid rain: real rain; toxins and damage in the open, it wears gear and bodies without a sealing module.',
            'Катастрофы приходят сами, первая — не раньше чем через 40 минут нового мира. За две минуты предупреждают сообщение, сирена и строка в HUD.\n\nКислотный дождь: настоящий дождь; на открытом месте токсины и урон, изнашивает снаряжение и тела без модуля герметизации.'),
        text('Toxic storm: a thick yellow-green fog, toxins come fast; a respirator and a roof help.\n\nRadiation burst: the world turns brownish and grainy, more so the stronger it is, and the dose comes fast. A roof does not save you; a heavy suit and armor do, and in a shelter it is half as strong.',
            'Токсичный шторм: густой жёлто-зелёный туман, токсины набираются быстро; помогают респиратор и крыша.\n\nРадиационный выброс: мир становится коричневатым и зернистым, тем сильнее, чем сильнее выброс, и доза растёт быстро. Крыша не спасает; защищают тяжёлый костюм и броня, в убежище радиация вдвое слабее.'),
        text('A weather station sees a catastrophe coming earlier than the warning: ten minutes ahead roughly, thirty minutes ahead exactly with its long range module, and gives a redstone signal before it comes.',
            'Метеостанция видит катастрофу раньше предупреждения: за десять минут примерно, с модулем дальнего прогноза — за тридцать минут точно, и даёт сигнал красного камня до её начала.'),
        note('A radiation burst and a toxic storm leave residual spots in the world, see Contaminated Zones.',
            'Радиационный выброс и токсичный шторм оставляют в мире остаточные пятна, см. «Заражённые территории».')
    ]),
    entry('contaminated_zones', 'minecraft:poisonous_potato', -2, -2, ['catastrophes'], 'Contaminated Zones', 'Заражённые территории', [
        text('Since 1.6 a radiation burst and a toxic storm leave spots behind: when the catastrophe fades, several spots of residual radiation or toxins appear in the world, near the players, 1 to 3 chunks wide, strongest in the middle. Toxic spots like swamps, rivers and lowlands.',
            'С 1.6 радиационный выброс и токсичный шторм оставляют после себя пятна: когда катастрофа утихает, рядом с игроками в мире появляется несколько пятен остаточной радиации или токсинов шириной в 1–3 чанка, самых сильных в середине. Токсичные пятна любят болота, реки и низины.'),
        text('Inside a spot the air is dirtier and the radiation higher, just like in a catastrophe: a respirator, a suit and a sealed shelter protect as usual. A body or an armor does not fall ill, but gathers contamination on itself and carries it home.\n\nA spot fades by itself: radiation in a few days, toxins in a day or two.',
            'Внутри пятна воздух грязнее, а радиация выше, как в катастрофу: респиратор, костюм и герметичное убежище защищают как обычно. Тело или броня не болеют, но набирают на себя загрязнение и заносят его домой.\n\nПятно проходит само: радиация за несколько дней, токсины за день-два.'),
        note('The HUD line Contaminated zone tells you are in one; the dosimeter shows the radiation. Inside you see rare particles and a light tint of the air. Spots do not land on the lands of factions.',
            'Строка HUD «Заражённая зона» говорит, что вы в пятне; дозиметр показывает радиацию. Внутри видны редкие частицы и лёгкий оттенок воздуха. Пятна не ложатся на земли фракций.')
    ]),
    entry('dust_storm', 'minecraft:sand', -3, -1, ['catastrophes'], 'Dust Storm', 'Пыльная буря', [
        text('Since 1.5 a dust storm comes too: thick brown fog, about eight blocks of view, sand in the wind. It is strongest in deserts and badlands, weaker in forests and swamps. A roof weakens it; deep ground and a sealed shelter keep it out.',
            'С 1.5 приходит и пыльная буря: густой бурый туман, видно блоков на восемь, песок в ветре. Сильнее всего в пустынях и столовых горах, слабее в лесах и болотах. Крыша её ослабляет, глубина и герметичное убежище не пускают.'),
        text('Without a respirator a person is slowed down, coughs now and then and gathers a little toxin. A respirator keeps the dust out, but its filter clogs three times as fast.\n\nA body without a sealing module wears and heats up. Solar panels give a quarter of their energy.',
            'Без респиратора человек замедляется, иногда заходится кашлем и набирает немного токсинов. Респиратор не пускает пыль, но фильтр засоряется втрое быстрее.\n\nТело без модуля герметизации изнашивается и греется. Солнечные панели дают четверть энергии.'),
        note('The pilot of a power armor is safe. A body with a sealing module does not notice the dust.', 'Пилот силовой брони в безопасности. Тело с модулем герметизации пыли не замечает.')
    ]),
    entry('protection', P + 'respirator', -1, 1, ['hostile_world'], 'Protective Gear', 'Защитное снаряжение', [
        item(P + 'respirator', 'The respirator keeps out polluted air while its filter lasts.', 'Респиратор не пускает грязный воздух, пока жив фильтр.'),
        recipe(P + 'respirator'),
        recipe(P + 'filter', 'A filter is used only in dirty air. Right-click puts it in.', 'Фильтр тратится только в грязном воздухе. ПКМ — вставить.'),
        text('The hazmat suit guards against toxins and acid, the heavy suit against radiation too. Protection is counted by the parts you wear: the helmet of a suit takes a filter like a respirator.\n\nTrousers and boots are made the same way as the parts below.',
            'Химкостюм защищает от токсинов и кислоты, тяжёлый костюм — ещё и от радиации. Защита считается по надетым частям: шлем костюма берёт фильтр, как респиратор.\n\nШтаны и ботинки делаются так же, как части ниже.'),
        recipe(P + 'hazmat_helmet'),
        recipe(P + 'hazmat_suit'),
        recipe(P + 'heavy_suit_helmet'),
        recipe(P + 'heavy_suit')
    ]),
    entry('emi_storm', P + 'emi_shielding', 3, 2, ['machines_environment'], 'Electromagnetic Storm', 'ЭМИ-буря', [
        text('Since 1.5 an electromagnetic storm comes too: a glow in the sky that glides from violet to green and back. People hardly feel it, but electronics do. Deep ground weakens it; only a sealed shelter keeps it out.',
            'С 1.5 приходит и ЭМИ-буря: сияние в небе, плавно переходящее от фиолетового к зелёному и обратно. Людям она почти не страшна, а электронике — да. Глубина её ослабляет, полностью защищает только герметичное убежище.'),
        text('A body: its HUD flickers, the jetpack cuts out, self-repair stops, charge runs out faster, and now and then a short fault slows it down and wears it a little. Control stays. Better bodies resist more.\n\nArmor: its HUD flickers, the floodlight blinks, the aim shakes.',
            'Тело: HUD мерцает, джетпак работает с перебоями, самовосстановление стоит, заряд тратится быстрее, иногда короткий сбой замедляет и немного изнашивает. Управление остаётся. Лучшие тела устойчивее.\n\nБроня: HUD мерцает, прожектор мигает, прицел дрожит.'),
        note('Stations outside a sealed shelter stop while the storm lasts and go on afterwards; generators, batteries, cables and life support work on.',
            'Станции вне герметичного убежища стоят, пока идёт буря, и продолжают после; генераторы, аккумуляторы, кабель и жизнеобеспечение работают.'),
        item(P + 'emi_shielding', 'Electronics shielding: a gadget that keeps 90% of the interference off a body.', 'Экранирование электроники: гаджет, гасит 90% помех тела.'),
        recipe(P + 'emi_shielding'),
        item(P + 'emi_screen', 'EMI screen: an armor upgrade for the chest, front or back; 90% less interference.', 'ЭМИ-экран: улучшение брони на корпус спереди или сзади; помех на 90% меньше.'),
        recipe(P + 'emi_screen')
    ]),
    entry('temperature', 'minecraft:snowball', -1, 3, ['protection'], 'Temperature', 'Температура', [
        text('Since 1.5 places have a temperature: deserts, badlands and the Nether are hot, snowy lands and high mountains cold; nights, rain and height make it colder, a roof or deep ground evens it out, and fire, lava and furnaces warm it. A sealed shelter keeps 20°C.',
            'С 1.5 у места есть температура: в пустынях, столовых горах и Незере жарко, в снежных землях и высоко в горах холодно; ночь, дождь и высота охлаждают, крыша и глубина сглаживают, огонь, лава и печи греют. В герметичном убежище всегда 20°C.'),
        text('Between 5 and 30°C you are comfortable. Outside, heat or cold stress grows; in comfort it goes away.\n\nFrom 30 weakness, from 60 slower healing, from 90 damage, and wounds no longer heal. Heat makes you hungry faster, cold slower.',
            'От 5 до 30°C вам комфортно. Вне этого растёт стресс от жары или холода, в комфорте он проходит.\n\nС 30 слабость, с 60 хуже лечится, с 90 урон, и раны больше не заживают. В жару голод приходит быстрее, в холод медленнее.'),
        note('Artificial bodies do not feel cold; heat goes into their overheating. The pilot of a power armor is protected.',
            'Искусственные тела холода не чувствуют, жара идёт в их перегрев. Пилот силовой брони защищён.'),
        note('In the HUD the line Temperature fills from the middle: to the right in heat, to the left in cold; a sun or a snowflake shows the stage.',
            'В HUD строка «Темп.» заполняется от середины: вправо в жару, влево в холод; солнце или снежинка показывают стадию.')
    ]),
    entry('thermal_gear', P + 'thermal_liner', 1, 3, ['temperature'], 'Against Heat and Cold', 'От жары и холода', [
        text('The heavy suit keeps off most heat and cold, the hazmat suit a little. Each piece counts by its share.\n\nA thermal liner sewn into any piece — a suit or ordinary armor — keeps off 70% for that piece.',
            'Тяжёлый костюм хорошо защищает от жары и холода, химкостюм — немного. Каждая часть считается своей долей.\n\nТермоподкладка, вшитая в любую часть — костюма или обычной брони, — защищает эту часть на 70%.'),
        item(P + 'thermal_liner', 'Put a piece and a liner on the crafting table. A lined piece alone gives the liner back.', 'Часть брони и подкладку — на верстак. Подшитая часть одна — подкладка вернётся.'),
        recipe(P + 'thermal_liner'),
        block(P + 'heater', 'Warms everything within 6 blocks by 20°C while it is cold around.', 'Пока вокруг холодно, греет всё в 6 блоках на 20°C.'),
        recipe(P + 'heater', 'It runs on energy from the cable.', 'Работает от энергии из кабеля.')
    ]),
    entry('radiation_sources', P + 'radioactive_debris', -4, -3, ['contaminated_zones'], 'Sources', 'Источники', [
        block(P + 'radioactive_debris', 'Radioactive debris: radiation up to 10 blocks around, strongest next to it (8 units), weaker with distance.', 'Радиоактивные обломки: радиация до 10 блоков вокруг, сильнее всего вплотную (8 единиц), с расстоянием слабее.'),
        block(P + 'leaking_waste_barrel', 'A leaking waste barrel pollutes the air up to 7 blocks around (5 units next to it) and drips.', 'Протекающая бочка с отходами отравляет воздух до 7 блоков вокруг (5 единиц вплотную) и капает.'),
        text('Sources add to the background where you stand, so the dosimeter, the HUD, the contamination on you, bodies and armor all count them. Each full solid block between you and a source halves it: a wall, rock or the wall of a shelter is a good shield.',
            'Источники добавляются к фону там, где вы стоите, поэтому дозиметр, HUD, загрязнение на вас, тела и броня учитывают их сами. Каждый целый сплошной блок между вами и источником ослабляет его вдвое: стена, камень или стена убежища — хорошая защита.'),
        note('They are found in abandoned places and cannot be carried away whole: broken, debris gives cobbled deepslate and now and then raw uranium, a barrel some iron nuggets, sludge nothing. Breaking one is the way to clear the place.',
            'Их находят в заброшенных местах, целиком унести их нельзя: разрушенные обломки дают глубинный булыжник и иногда сырой уран, бочка — железные самородки, шлам — ничего. Разрушить источник — способ очистить место.')
    ]),
    entry('uranium', P + 'uranium_ingot', -2, -3, ['radiation_sources'], 'Uranium', 'Уран', [
        text('Uranium ore lies rarely and deep, from y −64 to −16, in small veins, in stone and in deepslate. It needs an iron pickaxe and radiates a little around itself (up to 3 blocks).',
            'Урановая руда лежит редко и глубоко, от y −64 до −16, небольшими жилами, в камне и в глубинном сланце. Её добывают железной киркой; она слегка фонит вокруг (до 3 блоков).'),
        block(P + 'deepslate_uranium_ore', 'A furnace or a blast furnace makes an ingot of the ore or of the raw uranium.', 'Печь или плавильная печь делает слиток из руды или из сырого урана.'),
        recipe(P + 'uranium_ingot_from_blasting'),
        note('Raw uranium and ingots radiate in the inventory: 0.03 units for each item, up to 4. A hazmat or heavy suit lessens it as it lessens any radiation. Keep the stock in chests.',
            'Сырой уран и слитки фонят в инвентаре: по 0,03 единицы за предмет, не больше 4. Химкостюм или тяжёлый костюм ослабляют это, как любую радиацию. Храните запас в сундуках.')
    ]),
    entry('ruins', 'minecraft:chest', -4, -5, ['radiation_sources'], 'Ruins of the Past', 'Руины прошлого', [
        text('Old buildings stand in newly explored land. The richer the place, the more dangerous it is.\n\nAn abandoned laboratory (small, in forests, plains and swamps) keeps electronics, filters and medicine among leaking waste barrels.\n\nIndustrial ruins (medium, in open land) keep metals, parts, upgrades and now and then a packed worn body, near radioactive debris.\n\nA ruined reactor (large and rare) has a core heaped with radioactive debris; the strongest technology lies there: processors, titanium, uranium, modules, worn bodies and wrecked power armor.',
            'В новых, ещё не исследованных землях стоят старые постройки. Чем богаче место, тем оно опаснее.\n\nЗаброшенная лаборатория (малая, в лесах, на равнинах и болотах) хранит электронику, фильтры и лекарства среди протекающих бочек с отходами.\n\nПромышленные руины (средние, на открытых местах) хранят металлы, детали, улучшения и иногда упакованное изношенное тело — рядом с радиоактивными обломками.\n\nРазрушенный реактор (большой и редкий): его активная зона завалена радиоактивными обломками; там лежит лучшая техника — процессоры, титан, уран, модули, изношенные тела и разбитая силовая броня.'),
        note('Found bodies and armor are badly damaged: repair them before use. /locate structure projectrebirth:abandoned_lab (industrial_ruins, ruined_reactor) finds the nearest one.',
            'Найденные тела и броня сильно повреждены: перед использованием их нужно чинить. /locate structure projectrebirth:abandoned_lab (industrial_ruins, ruined_reactor) находит ближайшую.')
    ]),
    entry('large_structures', P + 'wrecked_turret', -6, -5, ['ruins'], 'Great Ruins', 'Большие руины', [
        text('Far from home stand whole compounds of the old world. They are rarer than the small ruins and richer.\n\nA military base: walls, towers with dead turrets, a headquarters of three floors and a garage of wrecked power armors. Ammunition, plates, sometimes a weapon.\n\nA science complex: a hall of specimen tanks, a server room and an observatory under a dome. Electronics, medicine, synthetic bodies.\n\nAn abandoned factory: a smelting hall with glowing slag, presses and two smoking chimneys. Metals, parts, a worker armor now and then.',
            'Вдали от дома стоят целые комплексы старого мира. Они реже малых руин и богаче.\n\nВоенная база: стены, башни с мёртвыми турелями, штаб в три этажа и гараж с разбитой силовой бронёй. Боеприпасы, пластины, иногда оружие.\n\nНаучный комплекс: зал колб с образцами, серверная и обсерватория под куполом. Электроника, лекарства, синтетические тела.\n\nЗаброшенный завод: плавильный цех со светящимся шлаком, прессы и две дымящие трубы. Металлы, детали, иногда рабочая броня.'),
        text('A communication station: a lattice mast with red lamps and dishes, a room with a wall of screens. Electronics and batteries.\n\nA defensive outpost: walls, two towers with searchlights, barracks and an armory.\n\nA contamination zone: an ancient arch over pools of irradiated sludge. Each block of sludge radiates a little, and a pool adds them all up: come in protection. The richest loot here lies by a wrecked armor of an expedition that never came back.',
            'Станция связи: решётчатая мачта с красными огнями и тарелками, зал со стеной экранов. Электроника и аккумуляторы.\n\nОборонительный аванпост: стены, две башни с прожекторами, казарма и оружейная.\n\nЗона заражения: древняя арка над лужами радиоактивного шлама. Каждый блок шлама фонит немного, а лужа складывает их все: приходите в защите. Самый богатый лут здесь лежит у разбитой брони экспедиции, которая не вернулась.'),
        note('A wrecked power armor cannot be repaired, but a wrench with sneaking takes it apart for iron and armor plates. Broken, a monitor gives a circuit board, a rack boards, iron and cable, a tank glass, an ammo crate ammunition; with silk touch they come away whole for your own base, and a lamp always does. Most screens, racks and tanks in the ruins are broken and most machines rusted through: only about one machine in five still works; the rest give scrap. /locate structure projectrebirth:military_base (science_complex, abandoned_factory, comm_station, defensive_outpost, contamination_zone).',
            'Разбитую силовую броню не починить, но гаечный ключ с зажатым Shift разбирает её на железо и бронепластины. Разобранный монитор даёт плату, стойка — платы, железо и кабель, колба — стекло, оружейный ящик — патроны; с «Шёлковым касанием» они снимаются целиком для своей базы, а лампа снимается всегда. Большинство экранов, стоек и колб в руинах разбиты, а машины проржавели: работает примерно одна из пяти, остальные идут на лом. /locate structure projectrebirth:military_base (science_complex, abandoned_factory, comm_station, defensive_outpost, contamination_zone).')
    ]),
    entry('guard_turret', P + 'turret', -8, -5, ['large_structures'], 'Guard Turrets', 'Турели охраны', [
        text('Some turrets of the military base, the outpost and the communication station still work. A working turret sweeps its sector; when it sees you within about 20 blocks it beeps, takes a second to aim, then fires bursts of three. Its bullets hit like a rifle: armor and a power armor take them.\n\nIt sees only in a straight line: a wall, a corner or a hill hides you. It does not move and does not chase. Destroyed, it is left as a broken turret and drops scrap, ammunition and sometimes a board or an actuator.',
            'Часть турелей военной базы, аванпоста и станции связи ещё работает. Рабочая турель осматривает свой сектор; заметив вас примерно в 20 блоках, она пищит, секунду целится и бьёт очередями по три. Пули бьют как винтовка: броня и силовая броня их принимают.\n\nОна видит только по прямой: стена, угол или холм вас скрывают. Она не двигается и не преследует. Уничтоженная остаётся сломанной турелью и роняет лом, патроны и иногда плату или привод.'),
        note('Approach from cover, or from behind where its head has to turn. Switched off in the config ([hostile]), the turrets stand broken.',
            'Подходите из-за укрытия или сзади, где ей приходится поворачивать голову. Если их выключить в конфиге ([hostile]), турели стоят сломанными.')
    ]),
    entry('patrol_robot', P + 'patrol_robot_spawn_egg', -10, -5, ['guard_turret'], 'Patrol Robots', 'Патрульные роботы', [
        text('Two-metre walkers guard the military base, the outpost, the factory, the communication station and the industrial ruins. Each keeps to its post and walks round it; when it sees you within about 16 blocks it comes closer and fires short bursts from the gun of its right arm. Come up close and it swings its heavy left fist.\n\nDestroyed, it leaves its wreck where it stood, like a broken power armor: take it apart with the repair wrench while sneaking for iron, boards, actuators and cable.',
            'Двухметровые шагающие машины охраняют военную базу, аванпост, завод, станцию связи и промышленные руины. Каждая держится своего поста и обходит его; заметив вас примерно в 16 блоках, подходит ближе и бьёт короткими очередями из пушки на правой руке. Если подойти вплотную, бьёт тяжёлым левым кулаком.\n\nУничтоженная оставляет свой остов на месте, как разбитая силовая броня: разберите его гаечным ключом, зажав Shift, — получите железо, платы, приводы и кабель.'),
        note('A wall breaks its line of fire, as with the turrets. The machines of the ruins do not hurt each other. Settings in the config ([hostile.patrol_robot]).',
            'Стена закрывает её линию огня, как у турелей. Машины руин друг друга не ранят. Настройки в конфиге ([hostile.patrol_robot]).')
    ]),
    entry('mech', P + 'mech_spawn_egg', -10, -3, ['patrol_robot'], 'The Mech', 'Мех', [
        text('One heavy walker, about three and a half blocks tall, guards the yard of each military base; its health bar shows while you are near. A twin machine gun under its right arm fires long bursts. Now and then the rocket pod on its left shoulder marks the ground under you with a red ring and an alarm: a moment later the rockets fall there, so do not stay in the ring. Come up close and it stomps, throwing everyone back.\n\nAt half its health it kneels and sheds its chest plate and rocket pod (it takes no damage meanwhile). Then it fights stripped, its glowing core bare: a gatling rises from behind its back and sprays long bursts; it charges straight at you and stomps more often, and on the jetpack on its back it takes off, circles over you firing and comes down with a shock. There is no hiding on a roof from it. No more rockets.',
            'Одна тяжёлая шагающая машина ростом около трёх с половиной блоков охраняет двор каждой военной базы; пока вы рядом, видна полоса её здоровья. Спаренный пулемёт под правой рукой бьёт длинными очередями. Время от времени ракетный блок на левом плече отмечает землю под вами красным кругом и сигналом тревоги: через мгновение туда падают ракеты, поэтому не оставайтесь в круге. Вблизи она топает и отбрасывает всех вокруг.\n\nПотеряв половину здоровья, мех опускается на колено и сбрасывает нагрудную плиту и ракетный блок (в это время урон ему не проходит). Дальше он дерётся без брони, со светящимся ядром наружу: из-за спины поднимается пулемёт Гатлинга с длинными очередями, мех бросается тараном и чаще топает, а на джетпаке взлетает, кружит над вами, стреляя, и приземляется с ударной волной. На крыше от него не спрятаться. Ракет больше нет.'),
        note('Destroyed, it leaves its wreck: the repair wrench while sneaking takes it apart for a lot of metal, titanium, boards, actuators, processors and power cells. The rockets do not break blocks unless the config says so ([hostile.mech]).',
            'Уничтоженный, он оставляет остов: гаечный ключ с зажатым Shift разбирает его на много металла, титан, платы, приводы, процессоры и энергоячейки. Ракеты не разрушают блоки, если это не включено в конфиге ([hostile.mech]).')
    ]),
    entry('mutants', P + 'glowing_ghoul_spawn_egg', -3, -2, ['contaminated_zones'], 'Mutants', 'Мутанты', [
        text('Where a catastrophe left its contamination, in the contamination zone and in the laboratories and the reactor, mutants live. Mutated hounds are skinned, fast and come only in packs of three to five: when one sees you, the whole pack runs at you. The glowing ghoul, a worker in the rags of a hazmat suit, is slow, but spits toxic slime from afar; standing near it gives you radiation and toxins, and when it dies it leaves a toxic puddle that dries up after a few minutes.',
            'Там, где катастрофа оставила загрязнение, в «Зоне заражения», в лабораториях и у реактора живут мутанты. Мутировавшие псы — ободранные, быстрые и ходят только стаями по три–пять: заметит один — бросится вся стая. Светящийся гуль, рабочий в лохмотьях защитного костюма, медленный, но издалека плюётся токсичной слизью; рядом с ним вы получаете радиацию и токсины, а умирая, он оставляет токсичную лужу, которая высыхает через несколько минут.'),
        text('The brute is rare and very strong. Its blow throws you into the air. From a distance it roars and charges straight at you (dodge aside: a wall it runs into stuns it for a moment), or leaps onto you and lands with a shock. Its body stays after death, lying face down, and radiates until it rots away and sinks into the ground.',
            'Громила редкий и очень сильный. Его удар подбрасывает в воздух. Издалека он ревёт и бросается на вас тараном (уклоняйтесь в сторону: врезавшись в стену, он на миг теряет ориентацию) или прыгает на вас и приземляется с ударной волной. Его тело остаётся после смерти лицом вниз и фонит радиацией, пока не сгниёт и не уйдёт в землю.'),
        note('Mutants do not burn in the sun and do not come in peaceful. Settings in the config ([hostile.mutants]).',
            'Мутанты не горят на солнце и не появляются на мирной сложности. Настройки в конфиге ([hostile.mutants]).')
    ]),
    entry('polluted_water', P + 'polluted_water_bottle', -1, -3, ['contaminated_zones'], 'Polluted Water', 'Загрязнённая вода', [
        text('Water is polluted where the ground is contaminated (a contaminated zone on the HUD), and during a toxic storm or acid rain wherever it lies under the open sky. It looks like any water. Swimming in it gives a person toxins (less in a protective suit) and soils whoever swims, so a decontamination is due afterwards.\n\nA glass bottle or a bucket filled there holds polluted water. Drunk, a bottle of it gives toxins; it is no potion, so neither the brewing stand nor any recipe takes it. Poured from the bucket, it is just water again.',
            'Вода загрязнена там, где заражена земля (в HUD — «заражённая зона»), а во время токсичного шторма и кислотного дождя — везде, где она лежит под открытым небом. На вид это обычная вода. Купание в ней даёт человеку токсины (в защитном костюме меньше) и пачкает того, кто плавает, — после этого нужна дезактивация.\n\nБутылка или ведро, наполненные там, содержат загрязнённую воду. Выпитая бутылка такой воды даёт токсины; это не зелье, поэтому её не принимает ни варочная стойка, ни один рецепт. Вылитая из ведра, она снова просто вода.'),
        block(P + 'water_purifier', 'The water purifier cleans it: polluted water in, a filter cartridge of the air filtration unit, clean water out (a bottle into a water bottle, a bucket into a water bucket). It needs energy; each bottle wears the cartridge a little (fifty bottles a cartridge), a used one turns spent. Hoppers bring water and cartridges and take the clean water away.',
            'Водоочиститель очищает её: загрязнённая вода на входе, фильтрующий картридж установки фильтрации воздуха, чистая вода на выходе (бутылка — в бутылку воды, ведро — в ведро воды). Нужна энергия; каждая бутылка немного изнашивает картридж (пятьдесят бутылок на картридж), израсходованный становится отработанным. Воронки подают воду и картриджи и забирают чистую воду.'),
        recipe(P + 'water_purifier'),
        note('Settings in the config ([water]).', 'Настройки в конфиге ([water]).')
    ]),
    entry('weather_station', P + 'weather_station', 1, -3, ['catastrophes'], 'Weather Station', 'Метеостанция', [
        block(P + 'weather_station', 'The weather station foresees the next catastrophe of its dimension, earlier than the usual warning: ten minutes ahead it knows the kind and the time within five minutes. It needs energy and the open sky right above it. Its window shows the forecast, the sky now and the last catastrophes.',
            'Метеостанция заранее видит следующую катастрофу своего измерения, раньше обычного предупреждения: за десять минут она знает её вид и время с точностью до пяти минут. Ей нужны энергия и открытое небо прямо над ней. В окне — прогноз, состояние неба сейчас и последние катастрофы.'),
        text('Whoever placed the station, the members of their faction (anywhere in the dimension) and every player within 64 blocks of it see the forecast in the HUD, in place of the line of the warning.\n\nThe station gives a redstone signal five minutes before a catastrophe it foresees and while one goes on: close the airlock, start the reserve. A comparator reads how close the catastrophe is (stronger as it comes nearer, 15 during it).',
            'Тот, кто поставил станцию, участники его фракции (где угодно в измерении) и все игроки в 64 блоках от неё видят прогноз в HUD вместо строки предупреждения.\n\nСтанция даёт сигнал красного камня за пять минут до катастрофы, которую видит, и пока та идёт: закройте шлюз, включите резерв. Компаратор показывает, насколько близко катастрофа (чем ближе, тем сильнее, во время неё — 15).'),
        recipe(P + 'weather_station'),
        text('The long range forecast module, put into the station, sees thirty minutes ahead and tells the exact time, the intensity and the length of the catastrophe. It takes more energy.',
            'Модуль дальнего прогноза, вставленный в станцию, видит на тридцать минут вперёд и сообщает точное время, интенсивность и длительность катастрофы. Он потребляет больше энергии.'),
        recipe(P + 'long_range_forecast_module'),
        note('Settings in the config ([weather_station]).', 'Настройки в конфиге ([weather_station]).')
    ]),
    entry('monitoring_station', P + 'monitoring_station', 0, -4, ['contaminated_zones', 'weather_station'], 'Monitoring Station', 'Станция мониторинга', [
        block(P + 'monitoring_station', 'The environmental monitoring station scans the contamination of the ground around it, eight chunks each way, every few seconds, as long as it has energy. Its window shows a map of the chunks: radiation yellow, toxins green, the brighter the stronger; the station, the shelters (air filtration units) and the players are marked. Point at a chunk for its numbers. Below, a graph of the peaks of the last game days.',
            'Станция экологического мониторинга, пока у неё есть энергия, каждые несколько секунд сканирует заражение земли вокруг — по восемь чанков в каждую сторону. В её окне карта чанков: радиация жёлтым, токсины зелёным, чем ярче, тем сильнее; отмечены станция, убежища (установки фильтрации) и игроки. Наведите на чанк, чтобы увидеть числа. Ниже — график пиков за последние игровые дни.'),
        text('Put paper in and press Print: the station prints the scan as a contamination map for an expedition. Used from the hand, the map shows the picture of the day it was printed; it does not change. A comparator next to the station reads the strongest contamination close to it.',
            'Положите бумагу и нажмите «Печать»: станция напечатает скан как карту заражения для экспедиции. Если использовать карту из руки, она покажет снимок на день печати; со временем он не меняется. Компаратор рядом со станцией показывает самое сильное заражение возле неё.'),
        recipe(P + 'monitoring_station'),
        note('Settings in the config ([monitoring_station]).', 'Настройки в конфиге ([monitoring_station]).')
    ]),
    entry('field_scouting', P + 'field_analyzer', 2, -4, ['monitoring_station'], 'Scouting in the Field', 'Разведка в поле', [
        item(P + 'field_analyzer', 'The field analyzer, held in either hand, shows under the status panel how much radiation and toxins the ground of your chunk holds and what the sources around you (radioactive blocks, uranium ore, creatures) give, with a small map of the chunks around. Near a source it clicks, the faster the stronger.',
            'Полевой анализатор, если держать его в любой руке, показывает под панелью статуса, сколько радиации и токсинов в земле вашего чанка и сколько дают источники рядом (радиоактивные блоки, урановая руда, существа), и небольшую карту чанков вокруг. Возле источника он щёлкает — чем сильнее, тем чаще.'),
        recipe(P + 'field_analyzer'),
        item(P + 'sensor_module', 'The sensor module is a gadget of artificial bodies: the body shows a map of the contamination around, with the source blocks (violet crosses) and the ruins of the mod (brown squares) marked. Its reach grows with the class of the body: from two chunks each way for the basic robot to six for the premium body.',
            'Сенсорный модуль — гаджет искусственных тел: тело показывает карту заражения вокруг с отметками блоков-источников (фиолетовые крестики) и руин мода (коричневые квадраты). Дальность растёт с классом тела: от двух чанков в каждую сторону у базового робота до шести у премиального тела.'),
        recipe(P + 'sensor_module'),
        item(P + 'sensor_unit', 'The sensor unit does the same for the pilot of power armor; it goes on the helmet and reaches five chunks each way.',
            'Сенсорный блок делает то же для пилота силовой брони; ставится на шлем и видит на пять чанков в каждую сторону.'),
        recipe(P + 'sensor_unit'),
        note('Settings in the config ([scouting]).', 'Настройки в конфиге ([scouting]).')
    ]),
    entry('dosimeter', P + 'dosimeter', 1, 1, ['protection'], 'Dosimeter', 'Дозиметр', [
        item(P + 'dosimeter', 'Carried, it shows your dose and the radiation, and clicks.', 'В инвентаре показывает дозу и радиацию и щёлкает.'),
        recipe(P + 'dosimeter'),
        note('Nobody feels radiation. Without a dosimeter the HUD does not show the dose at all.', 'Радиацию никто не чувствует. Без дозиметра HUD дозу вовсе не показывает.'),
        text('The dosimeter tells what reaches you. Where it comes from, the field analyzer tells: held in hand, it shows the contamination of the ground and of the sources around you (see Scouting in the Field).',
            'Дозиметр говорит, сколько радиации до вас доходит. Откуда она — скажет полевой анализатор: в руке он показывает заражение земли и источников вокруг (см. «Разведка в поле»).')
    ]),
    entry('medicine', P + 'antiradin', 1, -1, ['catastrophes'], 'Medicine', 'Лекарства', [
        item(P + 'antiradin', 'Antiradin: drink it to lose 80 of the dose.', 'Антирадин: выпейте, чтобы снять 80 единиц дозы.'),
        recipe(P + 'antiradin'),
        item(P + 'sorbent', 'Sorbent: drink it to lose 50 of the toxic load.', 'Сорбент: выпейте, чтобы снять 50 единиц токсинов.'),
        recipe(P + 'sorbent')
    ]),
    entry('machines_environment', P + 'sealing_module', 3, 0, ['medicine', 'dosimeter'], 'Bodies and Armor', 'Тела и броня', [
        text('Artificial bodies do not get sick, but without a sealing module acid adds wear (since 1.5, not damage); better bodies wear slower.\n\nA power armor pilot is safe from toxins and acid, and only a fifth of the radiation reaches them. Plating adds more protection.',
            'Искусственные тела не болеют, но без модуля герметизации кислота копит износ (с 1.5 — не урон); лучшие тела — медленнее.\n\nПилот силовой брони защищён от токсинов и кислоты, а радиации до него доходит только пятая часть. Обшивка добавляет защиту.'),
        note('In a catastrophe, contamination settles on bodies and armor too. Wash it off before you go into the shelter.', 'В катастрофу загрязнение оседает и на телах, и на броне. Смойте его, прежде чем входить в убежище.')
    ])
]);

// ================================================================ shelter

category('shelter', P + 'air_filtration_unit', 'Shelter', 'Убежище', [
    entry('sealed_room', P + 'air_filtration_unit', -3, 0, [], 'Sealed Room', 'Герметичное помещение', [
        text('A sealed room is the only place where a person is fully safe without gear: its own air, half the radiation, no acid.\n\nThe air filtration unit placed inside finds the room by itself: up to 4096 blocks of air per unit, up to 8 units in one room.',
            'Герметичное помещение — единственное место, где человек полностью защищён без снаряжения: свой воздух, радиация вдвое слабее, кислоты нет.\n\nУстановка фильтрации воздуха, поставленная внутри, сама находит помещение: до 4096 блоков воздуха на установку, до 8 установок в одном помещении.'),
        block(P + 'air_filtration_unit', 'It needs energy and a filter cartridge.', 'Ей нужны энергия и фильтрующий картридж.'),
        recipe(P + 'air_filtration_unit'),
        text('Walls are blocks that close a whole face: full blocks, glass, slabs and stairs by their whole side. Leaves, fences, panes and open sky leak.',
            'Стены — блоки, закрывающие грань целиком: полные блоки, стекло, полублоки и ступени целой гранью. Листва, заборы, панели и открытое небо — утечка.'),
        note('The mod\'s energy cable may go through a wall: the room stays sealed.', 'Энергокабель мода можно провести сквозь стену: помещение останется герметичным.')
    ]),
    entry('shelter_doors', P + 'airlock_door', -1, -1, ['sealed_room'], 'Doors and Airlock', 'Двери и шлюз', [
        block(P + 'airlock_door', 'The airlock door holds the air when closed.', 'Шлюзовая дверь держит воздух, когда закрыта.'),
        recipe(P + 'airlock_door'),
        recipe(P + 'sealed_hatch', 'The sealed hatch is the same for floors and ceilings.', 'Герметичный люк — то же для пола и потолка.'),
        text('Iron and copper doors and trapdoors leak a little; wooden ones do not hold the air at all.\n\nTwo airlock doors with a small room between them (up to 64 blocks) make an airlock: they never open at the same time.',
            'Железные и медные двери и люки пропускают немного воздуха, деревянные не держат вовсе.\n\nДве шлюзовые двери с маленькой комнатой между ними (до 64 блоков) — это шлюз: они никогда не открываются одновременно.')
    ]),
    entry('filter_cartridges', P + 'filter_cartridge', -1, 1, ['sealed_room'], 'Cartridges and Energy', 'Картриджи и энергия', [
        item(P + 'filter_cartridge', 'The cartridge cleans the air; the dirtier, the faster it wears.', 'Картридж очищает воздух; чем грязнее, тем быстрее он тратится.'),
        recipe(P + 'filter_cartridge'),
        recipe(P + 'filter_cartridge_refill', 'A spent cartridge is refilled at the assembly station.', 'Отработанный картридж заправляют на сборочной станции.'),
        text('The unit takes energy through the cable: 4 FE a tick and 10 more per 1000 blocks of its room. Its window shows the room, the air, the energy and the cartridge. Hoppers bring new cartridges and take the spent ones.',
            'Установка берёт энергию по кабелю: 4 FE за тик и ещё 10 за каждую 1000 блоков её помещения. Окно установки показывает помещение, воздух, энергию и картридж. Воронки подают новые картриджи и забирают отработанные.')
    ]),
    entry('decontamination', P + 'decontamination_chamber', 1, -1, ['shelter_doors'], 'Decontamination', 'Дезактивация', [
        text('In a catastrophe, external contamination settles on people, their gear, bodies and armor. What is not washed off is brought into the air of the shelter.',
            'В катастрофу на людях, их снаряжении, телах и броне оседает внешнее загрязнение. Не смытое, оно заносится в воздух убежища.'),
        block(P + 'decontamination_chamber', 'Step in: the chamber washes you in a few seconds.', 'Войдите: камера смоет загрязнение за несколько секунд.'),
        recipe(P + 'decontamination_chamber'),
        recipe(P + 'decontamination_solution', 'Right-click the chamber with it, or use hoppers.', 'ПКМ по камере или подача воронкой.'),
        note('Armor and bodies are too large to step in: they are washed standing right in front of the chamber.', 'Броня и тела в камеру не помещаются: их моет, когда они стоят вплотную перед ней.')
    ]),
    entry('radiation_purge', P + 'radiation_purge_station', 3, -1, ['decontamination'], 'Radiation Purge', 'Радиационная очистка', [
        block(P + 'radiation_purge_station', 'Stand on it to lose the dose slowly.', 'Встаньте на неё — доза будет медленно уходить.'),
        recipe(P + 'radiation_purge_station'),
        text('The station works only in a sealed room with clean air and uses 80 FE a tick. It is a late machine: titanium, processors and power cells.',
            'Станция работает только в герметичном помещении с чистым воздухом и тратит 80 FE за тик. Это поздняя машина: титан, процессоры и энергоячейки.')
    ]),
    entry('backup_battery', P + 'backup_battery', 1, 1, ['filter_cartridges'], 'Backup Battery', 'Резервный аккумулятор', [
        block(P + 'backup_battery', 'It feeds only life support, and only when needed.', 'Питает только жизнеобеспечение и только при нехватке.'),
        recipe(P + 'backup_battery'),
        text('Life support is the air filtration unit and the decontamination chamber. The cable feeds them first, and when that is not enough the backup steps in.',
            'Жизнеобеспечение — установка фильтрации и дезактивационная камера. Кабель питает их первыми, а при нехватке включается резерв.')
    ]),
    entry('shelter_panel', P + 'shelter_control_panel', 3, 1, ['backup_battery'], 'Shelter Control Panel', 'Панель контроля убежища', [
        block(P + 'shelter_control_panel', 'It shows the rooms and the reserve, and finds a leak.', 'Показывает помещения и резерв, находит утечку.'),
        recipe(P + 'shelter_control_panel'),
        text('The panel gives a redstone signal by the danger: 0 all is well … 15 a leak. When a leak or a lack of energy lasts 10 seconds, the owner and their faction are warned; so too about cartridges and the reserve.\n\nThe HUD shows a "Shelter" line and the contamination on you.',
            'Панель выдаёт сигнал красного камня по опасности: 0 порядок … 15 утечка. Если утечка или нехватка энергии длится 10 секунд, владелец и его фракция получают предупреждение; так же — о картриджах и резерве.\n\nВ HUD видны строка «Убежище» и загрязнение на вас.')
    ])
]);

// ================================================================ garrison (1.7.0)

category('garrison', P + 'body_workshop_controller', 'Garrison', 'Гарнизон', [
    entry('multiblocks', P + 'energy_port', 0, 0, [], 'Multiblocks', 'Мультиблоки', [
        text('A multiblock is a machine built of many blocks around a controller. Build the frame by its plan: the controller checks it itself whenever a block inside it changes. Until the frame is whole, the work stands still and loses nothing.\n\nRight-click the controller with the repair wrench or this Codex: a hologram shows the missing blocks in place, wrong ones in a red frame. Click again to hide it.',
            'Мультиблок — машина из многих блоков вокруг контроллера. Постройте конструкцию по схеме: контроллер сам проверяет её, когда меняется любой блок внутри. Пока конструкция неполная, работа стоит и ничего не теряет.\n\nПКМ по контроллеру ремонтным ключом или этим Кодексом: голограмма покажет недостающие блоки на их местах, неверные — в красной рамке. Повторный щелчок её скрывает.'),
        text('The controller window holds the item the assembly starts from, the materials and the result. An assembly goes in stages (frame, actuators, electronics, plating, calibration): the materials of a stage are taken when it begins, its time runs while there is energy.\n\nThe controller itself takes no energy: it comes through an energy port. A cargo port lets hoppers and pipes bring materials in and take the result out.',
            'Окно контроллера: ячейка исходного предмета, материалы и результат. Сборка идёт по этапам (каркас, приводы, электроника, обшивка, калибровка): материалы этапа забираются в его начале, время идёт, пока есть энергия.\n\nСам контроллер энергию не берёт: она приходит через энергопорт. Грузовой порт позволяет воронкам и трубам подавать материалы и забирать готовое.'),
        recipe(P + 'energy_port', 'The energy port: FE from cables of this mod and others.', 'Энергопорт: FE от кабелей этого мода и других.'),
        recipe(P + 'cargo_port', 'The cargo port: items in and out.', 'Грузовой порт: предметы внутрь и наружу.'),
        recipe(P + 'assembly_plate', 'Assembly plates: the floor of every workshop.', 'Сборочные плиты: пол любого цеха.'),
        recipe(P + 'assembly_frame', 'Assembly frames: the walls and roof of the body workshop.', 'Сборочные рамы: стены и крыша цеха тел.'),
        recipe(P + 'heavy_frame', 'Heavy frames: the walls of the heavy dock.', 'Тяжёлые рамы: стены тяжёлого дока.'),
        recipe(P + 'crane_beam', 'Crane beams: the roof of the heavy dock.', 'Крановые балки: крыша тяжёлого дока.'),
        text('Manipulators are robot arms with a telescopic forearm. While the assembly goes, each reaches the machine with its tool: the welder runs its torch over it in sparks, the gripper brings the parts of the current stage and sets them in place, the screwdriver drives screws into it. Any kind fits any manipulator place of a plan; mix them as you like.',
            'Манипуляторы — руки-роботы с телескопическим предплечьем. Пока идёт сборка, каждый дотягивается до машины своим инструментом: сварочный водит горелкой по корпусу в искрах, захват подносит детали текущего этапа и ставит их на место, шуруповёрт закручивает винты. Любой вид подходит на любое место манипулятора в схеме — сочетайте как хотите.'),
        recipe(P + 'welding_manipulator', 'The welding manipulator.', 'Сварочный манипулятор.'),
        recipe(P + 'gripper_manipulator', 'The gripper manipulator: three fingers.', 'Манипулятор-захват: три пальца.'),
        recipe(P + 'screwdriver_manipulator', 'The screwdriver manipulator.', 'Манипулятор-шуруповёрт.')
    ]),
    entry('body_workshop', P + 'body_workshop_controller', 2, -1, ['multiblocks'], 'Body Workshop', 'Сборочный цех тел', [
        text('The body workshop is 3×3 and 4 high. A body grows in its middle part by part as the stages go.\n\nSynthetic bodies (advanced, high-tech, premium) are assembled only here, each from the body before it. Robot bodies can be made here cheaper than at the assembly station. Upgrades of the old body go over to the new one.',
            'Сборочный цех тел — 3×3 и 4 в высоту. Тело растёт в его середине деталь за деталью по ходу этапов.\n\nСинтетические тела (продвинутое, высокотехнологичное, премиальное) собираются только здесь, каждое из предыдущего. Тела-роботы здесь дешевле, чем на сборочной станции. Улучшения старого тела переходят на новое.'),
        multiblock(P + 'body_workshop', 'Plates, frames, two manipulators; the controller in front, both ports at the back.', 'Плиты, рамы, два манипулятора; контроллер спереди, оба порта сзади.'),
        recipe(P + 'body_workshop_controller', 'The controller: at the electronics fabricator.', 'Контроллер: в цехе электроники.'),
        recipe(P + 'workshop_basic_robot_body'),
        recipe(P + 'workshop_improved_robot_body'),
        recipe(P + 'workshop_advanced_synthetic_body'),
        recipe(P + 'workshop_hightech_synthetic_body'),
        recipe(P + 'workshop_premium_synthetic_body')
    ]),
    entry('heavy_dock', P + 'heavy_dock_controller', 2, 1, ['multiblocks'], 'Heavy Assembly Dock', 'Тяжёлый сборочный док', [
        text('The heavy dock is 7×7 and 7 high, with a gate 5×5 in front. Power armor of all three kinds is assembled here from a power frame, heavy actuators, a processor, power cells and a hull of its kind. A finished armor does not go into the window: it rolls out through the gate by itself and stands in front of the dock, ready to climb in.\n\nThe armor workstation makes the power frame and the hulls; the assembly station makes heavy actuators.',
            'Тяжёлый сборочный док — 7×7 и 7 в высоту, спереди ворота 5×5. Здесь собирается силовая броня всех трёх видов: силовой каркас, тяжёлые приводы, процессор, энергоячейки и корпус своего вида. Готовая броня не попадает в окно: она сама выезжает через ворота и встаёт перед доком — можно садиться.\n\nСиловой каркас и корпуса делает станция брони, тяжёлые приводы — сборочная станция.'),
        multiblock(P + 'heavy_dock', 'Heavy frames, crane beams on top, four manipulators; the controller left of the gate.', 'Тяжёлые рамы, сверху балки, четыре манипулятора; контроллер слева от ворот.'),
        recipe(P + 'heavy_dock_controller', 'The controller: at the armor workstation.', 'Контроллер: на станции брони.'),
        recipe(P + 'heavy_actuator'),
        recipe(P + 'power_frame'),
        recipe(P + 'combat_armor_hull'),
        recipe(P + 'mobile_armor_hull'),
        recipe(P + 'worker_armor_hull'),
        recipe(P + 'dock_power_armor'),
        recipe(P + 'dock_mobile_power_armor'),
        recipe(P + 'dock_worker_power_armor'),
        note('With multiblocks turned off in the server settings, the old station recipes of synthetic bodies and power armor come back.', 'Если мультиблоки выключены в настройках сервера, возвращаются старые рецепты синтетических тел и силовой брони на станциях.')
    ]),
    entry('ai_core', P + 'ai_core', 4, 0, ['body_workshop', 'heavy_dock'], 'AI Core', 'Ядро ИИ', [
        text('The AI core is the mind of every machine of yours: an android, a turret, the mech. One core, one machine. It does not stack, and it serves the first player who carries it: the one who made or restored it, and their faction at that moment.',
            'Ядро ИИ — разум любой вашей машины: андроида, турели, меха. Одно ядро — одна машина. Ядра не складываются в стопку; ядро служит первому игроку, который его взял (тому, кто его изготовил или восстановил), и его фракции на тот момент.'),
        recipe(P + 'neural_matrix', 'The neural matrix, at the electronics fabricator: processors, silver, quartz, an echo shard and uranium.', 'Нейроматрица — в цехе электроники: процессоры, серебро, кварц, осколок эха и уран.'),
        recipe(P + 'ai_core', 'The core itself, at the assembly station.', 'Само ядро — на сборочной станции.'),
        text('The cheaper way is riskier: the wrecks of the patrol robots and of the mech in the ruins sometimes give a damaged AI core when taken apart with the wrench (the mech always). The electronics fabricator restores it, and the restored core serves whoever takes it.',
            'Дешевле, но опаснее: остовы патрульных роботов и меха в руинах при разборке ключом иногда дают повреждённое ядро ИИ (мех — всегда). Цех электроники восстанавливает его, и восстановленное ядро служит тому, кто его взял.'),
        recipe(P + 'ai_core_restored', 'Restoring a damaged core.', 'Восстановление повреждённого ядра.'),
        recipe(P + 'combat_module', 'The combat module: guarding, patrols, fighting.', 'Боевой модуль: охрана, патруль, бой.'),
        recipe(P + 'work_module', 'The work module: mining, cutting wood, farming.', 'Рабочий модуль: шахта, лес, ферма.')
    ]),
    entry('machine_integrity', P + 'repair_wrench', 6, -1, ['ai_core'], 'Machines: Integrity and Wrecks', 'Машины: целостность и остов', [
        text('Every machine has two bars, like power armor. Structural integrity takes any damage and comes back by itself after a pause; critical integrity is lost to explosions and fire, and to any damage once structural is gone.\n\nNo structural integrity: the machine shuts down and stands still. No critical: it becomes a wreck.',
            'У каждой машины две шкалы, как у силовой брони. Структурная принимает любой урон и сама восстанавливается после паузы; критическую отнимают взрывы и огонь, а когда структурной не осталось — любой урон.\n\nСтруктурная на нуле — машина отключается и замирает. Критическая на нуле — машина становится остовом.'),
        text('The repair wrench mends structural integrity with iron and starts a shut-down machine again. With Shift it packs a machine into its item: only the owner, or a leader or officer of the owner\'s faction. A service post mends structural integrity too, with iron or armor plates from its chest. Critical integrity and wear are mended only at the maintenance station: plates, iron and actuators.\n\nArmor plating installed on a turret or the mech keeps a share of the damage off: copper 10 %, iron 20 %, titanium 30 %.',
            'Ремонтный ключ чинит структурную целостность железом и запускает отключённую машину снова. С Shift ключ упаковывает машину в предмет — только владелец или лидер и офицер его фракции. Пост обслуживания тоже чинит структурную — железом или бронепластинами из своего сундука. Критическую целостность и износ чинит только станция обслуживания: бронепластины, железо и приводы.\n\nОбшивка брони на турели или мехе снимает часть урона: медная 10 %, железная 20 %, титановая 30 %.'),
        text('Anyone can take a wreck apart with the wrench and Shift: parts, its upgrades and a damaged AI core. Machines cannot be stolen: a stranger can only destroy one.\n\nAcid rain and dust wear machines outside; an electromagnetic storm disturbs them outside a shelter unless an EMI screen is installed.',
            'Остов разбирает ключом с Shift кто угодно: детали, улучшения и повреждённое ядро ИИ. Украсть машину нельзя: чужой может её только уничтожить.\n\nКислотный дождь и пыль изнашивают машины снаружи; ЭМИ-буря сбивает их вне убежища, если не стоит ЭМИ-экран.'),
        note('Limits: per player 6 androids, 10 turrets, 1 mech; per faction 12, 20 and 3 (server settings).', 'Лимиты: на игрока 6 андроидов, 10 турелей, 1 мех; на фракцию 12, 20 и 3 (настройки сервера).')
    ]),
    entry('target_rules', 'minecraft:target', 6, 1, ['ai_core'], 'Rules of Targets', 'Правила цели', [
        text('Machines decide whom to attack by the rules of the factions.\n\nNever: you, your faction, your allies, players in creative mode, peaceful creatures.\nAlways: monsters, mutants and the machines of the ruins.\nNeutral creatures and other players: only if they attacked the machine, its owner or a member of its faction nearby.',
            'Машины выбирают цель по правилам фракций.\n\nНикогда: вы, ваша фракция, союзники, игроки в творческом режиме, мирные существа.\nВсегда: монстры, мутанты и машины руин.\nНейтральные существа и чужие игроки — только если напали на машину, её владельца или участника фракции рядом.'),
        text('In an active war, machines fight the players and machines of the enemy faction, but not on the enemy\'s land while none of them is online: the protection of territories holds for machines too.\n\nThe intruder mode (a switch on the console, off by default; the server may forbid it) attacks any outsider on your faction\'s land, in peace too, while the machine stands on that land.',
            'Во время активной войны машины бьют игроков и машины враждебной фракции, но не на их земле, пока никого из них нет в сети: защита территорий действует и для машин.\n\nРежим «нарушители» (переключатель на пульте, по умолчанию выключен; сервер может его запретить) — бить любого чужого на земле своей фракции, в том числе в мирное время, пока машина стоит на этой земле.')
    ]),
    entry('console', P + 'console', 8, 0, ['machine_integrity', 'target_rules'], 'Command Console', 'Пульт управления', [
        text('The command console is the post of your machines. It runs on energy from a cable. A machine set up within 32 blocks of your console answers to it by itself; others are linked with the tactical tablet. One console commands up to 64 machines within 256 blocks, in its own dimension.\n\nMachines far from players stand still, as everything does: the console shows them as they were last heard.',
            'Пульт управления — штаб ваших машин. Он работает от энергии по кабелю. Машина, поставленная в 32 блоках от вашего пульта, подчиняется ему сама; остальных привязывают тактическим планшетом. Один пульт командует 64 машинами в 256 блоках, в своём измерении.\n\nМашины вдали от игроков замирают, как и всё остальное: пульт показывает их такими, какими они были на последней связи.'),
        recipe(P + 'console', 'At the electronics fabricator.', 'В цехе электроники.'),
        text('Machines: the list, the chosen machine, its group (up to eight groups) and the orders: guard a point, patrol a route, escort you, hold where it stands, work (miner, lumberjack, farmer), go to the post, take cover, stand down. Points, routes and areas come from the tablet you carry.\n\nMap: the machines around the console; a monitoring station near it adds the contamination under them.',
            'Машины: список, выбранная машина, её группа (до восьми групп) и приказы: охранять точку, патрулировать маршрут, сопровождать вас, держать позицию, работать (шахтёр, лесоруб, фермер), к посту, укрыться, отбой. Точки, маршруты и области берутся из планшета, который у вас с собой.\n\nКарта: машины вокруг пульта; станция мониторинга рядом добавляет заражение под ними.'),
        text('Rules: the intruder mode for the whole console and for each group (attack any outsider on your faction\'s land, in peace too; the server may forbid it), and the weather link: before a catastrophe a weather station near foresees, every machine takes cover.\n\nLog: the alarms. Intruder, fight, machine shut down, machine lost, no energy or ammunition: each is told to you and your faction once when it starts and once when it ends.',
            'Правила: режим «нарушители» для всего пульта и для каждой группы (бить любого чужого на земле вашей фракции, и в мирное время; сервер может запретить) и связь с метеостанцией: перед катастрофой, которую предскажет метеостанция рядом, все машины укрываются.\n\nЖурнал: тревоги. Нарушитель, бой, машина отключена, машина потеряна, нет энергии или боеприпасов — о каждой вам и вашей фракции сообщат один раз в начале и один раз в конце.'),
        note('Redstone: the console gives a signal by the worst alarm (intruder 15, fight 12, lost 9, shut down 6, no supply 3); a comparator reads the share of the machines in working order.',
            'Красный камень: пульт даёт сигнал по самой серьёзной тревоге (нарушитель 15, бой 12, потеряна 9, отключена 6, нет снабжения 3); компаратор показывает долю машин в строю.')
    ]),
    entry('tactical_tablet', P + 'tactical_tablet', 10, 0, ['console'], 'Tactical Tablet', 'Тактический планшет', [
        text('The console\'s orders in the field. Right-click a console to link the tablet; right-click a machine to put it under that console.\n\nShift + right-click in the air chooses what the tablet marks: a point to guard, a patrol route (up to 16 points; Shift + right-click on a block clears it), the two corners of a work area, the chest the work is handed in. Right-click a block to set the mark. The marks are drawn in the world while the tablet is in hand.',
            'Приказы пульта в поле. ПКМ по пульту привязывает планшет; ПКМ по машине передаёт её этому пульту.\n\nShift + ПКМ в воздух выбирает, что отмечает планшет: точку охраны, маршрут патруля (до 16 точек; Shift + ПКМ по блоку стирает его), два угла рабочей области, сундук сдачи. ПКМ по блоку ставит отметку. Отметки видны в мире, пока планшет в руке.'),
        text('Right-click in the air: the short list of the console\'s machines and the quick orders, for the chosen machine or a group, within 256 blocks of the console.\n\nA work area is at most 16 blocks each side and 64 deep for a miner, 32 for a lumberjack and a farmer.',
            'ПКМ в воздух — короткий список машин пульта и быстрые приказы выбранной машине или группе, в 256 блоках от пульта.\n\nРабочая область — не больше 16 блоков в сторону и 64 в глубину для шахтёра, 32 для лесоруба и фермера.'),
        recipe(P + 'tactical_tablet', 'At the electronics fabricator.', 'В цехе электроники.')
    ]),
    entry('androids', P + 'basic_robot_body', 8, -2, ['ai_core', 'body_workshop'], 'Androids', 'Андроиды', [
        text('An android is an artificial body run by an AI core instead of a mind. It is a machine of the core\'s owner: it obeys the console and the tactical tablet, keeps the rules of targets and counts toward the limits of machines.\n\nNobody can move into a body that has an AI core: the capsule does not list it.',
            'Андроид — искусственное тело, которым управляет ядро ИИ, а не сознание. Это машина владельца ядра: она подчиняется пульту и тактическому планшету, соблюдает правила цели и входит в лимиты машин.\n\nВ тело с ядром ИИ перенестись нельзя: капсула его не показывает.'),
        text('Assembly, in the body workshop: a packed body of any class, an AI core and a module: the combat module or the work module. The body keeps its upgrades, charge and wear.\n\nThe reverse recipe takes the core out: the body, the core and the module come back whole.',
            'Сборка — в сборочном цехе тел: упакованное тело любого класса, ядро ИИ и модуль: боевой или рабочий. Тело сохраняет улучшения, заряд и износ.\n\nОбратный рецепт вынимает ядро: тело, ядро и модуль возвращаются целыми.'),
        recipe(P + 'workshop_android_basic_robot_body'),
        recipe(P + 'workshop_android_core_out_basic_robot_body'),
        text('Integrity: the structural bar is the body\'s own integrity; the critical bar is half of it. The repair wrench mends the structural bar with iron. At the maintenance station a packed android takes the body\'s materials (iron or synthetic fiber, actuators or circuit boards) and armor plates for the critical bar.',
            'Целостность: структурная шкала — это целостность самого тела, критическая — её половина. Ремонтный ключ чинит структурную железом. На станции обслуживания упакованный андроид принимает материалы тела (железо или синтетическое волокно, приводы или платы) и бронепластины для критической шкалы.')
    ]),
    entry('combat_android', P + 'combat_module', 10, -2, ['androids', 'target_rules'], 'Combat Android', 'Боевой андроид', [
        entity(P + 'android_basic_robot_body', 'A combat android: the visor and the core antenna glow in the colour of its faction.',
            'Боевой андроид: визор и антенна ядра светятся цветом его фракции.'),
        text('Right-click the android to open its gear: 36 slots (the first is the hand), armor and the off hand. Sneak + right-click packs it up; its gear falls out where it stood.\n\nIt fights with what it holds: a sword, an axe, a trident; a bow or a crossbow with arrows from its gear. A shield in the off hand blocks. Armor works as on a player. It never uses the guns of power armor.',
            'ПКМ по андроиду открывает снаряжение: 36 ячеек (первая — в руке), броня и вторая рука. Shift + ПКМ упаковывает его; снаряжение выпадает на месте.\n\nОн сражается тем, что держит: мечом, топором, трезубцем; луком или арбалетом со стрелами из своего снаряжения. Щит во второй руке блокирует удары. Броня работает как у игрока. Оружие силовой брони андроид не использует никогда.'),
        text('The gadgets of the body work: the jetpack lifts it over obstacles, plates and sealing protect it.\n\nThe better the body, the better the fighter: from the basic robot to the premium synthetic body the android is faster, reacts sooner, shoots more accurately and sees farther (16 to 32 blocks).',
            'Улучшения тела работают: реактивный ранец переносит его через препятствия, пластины и герметизация защищают.\n\nЧем лучше тело, тем лучше боец: от базового робота до премиального синтетического тела андроид быстрее, раньше реагирует, точнее стреляет и дальше видит (от 16 до 32 блоков).'),
        text('Orders from the console: guard a point, patrol a route, escort you, hold its place, go to the post, take cover.\n\nCharge: when it runs low, the android walks to the nearest charging pad (within 32 blocks) or service post, whichever is nearer, and waits there. With no charge left it stops in emergency mode, its visor blinks red and the console raises the alarm "no supply".',
            'Приказы с пульта: охранять точку, патрулировать маршрут, сопровождать вас, держать позицию, к посту, укрыться.\n\nЗаряд: когда он садится, андроид идёт к ближайшей зарядной платформе (в 32 блоках) или к посту обслуживания — что ближе — и ждёт там. Без заряда он замирает в аварийном режиме, визор мигает красным, а пульт поднимает тревогу «нет снабжения».')
    ]),
    entry('work_android', P + 'work_module', 10, -4, ['androids', 'tactical_tablet'], 'Work Android', 'Рабочий андроид', [
        text('A work android is an android with the work module: it mines, fells trees or farms. The work is chosen at the console and changes without taking the android apart.\n\nWith the tactical tablet mark the two corners of the area and the chest it hands in at, then give the order "Work" with the work you want. It works as fast as you would with the same tool, times the work speed of its body class.',
            'Рабочий андроид — андроид с рабочим модулем: он копает, рубит лес или ведёт ферму. Работа выбирается на пульте и меняется без пересборки.\n\nОтметьте тактическим планшетом два угла области и сундук сдачи, затем дайте приказ «Работа» с нужной работой. Он работает так же быстро, как вы с тем же инструментом, с поправкой на класс тела.'),
        recipe(P + 'workshop_android_work_basic_robot_body'),
        text('What it gets goes into its gear. When the gear is full, or the work is done, it carries it to the chest and keeps a few seeds, saplings or plugging blocks. Low on charge it goes to a charging pad and then goes on.\n\nIt never breaks containers, the machines of the mod, capsules, shelter doors and hatches, the faction core, nor anything on land its owner may not use. Attacked, it runs to its chest and the console hears of the fight. It does not fight.',
            'Добыча идёт в снаряжение. Когда оно полно или работа закончена, андроид относит всё в сундук и оставляет себе немного семян, саженцев или блоков для заделки. При низком заряде идёт к зарядной платформе, потом продолжает.\n\nОн никогда не ломает контейнеры, машины мода, капсулы, двери и люки убежища, ядро фракции и ничего на земле, где его владельцу нельзя. Если на него напали, он бежит к сундуку, а пульт узнаёт о бое. Сам он не дерётся.'),
        text('Miner: up to 16 by 16 blocks and 64 deep. It takes the area layer by layer from the top and leaves a spiral stair along the wall to climb out. It needs a pickaxe in hand; when it breaks, the android takes a new one from the chest or reports "no tool". Water and lava are plugged with cobblestone or other stone from its gear, or skipped.\n\nThe work "miner: ores only" mines the whole area too, but keeps only ores and the valuable blocks of the config: the stone stays behind as nothing.',
            'Шахтёр: область до 16 на 16 блоков и до 64 в глубину. Снимает её слой за слоем сверху и оставляет вдоль стены винтовую лестницу, чтобы выбраться. Нужна кирка в руке; сломалась — берёт новую из сундука или сообщает «нет инструмента». Воду и лаву заделывает булыжником или другим камнем из снаряжения либо пропускает.\n\nРабота «шахтёр: только руда» тоже снимает всю область, но оставляет себе только руду и ценные блоки из конфига: камень пропадает.'),
        text('Lumberjack: up to 32 by 32 blocks. It fells whole trees from the bottom, branches and big 2 by 2 trees too, and cuts their leaves. A log counts as a tree only with natural leaves near it, so it leaves your buildings alone. It picks up logs, saplings, apples and sticks and plants a sapling where the tree stood. An axe in hand makes it faster; without one it fells by hand.',
            'Лесоруб: область до 32 на 32 блока. Рубит деревья целиком снизу вверх, с ветками и большими деревьями 2 на 2, и срезает их листву. Дерево — это бревно с естественной листвой рядом, поэтому постройки он не трогает. Собирает брёвна, саженцы, яблоки и палки и сажает саженец на место дерева. С топором в руке рубит быстрее, без него — рукой.'),
        text('Farmer: up to 32 by 32 blocks. It harvests only what is ripe: wheat, carrots, potatoes, beetroots, nether wart, cocoa, sweet berries, melons and pumpkins (the stem stays), sugar cane and bamboo (the lowest block stays). It replants from what it gathered and sows empty farmland. It does not till new land nor touch animals, and never tramples the fields.',
            'Фермер: область до 32 на 32 блока. Собирает только созревшее: пшеницу, морковь, картофель, свёклу, незерский нарост, какао, сладкие ягоды, арбузы и тыквы (стебель остаётся), сахарный тростник и бамбук (нижний блок остаётся). Пересаживает из собранного и засевает пустые грядки. Новую землю не вспахивает, животных не трогает, грядки не вытаптывает.'),
        text('Its reports at the console: the work is done or there is nothing to do (in the log only), no tool, the chest is full or out of reach, no charge, attacked.',
            'Сообщения на пульте: работа закончена или нечего делать (только в журнале), нет инструмента, сундук полон или недоступен, нет заряда, атакован.')
    ]),
    entry('defence_turrets', P + 'machine_gun_turret', 12, -2, ['ai_core', 'heavy_dock', 'target_rules'], 'Defence Turrets', 'Турели обороны', [
        entity(P + 'machine_gun_turret', 'The machine gun turret: the guard turret of the ruins in service paint, with stripes in the colour of its faction.',
            'Пулемётная турель: турель охраны из руин в рабочей окраске, с полосами цвета своей фракции.'),
        text('Your own guard turrets. A turret stands on a turret mount: cables bring the mount energy, hoppers or your hand bring it ammunition. Each shot takes its energy and one round or rocket from the mount. Without them the turret is silent and the console raises the alarm "no supply".\n\nRight-click the mount to see what it holds.',
            'Собственные турели охраны. Турель стоит на основании: кабель подводит к нему энергию, воронка или вы сами кладёте боеприпасы. Каждый выстрел берёт из основания энергию и один патрон или ракету. Без них турель молчит, а пульт поднимает тревогу «нет снабжения».\n\nПКМ по основанию показывает, что в нём есть.'),
        recipe(P + 'turret_mount'),
        text('Assembly, in the heavy dock: armor plates and actuators, a weapon node, an AI core and circuit boards. The turret belongs to the owner of its core. Set it up by right-clicking the top of a mount.\n\nThe machine gun turret: 24 blocks, bursts of assault rifle rounds, integrity 80 / 40. It never hits its own.',
            'Сборка — в тяжёлом доке: бронепластины и приводы, оружейный узел, ядро ИИ и платы. Турель принадлежит владельцу ядра. Ставится ПКМ по верху основания.\n\nПулемётная турель: 24 блока, очереди патронами автоматической винтовки, целостность 80 / 40. В своих не попадает.'),
        recipe(P + 'dock_machine_gun_turret'),
        text('The rocket turret: 6 to 32 blocks, a rocket along an arc against groups, mechs and brutes, integrity 100 / 50. Its blast breaks no blocks, and it holds fire while one of its own is near the target.',
            'Ракетная турель: от 6 до 32 блоков, ракета по дуге против групп, мехов и громил, целостность 100 / 50. Взрыв не ломает блоки, и турель не стреляет, пока рядом с целью кто-то свой.'),
        recipe(P + 'dock_rocket_turret'),
        recipe(P + 'rocket'),
        text('A turret finds its target by the rules of targets and obeys the console: hold, stand down. Upgrades, two slots, with the upgrade mounting tool: armor plating (less damage), an EMI screen, a sensor unit (8 blocks more range), a stabilizer (half the spread).\n\nA dust storm spreads its fire. An EMI storm outside a shelter throws its aim off and now and then shuts it down for a few seconds.',
            'Цель турель выбирает по правилам цели и подчиняется пульту: держать, отбой. Улучшения, два слота, через устройство монтажа: обшивка брони (меньше урона), ЭМИ-экран, сенсорный блок (+8 блоков дальности), стабилизатор (вдвое меньше разброс).\n\nПыльная буря увеличивает разброс. ЭМИ-буря вне убежища сбивает прицел и иногда отключает турель на несколько секунд.'),
        recipe(P + 'stabilizer'),
        text('Integrity as for every machine: at no structural integrity it shuts down and its barrel hangs; the repair wrench with iron starts it again. At no critical integrity it is a wreck: Shift + the wrench takes it apart for plates, its upgrades and a damaged AI core.',
            'Целостность — как у всех машин: без структурной турель отключается и опускает ствол, ремонтный ключ с железом запускает её снова. Без критической — остов: Shift + ключ разбирает его на пластины, улучшения и повреждённое ядро ИИ.')
    ]),
    entry('guard_mech', P + 'guard_mech', 12, 0, ['defence_turrets', 'heavy_dock'], 'Guard Mech', 'Мех охраны', [
        entity(P + 'guard_mech', 'The guard mech: the mech of the ruins in service, with a stripe and a banner in the colour of its faction.',
            'Мех охраны: мех из руин на службе, с полосой и знаменем цвета своей фракции.'),
        text('The top of your garrison. Built in the heavy dock: a power frame and heavy actuators, a mech armor hull, two weapon nodes, an AI core with processors and power cells. Integrity 250 / 125, three upgrade slots (power armor upgrades). Nobody pilots it: for a pilot there is power armor. It has no second phase: it is a machine in service, not a boss.',
            'Вершина гарнизона. Собирается в тяжёлом доке: силовой каркас и тяжёлые приводы, бронекорпус меха, два оружейных узла, ядро ИИ с процессорами и энергоячейками. Целостность 250 / 125, три слота улучшений (улучшения силовой брони). Пилота у него нет: для пилота есть силовая броня. Второй фазы нет: это машина на службе, а не босс.'),
        recipe(P + 'dock_guard_mech_gun_rocket'),
        recipe(P + 'mech_armor_hull'),
        text('Weapons: on each side a machine gun node or a rocket launcher node, in any mix. Right-click it with a node to put it on the right, with Shift on the left; the old node comes back to you.\n\nIt has its own energy and magazine. Right-click it with rounds or rockets to load them; with an empty hand it tells its energy and magazine. Rockets break no blocks and are not fired near its own.',
            'Оружие: на каждом борту пулемётный или ракетный узел, в любом сочетании. ПКМ по меху узлом ставит его на правый борт, с Shift — на левый; старый узел возвращается вам.\n\nУ меха свой запас энергии и магазин. ПКМ патронами или ракетами заряжает их; пустой рукой — показывает энергию и магазин. Ракеты не ломают блоки и не летят, если рядом с целью свои.'),
        text('Orders from the console as for androids. The mech is wide and heavy: it walks open ground and does not pass narrow doors. When you give it a patrol route, the console warns you of places too narrow for it.\n\nBelow a fifth of its energy it walks to the nearest service post and waits there until charged. Empty, it stops and the console raises the alarm "no supply", as it does when it has no ammunition left.',
            'Приказы с пульта — как у андроидов. Мех широкий и тяжёлый: ходит по открытой местности и не проходит в узкие двери. Если дать ему маршрут патруля, пульт предупредит об узких местах.\n\nПри пятой части энергии он идёт к ближайшему посту обслуживания и ждёт там зарядки. Пустой — останавливается, а пульт поднимает тревогу «нет снабжения», как и когда кончаются боеприпасы.')
    ]),
    entry('service_post', P + 'service_post', 14, -1, ['guard_mech', 'combat_android'], 'Service Post', 'Пост обслуживания', [
        text('A station for your machines. Lay four post plates in a square of two by two, set the post console next to them, a chest next to the console, and bring the console energy by cable.\n\nA machine standing on the plates is charged, its structural integrity mended with iron ingots or armor plates from the chest, and a mech\'s magazine filled with rounds and rockets from it. Critical integrity is not mended here: pack the machine and take it to the maintenance station.',
            'Станция для ваших машин. Положите четыре плиты поста квадратом 2 на 2, рядом с ними — пульт поста, к пульту — сундук, и подведите к пульту энергию кабелем.\n\nМашина на плитах заряжается, её структурная целостность чинится железом или бронепластинами из сундука, а магазин меха пополняется патронами и ракетами оттуда же. Критическую целостность пост не чинит: машину упаковывают и несут на станцию обслуживания.'),
        recipe(P + 'service_post'),
        recipe(P + 'service_post_plate'),
        text('The order "to the post" sends androids and mechs to the nearest post (or to their console when there is none). Low on energy, mechs come by themselves; androids go to a post or to a charging pad, whichever is nearer. Turrets need no post: their mount feeds them.\n\nRight-click the post console to see its energy and the machines it serves.',
            'Приказ «к посту» отправляет андроидов и мехов к ближайшему посту (или к их пульту, если поста нет). При низкой энергии мехи приходят сами, андроиды идут к посту или к зарядной платформе — что ближе. Турелям пост не нужен: их кормит основание.\n\nПКМ по пульту поста показывает его энергию и обслуживаемые машины.')
    ])
]);

// ================================================================ output

function langPage(entryId, index) {
    return 'codex.projectrebirth.' + entryId + '.p' + (index + 1);
}

function writeJson(file, value) {
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}

const en = {}, ru = {};
const entriesDir = ROOT + 'codex/entries/';
fs.mkdirSync(entriesDir, {recursive: true});
for (const f of fs.readdirSync(entriesDir)) fs.unlinkSync(entriesDir + f);
const index = {categories: []};
const seen = new Set();
for (const c of CATEGORIES) {
    en['codex.projectrebirth.category.' + c.id] = c.en;
    ru['codex.projectrebirth.category.' + c.id] = c.ru;
    index.categories.push({id: c.id, icon: c.icon, entries: c.entries.map(e => e.id)});
    for (const e of c.entries) {
        if (seen.has(e.id)) throw new Error('Two entries ' + e.id);
        seen.add(e.id);
        en['codex.projectrebirth.entry.' + e.id] = e.en;
        ru['codex.projectrebirth.entry.' + e.id] = e.ru;
        const pages = e.pages.map(([type, value, enText, ruText], i) => {
            const page = {type};
            if (value) page.value = value;
            if (enText) {
                const key = langPage(e.id, i);
                page.text = key;
                en[key] = enText;
                ru[key] = ruText;
            }
            return page;
        });
        const json = {icon: e.icon, x: e.x, y: e.y};
        if (e.parents.length) json.parents = e.parents;
        json.pages = pages;
        writeJson(entriesDir + e.id + '.json', json);
    }
}
writeJson(ROOT + 'codex/index.json', index);

// The screen's own strings stay; every other codex string is written anew.
const KEEP = new Set(['codex.projectrebirth.map_hint', 'codex.projectrebirth.back', 'codex.projectrebirth.no_recipe', 'codex.projectrebirth.recipe_time']);
for (const [file, add] of [['en_us', en], ['ru_ru', ru]]) {
    const p = ROOT + 'lang/' + file + '.json';
    const raw = fs.readFileSync(p, 'utf8');
    const cr = raw.includes('\r\n');
    const json = JSON.parse(raw);
    for (const key of Object.keys(json)) {
        if (key.startsWith('codex.projectrebirth.') && !KEEP.has(key)) delete json[key];
    }
    Object.assign(json, add);
    let s = JSON.stringify(json, null, 2) + '\n';
    if (cr) s = s.replace(/\n/g, '\r\n');
    fs.writeFileSync(p, s);
}
const count = CATEGORIES.reduce((n, c) => n + c.entries.length, 0);
console.log(CATEGORIES.length + ' categories, ' + count + ' entries, ' + Object.keys(en).length + ' strings per language');
