package tr.com.apexlions.ytdownloader.android

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class YtDlpGuncellemeIsci(
    uygulamaBaglami: Context,
    parametreler: WorkerParameters,
) : CoroutineWorker(uygulamaBaglami, parametreler) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            YoutubeDL.getInstance().init(applicationContext)
            val durum = YoutubeDL.getInstance().updateYoutubeDL(
                applicationContext,
                YoutubeDL.UpdateChannel._NIGHTLY,
            )
            val surum = YoutubeDL.getInstance().versionName(applicationContext).orEmpty()

            Log.i("YTİndirici", "yt-dlp güncelleme sonucu: $durum, sürüm: $surum")
            Result.success(
                workDataOf(
                    "durum" to durum.toString(),
                    "surum" to surum,
                ),
            )
        }.getOrElse { hata ->
            Log.e("YTİndirici", "yt-dlp güncellenemedi; mevcut sürüm korunuyor", hata)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object YtDlpGuncellemePlanlayici {
    private const val ANLIK_IS_ADI = "yt-dlp-anlik-guncelleme"
    private const val DONEMSEL_IS_ADI = "yt-dlp-donemsel-guncelleme"

    fun planla(context: Context) {
        val kosullar = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val anlikIstek = OneTimeWorkRequestBuilder<YtDlpGuncellemeIsci>()
            .setConstraints(kosullar)
            .build()

        val donemselIstek = PeriodicWorkRequestBuilder<YtDlpGuncellemeIsci>(
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(kosullar)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ANLIK_IS_ADI,
            ExistingWorkPolicy.KEEP,
            anlikIstek,
        )

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DONEMSEL_IS_ADI,
            ExistingPeriodicWorkPolicy.UPDATE,
            donemselIstek,
        )
    }
}
