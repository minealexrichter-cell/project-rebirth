# Project: Rebirth: источники ресурсов

## Звуки

**По решению владельца мод играет ванильные звуки Minecraft**: в
`src/main/resources/assets/projectrebirth/sounds.json` каждое событие мода ссылается на ванильное звуковое событие
(`"type": "event"`), своих файлов в моде нет. Ниже — подобранные ранее звуки Kenney; они лежат в
`assets_src/sounds/kenney/` и в мод не входят. Чтобы вернуть их, скопируйте `assets_src/sounds/kenney/sounds/` в
`src/main/resources/assets/projectrebirth/sounds/` и `assets_src/sounds/kenney/sounds.json` на место `sounds.json`.

| Событие мода | Ванильное событие сейчас |
|---|---|
| шаги робота | `block.metal.step` (выше тоном) |
| урон / уничтожение робота | `entity.iron_golem.hurt` / `entity.iron_golem.death` (выше тоном) |
| шаги / урон брони | `entity.iron_golem.step` / `entity.iron_golem.hurt` (ниже тоном) |
| отключение / разрушение брони | `block.beacon.deactivate` / `entity.generic.explode` |
| вход / выход из брони | `block.iron_door.close` / `block.iron_door.open` |
| работа станции (цикл) | `block.beacon.ambient` (выше тоном) |
| конец операции | `block.note_block.chime` |
| работа генератора (цикл) | `block.furnace.fire_crackle` |
| перенос / возвращение | `block.beacon.activate` / `block.beacon.deactivate` |
| установка / снятие улучшения | `block.smithing_table.use` / `block.iron_trapdoor.open` |
| джетпак (короткий звук, сервер повторяет его в полёте) | `entity.blaze.burn` (ниже тоном) |
| прожектор включён / выключен | `block.lever.click` (выше / ниже тоном) |

### Звуки Kenney (сейчас не используются)

Эти звуки взяты из набора **Sci-fi Sounds** от **Kenney** (www.kenney.nl) под лицензией
**Creative Commons Zero (CC0 1.0)**: их можно свободно использовать в любых проектах, указывать автора не обязательно
(Kenney просит об этом как о любезности, поэтому он указан здесь). Текст лицензии:
http://creativecommons.org/publicdomain/zero/1.0/

| Набор | Страница | Версия, дата | Получен |
|---|---|---|---|
| Sci-fi Sounds | https://kenney.nl/assets/sci-fi-sounds | 1.0, 11.10.2020 | 03.11.2026, `kenney_sci-fi-sounds.zip`, 5 875 104 байт |

Набор Impact Sounds (тоже Kenney, CC0) был скачан, но не используется: все его файлы стерео, а Minecraft делает
объёмными (тише с расстоянием) только моно-звуки.

Файлы скопированы без изменений и переименованы. Одни и те же файлы с разной громкостью и высотой тона служат
нескольким событиям; это задано в `assets_src/sounds/kenney/sounds.json`.

| Файл (`assets_src/sounds/kenney/sounds/`) | Исходный файл Sci-fi Sounds | События |
|---|---|---|
| `metal/impact1–5.ogg` | `impactMetal_000–004.ogg` | шаги и урон робота, шаги и урон брони |
| `body/robot_destroyed1–3.ogg` | `explosionCrunch_000–002.ogg` | уничтожение робота |
| `armor/wrecked1–2.ogg` | `explosionCrunch_003–004.ogg` | разрушение брони |
| `armor/enter1–3.ogg` | `doorClose_000–002.ogg` | вход в броню (люк закрывается); `enter3` ниже тоном — отключение брони |
| `armor/exit1–3.ogg` | `doorOpen_000–002.ogg` | выход из брони (люк открывается) |
| `station/working.ogg` | `computerNoise_000.ogg` | работа станции (цикл) |
| `station/done1.ogg` | `laserRetro_000.ogg` | станция закончила операцию |
| `generator/running.ogg` | `engineCircular_000.ogg` | работа генератора (цикл) |
| `transfer/enter1–2.ogg` | `forceField_000–001.ogg` | перенос в тело |
| `transfer/return1–2.ogg` | `forceField_002–003.ogg` | возвращение в своё тело |

Чтобы заменить звук, положите свой `.ogg` **в моно** с тем же именем или поправьте `sounds.json`; код менять
не нужно.

### Звуки оружия

**По решению владельца у оружия настоящие звуки, не ванильные.** Файлы лежат в `assets/projectrebirth/sounds/weapon/`. Все источники под лицензией **CC0**, лицензия каждой страницы проверена 05.11.2026. Исходные файлы в проект не входят (194 МБ), подготовка записана в `assets_src/sounds/weapons/prepare_weapon_sounds.sh`: моно, 44,1 кГц, обрезка, выравнивание пика, короткое затухание, `.ogg`.

| Файлы мода | Исходный файл | Автор | Источник |
|---|---|---|---|
| `assault_rifle_shot1–2` | `AR-15/D_32P.wav`, вблизи | Ben Jaszczak, Brian Nelson, Kevin Heras, Matthew Nanney | [The Free Firearm Sound Library](https://opengameart.org/content/the-free-firearm-sound-library), `Prepared SFX Library.7z`, 193 954 738 байт |
| `battle_rifle_shot1–2` | `SKS/U_14P.wav`, вблизи | те же | тот же |
| `combat_shotgun_shot1–2` | `Nova/O_21P.wav`, вблизи | те же | тот же |
| `assault_rifle_reload` | `assaultriflereload1.wav` | SpringySpringo | [Gun reload sounds](https://opengameart.org/content/gun-reload-sounds) |
| `battle_rifle_reload` | `gunreload1.wav` | SpringySpringo | тот же |
| `combat_shotgun_reload` | `singlebullet1.wav` | BMacZero | [Gun reload sound effects](https://opengameart.org/content/gun-reload-sound-effects) |
| `assault_rifle_empty`, `battle_rifle_empty`, `combat_shotgun_empty` | три щелчка из `equipment_clicks3.wav` | LFA | [Equipment clicks III](https://opengameart.org/content/equipment-clicks-iii) |

Скачаны, но не используются: `shotguncock.wav`, `clipload1.wav`, `clipload2.wav`.

### Звуки среды (1.3.0)

**С разрешения владельца** (07.11.2026). Файлы лежат в `assets/projectrebirth/sounds/environment/`. Лицензия каждой страницы проверена 07.11.2026: **CC0**. Исходные файлы в проект не входят, подготовка записана в `assets_src/sounds/environment/prepare_environment_sounds.sh`: моно, 44,1 кГц, обрезка, выравнивание пика, плавное начало и конец, `.ogg`.

| Файлы мода | Исходный файл | Автор | Источник |
|---|---|---|---|
| `siren` (первые 8 с) | `siren_0.mp3`, 661 211 байт | aquinn | [Sirens and Alarm Noise](https://opengameart.org/content/sirens-and-alarm-noise) |
| `acid_hiss1–5` | `steam hisses - Marker #1–5.wav` из `steam_hisses.zip`, 622 313 байт | bart | [Steam release sounds](https://opengameart.org/content/steam-release-sounds) |
| `geiger_click1–3` | сделаны тем же скриптом из белого шума (ffmpeg), ничего не скачано | — | — |

Подходящих щелчков счётчика Гейгера под CC0 на OpenGameArt не нашлось, поэтому щелчки сделаны программно.

### Звуки убежища (1.4.0)

**Решение владельца:** звуки сделаны программно, ничего не скачано. Скрипт `assets_src/sounds/shelter/make_shelter_sounds.sh` синтезирует их через ffmpeg из синусов и шума (моно, 44,1 кГц, `.ogg`); файлы лежат в `assets/projectrebirth/sounds/shelter/`.

| Файл мода | Из чего |
|---|---|
| `filtration_hum` (петля 4 с) | гул мотора 90/180/270 Гц и пульсирующий шум лопастей |
| `airlock_hiss` (1,3 с) | удар засовов 70 Гц и затухающее шипение высокочастотного шума |
| `decontamination_shower` (петля 3 с) | струи — полосовой белый шум, напор в трубах — низкий «коричневый» шум |
| `leak_alarm` (петля 2 с) | два тона 880 и 660 Гц по четверти секунды |

### Звуки технических ограничений (1.5.0)

**По плану 1.5.0 — синтез, ничего не скачано.** Скрипт `assets_src/sounds/power/make_power_sounds.sh` синтезирует их через ffmpeg (моно, 44,1 кГц, `.ogg`); файлы лежат в `assets/projectrebirth/sounds/power/`.

| Файл мода | Из чего |
|---|---|
| `dust_wind` (петля 8 с) | гул «коричневого» шума и шорох песка (полосовой белый шум), порывы — медленные волны громкости |
| `emi_crackle` (1,2 с) | рваный электрический гул 50/150/250 Гц и щелчки статики |
| `emi_fault` (0,7 с) | падающий прерывистый тон и короткий гул в конце |
| `low_charge` (0,6 с) | два коротких писка 1100 и 780 Гц |
| `overheat_alarm` (1,2 с) | три высоких импульса поверх нарастающего тона |
| `charging_hum` (петля 3 с) | гул трансформатора 100 Гц с гармониками и тихий пульсирующий писк |

### Звуки разведки (1.6.0)

| Файл | Что это | Источник |
|---|---|---|
| `scouting/source_hum` | гул радиоактивных обломков и шлама: 50 Гц с гармониками, тихий треск | синтез |
| `scouting/analyzer_beep` | двойной писк полевого анализатора в заражённой зоне | синтез |
| `scouting/map_print` | станция мониторинга печатает карту: треск печатающей головки и гул мотора | синтез |

**По плану 1.6.0 — синтез, ничего не скачано.** Скрипт `assets_src/sounds/scouting/make_scouting_sounds.sh` делает их через ffmpeg (моно, 44,1 кГц, `.ogg`); файлы лежат в `assets/projectrebirth/sounds/scouting/`. Щелчки анализатора у источников — звук дозиметра выше тоном.

## Модели и текстуры

Все модели и текстуры сделаны для мода программно: тела и броня — скриптом в Blockbench
`blockbench/tools/build_models.js`, текстуры блоков и предметов — скриптом `blockbench/tools/build_textures.js`, объёмные модели блоков — скриптом `blockbench/tools/build_block_models.js`.
Сторонних моделей и текстур нет.
