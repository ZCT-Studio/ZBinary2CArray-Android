# ZBinary2CArray-Android

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

An Android application that converts binary files into C/C++ source arrays. Built with Java + C++20 via JNI, wrapping the core ZBinary2CArray library, featuring a modern Material Design UI with full internationalization support.

## Features

- Convert any binary file to a C/C++ header (`.hpp`) or source + header pair (`.cpp` + `.h`)
- Supports `unsigned char` (u8), `unsigned short` (u16), `unsigned int` (u32), and `unsigned long long` (u64) element types
- Configurable storage specifier (`none`, `static`, `inline`) and const qualifier (`none`, `const`, `constexpr`)
- Include guard and tidy formatting options
- Annotation support: tool name, runner name, file info, size, and timestamp
- Configurable numbers per line (0 = auto)
- System theme following (light/dark) with no manual toggle
- Multilingual UI: English, Simplified Chinese, Traditional Chinese
- First-run language selection dialog (defaults to system language)
- Non-blocking native conversion via JNI on a background thread
- Android ABI support: arm64-v8a, armeabi-v7a, x86_64, and x86

## Requirements

- Android Studio Hedgehog (2023.1.1) or later, or Gradle command line
- JDK 17 (bundled with Android Studio)
- Android SDK 36 with the following components:
  - Android SDK Platform 36
  - Android SDK Build-Tools 36.0.0
  - NDK (Side by side) 27.0.12077973
  - CMake 4.1.2
- Java 11+ for the app module source compatibility
- Android 11 (API 30+) device or emulator for runtime (minimum SDK 30)

## Project Structure

```
ZBinary2CArray-Android/
├── app/
│   ├── build.gradle.kts                       # App module Gradle config (Kotlin DSL)
│   ├── proguard-rules.pro                     # R8 / ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml                # Manifest, permissions, theme
│       ├── assets/lang/                       # Translation files (JSON)
│       │   ├── en-US.json
│       │   ├── zh-CN.json
│       │   └── zh-TW.json
│       ├── cpp/
│       │   ├── CMakeLists.txt                 # NDK build config, C++20
│       │   ├── jni_bridge.cpp                 # JNI entry points ↔ Java API
│       │   ├── core/                          # Native runtime helpers
│       │   │   ├── i18n_manager.hpp / .cpp    # Singleton translation manager
│       │   │   ├── json.hpp                   # Minimal JSON parser
│       │   │   └── settings.hpp / .cpp        # AppSettings model + JSON persistence
│       │   └── ZBinary2CArray/                # Core conversion library (bundled)
│       │       ├── zbtca.h                    # Public API header (umbrella)
│       │       ├── types.hpp                  # OutputCfg, TypeFlag, AnnotationCfg
│       │       ├── bin.hpp                    # Binary file reader
│       │       ├── output.hpp                 # C/C++ array output writer
│       │       ├── response.hpp               # Conversion response (status + message)
│       │       ├── details.hpp                # Internal implementation details
│       │       └── LICENSE.TXT                # Library license
│       ├── java/.../
│       │   ├── MainActivity.java              # Main UI controller
│       │   ├── NativeBridge.java              # Java ↔ JNI bridge
│       │   └── FilePickerDialog.java          # SAF-based file picker
│       └── res/                               # Android resources
│           ├── layout/activity_main.xml
│           ├── values/colors.xml, strings.xml, themes.xml
│           ├── values-night/themes.xml
│           └── drawable/, mipmap-*/           # Launcher icons, Telegram icon
├── build.gradle.kts                           # Root project Gradle config
├── settings.gradle.kts                        # Project settings (Kotlin DSL)
├── gradle/libs.versions.toml                  # Version catalog
├── gradle.properties
├── gradlew / gradlew.bat                      # Gradle Wrapper
├── .github/workflows/build.yml                # CI/CD pipeline
├── LICENSE
└── README.md
```

## Building

### Android Studio

1. Open the project folder in Android Studio.
2. Let Android Studio sync Gradle automatically.
3. Make sure the SDK Manager has Platform 36, Build-Tools 36.0.0, NDK 27.0.12077973, and CMake 4.1.2 installed.
4. Connect an Android 11+ device or start an emulator.
5. Click **Run** ▶ or use **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Command Line

```shell
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing keystore + env vars)
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew assembleRelease
```

APKs are written to `app/build/outputs/apk/<debug|release>/`.

## Usage

1. Launch the app.
2. On first run, a language selection dialog appears (defaults to your system language). Choose a language and confirm.
3. Grant **All files access** when prompted (required for reading arbitrary binaries and writing to any output folder).
4. Tap **Browse** next to the input field to pick a binary file via the system file picker.
5. Tap **Browse** next to the output directory to choose where the generated source will be saved.
6. Enter an output file name (without extension).
7. Configure options as needed:
   - **Element type**: u8, u16, u32, or u64
   - **Output mode**: Header only (`.hpp`) or Source + header (`.cpp` + `.h`)
   - **Include guard**: Wrap output in `#ifndef`/`#define`/`#endif`
   - **Tidy formatting**: Align and format the output array
   - **Storage specifier**: `none`, `static`, or `inline`
   - **Const specifier**: `none`, `const`, or `constexpr`
   - **Numbers per line**: 0 for auto, or a specific count
   - **Annotations**: Toggle tool name, runner name, file info, size, and timestamp
8. Tap **Convert**. A toast shows success with the output path or an error message.

Use the language selector to switch languages at any time. The app follows your system's light/dark theme automatically.

## Settings Persistence

Settings are stored in a JSON file at:
- **Android**: `context.getFilesDir()/settings.json` (inside the app's private data directory)

The settings file stores the selected language tag. Theme is always the system value and is not persisted.

## Adding a New Language

1. Create a new JSON file in `app/src/main/assets/lang/` (e.g., `ja-JP.json`).
2. Copy the structure from `en-US.json` and translate all values.
3. Register the new locale tag in `MainActivity.java` (where the language list is defined).
4. Rebuild the APK — the native `i18n_manager` auto-loads locale assets at runtime.

## CI/CD

The GitHub Actions workflow (`.github/workflows/build.yml`) is split into two jobs and supports three triggers:

- **Push / Pull Request** to `main` or `master` → runs the `build` job
- **Push a tag** starting with `v` (e.g. `v1.0.0`) → runs both `build` and `release`
- **workflow_dispatch** → manual run from the Actions tab

### `build` job — runs on every trigger

| Step | What it does |
|---|---|
| Checkout | `actions/checkout@v4` |
| Set up JDK 17 | `actions/setup-java@v4` (Temurin) |
| Setup Android SDK | `android-actions/setup-android@v3` — Platform 36, Build-Tools 36.0.0, NDK 27.0.12077973, CMake 4.1.2 |
| Setup Gradle | `gradle/actions/setup-gradle@v4` — auto Gradle cache |
| Restore release keystore (optional) | If `ANDROID_KEYSTORE_BASE64` secret is set, decodes it into `$RUNNER_TEMP/release.jks` with `chmod 600` |
| Build Release APK | `./gradlew assembleRelease`, keystore path/password/alias pulled from env |
| Verify APKs | Fails the build if no APK was produced |
| Upload artifact | Release APKs → `ZBinary2CArray-Android-release` (fails if empty) |
| Upload build logs | On failure only |
| Remove temporary keystore | `always()`, wipes `$RUNNER_TEMP/release.jks` |

### `release` job — only on `v*` tag push, needs `build`

1. Downloads the `ZBinary2CArray-Android-release` artifact.
2. Renames each APK to `ZBinary2CArray-Android_<tag>_<abi>.apk` (ABI detected from the original filename).
3. Creates a GitHub Release via `softprops/action-gh-release@v2`. Tags containing `-` (e.g. `v1.0.0-rc1`) are marked **prerelease**.

### Required GitHub Repository Secrets

| Secret | Required | Description |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | For **tag releases** only | Base64-encoded contents of your `.jks` / `.keystore` file. Stored as a single multiline secret. |
| `ANDROID_KEYSTORE_PASSWORD` | Yes | Keystore password |
| `ANDROID_KEY_ALIAS` | Yes | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Yes | Key password |

> If `ANDROID_KEYSTORE_BASE64` is not configured, PR and push builds still run but **cannot produce a signed Release APK** — the build will fail at `:validateSigningRelease`, which is intentional (release signing is strict, no fallback to debug keys).

## Related Projects & Community

- [ZBinary2CArray](https://github.com/ZCT-Studio/ZBinary2CArray) — The core C/C++ binary-to-array conversion library that this application is built upon.
- [@ZCT_Studio on Telegram](https://t.me/ZCT_Studio) — Follow for project updates and announcements.

## License

Licensed under the [MIT License](LICENSE). See `app/src/main/cpp/ZBinary2CArray/LICENSE.TXT` for the core library license.
