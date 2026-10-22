plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yoonki.watchrecorder"
    compileSdk = 35

    defaultConfig {
        // Must match :wear — the Data Layer only connects apps with the same package name and signature.
        applicationId = "com.yoonki.watchrecorder"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
    }

    buildFeatures {
        compose = true
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
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
}
