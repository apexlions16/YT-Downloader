import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "tr.com.apexlions.ytdownloader"
version = "0.2.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.openjfx:javafx-base:21.0.7:win")
    implementation("org.openjfx:javafx-graphics:21.0.7:win")
    implementation("org.openjfx:javafx-controls:21.0.7:win")
    implementation("org.openjfx:javafx-media:21.0.7:win")
    implementation("org.openjfx:javafx-swing:21.0.7:win")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "tr.com.apexlions.ytdownloader.desktop.AnaKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "YT İndirici"
            packageVersion = "0.2.0"
            description = "Hız odaklı, Türkçe Android ve Windows medya kütüphanesi"
            vendor = "ApexLions16"
            modules("java.net.http", "java.prefs", "java.desktop", "jdk.crypto.cryptoki")

            windows {
                iconFile.set(project.file("src/main/resources/uygulama.ico"))
                menuGroup = "YT İndirici"
                upgradeUuid = "d850b09a-4ed9-4da3-bad0-d3bd6b5b9470"
                perUserInstall = true
                shortcut = true
                menu = true
                dirChooser = true
            }
        }
    }
}
