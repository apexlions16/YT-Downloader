package tr.com.apexlions.ytdownloader.desktop

import tr.com.apexlions.ytdownloader.model.DepolamaHedefi
import java.io.File
import java.util.prefs.Preferences

class DiskSecimServisi(
    private val developerSurumu: Boolean = SurumBilgisi.developerSurumu,
) {
    private val tercihler = Preferences.userRoot().node(
        if (developerSurumu) "tr/com/apexlions/bpc/developer" else "tr/com/apexlions/bpc/normal",
    )

    fun diskleriListele(): List<DepolamaHedefi> {
        val hamDiskler = File.listRoots()
            .filter { it.exists() && it.canWrite() }
            .map { kok ->
                DepolamaHedefi(
                    kokYolu = kok.absolutePath,
                    gorunenAd = "${kok.absolutePath} diski",
                    toplamBayt = kok.totalSpace,
                    kullanilabilirBayt = kok.usableSpace,
                )
            }
            .sortedByDescending { it.kullanilabilirBayt }

        val enGenisDisk = hamDiskler.firstOrNull()?.kokYolu
        return hamDiskler.map { it.copy(onerilen = it.kokYolu == enGenisDisk) }
    }

    fun seciliDiskYolu(): String? {
        val kayitli = tercihler.get(SECILI_DISK_ANAHTARI, null)
        return kayitli?.takeIf { File(it).exists() && File(it).canWrite() }
    }

    fun diskSec(kokYolu: String) {
        val disk = File(kokYolu)
        require(disk.exists() && disk.canWrite()) { "Seçilen diske yazılamıyor: $kokYolu" }

        val kutuphane = disk.resolve(kutuphaneKlasoru)
        if (!kutuphane.exists()) {
            check(kutuphane.mkdirs()) { "Kütüphane klasörü oluşturulamadı: ${kutuphane.absolutePath}" }
        }
        tercihler.put(SECILI_DISK_ANAHTARI, disk.absolutePath)
    }

    fun kutuphaneYolu(kokYolu: String): File = File(kokYolu).resolve(kutuphaneKlasoru)

    val kutuphaneKlasoru: String
        get() = if (developerSurumu) "BPC Developer İndirmeleri" else "BPC Kütüphanesi"

    private companion object {
        const val SECILI_DISK_ANAHTARI = "secili_disk_yolu"
    }
}