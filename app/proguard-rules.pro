# ProGuard rules for ZBinary2CArray-Android

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge
-keep class io.github.zct_studio.zbinary2carray_android.NativeBridge { *; }

# Keep MainActivity
-keep class io.github.zct_studio.zbinary2carray_android.MainActivity { *; }

# Keep FilePickerDialog
-keep class io.github.zct_studio.zbinary2carray_android.FilePickerDialog { *; }

# Keep serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**