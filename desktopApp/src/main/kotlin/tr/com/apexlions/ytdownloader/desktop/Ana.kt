package tr.com.apexlions.ytdownloader.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.ui.YTIndiriciUygulamasi

fun main() {
    WindowsYtDlpGuncellemePlanlayici.baslat()

    application {
        val denetleyici = remember { WindowsUygulamaDenetleyicisi() }
        val developer = SurumBilgisi.developerSurumu
        val uygulamaAdi = SurumBilgisi.uygulamaAdi

        Window(
            onCloseRequest = ::exitApplication,
            title = uygulamaAdi,
            icon = painterResource("uygulama.png"),
        ) {
            window.minimumSize = java.awt.Dimension(900, 780)

            YTIndiriciUygulamasi(
                denetleyici = denetleyici,
                platform = PlatformBilgisi(
                    ad = uygulamaAdi,
                    diskSecimiDestekleniyor = true,
                    depolamaAciklamasi = if (developer) {
                        "Hedef diski seç. İçerikler BPC Developer İndirmeleri klasörüne normal medya dosyası olarak yazılır."
                    } else {
                        "Hedef diski seç. AES-256-GCM şifreli BPC kütüphanesi seçilen diskte oluşturulur."
                    },
                    developerSurumu = developer,
                ),
            )
        }
    }
}