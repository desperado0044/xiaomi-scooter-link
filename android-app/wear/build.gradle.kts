import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wear OS companion: sideloaded directly onto the watch, not distributed via Play Store's
// automatic phone/watch embedding. Shares the BLE/crypto/protocol code with the phone app via
// :core - no protocol logic is duplicated here.
//
// applicationId is DELIBERATELY THE SAME as the phone app's (com.scooterre.client), and the
// release build is signed with the same key - the Wearable Data Layer (MessageClient/DataClient)
// identifies a phone/watch app pair by matching applicationId + signing certificate, and silently
// fails to deliver ("Failed to deliver message to AppKey...") if either differs. This is the
// deliberate exception to "namespace matches applicationId": the Kotlin package/namespace stays
// .wear for code organization, only the applicationId is shared.
android {
    namespace = "com.scooterre.client.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scooterre.client"
        minSdk = 30 // Wear OS 3+ (Compose Material 3 for Wear requires this baseline)
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"
    }

    // Same release key as the phone app - see the comment above for why this must match.
    val signingProps = Properties().apply {
        val f = File(System.getProperty("user.home"), ".scooter-signing/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasReleaseKey = signingProps.getProperty("storeFile")?.let { File(it).exists() } == true

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
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
    // registerForActivityResult requires Fragment 1.3.0+; nothing else here pulls in a recent
    // enough transitive version (lint: InvalidFragmentVersionForActivityResult).
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // HorizontalPager + pinch/double-tap zoom for the documents viewer (same primitives :app's
    // DocumentViewer.kt uses) - not otherwise guaranteed to be pulled in transitively, so declared
    // explicitly (version resolved by the BOM above, same as the other compose.ui lines).
    implementation("androidx.compose.foundation:foundation")

    // Wear-specific Compose (Material 3 Expressive). 1.5.0 is the latest stable line; newer
    // betas (1.7.x) need compileSdk 37 + AGP 9.1, which the rest of this project isn't on yet.
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")

    // Data Layer API (phone <-> watch sync) and Tiles (glanceable watch-face-adjacent status).
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear.tiles:tiles:1.6.0-rc02")
    implementation("androidx.wear.tiles:tiles-material:1.6.0-rc02")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}
