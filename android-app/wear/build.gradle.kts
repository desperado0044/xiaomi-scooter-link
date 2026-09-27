plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wear OS companion: standalone app for the watch, own applicationId (sideloaded directly,
// not distributed via Play Store's automatic phone/watch pairing). Shares the BLE/crypto/
// protocol code with the phone app via :core - no protocol logic is duplicated here.
android {
    namespace = "com.scooterre.client.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scooterre.client.wear"
        minSdk = 30 // Wear OS 3+ (Compose Material 3 for Wear requires this baseline)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Wear-specific Compose (Material 3 Expressive). 1.5.0 is the latest stable line; newer
    // betas (1.7.x) need compileSdk 37 + AGP 9.1, which the rest of this project isn't on yet.
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")

    // Data Layer API (phone <-> watch sync) and Tiles (glanceable watch-face-adjacent status).
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear.tiles:tiles:1.6.0-rc02")
    implementation("androidx.wear.tiles:tiles-material:1.6.0-rc02")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}
