# Project: Rebirth: статус разработки

Текущий этап: **0. Подготовка** (почти завершён: осталось подключить GeckoLib).

## Окружение

| Что | Версия / путь |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Плагин сборки | ModDevGradle 2.0.148 (из официального MDK) |
| Gradle | 9.2.1 (wrapper из MDK) |
| Parchment (имена параметров) | 2024.11.17 |
| Java для сборки и игры | Microsoft JDK 21.0.9 (`C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`) |
| Git | 2.56 |

Примечание: Gradle сам не находит Microsoft JDK 21, поэтому её путь прописан в
пользовательском файле `%USERPROFILE%\.gradle\gradle.properties`
(`org.gradle.java.installations.paths`). В репозиторий этот путь не попадает.
На другой машине Gradle либо найдёт свою JDK 21, либо скачает её автоматически (плагин foojay в `settings.gradle`).

Источник шаблона: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle (коммит `e93d7f0`).

## Этап 0: что сделано

- [x] Проверена Java (по умолчанию в системе Java 17, но установлена и используется JDK 21).
- [x] Проект создан из официального MDK, пример `examplemod` убран, mod id `projectrebirth`, пакет `com.projectrebirth`, версия `0.1.0`.
- [x] `.\gradlew build` проходит, собирается `build/libs/projectrebirth-0.1.0.jar`.
- [x] `.\gradlew runClient` запускается: мод найден загрузчиком, общая и клиентская инициализация отработали,
      ресурсы загружены, окно игры открыто, ошибок (`ERROR`) в логе нет.
- [x] Git инициализирован, `.gitignore` из MDK + дополнения.
- [ ] GeckoLib: совместимая версия найдена (`geckolib-neoforge-1.21.1` 4.9.3), но ещё не подключена.

## Что НЕ проверено

- Вход в мир на клиенте: проверялось только по логу до главного меню, экран глазами не смотрелся.
- `.\gradlew runServer` (выделенный сервер) не запускался.

## Структура кода

- `com.projectrebirth.ProjectRebirth`: общая точка входа (клиент и сервер).
- `com.projectrebirth.client.ProjectRebirthClient`: клиентская точка входа, на сервере не загружается.
- Остальные подпакеты (`core`, `rebirth`, `bodies`, `powerarmor`, `technology`, `factions`, `config`)
  будут созданы, когда в них появится код.

## Модели и заглушки

Пока нет ни одной модели, текстуры или анимации.
