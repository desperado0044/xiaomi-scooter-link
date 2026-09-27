plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Shared BLE/crypto/MIoT-protocol core, used by both the phone app and the Wear OS companion.
// No UI, no Compose, no phone-only features (backup/export, ride history, reminders, widgets) -
// those stay in :app. See docs/HELP.md for the protocol overview.
android {
    namespace = "com.scooterre.client"
    compileSdk = 35

    defaultConfig {
        minSdk = 26 // AES/CCM via the platform provider needs API 26+ (Conscrypt)
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Update checking/downloading (UpdateChecker/UpdateInstaller) - shared by :app and :wear.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
