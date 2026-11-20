import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mwask.bat"
    compileSdk {
        version = release(37)
    }

    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
        .takeIf { it.exists() }
        ?: rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.mwask.bat"
        minSdk = 24
        targetSdk = 37
        // SpotiBat 1.2.4 shipped with applicationId com.mwask.bat and versionCode 25,
        // so new builds must keep climbing from there to upgrade in place.
        versionCode = 29
        versionName = "2.1.2"
        multiDexEnabled = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    flavorDimensions += "islandId"
    productFlavors {
        create("standard") {
            dimension = "islandId"
            applicationId = "com.mwask.bat"
        }
        create("spotify") {
            dimension = "islandId"
            applicationId = "com.spotify.music"
        }
        create("apple") {
            dimension = "islandId"
            applicationId = "com.apple.android.music"
        }
    }

    signingConfigs {
        create("spotibat") {
            val envStoreFile = System.getenv("RELEASE_STORE_FILE")
            val propsStoreFile = keystoreProperties["signing.storeFile"] as String?
            // Legacy SpotiBat keystore.properties: storeFile is relative to keystore/.
            val legacyStoreFile = (keystoreProperties["storeFile"] as String?)
                ?.let { "keystore/$it" }
            val storePath = when {
                envStoreFile != null && rootProject.file(envStoreFile).exists() -> envStoreFile
                propsStoreFile != null && rootProject.file(propsStoreFile).exists() -> propsStoreFile
                legacyStoreFile != null && rootProject.file(legacyStoreFile).exists() -> legacyStoreFile
                else -> null
            }
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    ?: keystoreProperties["signing.storePassword"] as String?
                    ?: keystoreProperties["storePassword"] as String?
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: keystoreProperties["signing.keyAlias"] as String?
                    ?: keystoreProperties["keyAlias"] as String?
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: keystoreProperties["signing.keyPassword"] as String?
                    ?: keystoreProperties["keyPassword"] as String?
            } else {
                storeFile = null
                storePassword = null
                keyAlias = null
                keyPassword = null
            }
        }
    }

    val releaseSigning = signingConfigs.getByName("spotibat")
        .takeIf { it.storeFile != null }
        ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "SpotiBat β"
            signingConfig = releaseSigning
        }
        release {
            manifestPlaceholders["appName"] = "SpotiBat"
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            optimization {
                enable = true
                keepRules {
                    ignoreFromAllExternalDependencies = false
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

val islandFlavors = mapOf(
    "spotify" to "com.spotify.music",
    "apple" to "com.apple.android.music"
)

androidComponents {
    onVariants { variant ->
        islandFlavors[variant.flavorName]?.let { id ->
            variant.applicationId.set(id)
        }
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val appName = "SpotiBat"
                val versionName = android.defaultConfig.versionName ?: "1.0.0"
                val flavor = variant.flavorName ?: "standard"
                val type = variant.buildType ?: "debug"
                output.outputFileName.set("$appName-v$versionName-$flavor-$type.apk")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(project(":ui"))
    implementation(project(":krespot-connect"))
    implementation(project(":krespot-player"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media)
    // The download service and its queue live on media3's offline stack.
    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
