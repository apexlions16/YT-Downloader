package tr.com.apexlions.ytdownloader.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IndirmeHizmeti : Service() {
    override fun onCreate() {
        super.onCreate()
        kanalOlustur()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            EYLEM_DURDUR -> stopForeground(STOP_FOREGROUND_REMOVE).also { stopSelf() }
            EYLEM_GUNCELLE -> {
                val baslik = intent.getStringExtra(EK_BASLIK).orEmpty().ifBlank { "Medya indiriliyor" }
                val ilerleme = intent.getIntExtra(EK_ILERLEME, 0).coerceIn(0, 100)
                val bildirim = bildirimOlustur(baslik, ilerleme)
                startForeground(BILDIRIM_KIMLIGI, bildirim)
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(BILDIRIM_KIMLIGI, bildirim)
            }
            else -> {
                val baslik = intent?.getStringExtra(EK_BASLIK).orEmpty().ifBlank { "Turbo indirme hazırlanıyor" }
                startForeground(BILDIRIM_KIMLIGI, bildirimOlustur(baslik, 0))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bildirimOlustur(baslik: String, ilerleme: Int): Notification =
        NotificationCompat.Builder(this, KANAL_KIMLIGI)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("YT İndirici")
            .setContentText(baslik)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, ilerleme, ilerleme <= 0)
            .build()

    private fun kanalOlustur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val kanal = NotificationChannel(
            KANAL_KIMLIGI,
            "İndirmeler",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "YT İndirici indirme ilerlemesi"
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(kanal)
    }

    companion object {
        private const val KANAL_KIMLIGI = "yt_indirici_indirmeler"
        private const val BILDIRIM_KIMLIGI = 2401
        private const val EYLEM_GUNCELLE = "tr.com.apexlions.ytdownloader.GUNCELLE"
        private const val EYLEM_DURDUR = "tr.com.apexlions.ytdownloader.DURDUR"
        private const val EK_BASLIK = "baslik"
        private const val EK_ILERLEME = "ilerleme"

        fun baslat(context: Context, baslik: String) {
            val intent = Intent(context, IndirmeHizmeti::class.java).putExtra(EK_BASLIK, baslik)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun guncelle(context: Context, baslik: String, ilerleme: Int) {
            val intent = Intent(context, IndirmeHizmeti::class.java)
                .setAction(EYLEM_GUNCELLE)
                .putExtra(EK_BASLIK, baslik)
                .putExtra(EK_ILERLEME, ilerleme)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun durdur(context: Context) {
            context.startService(Intent(context, IndirmeHizmeti::class.java).setAction(EYLEM_DURDUR))
        }
    }
}
