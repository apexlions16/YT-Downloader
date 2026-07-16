package tr.com.apexlions.ytdownloader.android

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YTIndiriciUygulamasi : Application() {
    lateinit var denetleyici: AndroidUygulamaDenetleyicisi
        private set

    @Volatile
    private var motorHazir = false

    override fun onCreate() {
        super.onCreate()
        denetleyici = AndroidUygulamaDenetleyicisi(this)
        Thread {
            runCatching { motoruHazirla() }
                .onFailure { Log.e("YTİndirici", "İndirme motoru başlatılamadı", it) }
        }.start()
    }

    @Synchronized
    fun motoruHazirla() {
        if (motorHazir) return
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
        Aria2c.getInstance().init(this)
        motorHazir = true
    }
}
