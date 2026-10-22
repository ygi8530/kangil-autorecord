plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yoonki.watchrecorder"
    compileSdk = 35

    defaultConfig {
        // Must match :mobile — the Data Layer only connects apps with the same package name and signature.
        applicationId = "com.yoonki.watchrecorder"
        minSdk = 33
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
    }

    buildTypes {
        release {
            // Shrunk build for dist/. Signed with the debug key so it pairs with debug installs (Data Layer needs matching signatures).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Wearable Data Layer: the only public way to move files from watch to phone
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
