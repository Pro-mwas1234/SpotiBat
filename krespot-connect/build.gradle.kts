plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mwask.bat.backend.connect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":krespot-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
}
