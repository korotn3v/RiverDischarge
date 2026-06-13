# Сборка приложения RiverDischarge

Инструкция для этой машины (Debian 13, без Android Studio). Окружение уже настроено.

## Что нужно (уже установлено)

- **JDK 17 (портативный Temurin):** `/home/anton/jdks/jdk-17.0.19+10`
  (в Debian 13 нет пакета `openjdk-17`, поэтому используется портативный из `~/jdks`)
- **Android SDK:** `~/Android/Sdk` — пакеты `cmdline-tools/latest`, `platform-tools`,
  `platforms;android-34`, `build-tools;34.0.0` (лицензии приняты)
- **Прокси для Gradle:** прописан в `~/.gradle/gradle.properties`
  (этот файл локальный для машины, в репозиторий его класть НЕ нужно)

## Сборка debug-APK

```bash
cd "/home/anton/vibe code/RiverDischarge"
export JAVA_HOME="/home/anton/jdks/jdk-17.0.19+10"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
./scripts/build-debug.sh
```

Готовый файл:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Установка на телефон

Подключить телефон по USB, включить «Отладку по USB», затем:

```bash
./scripts/install-debug.sh
```

## Если что-то не работает

- **`JAVA_HOME is not set ...`** — не выполнены три `export` выше. Их нужно делать
  каждый раз в новом терминале (или добавить в `~/.bashrc`).
- **Нет `gradlew` / ошибка про wrapper** — скачать обёртку Gradle:
  ```bash
  ./scripts/bootstrap-gradle.sh assembleDebug
  ```
- **Ошибки скачивания зависимостей (TLS / read timeout)** — значит не подхватился прокси
  из `~/.gradle/gradle.properties`. Проверить, что файл на месте и содержит
  `systemProp.http(s).proxyHost/Port`.
- **Долгая первая сборка** — это нормально: Gradle качает зависимости. Повторные сборки
  быстрее (кэш + UP-TO-DATE).

## Чтобы не вводить export каждый раз

Можно один раз добавить в конец `~/.bashrc`:

```bash
export JAVA_HOME="/home/anton/jdks/jdk-17.0.19+10"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

Тогда сборка сведётся к:

```bash
cd "/home/anton/vibe code/RiverDischarge" && ./scripts/build-debug.sh
```
