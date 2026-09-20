package io.github.zct_studio.zbinary2carray_android;

public class NativeBridge {
    static { System.loadLibrary("zbinary2carray"); }

    public static native String nativeConvert(String inputPath, String outputDir,
                                              String outputStem, String cfgJson);

    public static native void   nativeLoadLanguage(String tag, String jsonData);
    public static native void   nativeSetLanguage(String tag);
    public static native String nativeGetCurrentLanguage();
    public static native String nativeTranslate(String key);
    public static native String nativeTranslateArgs(String key, String args);

    public static native String nativeLoadSettings(String path);
    public static native void   nativeSaveSettings(String path, String settingsJson);
}