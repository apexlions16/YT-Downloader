import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val developerSurumu = providers.gradleProperty("developerSurumu")
    .orElse("false")
    .map(String::toBoolean)
    .get()
val varyantAdi = if (developerSurumu) "developer" else "normal"
val paketAdi = if (developerSurumu) "BPC Developer" else "BPC"

layout.buildDirectory.set(file("build/$varyantAdi"))

group = "tr.com.apexlions.bpc"
version = "0.3.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "tr.com.apexlions.ytdownloader.desktop.AnaKt"
        jvmArgs += "-Dbpc.developerSurumu=$developerSurumu"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = paketAdi
            packageVersion = "0.3.0"
            description = if (developerSurumu) {
                "Hız odaklı, Türkçe ve açık dosya indiren Windows medya uygulaması"
            } else {
                "Hız odaklı, Türkçe ve şifreli Windows medya kütüphanesi"
            }
            vendor = "ApexLions16"
            modules("java.net.http", "java.prefs", "java.desktop", "jdk.crypto.cryptoki")

            windows {
                iconFile.set(project.file("src/main/resources/uygulama.ico"))
                menuGroup = paketAdi
                upgradeUuid = if (developerSurumu) {
                    "414f5e64-2e39-4ed6-ae21-30da4cabfe53"
                } else {
                    "d850b09a-4ed9-4da3-bad0-d3bd6b5b9470"
                }
                perUserInstall = true
                shortcut = true
                menu = true
                dirChooser = true
            }
        }
    }
}