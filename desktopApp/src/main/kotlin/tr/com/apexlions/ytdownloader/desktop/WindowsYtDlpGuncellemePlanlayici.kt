package tr.com.apexlions.ytdownloader.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
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
import java.util.zip.ZipInputStream

object WindowsYtDlpGuncellemePlanlayici {
    private val baslatildi = AtomicBoolean(false)
    private val zamanlayici = Executors.newSingleThreadScheduledExecutor { islem ->
        Thread(islem, "yt-dlp-guncelleme").apply { isDaemon = true }
    }

    fun baslat() {
        if (!baslatildi.compareAndSet(false, true)) return
        zamanlayici.scheduleWithFixedDelay(
            {
                runCatching { WindowsMotorKurucusu().hazirla() }
                    .onSuccess { println("[YT İndirici] Motor bileşenleri güncel.") }
                    .onFailure { hata -> System.err.println("[YT İndirici] Motor güncellenemedi; mevcut bileşenler korunuyor: ${hata.message}") }
            },
            0,
            6,
            TimeUnit.HOURS,
        )
    }
}

data class WindowsMotorYollari(
    val ytDlp: Path,
    val ffmpeg: Path,
    val ffprobe: Path,
    val deno: Path?,
)

class WindowsMotorKurucusu(
    private val motorDizini: Path = varsayilanMotorDizini(),
) {
    private val istemci = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(25))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun hazirla(): WindowsMotorYollari {
        Files.createDirectories(motorDizini)
        val ytDlp = motorDizini.resolve("yt-dlp.exe")
        val ffmpeg = motorDizini.resolve("ffmpeg.exe")
        val ffprobe = motorDizini.resolve("ffprobe.exe")
        val deno = motorDizini.resolve("deno.exe")

        ytDlpHazirla(ytDlp)
        if (!Files.isRegularFile(ffmpeg) || !Files.isRegularFile(ffprobe)) {
            arsivdenBilesenKur(
                apiAdresi = "https://api.github.com/repos/yt-dlp/FFmpeg-Builds/releases/latest",
                assetSecici = { ad -> ad.contains("win64", ignoreCase = true) && ad.contains("gpl", ignoreCase = true) && ad.endsWith(".zip") },
                hedefler = mapOf("ffmpeg.exe" to ffmpeg, "ffprobe.exe" to ffprobe),
            )
        }
        if (!Files.isRegularFile(deno)) {
            runCatching {
                arsivdenBilesenKur(
                    apiAdresi = "https://api.github.com/repos/denoland/deno/releases/latest",
                    assetSecici = { ad -> ad == "deno-x86_64-pc-windows-msvc.zip" },
                    hedefler = mapOf("deno.exe" to deno),
                )
            }.onFailure { hata ->
                System.err.println("[YT İndirici] Deno kurulamadı; yt-dlp mevcut yöntemle devam edecek: ${hata.message}")
            }
        }

        return WindowsMotorYollari(
            ytDlp = ytDlp,
            ffmpeg = ffmpeg,
            ffprobe = ffprobe,
            deno = deno.takeIf(Files::isRegularFile),
        )
    }

    fun surum(): String {
        val yollar = hazirla()
        val surec = ProcessBuilder(yollar.ytDlp.toString(), "--version").redirectErrorStream(true).start()
        val cikti = surec.inputStream.bufferedReader().use { it.readText().trim() }
        check(surec.waitFor(30, TimeUnit.SECONDS) && surec.exitValue() == 0) { "yt-dlp sürümü okunamadı" }
        return cikti
    }

    private fun ytDlpHazirla(hedef: Path) {
        if (Files.isRegularFile(hedef)) {
            val surec = ProcessBuilder(hedef.toString(), "--update-to", "nightly")
                .redirectErrorStream(true)
                .start()
            if (!surec.waitFor(2, TimeUnit.MINUTES)) {
                surec.destroyForcibly()
                error("yt-dlp güncellemesi zaman aşımına uğradı")
            }
            val cikti = surec.inputStream.bufferedReader().use { it.readText().trim() }
            check(surec.exitValue() == 0) { "yt-dlp güncellemesi başarısız: ${cikti.ifBlank { "bilinmeyen hata" }}" }
            return
        }

        val toplamlar = metinIndir("https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/SHA2-256SUMS")
        val beklenen = toplamlar.lineSequence()
            .map(String::trim)
            .firstOrNull { it.endsWith("yt-dlp.exe") }
            ?.substringBefore(' ')
            ?.takeIf { it.length == 64 }
            ?: error("Resmî SHA-256 listesinde yt-dlp.exe bulunamadı")

        val gecici = hedef.resolveSibling("yt-dlp.exe.indiriliyor")
        dosyaIndir("https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp.exe", gecici)
        check(sha256(gecici).equals(beklenen, ignoreCase = true)) {
            Files.deleteIfExists(gecici)
            "yt-dlp SHA-256 doğrulaması başarısız"
        }
        atomikTasi(gecici, hedef)
    }

    private fun arsivdenBilesenKur(
        apiAdresi: String,
        assetSecici: (String) -> Boolean,
        hedefler: Map<String, Path>,
    ) {
        val kok = json.parseToJsonElement(metinIndir(apiAdresi)).jsonObject
        val varliklar: JsonArray = kok["assets"]?.jsonArray ?: error("Sürüm varlıkları bulunamadı")
        val varlik: JsonObject = varliklar
            .map { it.jsonObject }
            .firstOrNull { assetSecici(it["name"]?.jsonPrimitive?.content.orEmpty()) }
            ?: error("Uygun Windows paketi bulunamadı")

        val indirmeAdresi = varlik["browser_download_url"]?.jsonPrimitive?.content
            ?: error("Paket indirme adresi bulunamadı")
        val assetAdi = varlik["name"]?.jsonPrimitive?.content ?: "motor.zip"
        val beklenenOzet = varlik["digest"]?.jsonPrimitive?.content
            ?.removePrefix("sha256:")
            ?.takeIf { it.length == 64 }

        val arsiv = motorDizini.resolve("$assetAdi.indiriliyor")
        dosyaIndir(indirmeAdresi, arsiv)
        if (beklenenOzet != null) {
            check(sha256(arsiv).equals(beklenenOzet, ignoreCase = true)) {
                Files.deleteIfExists(arsiv)
                "$assetAdi SHA-256 doğrulaması başarısız"
            }
        }

        val kalan = hedefler.toMutableMap()
        ZipInputStream(BufferedInputStream(Files.newInputStream(arsiv))).use { zip ->
            while (true) {
                val giris = zip.nextEntry ?: break
                if (!giris.isDirectory) {
                    val dosyaAdi = Path.of(giris.name).fileName.toString().lowercase()
                    kalan.entries.firstOrNull { it.key.lowercase() == dosyaAdi }?.let { eslesme ->
                        val gecici = eslesme.value.resolveSibling("${eslesme.value.fileName}.indiriliyor")
                        Files.newOutputStream(gecici).use { cikti -> zip.copyTo(cikti, DEFAULT_BUFFER_SIZE * 16) }
                        atomikTasi(gecici, eslesme.value)
                        kalan.remove(eslesme.key)
                    }
                }
                zip.closeEntry()
                if (kalan.isEmpty()) break
            }
        }
        Files.deleteIfExists(arsiv)
        check(kalan.isEmpty()) { "Arşivde bulunamayan bileşenler: ${kalan.keys.joinToString()}" }
    }

    private fun metinIndir(adres: String): String {
        val yanit = istemci.send(istek(adres), HttpResponse.BodyHandlers.ofString())
        check(yanit.statusCode() in 200..299) { "Güncelleme bilgisi alınamadı: HTTP ${yanit.statusCode()}" }
        return yanit.body()
    }

    private fun dosyaIndir(adres: String, hedef: Path) {
        Files.deleteIfExists(hedef)
        val yanit = istemci.send(istek(adres), HttpResponse.BodyHandlers.ofFile(hedef))
        check(yanit.statusCode() in 200..299) {
            Files.deleteIfExists(hedef)
            "Dosya indirilemedi: HTTP ${yanit.statusCode()}"
        }
    }

    private fun istek(adres: String): HttpRequest = HttpRequest.newBuilder(URI.create(adres))
        .timeout(Duration.ofMinutes(5))
        .header("User-Agent", "YT-Indirici/0.2")
        .header("Accept", "application/vnd.github+json")
        .GET()
        .build()

    private fun sha256(dosya: Path): String {
        val ozet = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(dosya).use { akis ->
            val tampon = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            while (true) {
                val okunan = akis.read(tampon)
                if (okunan < 0) break
                ozet.update(tampon, 0, okunan)
            }
        }
        return ozet.digest().joinToString("") { "%02x".format(it) }
    }

    private fun atomikTasi(kaynak: Path, hedef: Path) {
        try {
            Files.move(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun varsayilanMotorDizini(): Path {
            val yerelUygulamaVerisi = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString()
            return Path.of(yerelUygulamaVerisi, "YT İndirici", "motor")
        }
    }
}

@Deprecated("WindowsMotorKurucusu kullanın")
class WindowsYtDlpGuncelleyici {
    fun guncelle(): String {
        WindowsMotorKurucusu().hazirla()
        return "yt-dlp, FFmpeg ve Deno bileşenleri denetlendi."
    }
}
