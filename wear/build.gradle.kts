plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yoonki.watchrecorder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yoonki.watchrecorder"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
