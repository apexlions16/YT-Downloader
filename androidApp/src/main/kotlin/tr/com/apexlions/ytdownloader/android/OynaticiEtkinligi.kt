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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

@UnstableApi
class OynaticiEtkinligi : ComponentActivity() {
    private var oynatici: ExoPlayer? = null
    private var geciciDosya: File? = null
    private lateinit var sesDugmesi: Button
    private lateinit var altyaziDugmesi: Button
    private lateinit var hizDugmesi: Button
    private lateinit var durumMetni: TextView
    private var tamEkran = false
    private val hizlar = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    private var hizSirasi = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val hamKonum = intent.getStringExtra(EK_MEDYA_KONUMU)
            ?: intent.getStringExtra(EK_DOSYA_YOLU)
        if (hamKonum.isNullOrBlank()) {
            hataGosterVeBitir("Oynatılacak medya bulunamadı.")
            return
        }

        val medyaUri = konumuUriyeCevir(hamKonum)
        if (!kaynakErisilebilir(medyaUri)) {
            hataGosterVeBitir("İndirilen medya dosyasına erişilemiyor. Dosya silinmiş veya hazırlanamamış olabilir.")
            return
        }

        title = intent.getStringExtra(EK_BASLIK).orEmpty().ifBlank { "Bmobil Oynatıcı" }
        if (intent.getBooleanExtra(EK_GECICI_DOSYA, false) && medyaUri.scheme == "file") {
            geciciDosya = medyaUri.path?.let(::File)
        }

        val kok = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val baslik = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 14, 20, 8)
            maxLines = 1
        }
        durumMetni = TextView(this).apply {
            text = "Video hazırlanıyor…"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(20, 0, 20, 10)
            visibility = View.VISIBLE
        }
        val playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
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
        dugme("+10 sn") {
            oynatici?.let {
                val sure = it.duration.takeIf { deger -> deger > 0 } ?: Long.MAX_VALUE
                it.seekTo((it.currentPosition + 10_000).coerceAtMost(sure))
            }
        }
        sesDugmesi = dugme("Ses") { parcaPenceresiniAc(C.TRACK_TYPE_AUDIO) }
        altyaziDugmesi = dugme("Altyazı") { parcaPenceresiniAc(C.TRACK_TYPE_TEXT) }
        hizDugmesi = dugme("Hız 1×") { hiziDegistir() }
        dugme("Tam ekran") { tamEkraniDegistir() }

        sesDugmesi.isEnabled = false
        altyaziDugmesi.isEnabled = false

        kok.addView(baslik, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        kok.addView(durumMetni, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        kok.addView(playerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        kok.addView(altKontroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(kok)

        runCatching {
            oynaticiyiBaslat(playerView, medyaUri)
        }.onFailure { hata ->
            oynatmaHatasiniGoster("Oynatıcı başlatılamadı", hata)
        }
    }

    private fun oynaticiyiBaslat(playerView: PlayerView, medyaUri: Uri) {
        val parcaSecici = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        val dataSourceFactory = DefaultDataSource.Factory(this)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

        val medyaOgesiKurucu = MediaItem.Builder().setUri(medyaUri)
        mimeTuru(intent.getStringExtra(EK_ASIL_UZANTI), medyaUri)?.let(medyaOgesiKurucu::setMimeType)
        val mediaSource = mediaSourceFactory.createMediaSource(medyaOgesiKurucu.build())

        oynatici = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(parcaSecici)
            .build()
            .also { player ->
                playerView.player = player
                player.setHandleAudioBecomingNoisy(true)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        durumMetni.visibility = View.VISIBLE
                        durumMetni.text = when (playbackState) {
                            Player.STATE_IDLE -> "Oynatıcı hazırlanıyor…"
                            Player.STATE_BUFFERING -> "Video yükleniyor…"
                            Player.STATE_READY -> {
                                durumMetni.visibility = View.GONE
                                ""
                            }
                            Player.STATE_ENDED -> "Oynatma tamamlandı."
                            else -> "Video hazırlanıyor…"
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        sesDugmesi.isEnabled = parcalariGetir(C.TRACK_TYPE_AUDIO).isNotEmpty()
                        altyaziDugmesi.isEnabled = parcalariGetir(C.TRACK_TYPE_TEXT).isNotEmpty()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val neden = error.cause?.message?.takeIf(String::isNotBlank)
                        val ayrinti = listOfNotNull(error.errorCodeName, error.message, neden)
                            .distinct()
                            .joinToString(" • ")
                        oynatmaHatasiniGoster("Video oynatılamadı", IllegalStateException(ayrinti, error))
                    }
                })
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true
            }
    }

    private fun konumuUriyeCevir(konum: String): Uri {
        val ayrisitirilmis = Uri.parse(konum)
        if (!ayrisitirilmis.scheme.isNullOrBlank()) return ayrisitirilmis
        return Uri.fromFile(File(konum))
    }

    private fun kaynakErisilebilir(uri: Uri): Boolean = runCatching {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let(::File)?.let { it.isFile && it.length() > 0L } == true
            "content" -> contentResolver.openAssetFileDescriptor(uri, "r")?.use { tanimlayici ->
                tanimlayici.length != 0L
            } == true
            else -> false
        }
    }.getOrDefault(false)

    private fun mimeTuru(uzantiEkstra: String?, uri: Uri): String? {
        val uzanti = uzantiEkstra
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        return when (uzanti) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> null
        }
    }

    private fun oynatmaHatasiniGoster(onEk: String, hata: Throwable) {
        val ayrinti = hata.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(500)
            ?: hata.javaClass.simpleName
        durumMetni.visibility = View.VISIBLE
        durumMetni.setTextColor(Color.rgb(255, 150, 150))
        durumMetni.text = "$onEk: $ayrinti"
        Toast.makeText(this, "$onEk: $ayrinti", Toast.LENGTH_LONG).show()
    }

    private fun hataGosterVeBitir(mesaj: String) {
        Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show()
        finish()
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
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                } else {
                    val secilen = parcalar[sira - if (tur == C.TRACK_TYPE_TEXT) 1 else 0]
                    val override = TrackSelectionOverride(secilen.grup.mediaTrackGroup, secilen.parcaSirasi)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(tur, false)
                        .clearOverridesOfType(tur)
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

    override fun onStop() {
        oynatici?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        oynatici?.release()
        oynatici = null
        if (isFinishing && !isChangingConfigurations) {
            geciciDosya?.delete()
            geciciDosya?.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
        }
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
        const val EK_ASIL_UZANTI = "asil_uzanti"
    }
}
