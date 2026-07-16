package tr.com.apexlions.ytdownloader.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.ui.YTIndiriciUygulamasi

class AnaEtkinlik : ComponentActivity() {
    private val bildirimIzni = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        YtDlpGuncellemePlanlayici.planla(this)
        bildirimIzniniIste()

        val uygulama = application as YTIndiriciUygulamasi
        val developer = BuildConfig.DEVELOPER_SURUMU
        setContent {
            YTIndiriciUygulamasi(
                denetleyici = uygulama.denetleyici,
                platform = PlatformBilgisi(
                    ad = if (developer) "Bmobil Developer" else "Bmobil",
                    diskSecimiDestekleniyor = false,
                    depolamaAciklamasi = if (developer) {
                        "İçerikler cihazın İndirilenler/Bmobil Developer klasörüne normal medya dosyası olarak aktarılır."
                    } else {
                        "İçerikler uygulamanın dahili, özel ve AES-256-GCM şifreli kütüphanesinde tutulur."
                    },
                    developerSurumu = developer,
                ),
                ilkBaglanti = paylasilanBaglanti(intent).orEmpty(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        paylasilanBaglanti(intent)?.let {
            (application as YTIndiriciUygulamasi).denetleyici.baglantiyiDegistir(it)
        }
    }

    private fun paylasilanBaglanti(intent: Intent?): String? =
        intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun bildirimIzniniIste() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            bildirimIzni.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}