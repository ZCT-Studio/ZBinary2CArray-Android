# ZBinary2CArray-Android

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

一款将二进制文件转换为 C/C++ 数组源码的 Android 应用。采用 Java + C++20 (JNI) 构建，封装了核心 ZBinary2CArray 库，基于 Material Design 实现了现代 UI，并提供完整的国际化支持。

## 功能

- 将任意二进制文件转换为 C/C++ 头文件（`.hpp`）或源文件 + 头文件对（`.cpp` + `.h`）
- 支持 `unsigned char`（u8）、`unsigned short`（u16）、`unsigned int`（u32）和 `unsigned long long`（u64）元素类型
- 可配置存储说明符（`none`、`static`、`inline`）和 const 限定符（`none`、`const`、`constexpr`）
- 支持包含保护（Include guard）和整洁格式化
- 注释支持：工具名称、运行器名称、文件信息、文件大小与时间戳
- 可配置每行数字个数（0 = 自动）
- 跟随系统浅色/深色主题，不提供手动切换
- 多语言界面：English、简体中文、繁體中文
- 首次启动显示语言选择对话框（默认跟随系统语言）
- 通过 JNI 在后台线程执行原生转换，不阻塞 UI
- Android ABI 支持：arm64-v8a、armeabi-v7a、x86_64 和 x86

## 需求

- Android Studio Hedgehog（2023.1.1）及以上，或命令行 Gradle
- JDK 17（Android Studio 自带）
- Android SDK 36，需安装以下组件：
  - Android SDK Platform 36
  - Android SDK Build-Tools 36.0.0
  - NDK（Side by side）27.0.12077973
  - CMake 4.1.2
- 应用模块源码兼容级别为 Java 11+
- 运行设备或模拟器需 Android 11（API 30+）（最低 SDK 30）

## 项目结构

```
ZBinary2CArray-Android/
├── app/
│   ├── build.gradle.kts                       # App 模块 Gradle 配置（Kotlin DSL）
│   ├── proguard-rules.pro                     # R8 / ProGuard 规则
│   └── src/main/
│       ├── AndroidManifest.xml                # 清单、权限、主题
│       ├── assets/lang/                       # 翻译文件（JSON）
│       │   ├── en-US.json
│       │   ├── zh-CN.json
│       │   └── zh-TW.json
│       ├── cpp/
│       │   ├── CMakeLists.txt                 # NDK 构建配置，C++20
│       │   ├── jni_bridge.cpp                 # JNI 入口 ↔ Java API
│       │   ├── core/                          # Native 运行时辅助模块
│       │   │   ├── i18n_manager.hpp / .cpp    # 单例翻译管理器
│       │   │   ├── json.hpp                   # 极简 JSON 解析器
│       │   │   └── settings.hpp / .cpp        # AppSettings 模型 + JSON 持久化
│       │   └── ZBinary2CArray/                # 核心转换库（内置）
│       │       ├── zbtca.h                    # 公开 API 头文件（总览）
│       │       ├── types.hpp                  # OutputCfg、TypeFlag、AnnotationCfg
│       │       ├── bin.hpp                    # 二进制文件读取器
│       │       ├── output.hpp                 # C/C++ 数组输出写入器
│       │       ├── response.hpp               # 转换响应（状态 + 消息）
│       │       ├── details.hpp                # 内部实现细节
│       │       └── LICENSE.TXT                # 库许可证
│       ├── java/.../
│       │   ├── MainActivity.java              # 主 UI 控制器
│       │   ├── NativeBridge.java              # Java ↔ JNI 桥接
│       │   └── FilePickerDialog.java          # 基于 SAF 的文件选择器
│       └── res/                               # Android 资源
│           ├── layout/activity_main.xml
│           ├── values/colors.xml, strings.xml, themes.xml
│           ├── values-night/themes.xml
│           └── drawable/, mipmap-*/           # 启动器图标、Telegram 图标
├── build.gradle.kts                           # 根项目 Gradle 配置
├── settings.gradle.kts                        # 项目设置（Kotlin DSL）
├── gradle/libs.versions.toml                  # 版本目录
├── gradle.properties
├── gradlew / gradlew.bat                      # Gradle Wrapper
├── .github/workflows/build.yml                # CI/CD 流水线
├── LICENSE
└── README.md
```

## 构建

### Android Studio

1. 用 Android Studio 打开项目目录。
2. 让 Android Studio 自动同步 Gradle。
3. 在 SDK Manager 中确认已安装 Platform 36、Build-Tools 36.0.0、NDK 27.0.12077973 和 CMake 4.1.2。
4. 连接 Android 11+ 设备或启动模拟器。
5. 点击 ▶ 运行，或选择 **构建 → 构建 Bundle(s) / APK(s) → 构建 APK(s)**。

### 命令行

```shell
# Debug APK
./gradlew assembleDebug

# Release APK（需要签名 keystore + 环境变量）
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew assembleRelease
```

APK 输出路径为 `app/build/outputs/apk/<debug|release>/`。

## 使用方法

1. 启动应用。
2. 首次启动时，弹出语言选择对话框（默认跟随系统语言）。选择语言并确认。
3. 在提示时授予 **所有文件访问** 权限（读取任意二进制文件和写入任意输出目录均需要）。
4. 点击输入框旁的 **浏览**，通过系统文件选择器选取二进制文件。
5. 点击输出目录旁的 **浏览**，选择生成源码的保存位置。
6. 输入输出文件名（不含扩展名）。
7. 按需调整选项：
   - **元素类型**：u8、u16、u32 或 u64
   - **输出模式**：仅头文件（`.hpp`）或源文件 + 头文件（`.cpp` + `.h`）
   - **包含保护**：使用 `#ifndef`/`#define`/`#endif` 包裹输出
   - **整洁格式化**：对齐并格式化输出数组
   - **存储说明符**：`none`、`static` 或 `inline`
   - **Const 说明符**：`none`、`const` 或 `constexpr`
   - **每行数字个数**：0 为自动，或指定具体数量
   - **注释**：切换工具名称、运行器名称、文件信息、大小与时间戳
8. 点击 **转换**。成功时通过 Toast 提示输出路径，失败则显示错误信息。

可随时通过语言选择器切换界面语言。应用会自动跟随系统浅色/深色主题。

## 设置持久化

设置以 JSON 文件形式存储于：
- **Android**：`context.getFilesDir()/settings.json`（应用私有数据目录内）

文件仅保存所选语言标签；主题始终使用系统值，不做持久化。

## 添加新语言

1. 在 `app/src/main/assets/lang/` 下新建 JSON 文件（例如 `ja-JP.json`）。
2. 复制 `en-US.json` 的结构并翻译所有值。
3. 在 `MainActivity.java`（语言列表定义处）注册新的语言标签。
4. 重新构建 APK —— 原生 `i18n_manager` 会在运行时自动加载 locale 资源。

## CI/CD

GitHub Actions 工作流（`.github/workflows/build.yml`）拆分为两个 Job，支持三种触发方式：

- **推送 / Pull Request** 到 `main` 或 `master` → 执行 `build` Job
- **推送以 `v` 开头的标签**（例如 `v1.0.0`）→ 同时执行 `build` 和 `release`
- **workflow_dispatch** → 在 Actions 页面手动触发

### `build` Job — 每次触发均执行

| 步骤 | 说明 |
|---|---|
| Checkout | `actions/checkout@v4` |
| 配置 JDK 17 | `actions/setup-java@v4`（Temurin 发行版） |
| 配置 Android SDK | `android-actions/setup-android@v3` — Platform 36、Build-Tools 36.0.0、NDK 27.0.12077973、CMake 4.1.2 |
| 配置 Gradle | `gradle/actions/setup-gradle@v4` — 自动 Gradle 缓存 |
| 还原发布签名文件（可选） | 若设置了 `ANDROID_KEYSTORE_BASE64` secret，将其 base64 解码为 `$RUNNER_TEMP/release.jks` 并 `chmod 600` |
| 构建 Release APK | `./gradlew assembleRelease`，keystore 路径 / 密码 / alias 通过环境变量注入 |
| 校验 APK | 若未产出任何 APK 则构建失败 |
| 上传构建产物 | Release APK → `ZBinary2CArray-Android-release`（产物为空则构建失败） |
| 上传构建日志 | 仅构建失败时执行 |
| 删除临时 keystore | `always()` 条件，清理 `$RUNNER_TEMP/release.jks` |

### `release` Job — 仅 `v*` 标签推送时执行，依赖 `build`

1. 下载 `ZBinary2CArray-Android-release` 构建产物。
2. 将各 APK 重命名为 `ZBinary2CArray-Android_<tag>_<abi>.apk`（ABI 从原始文件名自动识别）。
3. 通过 `softprops/action-gh-release@v2` 创建 GitHub Release。带 `-` 后缀的标签（例如 `v1.0.0-rc1`）标记为 **预发布**。

### 需要配置的 GitHub 仓库 Secrets

| Secret | 是否必填 | 说明 |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | **标签发布时必填** | `.jks` / `.keystore` 文件的 base64 编码内容，作为多行 secret 存储 |
| `ANDROID_KEYSTORE_PASSWORD` | 是 | Keystore 密码 |
| `ANDROID_KEY_ALIAS` | 是 | Keystore 内的 key alias |
| `ANDROID_KEY_PASSWORD` | 是 | Key 密码 |

> 若未配置 `ANDROID_KEYSTORE_BASE64`，PR 和普通推送仍会触发构建，但 **无法产出带签名的 Release APK** —— `:validateSigningRelease` 会直接失败（release 签名无回退到 debug key 的逻辑，符合严格签名策略）。

## 相关项目与社区

- [ZBinary2CArray](https://github.com/ZCT-Studio/ZBinary2CArray) — 本应用所基于的核心 C/C++ 二进制转数组库。
- [Telegram: @ZCT_Studio](https://t.me/ZCT_Studio) — 获取项目更新与公告。

## 许可证

本项目采用 [MIT License](LICENSE)。核心库许可证见 `app/src/main/cpp/ZBinary2CArray/LICENSE.TXT`。
