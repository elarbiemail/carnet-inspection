plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vc = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "ma.carnet.inspection.natif"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.fm6education.carnetinspection.natif"
        minSdk = 26
        targetSdk = 35
        versionCode = vc
        versionName = "2.$vc"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../../android/keystore/carnet-inspection.jks")
            storePassword = "carnet2026"
            keyAlias = "carnet"
            keyPassword = "carnet2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
