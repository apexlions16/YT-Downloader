package tr.com.apexlions.ytdownloader.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class AndroidAcikMedyaDeposu(
    private val context: Context,
) {
    fun kaydet(kaynak: File, baslik: String, uzanti: String): String {
        val dosyaAdi = "${guvenliAd(baslik)}-${System.currentTimeMillis()}.$uzanti"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreKaydet(kaynak, dosyaAdi, uzanti).toString()
        } else {
            eskiAndroidKaydet(kaynak, dosyaAdi).toURI().toString()
        }
    }

    fun sil(konum: String) {
        val uri = Uri.parse(konum)
        if (uri.scheme == "content") {
            runCatching { context.contentResolver.delete(uri, null, null) }
        } else {
            runCatching { File(uri.path.orEmpty()).delete() }
        }
    }

    private fun mediaStoreKaydet(kaynak: File, dosyaAdi: String, uzanti: String): Uri {
        val degerler = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, dosyaAdi)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTuru(uzanti))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Bmobil Developer")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val koleksiyon = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(koleksiyon, degerler)
            ?: error("İndirilenler klasöründe dosya oluşturulamadı")

        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { cikti ->
                kaynak.inputStream().buffered().use { girdi -> girdi.copyTo(cikti, DEFAULT_BUFFER_SIZE * 32) }
            } ?: error("Dış depolama çıktı akışı açılamadı")
            val tamamlandi = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, tamamlandi, null, null)
            return uri
        } catch (hata: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw hata
        }
    }

    @Suppress("DEPRECATION")
    private fun eskiAndroidKaydet(kaynak: File, dosyaAdi: String): File {
        val dizin = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Bmobil Developer",
        ).apply { mkdirs() }
        val hedef = dizin.resolve(dosyaAdi)
        kaynak.copyTo(hedef, overwrite = true)
        return hedef
    }

    private fun guvenliAd(ad: String): String = ad
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "medya" }

    private fun mimeTuru(uzanti: String): String = when (uzanti.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}