package tr.com.apexlions.ytdownloader.android

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class OynaticiEtkinligi : ComponentActivity() {
    private var oynatici: ExoPlayer? = null
    private var geciciDosya: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val yol = intent.getStringExtra(EK_DOSYA_YOLU)
        if (yol.isNullOrBlank()) {
            finish()
            return
        }

        title = intent.getStringExtra(EK_BASLIK).orEmpty().ifBlank { "YT İndirici Oynatıcı" }
        geciciDosya = File(yol)

        val playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
        }
        setContentView(playerView)

        oynatici = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(geciciDosya)))
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onDestroy() {
        oynatici?.release()
        oynatici = null
        geciciDosya?.delete()
        super.onDestroy()
    }

    companion object {
        const val EK_DOSYA_YOLU = "dosya_yolu"
        const val EK_BASLIK = "baslik"
    }
}
