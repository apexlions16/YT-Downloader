package tr.com.apexlions.ytdownloader.android

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YTIndiriciUygulamasi : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread {
            runCatching {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                Aria2c.getInstance().init(this)
            }.onFailure { hata ->
                Log.e("YTİndirici", "İndirme motoru başlatılamadı", hata)
            }
        }.start()
    }
}
