package io.github.zct_studio.zbinary2carray_android;

/**
 * JNI bridge to the native ZBinary2CArray conversion engine.
 * All heavy lifting (file I/O, binary-to-C-array conversion) happens in C++.
 */
public class NativeBridge {
    static { System.loadLibrary("zbinary2carray"); }

    // ─── Conversion ────────────────────────────────────────────────
    /** @return JSON string: { "ok": bool, "message": str, "outputFile": str?, "outputDir": str? } */
    public static native String nativeConvert(String inputPath, String outputDir,
                                              String outputStem, String cfgJson);

    // ─── I18n ──────────────────────────────────────────────────────
    public static native void   nativeLoadLanguage(String tag, String jsonData);
    public static native void   nativeSetLanguage(String tag);
    public static native String nativeGetCurrentLanguage();
    public static native String nativeTranslate(String key);
    public static native String nativeTranslateArgs(String key, String args);

    // ─── Settings ──────────────────────────────────────────────────
    /** @return JSON: { "language": str, "theme": int, "first_run": bool } */
    public static native String nativeLoadSettings(String path);
    public static native void   nativeSaveSettings(String path, String settingsJson);
}