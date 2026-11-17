---
name: model-pipeline
description: Как делать и переэкспортировать модели, текстуры и анимации Project: Rebirth скриптами blockbench/tools через Blockbench MCP — сборка GeckoLib-моделей, проверка мерцающих граней и дыр текстуры, текстуры остова, модели блоков, снимки. Используй при любой правке модели, текстуры или анимации.
---

# Модели, текстуры, анимации

Все модели делаются **скриптами**, а не руками: исходник правды — `blockbench/tools/*.js`. Ресурсы игры — в `src/main/resources/assets/projectrebirth/`, проекты Blockbench — в `blockbench/*.bbmodel`. Подробный контракт костей и анимаций — `docs/MODEL_SPEC.md`.

| Скрипт | Что делает | Вход в Blockbench |
|---|---|---|
| `build_models.js` | GeckoLib-модели сущностей, оружия, манипуляторов и их анимации (`SPECS.<id>`) | `RebirthModels.build(id)`, `RebirthModels.save(id)`, `RebirthModels.checkAll()` |
| `build_block_models.js` | JSON-модели блоков и состояния (`MODELS`, `STATES`) | `RebirthBlockModels.saveAll()` |
| `build_textures.js` | текстуры блоков, предметов, GUI | `RebirthTextures.saveAll()` |
| `derive_textures.js` | производные текстуры: остов, маска свечения, значки | `await RebirthDerived.wreck(id)`, `glowmask`, `icon` |
| `build_gear.js` | защитное снаряжение (node, без Blockbench) | `node blockbench/tools/build_gear.js` |

## Порядок для GeckoLib-модели

1. Правка `SPECS.<id>` в `build_models.js` (кости, кубы, раскраска, анимации).
2. В Blockbench через MCP (`risky_eval`), активен GeckoLib-проект:
   ```js
   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/build_models.js','utf8'));
   try { RebirthModels.build('<id>'); 'ok' } catch (e) { String(e.message) }
   ```
   Сборка падает с понятной ошибкой, если:
   - есть **дыры текстуры** — дробный размер детали: размеры делать целыми, тонкое — через `inflate`;
   - есть **совпадающие грани** — при флаге `noCoplanarFaces`. Совпадающие грани мерцают в игре. Лечение — флаг `separateFaces: true` в спецификации (меньшая деталь пары раздувается на 0,1) или сдвиг деталей.
   Полный список ошибок: заменить в тексте скрипта `found.slice(0, 12).join('; ')` на `found.join('\n')` перед `eval`.
3. Снимок для проверки: `set_camera_angle` (перспектива, `max_size` 600). Не утверждать, что модель хороша, не посмотрев.
4. Сохранение — **отдельным вызовом**: `RebirthModels.save('<id>')` (пишет `.bbmodel` и текстуру).
5. Экспорт: `geckolib_export_model` → `.../geo/entity/<id>.geo.json`, `geckolib_export_animations` → `.../animations/entity/<id>.animation.json`, абсолютные пути, `overwrite: true`, `max_content_length: 0`.
6. Если у модели есть остов и менялась текстура — вывести заново (асинхронно, через IIFE):
   ```js
   eval(require('fs').readFileSync('E:/Projects/Project-Rebirth-mod/blockbench/tools/derive_textures.js','utf8'));
   (async () => { try { await RebirthDerived.wreck('<id>'); window.__wr = 'ok'; } catch (e) { window.__wr = String(e.message); } })();
   ```
   затем прочитать `window.__wr`.
7. `git status` — изменились только ожидаемые файлы. Новые кости, которые показываются по этапам сборки, — в `assets/projectrebirth/assembly_stages/<id>.json`.

## Блоки и текстуры

- Модели блоков: правка `MODELS`/`STATES` в `build_block_models.js`, затем `RebirthBlockModels.saveAll()`; проверить `git status`, что поменялись только нужные модели. Грань без `uv` получает развёртку по положению (1 px = 1/16 блока). Неполный блок — `noOcclusion()` в регистрации, иначе соседние блоки за ним пропадают.
- Текстуры: `RebirthTextures.saveAll()`. Текстуры блоков без прозрачных пикселей, кроме `CLEAR_BLOCKS`. Нужна ванильная «зернистость» (шум), а не плоская заливка.

## Проверка в игре

- Тестовый сервер и клиент: `./gradlew.bat runServer` (в `run/server.properties` на время включить `enable-rcon=true`, `rcon.password=test`, потом вернуть), `./gradlew.bat runClient2 -PquickPlayServer=localhost`.
- Снимки — в `docs/screenshots/` (для приёмки — `docs/screenshots/acceptance/`). Каждая новая модель **не принята**, пока владелец не посмотрел: записать её в STATUS, раздел «Не проверено и не принято».
