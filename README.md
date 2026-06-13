# River Discharge CLI Android App

Готовый Android-проект на Kotlin + Jetpack Compose для расчёта расхода реки по вертикалям.

## Что умеет приложение
- ввод ширины реки и списка вертикалей;
- расчёт расхода методом средних секций;
- показ частных расходов по каждой вертикали;
- сохранение замеров на устройстве;
- загрузка и удаление прошлых замеров;
- экспорт текущего расчёта в CSV.

## Структура
- `app/src/main/java/com/example/riverdischarge/MainActivity.kt` — вся основная логика и UI;
- `scripts/bootstrap-gradle.sh` — скачивает Gradle и запускает сборку без IDE;
- `scripts/build-debug.sh` — собирает debug APK;
- `scripts/install-debug.sh` — ставит debug APK на подключённое устройство через `adb`.

## Сборка APK на Arch Linux без IDE

### 1) Установи базовые пакеты
```bash
sudo pacman -S --needed jdk17-openjdk unzip curl
```

Проверь Java:
```bash
java -version
```

### 2) Скачай Android command-line tools
Скачай **Linux command line tools only** с официальной страницы Android Developers:
- https://developer.android.com/studio

Распакуй так, чтобы получилось:
```text
$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager
```

Пример:
```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
unzip ~/Downloads/commandlinetools-linux-*_latest.zip -d "$HOME/Android/Sdk/cmdline-tools"
mv "$HOME/Android/Sdk/cmdline-tools/cmdline-tools" "$HOME/Android/Sdk/cmdline-tools/latest"
```

### 3) Добавь переменные окружения
```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
```

Чтобы не прописывать это каждый раз, добавь те же строки в `~/.bashrc` или `~/.zshrc`.

### 4) Установи нужные Android SDK пакеты
```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 5) Собери debug APK
Из корня проекта:
```bash
./scripts/build-debug.sh
```

Готовый APK будет здесь:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 6) Установи APK на телефон
Включи на телефоне **USB debugging**, подключи его по USB и проверь, что `adb` видит устройство:
```bash
adb devices
```

Потом установи APK:
```bash
./scripts/install-debug.sh
```

## Пример исходных данных
- ширина реки: `10`
- вертикаль 1: `x=2`, `h=0.8`, `v=0.40`
- вертикаль 2: `x=5`, `h=1.2`, `v=0.55`
- вертикаль 3: `x=8`, `h=0.7`, `v=0.35`

Ожидаемый расход: около `3.39 м³/с`.

## Примечания
- приложение считает по **методу средних секций**;
- `x` должен быть строго внутри створа: `0 < x < B`;
- скорость вводится как **средняя по вертикали**;
- debug APK подходит для установки на своё устройство и тестирования.
