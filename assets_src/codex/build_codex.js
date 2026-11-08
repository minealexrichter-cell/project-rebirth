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
        text('4. The armor workstation makes armor plates and power armor; then come the implant fabricator, the armor upgrade fabricator and the weapon workshop.\n5. Stations need energy: a combustion generator, solar panels, a battery, cable.\n6. The world is hostile from the first day: a respirator, filters and a roof at first, a sealed shelter with air filtration later.',
            '4. Станция брони — бронепластины и силовую броню; дальше цех имплантов, цех модулей брони и оружейная мастерская.\n5. Станциям нужна энергия: генератор на угле, солнечные панели, аккумулятор, кабель.\n6. Мир враждебен с первого дня: сначала респиратор, фильтры и крыша, позже — герметичное убежище с фильтрацией воздуха.'),
        note('Each tab of this book is one branch of that chain.', 'Каждая вкладка этой книги — одна ветка этой цепочки.')
    ]),
    entry('keys', 'minecraft:compass', 2, 2, ['welcome'], 'Keys and Settings', 'Клавиши и настройки', [
        text('Keys of the mod (change them in Controls, section "Project: Rebirth"):\n\nZ — return to the human body\nV (hold) — emergency release of the hatch of a shut-down armor\n9 in armor — the ability slot\nB — worker armor: mine 3×3 or one block\nN — floodlight of the armor\nR — reload a weapon\nG — the faction window\nH — HUD and transfer animation settings',
            'Клавиши мода (меняются в настройках управления, раздел «Project: Rebirth»):\n\nZ — вернуться в человеческое тело\nV (удерживать) — аварийный сброс люка отключённой брони\n9 в броне — слот способности\nB — рабочая броня: добыча 3×3 или 1 блок\nN — прожектор брони\nR — перезарядка оружия\nG — окно фракции\nH — настройки HUD и анимации переноса'),
        text('H opens the HUD settings: turn it on or off, its size and where the panels stand, and the transfer animation. They are kept for each player.\n\nThe balance of the whole mod — times, health, damage, costs — is in config/projectrebirth-server.toml on the server (in single player, in the game folder).',
            'H открывает настройки HUD: включение, размер, где стоят панели, и анимацию переноса. Они хранятся у каждого игрока.\n\nБаланс всего мода — время, прочность, урон, стоимость — в файле config/projectrebirth-server.toml на сервере (в одиночной игре — в папке игры).')
    ])
]);

// ================================================================ technology

category('technology', P + 'electronics_fabricator', 'Technology', 'Технологии', [
    entry('ores', P + 'titanium_ore', -4, 0, [], 'Ores and Metals', 'Руды и металлы', [
        text('Five new ores lie in the world, in stone and in deepslate:\n\nTin — common, from y −16 to 112.\nAluminum — common, from 0 to 112.\nSilver — from −64 to 32.\nLithium — rare, from −48 to 16.\nTitanium — the rarest and deepest, from −80 to 0.\n\nMined ore gives raw metal; a furnace or a blast furnace makes ingots of it.',
            'В мире пять новых руд, в камне и в глубинном сланце:\n\nОлово — часто, от y −16 до 112.\nАлюминий — часто, от 0 до 112.\nСеребро — от −64 до 32.\nЛитий — редко, от −48 до 16.\nТитан — реже всего и глубже всего, от −80 до 0.\n\nДобытая руда даёт сырой металл; печь или плавильная печь делают из него слиток.'),
        block(P + 'tin_ore', 'Tin ore: needed for bronze from the very start.', 'Оловянная руда: нужна для бронзы с самого начала.'),
        block(P + 'deepslate_titanium_ore', 'Deepslate titanium ore: for the armor workstation, armor and the best bodies.', 'Титановая руда в глубинном сланце: для станции брони, брони и лучших тел.'),
        recipe(P + 'titanium_ingot_from_blasting', 'A blast furnace smelts raw metal twice as fast.', 'Плавильная печь плавит сырой металл вдвое быстрее.')
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
        block(P + 'assembly_station', 'Actuators, synthetic fiber, the transfer capsule and all artificial bodies.', 'Приводы, синтетическая ткань, капсула переноса и все искусственные тела.'),
        recipe(P + 'assembly_station', 'Aluminum, circuit boards, a crafting table and iron.', 'Алюминий, платы, верстак и железо.'),
        recipe(P + 'actuator', 'Actuators: bronze, iron and redstone.', 'Приводы: бронза, железо и красная пыль.'),
        recipe(P + 'synthetic_fiber', 'Synthetic fiber, the skin of synthetic bodies: string and a slime ball.', 'Синтетическая ткань — кожа синтетических тел: нитки и слизь.')
    ]),
    entry('armor_workstation', P + 'armor_workstation', 4, -1, ['assembly_station'], 'Armor Workstation', 'Станция брони', [
        block(P + 'armor_workstation', 'Armor plates, power armor of three kinds and the heavy protective suit.', 'Бронепластины, силовая броня трёх видов и тяжёлый защитный костюм.'),
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
        text('Put the packed armor or body in and add the materials: every two seconds the station uses one of them and 20 FE per tick.\n\nArmor: armor plates mend the critical integrity, iron ingots the structural one, up to the maximum with the plating of its upgrades. Robot bodies take iron ingots, synthetic bodies synthetic fiber.',
            'Положите упакованную броню или тело и материалы: каждые две секунды станция тратит один предмет и 20 FE за тик.\n\nБроня: бронепластины чинят критическую целостность, железные слитки — структурную, до максимума с учётом обшивки улучшений. Роботизированные тела чинятся железом, синтетические — синтетической тканью.')
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
        block(P + 'solar_panel', '8 FE per tick in daylight under the open sky; nothing at night and in rain.', '8 FE за тик днём под открытым небом; ночью и в дождь — ничего.'),
        recipe(P + 'solar_panel', 'Glass, aluminum, a circuit board and iron.', 'Стекло, алюминий, плата и железо.'),
        note('A panel gives energy only downwards: put the cable or the machine under it. It is placed like stairs, its high edge away from you.', 'Панель отдаёт энергию только вниз: кабель или машину ставьте под неё. Ставится как ступенька, высоким краем от вас.')
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
        text('Synthetic bodies (classes 3–5) mend themselves a few seconds after damage.\n\nAn empty body stands like a solid thing: you can stand on it and nothing pushes it away.',
            'Синтетические тела (классы 3–5) понемногу чинятся сами через несколько секунд после урона.\n\nПустое тело стоит как твёрдый предмет: на него можно встать, его ничто не сдвигает.'),
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
        recipe(P + 'jetpack')
    ])
]);

// ================================================================ power armor

category('armor', P + 'power_armor', 'Power Armor', 'Силовая броня', [
    entry('power_armor', P + 'power_armor', -3, 0, [], 'Power Armor', 'Силовая броня', [
        entity(P + 'power_armor', 'A walking suit of armor for a human or a body inside it.', 'Шагающая броня для человека или тела внутри.'),
        recipe(P + 'power_armor', 'Made at the armor workstation.', 'Делается на станции брони.'),
        text('Place it from its item. Right-click to climb in, Shift to get out. The pilot is protected: all damage goes to the armor.\n\nArmor strikes with both hands for more damage, reaches farther and jumps higher; items in its fists keep their poses — bow, crossbow, trident, shield.',
            'Ставится из предмета. ПКМ — сесть, Shift — выйти. Пилот защищён: весь урон получает броня.\n\nБроня бьёт обеими руками сильнее, дотягивается дальше и прыгает выше; предметы в её кулаках держатся как надо — лук, арбалет, трезубец, щит.'),
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
        recipe(P + 'mobile_power_armor', 'Mobile armor.', 'Мобильная броня.'),
        recipe(P + 'worker_power_armor', 'Worker armor.', 'Рабочая броня.')
    ]),
    entry('armor_upgrades', P + 'upgrade_mounting_tool', 1, 1, ['variants'], 'Armor Upgrades', 'Улучшения брони', [
        text('With the mounting tool, Shift + right-click your own empty armor: a window with 7 slots of hull parts opens. Putting an upgrade into a slot installs it, taking it out removes it. A banner goes on the back.',
            'Устройство монтажа: Shift + ПКМ по своей пустой броне открывает окно с 7 слотами частей корпуса. Положить улучшение в слот — установить, забрать — снять. Знамя ставится на спину.'),
        recipe(P + 'upgrade_mounting_tool'),
        text('Plating raises the most structural integrity, for each part: copper +10, iron +20, titanium +30 and +5 critical. The compressed air module raises the jump and the safe fall height. The floodlight (N) lights where the pilot looks.',
            'Обшивка поднимает максимум структурной целостности за каждую часть: медь +10, железо +20, титан +30 и +5 критической. Модуль сжатого воздуха повышает прыжок и безопасную высоту падения. Прожектор (N) освещает то, куда смотрит пилот.'),
        recipe(P + 'armor_titanium_plating'),
        recipe(P + 'compressed_air_module'),
        recipe(P + 'floodlight')
    ]),
    entry('weapons', P + 'assault_rifle', 1, 3, ['variants'], 'Weapons', 'Оружие', [
        text('Guns fire only from working power armor: left click fires, right click aims, R reloads. Without armor a gun can be carried, but it is a heavy load and does not fire. Each gun has its own rounds, made at the weapon workshop.',
            'Оружие стреляет только из работающей силовой брони: ЛКМ — огонь, ПКМ — прицеливание, R — перезарядка. Без брони его можно носить, но это тяжёлый груз, и оно не стреляет. У каждого оружия свои патроны, они делаются в оружейной мастерской.'),
        item(P + 'assault_rifle', 'Assault rifle: automatic, 30 rounds, 4 damage a bullet, 64 blocks.', 'Автоматическая винтовка: очередями, 30 патронов, 4 урона за пулю, 64 блока.'),
        recipe(P + 'assault_rifle'),
        recipe(P + 'assault_rifle_ammo'),
        item(P + 'battle_rifle', 'Battle rifle: one shot at a time with a scope, 10 rounds, 13 damage, 128 blocks.', 'Полуавтоматическая винтовка: по одному выстрелу, с прицелом, 10 патронов, 13 урона, 128 блоков.'),
        recipe(P + 'battle_rifle'),
        recipe(P + 'battle_rifle_ammo'),
        item(P + 'combat_shotgun', 'Combat shotgun: 6 shells, 8 pellets of 2.8 damage, 24 blocks; reloaded a shell at a time.', 'Боевой дробовик: 6 патронов, 8 дробин по 2,8 урона, 24 блока; заряжается по одному патрону.'),
        recipe(P + 'combat_shotgun'),
        recipe(P + 'shotgun_shell')
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
