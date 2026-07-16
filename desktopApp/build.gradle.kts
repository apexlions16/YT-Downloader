import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "tr.com.apexlions.ytdownloader"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "tr.com.apexlions.ytdownloader.desktop.AnaKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "YT İndirici"
            packageVersion = "0.1.0"
            description = "Hız odaklı Android ve Windows medya kütüphanesi"
            vendor = "ApexLions16"

            windows {
                iconFile.set(project.file("src/main/resources/uygulama.ico"))
                menuGroup = "YT İndirici"
                upgradeUuid = "d850b09a-4ed9-4da3-bad0-d3bd6b5b9470"
            }
        }
    }
}
