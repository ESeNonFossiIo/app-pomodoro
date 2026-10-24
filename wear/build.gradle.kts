plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One version for phone and watch: read it from the phone module so `make bump` covers both.
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val appVersionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(appGradle)!!.groupValues[1]
val appVersionCode = Regex("""versionCode\s*=\s*(\d+)""").find(appGradle)!!.groupValues[1].toInt()

android {
    namespace = "dev.pomo.wear"
    compileSdk = 35

    defaultConfig {
        // Same id as the phone app, so the Data Layer pairs the two.
        applicationId = "dev.pomo"
        minSdk = 30
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        System.getenv("DEBUG_KEYSTORE")?.let { path -> getByName("debug") { storeFile = file(path) } }
        create("release") {
            storeFile = System.getenv("KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// APKs are named pomodoro-wear-<versionName>-<variant>.apk
base.archivesName.set("pomodoro-wear-$appVersionName")

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
