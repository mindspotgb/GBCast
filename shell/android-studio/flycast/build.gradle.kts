import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version: shell/android-studio/VERSION holds the release version (x.y.z); the build number is the version code
// (CI's version_code input, or -PgbcastVersionCode / GBCAST_VERSION_CODE for local builds). Local builds without an
// explicit value choose a code above the highest GBCast version installed on a connected ADB device.
val gbcastVersion = rootProject.file("VERSION").readText().trim()
val explicitGbcastBuild = providers.gradleProperty("gbcastVersionCode").orNull
    ?: System.getenv("GBCAST_VERSION_CODE")
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.isFile) localPropertiesFile.inputStream().use { load(it) }
}

fun runLocalAdb(adb: File, vararg args: String): String? {
    return runCatching {
        val process = ProcessBuilder(listOf(adb.absolutePath) + args).redirectErrorStream(true).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            output.takeIf { process.exitValue() == 0 }
        }
    }.getOrNull()
}

fun highestInstalledGbcastVersionCode(): Int? {
    val sdkPath = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: localProperties.getProperty("sdk.dir")
    val adbName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "adb.exe" else "adb"
    val adb = sdkPath?.let { File(it, "platform-tools/$adbName") }?.takeIf { it.isFile } ?: File(adbName)
    val devices = runLocalAdb(adb, "devices") ?: return null
    val serials = devices.lineSequence().drop(1).mapNotNull { line ->
        val columns = line.trim().split(Regex("\\s+"))
        columns.firstOrNull().takeIf { columns.size >= 2 && columns[1] == "device" }
    }
    val versionCodePattern = Regex("versionCode[:=]\\s*(\\d+)")
    return serials.mapNotNull { serial ->
        runLocalAdb(adb, "-s", serial, "shell", "pm", "list", "packages", "--show-versioncode", "app.gbcast.emulator")
            ?.let { versionCodePattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    }.maxOrNull()
}

val installedGbcastBuild = if (explicitGbcastBuild == null) highestInstalledGbcastVersionCode() else null
val lastLocalGbcastBuild = localProperties.getProperty("gbcast.lastVersionCode")?.toIntOrNull()
val gbcastBuild = explicitGbcastBuild?.toInt()
    ?: maxOf(38, lastLocalGbcastBuild ?: 0, (installedGbcastBuild ?: 0) + if (installedGbcastBuild != null) 1 else 0)
if (explicitGbcastBuild == null && installedGbcastBuild != null)
    logger.lifecycle("GBCast local version code: installed $installedGbcastBuild; building $gbcastBuild")

val localVersionCodeLock = Any()
tasks.configureEach {
    if (name == "assembleDebug" || name == "assembleRelease") {
        doLast {
            synchronized(localVersionCodeLock) {
                runCatching {
                    val properties = Properties().apply {
                        if (localPropertiesFile.isFile) localPropertiesFile.inputStream().use { load(it) }
                    }
                    val previous = properties.getProperty("gbcast.lastVersionCode")?.toIntOrNull() ?: 0
                    properties.setProperty("gbcast.lastVersionCode", maxOf(previous, gbcastBuild).toString())
                    localPropertiesFile.outputStream().use { properties.store(it, "Local Android SDK and app version settings") }
                }.onFailure { error ->
                    logger.warn("Could not persist the last local GBCast version code: ${error.message}")
                }
            }
        }
    }
}

android {
    namespace = "app.gbcast.emulator"
    ndkVersion = "29.0.14206865"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.gbcast.emulator"
        // GBFrame declares minSdk 28. An app cannot be lower than its library, so GBCast matches it.
        // GBCast uses its own application ID and matching namespace.
        minSdk = 28
        targetSdk = 36
        versionCode = gbcastBuild
        versionName = "$gbcastVersion (build $gbcastBuild)"
        vectorDrawables.useSupportLibrary = true

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_ARM_MODE=arm"
                arguments += "-DSENTRY_UPLOAD_URL=" + (System.getenv("SENTRY_UPLOAD_URL") ?: "")
                arguments += "-DUSE_OPENMP=OFF"
                arguments += "-DANDROID_WEAK_API_DEFS=ON"
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
        }
        // Permanent release key: CI decodes the GBCAST_KEYSTORE_BASE64 secret to GBCAST_KEYSTORE_FILE (local builds point
        // it at the owner's copy). Every APK signed with it installs over the previous one. Without it (forks) the
        // repository's debug keystore is used, which can't update a released GBCast.
        val releaseKey = System.getenv("GBCAST_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
        create("release") {
            if (releaseKey != null) {
                storeFile = releaseKey
                storePassword = System.getenv("GBCAST_KEYSTORE_PASSWORD")
                keyAlias = "gbcast"
                keyPassword = System.getenv("GBCAST_KEYSTORE_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug {
            // Physical development devices use arm64; CI keeps x86_64 for its emulator.
            ndk.abiFilters += listOf("arm64-v8a", "x86_64")
        }
        release {
            // GBCast targets modern 64-bit Android hardware only.
            ndk.abiFilters += "arm64-v8a"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    externalNativeBuild {
        cmake {
            path = file("../../../CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            // not used
            excludes += "lib/*/libz.so"
            // This is necessary for libadrenotools custom driver loading
            useLegacyPackaging = true
        }
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // Installs the baseline profiles (Compose's own and src/main/baseline-prof.txt) on sideloaded APKs, so the UI runs
    // compiled from the first launch instead of interpreted until the device compiles it while idle
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.compose.icons)
    implementation(project(":gbframe"))
    implementation(libs.commons.lang3)
    implementation(libs.httpclient5)
    implementation(libs.slf4j.android)
    implementation(fileTree("libs") { include("*.aar", "*.jar") })
    implementation(libs.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
