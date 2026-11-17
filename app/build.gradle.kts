import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key lives in /signing (never committed). Without it, release builds are unsigned.
val signingProps = rootProject.file("signing/keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.personal.concertlyrics"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.personal.concertlyrics"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file("signing/" + signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    testImplementation("junit:junit:4.13.2")
}
