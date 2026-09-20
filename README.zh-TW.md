# ZBinary2CArray-Android

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

一款將二進位檔轉換為 C/C++ 陣列原始碼的 Android 應用程式。以 Java + C++20 (JNI) 建置，封裝了核心 ZBinary2CArray 函式庫，基於 Material Design 提供現代化 UI，並具備完整的國際化支援。

## 功能

- 將任意二進位檔轉換為 C/C++ 標頭檔（`.hpp`）或來源檔 + 標頭檔組合（`.cpp` + `.h`）
- 支援 `unsigned char`（u8）、`unsigned short`（u16）、`unsigned int`（u32）與 `unsigned long long`（u64）元素型別
- 可設定儲存指定詞（`none`、`static`、`inline`）與 const 限定詞（`none`、`const`、`constexpr`）
- 支援包含保護（Include guard）與整齊格式化
- 註解支援：工具名稱、執行器名稱、檔案資訊、大小與時間戳記
- 可設定每行數字個數（0 = 自動）
- 跟隨系統淺色/深色主題，不提供手動切換
- 多語言介面：English、简体中文、繁體中文
- 首次啟動顯示語言選擇對話框（預設跟隨系統語言）
- 透過 JNI 在背景執行緒執行原生轉換，不阻塞 UI
- Android ABI 支援：arm64-v8a、armeabi-v7a、x86_64 與 x86

## 需求

- Android Studio Hedgehog（2023.1.1）及以上版本，或命令列 Gradle
- JDK 17（Android Studio 內建）
- Android SDK 36，需安裝下列元件：
  - Android SDK Platform 36
  - Android SDK Build-Tools 36.0.0
  - NDK（Side by side）27.0.12077973
  - CMake 4.1.2
- App 模組來源相容等級為 Java 11+
- 執行裝置或模擬器需 Android 11（API 30+）（最低 SDK 30）

## 專案結構

```
ZBinary2CArray-Android/
├── app/
│   ├── build.gradle.kts                       # App 模組 Gradle 設定（Kotlin DSL）
│   ├── proguard-rules.pro                     # R8 / ProGuard 規則
│   └── src/main/
│       ├── AndroidManifest.xml                # 清單、權限、主題
│       ├── assets/lang/                       # 翻譯檔（JSON）
│       │   ├── en-US.json
│       │   ├── zh-CN.json
│       │   └── zh-TW.json
│       ├── cpp/
│       │   ├── CMakeLists.txt                 # NDK 建置設定，C++20
│       │   ├── jni_bridge.cpp                 # JNI 進入點 ↔ Java API
│       │   ├── core/                          # Native 執行期輔助模組
│       │   │   ├── i18n_manager.hpp / .cpp    # 單例翻譯管理器
│       │   │   ├── json.hpp                   # 極簡 JSON 剖析器
│       │   │   └── settings.hpp / .cpp        # AppSettings 模型 + JSON 持久化
│       │   └── ZBinary2CArray/                # 核心轉換函式庫（內建）
│       │       ├── zbtca.h                    # 公開 API 標頭檔（總覽）
│       │       ├── types.hpp                  # OutputCfg、TypeFlag、AnnotationCfg
│       │       ├── bin.hpp                    # 二進位檔讀取器
│       │       ├── output.hpp                 # C/C++ 陣列輸出寫入器
│       │       ├── response.hpp               # 轉換回應（狀態 + 訊息）
│       │       ├── details.hpp                # 內部實作細節
│       │       └── LICENSE.TXT                # 函式庫授權
│       ├── java/.../
│       │   ├── MainActivity.java              # 主 UI 控制器
│       │   ├── NativeBridge.java              # Java ↔ JNI 橋接
│       │   └── FilePickerDialog.java          # 基於 SAF 的檔案選擇器
│       └── res/                               # Android 資源
│           ├── layout/activity_main.xml
│           ├── values/colors.xml, strings.xml, themes.xml
│           ├── values-night/themes.xml
│           └── drawable/, mipmap-*/           # 啟動器圖示、Telegram 圖示
├── build.gradle.kts                           # 根專案 Gradle 設定
├── settings.gradle.kts                        # 專案設定（Kotlin DSL）
├── gradle/libs.versions.toml                  # 版本目錄
├── gradle.properties
├── gradlew / gradlew.bat                      # Gradle Wrapper
├── .github/workflows/build.yml                # CI/CD 管線
├── LICENSE
└── README.md
```

## 建置

### Android Studio

1. 用 Android Studio 開啟專案資料夾。
2. 讓 Android Studio 自動同步 Gradle。
3. 在 SDK Manager 確認已安裝 Platform 36、Build-Tools 36.0.0、NDK 27.0.12077973 與 CMake 4.1.2。
4. 連線 Android 11+ 裝置或啟動模擬器。
5. 點選 ▶ 執行，或選擇 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。

### 命令列

```shell
# Debug APK
./gradlew assembleDebug

# Release APK（需要簽章 keystore + 環境變數）
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew assembleRelease
```

APK 輸出路徑為 `app/build/outputs/apk/<debug|release>/`。

## 使用方式

1. 啟動應用程式。
2. 首次啟動時，彈出語言選擇對話框（預設跟隨系統語言）。選擇語言並確認。
3. 在提示時授予 **所有檔案存取** 權限（讀取任意二進位檔與寫入任意輸出資料夾皆需要）。
4. 點選輸入欄旁的 **瀏覽**，透過系統檔案選擇器選取二進位檔。
5. 點選輸出資料夾旁的 **瀏覽**，選擇產生原始碼的儲存位置。
6. 輸入輸出檔案名稱（不含副檔名）。
7. 視需要調整選項：
   - **元素型別**：u8、u16、u32 或 u64
   - **輸出模式**：僅標頭檔（`.hpp`）或來源檔 + 標頭檔（`.cpp` + `.h`）
   - **包含保護**：使用 `#ifndef`/`#define`/`#endif` 包裹輸出
   - **整齊格式化**：對齊並格式化輸出陣列
   - **儲存指定詞**：`none`、`static` 或 `inline`
   - **Const 指定詞**：`none`、`const` 或 `constexpr`
   - **每行數字個數**：0 為自動，或指定具體數量
   - **註解**：切換工具名稱、執行器名稱、檔案資訊、大小與時間戳記
8. 點選 **轉換**。成功時透過 Toast 顯示輸出路徑，失敗則顯示錯誤訊息。

可隨時透過語言選擇器切換介面語言。應用程式會自動跟隨系統淺色/深色主題。

## 設定持久化

設定以 JSON 檔形式儲存於：
- **Android**：`context.getFilesDir()/settings.json`（應用程式私有資料目錄內）

檔案僅保存所選語言標籤；主題一律使用系統值，不做持久化。

## 新增語言

1. 在 `app/src/main/assets/lang/` 下新增 JSON 檔（例如 `ja-JP.json`）。
2. 複製 `en-US.json` 的結構並翻譯所有值。
3. 在 `MainActivity.java`（語言清單定義處）註冊新的語言標籤。
4. 重新建置 APK —— 原生 `i18n_manager` 會在執行期自動載入 locale 資源。

## CI/CD

GitHub Actions 工作流（`.github/workflows/build.yml`）拆分為兩個 Job，支援三種觸發方式：

- **推送 / Pull Request** 到 `main` 或 `master` → 執行 `build` Job
- **推送以 `v` 開頭的標籤**（例如 `v1.0.0`）→ 同時執行 `build` 與 `release`
- **workflow_dispatch** → 在 Actions 頁面手動觸發

### `build` Job — 每次觸發均執行

| 步驟 | 說明 |
|---|---|
| Checkout | `actions/checkout@v4` |
| 設定 JDK 17 | `actions/setup-java@v4`（Temurin 發行版） |
| 設定 Android SDK | `android-actions/setup-android@v3` — Platform 36、Build-Tools 36.0.0、NDK 27.0.12077973、CMake 4.1.2 |
| 設定 Gradle | `gradle/actions/setup-gradle@v4` — 自動 Gradle 快取 |
| 還原發布簽章檔（可選） | 若設定了 `ANDROID_KEYSTORE_BASE64` secret，將其 base64 解碼為 `$RUNNER_TEMP/release.jks` 並 `chmod 600` |
| 建置 Release APK | `./gradlew assembleRelease`，keystore 路徑 / 密碼 / alias 透過環境變數注入 |
| 驗證 APK | 若未產出任何 APK 則建置失敗 |
| 上傳建置產物 | Release APK → `ZBinary2CArray-Android-release`（產物為空則建置失敗） |
| 上傳建置日誌 | 僅建置失敗時執行 |
| 刪除暫時 keystore | `always()` 條件，清理 `$RUNNER_TEMP/release.jks` |

### `release` Job — 僅 `v*` 標籤推送時執行，依賴 `build`

1. 下載 `ZBinary2CArray-Android-release` 建置產物。
2. 將各 APK 重新命名為 `ZBinary2CArray-Android_<tag>_<abi>.apk`（ABI 從原始檔名自動識別）。
3. 透過 `softprops/action-gh-release@v2` 建立 GitHub Release。帶 `-` 後綴的標籤（例如 `v1.0.0-rc1`）標記為 **預發行**。

### 需設定的 GitHub 倉庫 Secrets

| Secret | 是否必填 | 說明 |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | **標籤發布時必填** | `.jks` / `.keystore` 檔的 base64 編碼內容，作為多行 secret 儲存 |
| `ANDROID_KEYSTORE_PASSWORD` | 是 | Keystore 密碼 |
| `ANDROID_KEY_ALIAS` | 是 | Keystore 內的 key alias |
| `ANDROID_KEY_PASSWORD` | 是 | Key 密碼 |

> 若未設定 `ANDROID_KEYSTORE_BASE64`，PR 與一般推送仍會觸發建置，但 **無法產出帶簽章的 Release APK** —— `:validateSigningRelease` 會直接失敗（release 簽章無回退到 debug key 的邏輯，符合嚴格簽章策略）。

## 相關專案與社群

- [ZBinary2CArray](https://github.com/ZCT-Studio/ZBinary2CArray) — 本應用程式所基於的核心 C/C++ 二進位轉陣列函式庫。
- [Telegram: @ZCT_Studio](https://t.me/ZCT_Studio) — 追蹤專案更新與公告。

## 授權

本專案採用 [MIT License](LICENSE)。核心函式庫授權見 `app/src/main/cpp/ZBinary2CArray/LICENSE.TXT`。
