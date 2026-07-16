package tr.com.apexlions.ytdownloader.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.ui.YTIndiriciUygulamasi

class AnaEtkinlik : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        YtDlpGuncellemePlanlayici.planla(this)

        val medyaDizini = getExternalFilesDir("medya")?.absolutePath
            ?: filesDir.resolve("medya").absolutePath

        val paylasilanBaglanti = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        setContent {
            YTIndiriciUygulamasi(
                platform = PlatformBilgisi(
                    ad = "Android",
                    diskSecimiDestekleniyor = false,
                    depolamaAciklamasi = "Uygulamaya özel medya alanı: $medyaDizini",
                ),
            )
        }
    }
}
