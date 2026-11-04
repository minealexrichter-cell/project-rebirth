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

## Модели и текстуры

Все модели и текстуры сделаны для мода программно: тела и броня — скриптом в Blockbench
`blockbench/tools/build_models.js`, текстуры блоков и предметов — скриптом `blockbench/tools/build_textures.js`, объёмные модели блоков — скриптом `blockbench/tools/build_block_models.js`.
Сторонних моделей и текстур нет.
