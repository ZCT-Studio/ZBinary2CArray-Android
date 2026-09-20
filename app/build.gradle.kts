import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val envProps = Properties().apply {
    val f = rootProject.file("gradle.env.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.zct_studio.zbinary2carray_android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zct_studio.zbinary2carray_android"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "v1.0-b1+core1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = envProps.getProperty("ANDROID_KEYSTORE_FILE")
                ?: System.getenv("ANDROID_KEYSTORE_FILE")
            storeFile = storeFilePath?.let { path ->
                val resolved = File(path).let { if (it.isAbsolute) it else rootDir.resolve(path) }
                resolved
            }
            storePassword = (envProps.getProperty("ANDROID_KEYSTORE_PASSWORD")
                ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")).orEmpty()
            keyAlias = (envProps.getProperty("ANDROID_KEY_ALIAS")
                ?: System.getenv("ANDROID_KEY_ALIAS")).orEmpty()
            keyPassword = (envProps.getProperty("ANDROID_KEY_PASSWORD")
                ?: System.getenv("ANDROID_KEY_PASSWORD")).orEmpty()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Validate release signing configuration before assembleRelease."
    notCompatibleWithConfigurationCache("Reads env vars + gradle.env.properties at execution time.")

    doLast {
        val props = Properties().apply {
            val f = rootProject.file("gradle.env.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }

        fun rawValue(name: String): String? =
            props.getProperty(name) ?: System.getenv(name)

        fun resolveFile(path: String): File {
            val f = File(path)
            return if (f.isAbsolute) f else rootDir.resolve(path)
        }

        val missing = mutableListOf<String>()
        val hasEnvFile = rootProject.file("gradle.env.properties").exists()
        val sourceHint = if (hasEnvFile) "gradle.env.properties" else "environment variables"

        val keystorePathRaw = rawValue("ANDROID_KEYSTORE_FILE")
        if (keystorePathRaw == null) {
            missing += "ANDROID_KEYSTORE_FILE"
        } else {
            val keystoreFile = resolveFile(keystorePathRaw)
            if (!keystoreFile.exists()) {
                throw GradleException(
                    buildString {
                        appendLine("Keystore file not found: ${keystoreFile.absolutePath}")
                        appendLine("Configured via ANDROID_KEYSTORE_FILE = \"$keystorePathRaw\"")
                        appendLine()
                        appendLine("Update ANDROID_KEYSTORE_FILE in $sourceHint so it points to")
                        appendLine("an existing .jks / .keystore file.")
                    }
                )
            }
        }

        if (rawValue("ANDROID_KEYSTORE_PASSWORD") == null) missing += "ANDROID_KEYSTORE_PASSWORD"
        if (rawValue("ANDROID_KEY_ALIAS") == null) missing += "ANDROID_KEY_ALIAS"
        if (rawValue("ANDROID_KEY_PASSWORD") == null) missing += "ANDROID_KEY_PASSWORD"

        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Release signing is incomplete. Missing ${missing.size} value(s):")
                    appendLine("  ${missing.joinToString(", ")}")
                    appendLine()
                    appendLine("Configure them in $sourceHint.")
                    appendLine("Copy gradle.env.properties.example -> gradle.env.properties,")
                    appendLine("or set them as OS environment variables.")
                }
            )
        }
    }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
