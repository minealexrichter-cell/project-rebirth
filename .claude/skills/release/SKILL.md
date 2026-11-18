---
name: release
description: Выпуск версии мода Project: Rebirth по порядку из CLAUDE.md — автотесты, версия, CHANGELOG, jar, тег, копия в build/releases, версия разработки, архив STATUS. Используй, когда владелец говорит «выпускай» или план дошёл до блока выпуска.
---

# Выпуск версии Project: Rebirth

Владелец работает на Windows; команды ниже — для Bash-инструмента (Git Bash) из корня проекта. `gradle clean` не запускать никогда: он удаляет `build/`, где лежат выпущенные jar.

## 1. Проверки

1. Дерево чистое, кроме `.mcp.json` (его не коммитить никогда): `git status --short`.
2. Тестовый сервер не запущен (автотесты не идут, пока он держит `run/`).
3. Все группы автотестов, если владелец не сказал иначе:
   ```bash
   ./gradlew.bat runGameTestServer -PgameTests=projectrebirth,projectrebirth_atmosphere,projectrebirth_shelter,projectrebirth_codex,projectrebirth_power,projectrebirth_armor,projectrebirth_scouting,projectrebirth_garrison > "$TEMP/gt.log" 2>&1
   grep -E "required tests|LogTestReporter" "$TEMP/gt.log" | tail -8
   ```
   Нужна строка `All N required tests passed`. Список групп — в `src/main/java/com/projectrebirth/gametest/PlanTests.java`; если появилась новая группа, добавь её. Если владелец велел тесты не запускать — записать в STATUS, что именно не запускалось.

## 2. Версия и документы

- `gradle.properties`: `mod_version=<версия>` (без `-dev`).
- `CLAUDE.md`: строка «Версия мода».
- `README.md`: строка версии вверху, список «по версиям», имя jar в «Установке», раздел «Обновление с более ранних версий», если есть, о чём предупредить.
- `CHANGELOG.md`: раздел `## <версия> («Название»)` сверху, по-русски, для игроков; изменения поведения — с ⚠.
- `docs/STATUS.md`: строка «Последний выпуск» и проверки перед ним (что запускалось, на каком коммите, что не проверено).

## 3. Сборка и проверка jar

```bash
./gradlew.bat build -x test
unzip -p build/libs/projectrebirth-<версия>.jar META-INF/neoforge.mods.toml | grep "^version"
unzip -l build/libs/projectrebirth-<версия>.jar | tail -1        # размер и число файлов
mkdir -p build/releases && cp build/libs/projectrebirth-<версия>.jar build/releases/
```

## 4. Коммит и тег

```bash
git add -A          # .mcp.json уже в .gitignore; исключение ':!.mcp.json' для игнорируемого файла роняет git add
git commit -m "Version <версия> release: <коротко>

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git show HEAD:gradle.properties | grep "^mod_version"   # тег — только если тут <версия> без -dev
git tag v<версия>
```
Тег ставить **отдельной командой после проверки**, что коммит выпуска создан (в 1.7.3 `git add` упал, коммита не было, и тег встал на предыдущий коммит с `-dev`).
Если репозиторий на GitHub: `git push && git push --tags`, затем `gh run watch` — дождаться зелёной проверки.

## 5. После выпуска

1. Версию разработки сразу поднять: `mod_version=<следующая>-dev`, иначе следующие сборки перезапишут выпущенный jar в `build/libs`.
2. Разделы выпущенной версии перенести из `docs/STATUS.md` в `docs/history/STATUS_<версия>.md`; актуальные «не проверено» и «не принято» — в раздел STATUS «Не проверено и не принято». STATUS — не длиннее ~300 строк.
3. Отчёт владельцу по-русски: что вошло, что реально проверено (автотест / в игре / готовый jar / владелец), что нет.
