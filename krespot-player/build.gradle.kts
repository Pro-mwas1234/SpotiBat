plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mwask.bat.backend.player"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    api(project(":krespot-connect"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.dash)
    implementation(libs.okhttp)
    implementation(libs.newpipeextractor)
    coreLibraryDesugaring(libs.desugaring)
}
