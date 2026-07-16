package tr.com.apexlions.ytdownloader.desktop

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WindowsYtDlpGuncellemePlanlayici {
    private val baslatildi = AtomicBoolean(false)
    private val zamanlayici = Executors.newSingleThreadScheduledExecutor { is ->
        Thread(is, "yt-dlp-guncelleme").apply { isDaemon = true }
    }

    fun baslat() {
        if (!baslatildi.compareAndSet(false, true)) return

        zamanlayici.scheduleWithFixedDelay(
            {
                runCatching { WindowsYtDlpGuncelleyici().guncelle() }
                    .onSuccess { sonuc -> println("[YT İndirici] $sonuc") }
                    .onFailure { hata -> System.err.println("[YT İndirici] yt-dlp güncellenemedi; mevcut sürüm korunuyor: ${hata.message}") }
            },
            0,
            6,
            TimeUnit.HOURS,
        )
    }
}

class WindowsYtDlpGuncelleyici(
    private val motorDizini: Path = varsayilanMotorDizini(),
) {
    private val istemci = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun guncelle(): String {
        Files.createDirectories(motorDizini)
        val calistirilabilir = motorDizini.resolve("yt-dlp.exe")

        return if (Files.isRegularFile(calistirilabilir)) {
            mevcutSurumuGuncelle(calistirilabilir)
        } else {
            ilkSurumuIndir(calistirilabilir)
        }
    }

    private fun mevcutSurumuGuncelle(calistirilabilir: Path): String {
        val surec = ProcessBuilder(
            calistirilabilir.toAbsolutePath().toString(),
            "--update-to",
            "nightly",
        )
            .redirectErrorStream(true)
            .start()

        val tamamlandi = surec.waitFor(2, TimeUnit.MINUTES)
        if (!tamamlandi) {
            surec.destroyForcibly()
            error("Güncelleme iki dakika içinde tamamlanmadı")
        }

        val cikti = surec.inputStream.bufferedReader().use { it.readText().trim() }
        check(surec.exitValue() == 0) {
            "yt-dlp güncelleme komutu başarısız: ${cikti.ifBlank { "bilinmeyen hata" }}"
        }
        return cikti.ifBlank { "yt-dlp Nightly sürümü denetlendi." }
    }

    private fun ilkSurumuIndir(hedef: Path): String {
        val toplamlar = metinIndir(SHA256_ADRESI)
        val beklenen = beklenenSha256(toplamlar, "yt-dlp.exe")
            ?: error("Resmî SHA-256 listesinde yt-dlp.exe bulunamadı")

        val gecici = hedef.resolveSibling("yt-dlp.exe.indiriliyor")
        val yanit = istemci.send(
            istek(YT_DLP_ADRESI),
            HttpResponse.BodyHandlers.ofFile(gecici),
        )
        check(yanit.statusCode() in 200..299) { "yt-dlp indirilemedi: HTTP ${yanit.statusCode()}" }

        val gercek = sha256(gecici)
        check(gercek.equals(beklenen, ignoreCase = true)) {
            Files.deleteIfExists(gecici)
            "yt-dlp SHA-256 doğrulaması başarısız"
        }

        try {
            Files.move(
                gecici,
                hedef,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(gecici, hedef, StandardCopyOption.REPLACE_EXISTING)
        }

        return "yt-dlp Nightly indirildi ve SHA-256 doğrulaması tamamlandı."
    }

    private fun metinIndir(adres: String): String {
        val yanit = istemci.send(
            istek(adres),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(yanit.statusCode() in 200..299) { "Güncelleme bilgisi alınamadı: HTTP ${yanit.statusCode()}" }
        return yanit.body()
    }

    private fun istek(adres: String): HttpRequest = HttpRequest.newBuilder(URI.create(adres))
        .timeout(Duration.ofMinutes(2))
        .header("User-Agent", "YT-Indirici/0.1")
        .GET()
        .build()

    internal fun beklenenSha256(liste: String, dosyaAdi: String): String? = liste
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { satir -> satir.endsWith(dosyaAdi) }
        ?.substringBefore(' ')
        ?.takeIf { it.length == 64 }

    private fun sha256(dosya: Path): String {
        val ozet = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(dosya).use { akis ->
            val tampon = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val okunan = akis.read(tampon)
                if (okunan < 0) break
                ozet.update(tampon, 0, okunan)
            }
        }
        return ozet.digest().joinToString("") { bayt -> "%02x".format(bayt) }
    }

    companion object {
        private const val YT_DLP_ADRESI =
            "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp.exe"
        private const val SHA256_ADRESI =
            "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/SHA2-256SUMS"

        private fun varsayilanMotorDizini(): Path {
            val yerelUygulamaVerisi = System.getenv("LOCALAPPDATA")
                ?.takeIf { it.isNotBlank() }
                ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString()
            return Path.of(yerelUygulamaVerisi, "YT İndirici", "motor")
        }
    }
}
