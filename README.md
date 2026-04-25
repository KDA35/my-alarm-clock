# MyAlarm

Будильник для Android, аналог Samsung Clock с регулируемым snooze 1–60 минут. Single-activity Compose-приложение, рассчитанное на Pixel 10 XL под GrapheneOS.

## Status

Этап 1/7 — скелет проекта. На этом этапе функционала будильника ещё нет: цель — проверить цепочку «код → GitHub → APK → установка на GrapheneOS».

## Build

Сборка идёт автоматически в GitHub Actions: [Actions → Build APK](https://github.com/KDA35/my-alarm-clock/actions/workflows/build.yml).

Чтобы получить APK:

1. Открыть последний зелёный прогон в Actions.
2. В разделе **Artifacts** внизу страницы скачать `myalarm-debug-apk`.
3. Распаковать ZIP — внутри `app-debug.apk`.

Локальная сборка (если установлен Android SDK):

```bash
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`.

## Установка на GrapheneOS

1. **Settings → Apps → Sideload** — разрешить установку APK для браузера, через который будете скачивать (Vanadium / другой).
2. Скачать APK с GitHub Actions (см. раздел Build выше) либо передать файл по USB.
3. Открыть APK в файловом менеджере → подтвердить установку.
4. После установки в списке приложений появится **MyAlarm** с иконкой часов.

Запуск показывает один экран: TopAppBar «MyAlarm» и текст «Этап 1: скелет работает» по центру. Тёмная/светлая тема следует за системной.

## Stack

- Kotlin 2.0.21, JVM target 17
- Android Gradle Plugin 8.7.3, Gradle 8.10.2
- compileSdk / targetSdk = 35, minSdk = 33
- Jetpack Compose (BOM 2024.12.01) + Material 3
- Navigation Compose (заготовка под этап 2)

## Roadmap

1. **Этап 1** — скелет проекта, CI, установка на устройство ← *текущий*
2. Этап 2 — модель данных, Room, Hilt, экран списка будильников
3. Этап 3 — создание/редактирование будильника
4. Этап 4 — AlarmManager, Receiver, срабатывание
5. Этап 5 — настраиваемый snooze 1–60 минут
6. Этап 6 — мелодии, вибрация, постепенное усиление громкости
7. Этап 7 — релизный keystore, подписанная сборка
