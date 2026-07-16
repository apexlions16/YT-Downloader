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

        Window(
            onCloseRequest = ::exitApplication,
            title = "YT İndirici",
            icon = painterResource("uygulama.png"),
        ) {
            window.minimumSize = java.awt.Dimension(860, 760)

            YTIndiriciUygulamasi(
                denetleyici = denetleyici,
                platform = PlatformBilgisi(
                    ad = "Windows",
                    diskSecimiDestekleniyor = true,
                    depolamaAciklamasi = "Yalnızca hedef diski seç. Uygulama şifreli kütüphaneyi seçilen diskte otomatik oluşturur.",
                ),
            )
        }
    }
}
