package tr.com.apexlions.ytdownloader.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.ui.YTIndiriciUygulamasi

fun main() {
    WindowsYtDlpGuncellemePlanlayici.baslat()

    application {
        val diskServisi = remember { DiskSecimServisi() }
        val diskler = remember { diskServisi.diskleriListele() }
        var seciliDiskYolu by remember {
            mutableStateOf(
                diskServisi.seciliDiskYolu()
                    ?: diskler.firstOrNull { it.onerilen }?.kokYolu,
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "YT İndirici",
            icon = painterResource("uygulama.png"),
        ) {
            window.minimumSize = java.awt.Dimension(760, 720)

            YTIndiriciUygulamasi(
                platform = PlatformBilgisi(
                    ad = "Windows",
                    diskSecimiDestekleniyor = true,
                    depolamaAciklamasi = "Yalnızca hedef diski seç. Uygulama kendi kütüphane klasörünü otomatik oluşturur.",
                ),
                diskler = diskler,
                seciliDiskYolu = seciliDiskYolu,
                diskSecildi = { yeniDisk ->
                    diskServisi.diskSec(yeniDisk)
                    seciliDiskYolu = yeniDisk
                },
            )
        }
    }
}
