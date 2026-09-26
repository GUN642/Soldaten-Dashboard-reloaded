plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

// Versionsnummer an genau einer Stelle. Die Update-Prüfung vergleicht sie mit
// dem neuesten Release auf GitHub.
val appVersionName = "1.2.1"
val appVersionCode = 12

android {
    namespace = "de.gun.dashboard.reloaded"
    compileSdk = 35

    defaultConfig {
        // Eigene App-ID: läuft parallel zum alten Soldaten Dashboard
        applicationId = "de.gun.dashboard.reloaded"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Fester Schlüssel im Projekt: jede neue APK lässt sich über die
        // vorhandene installieren, ohne Deinstallation und Datenverlust.
        create("fest") {
            storeFile = rootProject.file("keystore/reloaded.keystore")
            storePassword = "reloaded2026"
            keyAlias = "reloaded"
            keyPassword = "reloaded2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fest")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("fest")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
