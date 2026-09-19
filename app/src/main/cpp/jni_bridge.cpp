// Licensed under the MIT License
//
// jni_bridge.cpp — JNI bridge connecting Java to the ZBinary2CArray engine.

#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <filesystem>

#include "ZBinary2CArray/zbtca.h"
#include "core/json.hpp"
#include "core/i18n_manager.hpp"
#include "core/settings.hpp"

namespace fs = std::filesystem;

// ─── Helper: jstring <-> std::string ───────────────────────────────

static std::string jstr2str(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    std::string s(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return s;
}

static jstring str2jstr(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// ─── Helper: split on comma ────────────────────────────────────────

static std::vector<std::string> splitArgs(const std::string& s) {
    std::vector<std::string> result;
    if (s.empty()) return result;
    size_t start = 0, end;
    while ((end = s.find('\n', start)) != std::string::npos) {
        result.push_back(s.substr(start, end - start));
        start = end + 1;
    }
    result.push_back(s.substr(start));
    return result;
}

// ─── Helper: build OutputCfg from JSON ─────────────────────────────

static ZBTCA_Types::OutputCfg cfgFromJson(const core::JsonValue& j) {
    ZBTCA_Types::OutputCfg cfg{};

    if (j.contains("type") && j.at("type").is_number()) {
        int t = j.at("type").as_int();
        switch (t) {
            case 1: cfg.ExportTypeFlags = ZBTCA_Types::TypeFlags::u16; break;
            case 2: cfg.ExportTypeFlags = ZBTCA_Types::TypeFlags::u32; break;
            case 3: cfg.ExportTypeFlags = ZBTCA_Types::TypeFlags::u64; break;
            default: cfg.ExportTypeFlags = ZBTCA_Types::TypeFlags::u8; break;
        }
    }
    if (j.contains("headerOnly"))  cfg.HeaderOnly  = j.at("headerOnly").as_bool();
    if (j.contains("incGuard"))    cfg.IncGuard    = j.at("incGuard").as_bool();
    if (j.contains("moreTidy"))    cfg.MoreTidy    = j.at("moreTidy").as_bool();
    if (j.contains("numsPerLine")) cfg.NumsPerLine  = j.at("numsPerLine").as_int();

    if (j.contains("storage")) {
        int s = j.at("storage").as_int();
        if (s == 1) cfg.StorageSpecifier = ZBTCA_Types::OutputCfg::StorageSpecifier_static;
        else if (s == 2) cfg.StorageSpecifier = ZBTCA_Types::OutputCfg::StorageSpecifier_inline;
    }
    if (j.contains("constSpec")) {
        int c = j.at("constSpec").as_int();
        if (c == 1) cfg.ConstSpecifier = ZBTCA_Types::OutputCfg::ConstSpecifier_const;
        else if (c == 2) cfg.ConstSpecifier = ZBTCA_Types::OutputCfg::ConstSpecifier_constexpr;
    }

    if (j.contains("annotation")) {
        auto& a = j.at("annotation");
        if (a.contains("tool"))   cfg.Annotation.Tool   = a.at("tool").as_bool();
        if (a.contains("runner")) cfg.Annotation.Runner = a.at("runner").as_bool();
        if (a.contains("toolName") && a.at("toolName").is_string())
            cfg.Annotation.ToolName = a.at("toolName").as_string();
        if (a.contains("runnerName") && a.at("runnerName").is_string())
            cfg.Annotation.RunnerName = a.at("runnerName").as_string();
        cfg.Annotation.File = true;
        cfg.Annotation.Size = true;
        cfg.Annotation.Time = true;
    }
    return cfg;
}

// ─── JNI: nativeConvert ────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeConvert(
        JNIEnv* env, jclass /*cls*/,
        jstring jInputPath,
        jstring jOutputDir,
        jstring jOutputStem,
        jstring jCfgJson) {

    std::string inputPath  = jstr2str(env, jInputPath);
    std::string outputDir  = jstr2str(env, jOutputDir);
    std::string outputStem = jstr2str(env, jOutputStem);
    std::string cfgJson    = jstr2str(env, jCfgJson);

    core::JsonValue result;
    result["ok"] = core::JsonValue(false);

    // Validate
    if (inputPath.empty()) {
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.no_input"));
        return str2jstr(env, result.dump());
    }
    if (outputDir.empty()) {
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.no_output_dir"));
        return str2jstr(env, result.dump());
    }
    if (outputStem.empty()) {
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.no_filename"));
        return str2jstr(env, result.dump());
    }

    // Validate filename
    bool validStem = true;
    for (unsigned char c : outputStem) {
        if (c < 32) { validStem = false; break; }
        for (char b : "<>:\"/\\|?*") {
            if (c == static_cast<unsigned char>(b)) { validStem = false; break; }
        }
        if (!validStem) break;
    }
    if (!outputStem.empty() && (outputStem.back() == '.' || outputStem.back() == ' '))
        validStem = false;
    if (!validStem) {
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.invalid_filename"));
        return str2jstr(env, result.dump());
    }

    // Parse config
    core::JsonValue cfgJ = core::JsonValue::parse(cfgJson);
    ZBTCA_Types::OutputCfg cfg = cfgFromJson(cfgJ);

    // Derive extension
    std::string ext;
    if (cfg.HeaderOnly) {
        ext = ".hpp";
    } else {
        ext = ".cpp";
    }

    // Build output path
    fs::path outPath = fs::path(outputDir) / fs::path(outputStem + ext);

    try {
        ZBTCA_Bin bin{fs::path(inputPath)};
        ZBTCA_Output output(bin);
        output.Config() = cfg;

        ZBTCA_Response resp = output(outPath);
        if (resp.status()) {
            result["ok"] = core::JsonValue(true);
            result["message"] = core::JsonValue(
                core::I18nManager::instance().tr("result.success",
                    { outPath.string() }));
            result["outputFile"] = core::JsonValue(outPath.string());
            result["outputDir"] = core::JsonValue(
                outPath.parent_path().string());
        } else {
            result["ok"] = core::JsonValue(false);
            result["message"] = core::JsonValue(
                resp.msg().empty()
                    ? core::I18nManager::instance().tr("errors.unexpected",
                        { "unknown" })
                    : resp.msg());
        }
    } catch (std::invalid_argument const& e) {
        result["ok"] = core::JsonValue(false);
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.read_failed",
                { e.what() }));
    } catch (std::runtime_error const& e) {
        result["ok"] = core::JsonValue(false);
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.write_failed",
                { e.what() }));
    } catch (std::exception const& e) {
        result["ok"] = core::JsonValue(false);
        result["message"] = core::JsonValue(
            core::I18nManager::instance().tr("errors.unexpected",
                { e.what() }));
    }

    return str2jstr(env, result.dump());
}

// ─── JNI: nativeLoadLanguage ───────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeLoadLanguage(
        JNIEnv* env, jclass /*cls*/,
        jstring jTag,
        jstring jJsonData) {

    std::string tag  = jstr2str(env, jTag);
    std::string data = jstr2str(env, jJsonData);

    core::JsonValue jv = core::JsonValue::parse(data);
    core::I18nManager::instance().load_language(tag, jv);
}

// ─── JNI: nativeSetLanguage ────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeSetLanguage(
        JNIEnv* env, jclass /*cls*/,
        jstring jTag) {
    std::string tag = jstr2str(env, jTag);
    core::I18nManager::instance().set_language(tag);
}

// ─── JNI: nativeGetCurrentLanguage ─────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeGetCurrentLanguage(
        JNIEnv* env, jclass /*cls*/) {
    return str2jstr(env, core::I18nManager::instance().current_language());
}

// ─── JNI: nativeTranslate ──────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeTranslate(
        JNIEnv* env, jclass /*cls*/,
        jstring jKey) {
    std::string key = jstr2str(env, jKey);
    return str2jstr(env, core::I18nManager::instance().tr(key));
}

// ─── JNI: nativeTranslateArgs ──────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeTranslateArgs(
        JNIEnv* env, jclass /*cls*/,
        jstring jKey,
        jstring jArgs) {
    std::string key  = jstr2str(env, jKey);
    std::string args = jstr2str(env, jArgs);
    auto argVec = splitArgs(args);
    return str2jstr(env, core::I18nManager::instance().tr(key, argVec));
}

// ─── JNI: nativeLoadSettings ───────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeLoadSettings(
        JNIEnv* env, jclass /*cls*/,
        jstring jPath) {
    std::string path = jstr2str(env, jPath);
    auto settings = core::load_settings(fs::path(path));

    core::JsonValue result;
    result["language"]  = core::JsonValue(settings.language);
    result["theme"]     = core::JsonValue(static_cast<int>(settings.theme));
    result["first_run"] = core::JsonValue(settings.first_run);

    return str2jstr(env, result.dump());
}

// ─── JNI: nativeSaveSettings ───────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_io_github_zct_1studio_zbinary2carray_1android_NativeBridge_nativeSaveSettings(
        JNIEnv* env, jclass /*cls*/,
        jstring jPath,
        jstring jSettingsJson) {

    std::string path     = jstr2str(env, jPath);
    std::string settings = jstr2str(env, jSettingsJson);

    core::JsonValue jv = core::JsonValue::parse(settings);
    core::AppSettings s{};

    if (jv.contains("language") && jv.at("language").is_string())
        s.language = jv.at("language").as_string();
    if (jv.contains("theme") && jv.at("theme").is_number()) {
        int t = jv.at("theme").as_int();
        if (t >= 0 && t <= 2)
            s.theme = static_cast<core::ThemePreference>(t);
    }
    if (jv.contains("first_run") && jv.at("first_run").is_bool())
        s.first_run = jv.at("first_run").as_bool();

    core::save_settings(s, fs::path(path));
    core::I18nManager::instance().set_language(s.language);
}