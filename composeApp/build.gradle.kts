plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("com.codingfeline.buildkonfig")
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

buildkonfig {
    packageName = "com.fortuneweather"
    defaultConfigs {
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "OPENWEATHER_KEY", localProperties.getProperty("OPENWEATHER_KEY") ?: "")
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "WEATHERAPI_KEY", localProperties.getProperty("WEATHERAPI_KEY") ?: "")
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "KMA_SERVICE_KEY", localProperties.getProperty("KMA_SERVICE_KEY") ?: "")
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                
                // Ktor & Serialization
                implementation("io.ktor:ktor-client-core:2.3.10")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.10")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.10")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                
                // Datetime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("com.google.android.gms:play-services-ads:23.0.0")
                implementation("com.google.android.gms:play-services-location:21.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
                
                // Lottie Android
                implementation("com.airbnb.android:lottie-compose:6.4.0")

                // Ktor Android Engine
                implementation("io.ktor:ktor-client-android:2.3.10")
            }
        }
    }
}

android {
    namespace = "com.fortuneweather"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fortuneweather"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
