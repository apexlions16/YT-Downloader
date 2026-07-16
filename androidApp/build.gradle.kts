plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tr.com.apexlions.ytdownloader.android"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 3
        versionName = "0.3.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    flavorDimensions += "depolama"
    productFlavors {
        create("normal") {
            dimension = "depolama"
            applicationId = "tr.com.apexlions.bmobil"
            buildConfigField("boolean", "DEVELOPER_SURUMU", "false")
            resValue("string", "app_name", "Bmobil")
        }
        create("developer") {
            dimension = "depolama"
            applicationId = "tr.com.apexlions.bmobil.developer"
            buildConfigField("boolean", "DEVELOPER_SURUMU", "true")
            resValue("string", "app_name", "Bmobil Developer")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
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
    implementation(project(":sharedUI"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$youtubedlAndroid")
}