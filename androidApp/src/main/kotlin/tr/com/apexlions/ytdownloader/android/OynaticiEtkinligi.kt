package tr.com.apexlions.ytdownloader.android

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.io.File

@UnstableApi
class OynaticiEtkinligi : ComponentActivity() {
    private var oynatici: ExoPlayer? = null
    private var geciciDosya: File? = null
    private lateinit var sesDugmesi: Button
    private lateinit var altyaziDugmesi: Button
    private lateinit var hizDugmesi: Button
    private var tamEkran = false
    private val hizlar = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    private var hizSirasi = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val konum = intent.getStringExtra(EK_MEDYA_KONUMU)
            ?: intent.getStringExtra(EK_DOSYA_YOLU)
        if (konum.isNullOrBlank()) {
            Toast.makeText(this, "Oynatılacak medya bulunamadı.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = intent.getStringExtra(EK_BASLIK).orEmpty().ifBlank { "Bmobil Oynatıcı" }
        if (intent.getBooleanExtra(EK_GECICI_DOSYA, false)) {
            geciciDosya = Uri.parse(konum).path?.let(::File)
        }

        val kok = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val baslik = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 14, 20, 14)
            maxLines = 1
        }
        val playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 4000
            setBackgroundColor(Color.BLACK)
        }
        val altKontroller = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 12)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        fun dugme(metin: String, tikla: () -> Unit): Button = Button(this).apply {
            text = metin
            isAllCaps = false
            setOnClickListener { tikla() }
            altKontroller.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        dugme("−10 sn") { oynatici?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } }
        dugme("+10 sn") { oynatici?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))) } }
        sesDugmesi = dugme("Ses") { parcaPenceresiniAc(C.TRACK_TYPE_AUDIO) }
        altyaziDugmesi = dugme("Altyazı") { parcaPenceresiniAc(C.TRACK_TYPE_TEXT) }
        hizDugmesi = dugme("Hız 1×") { hiziDegistir() }
        dugme("Tam ekran") { tamEkraniDegistir() }

        kok.addView(baslik, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        kok.addView(playerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        kok.addView(altKontroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(kok)

        val parcaSecici = DefaultTrackSelector(this)
        oynatici = ExoPlayer.Builder(this)
            .setTrackSelector(parcaSecici)
            .build()
            .also { player ->
                playerView.player = player
                player.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        sesDugmesi.isEnabled = parcalariGetir(C.TRACK_TYPE_AUDIO).isNotEmpty()
                        altyaziDugmesi.isEnabled = parcalariGetir(C.TRACK_TYPE_TEXT).isNotEmpty()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@OynaticiEtkinligi,
                            "Oynatma hatası: ${error.message ?: "Bilinmeyen hata"}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                })
                player.setMediaItem(MediaItem.fromUri(Uri.parse(konum)))
                player.prepare()
                player.playWhenReady = true
            }
    }

    private fun parcaPenceresiniAc(tur: Int) {
        val player = oynatici ?: return
        val parcalar = parcalariGetir(tur)
        if (parcalar.isEmpty()) {
            Toast.makeText(this, if (tur == C.TRACK_TYPE_AUDIO) "Başka ses parçası yok." else "İndirilen altyazı yok.", Toast.LENGTH_SHORT).show()
            return
        }

        val baslik = if (tur == C.TRACK_TYPE_AUDIO) "Ses parçasını seç" else "Altyazıyı seç"
        val etiketler = buildList {
            if (tur == C.TRACK_TYPE_TEXT) add("Kapalı")
            addAll(parcalar.map { it.etiket })
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(baslik)
            .setItems(etiketler) { pencere, sira ->
                if (tur == C.TRACK_TYPE_TEXT && sira == 0) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    val secilen = parcalar[sira - if (tur == C.TRACK_TYPE_TEXT) 1 else 0]
                    val override = TrackSelectionOverride(secilen.grup.mediaTrackGroup, secilen.parcaSirasi)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(tur, false)
                        .setOverrideForType(override)
                        .build()
                }
                pencere.dismiss()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun parcalariGetir(tur: Int): List<OynaticiParcasi> {
        val tracks = oynatici?.currentTracks ?: return emptyList()
        var sira = 1
        return buildList {
            tracks.groups.filter { it.type == tur }.forEach { grup ->
                for (index in 0 until grup.length) {
                    if (!grup.isTrackSupported(index)) continue
                    val format = grup.getTrackFormat(index)
                    val turAdi = if (tur == C.TRACK_TYPE_AUDIO) "Ses" else "Altyazı"
                    val etiket = format.label
                        ?: format.language?.let { dilAdi(it) }
                        ?: "$turAdi ${sira++}"
                    val ayrinti = listOfNotNull(format.language?.let(::dilAdi), format.codecs, format.sampleMimeType)
                        .distinct()
                        .joinToString(" • ")
                    add(OynaticiParcasi(grup, index, if (ayrinti.isBlank()) etiket else "$etiket — $ayrinti"))
                }
            }
        }
    }

    private fun hiziDegistir() {
        val player = oynatici ?: return
        hizSirasi = (hizSirasi + 1) % hizlar.size
        val hiz = hizlar[hizSirasi]
        player.setPlaybackSpeed(hiz)
        hizDugmesi.text = "Hız ${hiz}×"
    }

    private fun tamEkraniDegistir() {
        tamEkran = !tamEkran
        val denetleyici = WindowInsetsControllerCompat(window, window.decorView)
        if (tamEkran) {
            denetleyici.hide(WindowInsetsCompat.Type.systemBars())
            denetleyici.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            denetleyici.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun dilAdi(kod: String): String = runCatching {
        java.util.Locale.forLanguageTag(kod).getDisplayLanguage(java.util.Locale("tr")).ifBlank { kod }
    }.getOrDefault(kod)

    override fun onDestroy() {
        oynatici?.release()
        oynatici = null
        geciciDosya?.delete()
        geciciDosya?.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
        super.onDestroy()
    }

    private data class OynaticiParcasi(
        val grup: Tracks.Group,
        val parcaSirasi: Int,
        val etiket: String,
    )

    companion object {
        const val EK_DOSYA_YOLU = "dosya_yolu"
        const val EK_MEDYA_KONUMU = "medya_konumu"
        const val EK_BASLIK = "baslik"
        const val EK_GECICI_DOSYA = "gecici_dosya"
    }
}