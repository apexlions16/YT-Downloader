package tr.com.apexlions.ytdownloader.desktop

import tr.com.apexlions.ytdownloader.model.DepolamaHedefi
import java.io.File
import java.util.prefs.Preferences

class DiskSecimServisi {
    private val tercihler = Preferences.userRoot().node("tr/com/apexlions/ytdownloader")

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

        val kutuphane = disk.resolve(KUTUPHANE_KLASORU)
        if (!kutuphane.exists()) {
            check(kutuphane.mkdirs()) { "Kütüphane klasörü oluşturulamadı: ${kutuphane.absolutePath}" }
        }
        tercihler.put(SECILI_DISK_ANAHTARI, disk.absolutePath)
    }

    fun kutuphaneYolu(kokYolu: String): File = File(kokYolu).resolve(KUTUPHANE_KLASORU)

    private companion object {
        const val SECILI_DISK_ANAHTARI = "secili_disk_yolu"
        const val KUTUPHANE_KLASORU = "YT İndirici Kütüphanesi"
    }
}
