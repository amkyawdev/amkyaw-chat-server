plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.amkyawdev.chatserver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.amkyawdev.chatserver"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true
    }

    // Signing material is supplied through the environment so no keystore or
    // password ever lands in the repository. Without it the release build stays
    // unsigned, which is fine for a local smoke build but not for distribution.
    val keystorePath = providers.environmentVariable("AMKYAW_KEYSTORE_FILE")
        .orNull
        ?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("AMKYAW_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("AMKYAW_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("AMKYAW_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
        viewBinding = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // LAN chat transport
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
