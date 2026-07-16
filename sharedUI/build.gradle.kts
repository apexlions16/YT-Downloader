plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        named("androidMain") {
            dependencies {
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
            }
        }
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.common)
            }
        }
    }
}

android {
    namespace = "tr.com.apexlions.ytdownloader.sharedui"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
