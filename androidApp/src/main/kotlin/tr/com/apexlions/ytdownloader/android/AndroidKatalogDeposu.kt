package tr.com.apexlions.ytdownloader.android

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tr.com.apexlions.ytdownloader.model.KatalogDosyasi
import tr.com.apexlions.ytdownloader.model.KutuphaneKaydi
import java.io.File

class AndroidKatalogDeposu(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val katalogDosyasi = File(context.filesDir, "kutuphane/katalog.json")

    @Synchronized
    fun yukle(): List<KutuphaneKaydi> {
        if (!katalogDosyasi.isFile) return emptyList()
        return runCatching {
            json.decodeFromString<KatalogDosyasi>(katalogDosyasi.readText()).kayitlar
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun kaydet(kayitlar: List<KutuphaneKaydi>) {
        katalogDosyasi.parentFile?.mkdirs()
        val gecici = File(katalogDosyasi.parentFile, "${katalogDosyasi.name}.gecici")
        gecici.writeText(json.encodeToString(KatalogDosyasi(kayitlar = kayitlar)))
        if (!gecici.renameTo(katalogDosyasi)) {
            gecici.copyTo(katalogDosyasi, overwrite = true)
            gecici.delete()
        }
    }
}
