package tr.com.apexlions.ytdownloader.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

object WindowsOynatici {
    fun oynat(
        baslik: String,
        dosya: File,
        mpvYolu: Path,
        geciciDosya: Boolean,
    ) {
        require(dosya.isFile) { "Oynatılacak medya dosyası bulunamadı: ${dosya.absolutePath}" }
        require(File(mpvYolu.toString()).isFile) { "MPV oynatıcı motoru bulunamadı" }

        val pipeAdi = "bpc-mpv-${UUID.randomUUID()}"
        val pipeYolu = "\\\\.\\pipe\\$pipeAdi"
        val surec = ProcessBuilder(
            mpvYolu.toAbsolutePath().toString(),
            "--no-config",
            "--force-window=yes",
            "--keep-open=yes",
            "--osc=yes",
            "--input-default-bindings=yes",
            "--input-ipc-server=$pipeYolu",
            "--title=${SurumBilgisi.uygulamaAdi} • $baslik",
            "--autofit-larger=90%x90%",
            "--sub-auto=no",
            dosya.absolutePath,
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        val kapatildi = AtomicBoolean(false)
        val islemSirasi = Executors.newSingleThreadExecutor { islem ->
            Thread(islem, "bpc-mpv-kontrol").apply { isDaemon = true }
        }

        SwingUtilities.invokeLater {
            val durumMetni = JLabel("Oynatıcı hazırlanıyor…")
            val sesKutusu = JComboBox<ParcaSecimi>()
            val altyaziKutusu = JComboBox<ParcaSecimi>()
            val hizKutusu = JComboBox(arrayOf("0.75×", "1×", "1.25×", "1.5×", "2×")).apply { selectedIndex = 1 }
            val sesSeviyesi = JSlider(0, 100, 100).apply { preferredSize = Dimension(150, 28) }
            var ipc: MpvIpc? = null
            var tamEkran = false
            var secimGuncelleniyor = false

            val pencere = JFrame("${SurumBilgisi.uygulamaAdi} Oynatıcı Kontrolleri • $baslik").apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                minimumSize = Dimension(820, 210)
                setSize(980, 245)
                setLocationRelativeTo(null)
            }

            val ust = JPanel(BorderLayout(10, 6)).apply {
                border = EmptyBorder(12, 14, 6, 14)
                add(JLabel("<html><b>${htmlKacir(baslik)}</b><br><small>Video ayrı MPV penceresinde oynar. Ses ve altyazıyı buradan seçebilirsin.</small></html>"), BorderLayout.CENTER)
                add(durumMetni, BorderLayout.SOUTH)
            }
            val temelKontroller = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
            val parcaKontrolleri = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))

            fun dugme(metin: String, komut: () -> Unit): JButton = JButton(metin).apply {
                addActionListener { islemSirasi.submit(komut) }
                temelKontroller.add(this)
            }

            dugme("Oynat / Duraklat") { ipc?.komut("cycle", "pause") }
            dugme("−10 saniye") { ipc?.komut("seek", -10, "relative") }
            dugme("+10 saniye") { ipc?.komut("seek", 10, "relative") }
            dugme("Başa dön") { ipc?.komut("seek", 0, "absolute") }
            dugme("Tam ekran") {
                tamEkran = !tamEkran
                ipc?.komut("set_property", "fullscreen", tamEkran)
            }
            dugme("Durdur") { ipc?.komut("quit") }

            parcaKontrolleri.add(JLabel("Ses:"))
            sesKutusu.preferredSize = Dimension(235, 30)
            parcaKontrolleri.add(sesKutusu)
            parcaKontrolleri.add(JLabel("Altyazı:"))
            altyaziKutusu.preferredSize = Dimension(235, 30)
            parcaKontrolleri.add(altyaziKutusu)
            parcaKontrolleri.add(JLabel("Hız:"))
            parcaKontrolleri.add(hizKutusu)
            parcaKontrolleri.add(JLabel("Ses seviyesi:"))
            parcaKontrolleri.add(sesSeviyesi)

            sesKutusu.addActionListener {
                if (secimGuncelleniyor) return@addActionListener
                val secim = sesKutusu.selectedItem as? ParcaSecimi ?: return@addActionListener
                islemSirasi.submit { ipc?.komut("set_property", "aid", secim.kimlik) }
            }
            altyaziKutusu.addActionListener {
                if (secimGuncelleniyor) return@addActionListener
                val secim = altyaziKutusu.selectedItem as? ParcaSecimi ?: return@addActionListener
                islemSirasi.submit { ipc?.komut("set_property", "sid", secim.kimlik) }
            }
            hizKutusu.addActionListener {
                val hiz = when (hizKutusu.selectedIndex) {
                    0 -> 0.75
                    1 -> 1.0
                    2 -> 1.25
                    3 -> 1.5
                    else -> 2.0
                }
                islemSirasi.submit { ipc?.komut("set_property", "speed", hiz) }
            }
            sesSeviyesi.addChangeListener {
                if (!sesSeviyesi.valueIsAdjusting) {
                    val deger = sesSeviyesi.value
                    islemSirasi.submit { ipc?.komut("set_property", "volume", deger) }
                }
            }

            pencere.contentPane = JPanel(BorderLayout()).apply {
                add(ust, BorderLayout.NORTH)
                add(temelKontroller, BorderLayout.CENTER)
                add(parcaKontrolleri, BorderLayout.SOUTH)
            }
            pencere.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    if (kapatildi.compareAndSet(false, true)) {
                        runCatching { ipc?.komut("quit") }
                        runCatching { ipc?.close() }
                        if (surec.isAlive) surec.destroy()
                    }
                }
            })
            pencere.isVisible = true

            islemSirasi.submit {
                try {
                    ipc = MpvIpc.baglan(pipeYolu, surec)
                    val parcalar = ipc?.parcalariGetir().orEmpty()
                    SwingUtilities.invokeLater {
                        secimGuncelleniyor = true
                        sesKutusu.removeAllItems()
                        parcalar.filter { it.tur == "audio" }.forEach { sesKutusu.addItem(it) }
                        altyaziKutusu.removeAllItems()
                        altyaziKutusu.addItem(ParcaSecimi("no", "Kapalı", "sub"))
                        parcalar.filter { it.tur == "sub" }.forEach { altyaziKutusu.addItem(it) }
                        parcalar.firstOrNull { it.tur == "audio" && it.secili }?.let { secili ->
                            (0 until sesKutusu.itemCount)
                                .firstOrNull { sesKutusu.getItemAt(it).kimlik == secili.kimlik }
                                ?.let { sesKutusu.selectedIndex = it }
                        }
                        parcalar.firstOrNull { it.tur == "sub" && it.secili }?.let { secili ->
                            (0 until altyaziKutusu.itemCount)
                                .firstOrNull { altyaziKutusu.getItemAt(it).kimlik == secili.kimlik }
                                ?.let { altyaziKutusu.selectedIndex = it }
                        }
                        secimGuncelleniyor = false
                        durumMetni.text = "Hazır • ${sesKutusu.itemCount} ses • ${altyaziKutusu.itemCount - 1} altyazı"
                    }
                } catch (hata: Throwable) {
                    SwingUtilities.invokeLater {
                        durumMetni.text = "Kontrol bağlantısı kurulamadı; MPV'nin kendi kontrolleri kullanılabilir."
                        JOptionPane.showMessageDialog(
                            pencere,
                            "MPV açıldı ancak gelişmiş kontrol bağlantısı kurulamadı:\n${hata.message}",
                            "BPC Oynatıcı",
                            JOptionPane.WARNING_MESSAGE,
                        )
                    }
                }
            }

            Thread {
                surec.waitFor()
                runCatching { ipc?.close() }
                islemSirasi.shutdownNow()
                if (geciciDosya) {
                    runCatching { dosya.delete() }
                    dosya.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                }
                SwingUtilities.invokeLater {
                    if (pencere.isDisplayable) pencere.dispose()
                }
            }.apply {
                isDaemon = true
                name = "bpc-mpv-bekleyici"
                start()
            }
        }
    }

    private fun htmlKacir(metin: String): String = metin
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private data class ParcaSecimi(
    val kimlik: String,
    val etiket: String,
    val tur: String,
    val secili: Boolean = false,
) {
    override fun toString(): String = etiket
}

private class MpvIpc private constructor(
    private val kanal: RandomAccessFile,
) : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private var istekKimligi = 0

    @Synchronized
    fun komut(vararg alanlar: Any?): JsonElement? {
        val kimlik = ++istekKimligi
        val istek = buildJsonObject {
            put("command", buildJsonArray { alanlar.forEach { add(jsonDegeri(it)) } })
            put("request_id", JsonPrimitive(kimlik))
        }
        kanal.write((istek.toString() + "\n").toByteArray(StandardCharsets.UTF_8))

        while (true) {
            val satir = utf8SatirOku() ?: error("MPV kontrol bağlantısı kapandı")
            val nesne = runCatching { json.parseToJsonElement(satir).jsonObject }.getOrNull() ?: continue
            if (nesne["request_id"]?.jsonPrimitive?.intOrNull != kimlik) continue
            val hata = nesne["error"]?.jsonPrimitive?.contentOrNull
            if (!hata.isNullOrBlank() && hata != "success") error("MPV komutu başarısız: $hata")
            return nesne["data"]
        }
    }

    fun parcalariGetir(): List<ParcaSecimi> {
        val veri = komut("get_property", "track-list") as? JsonArray ?: return emptyList()
        return veri.mapNotNull { eleman ->
            val nesne = eleman as? JsonObject ?: return@mapNotNull null
            val tur = nesne["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (tur !in setOf("audio", "sub")) return@mapNotNull null
            val kimlik = nesne["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val dil = nesne["lang"]?.jsonPrimitive?.contentOrNull
            val baslik = nesne["title"]?.jsonPrimitive?.contentOrNull
            val kodek = nesne["codec"]?.jsonPrimitive?.contentOrNull
            val secili = nesne["selected"]?.jsonPrimitive?.contentOrNull?.toBoolean() == true
            val turAdi = if (tur == "audio") "Ses" else "Altyazı"
            val etiket = listOfNotNull(baslik, dil, kodek).distinct().joinToString(" • ").ifBlank { "$turAdi $kimlik" }
            ParcaSecimi(kimlik, etiket, tur, secili)
        }
    }

    private fun utf8SatirOku(): String? {
        val tampon = ByteArrayOutputStream()
        while (true) {
            val bayt = kanal.read()
            if (bayt < 0) return if (tampon.size() == 0) null else tampon.toString(StandardCharsets.UTF_8)
            if (bayt == '\n'.code) return tampon.toString(StandardCharsets.UTF_8)
            if (bayt != '\r'.code) tampon.write(bayt)
        }
    }

    override fun close() {
        kanal.close()
    }

    companion object {
        fun baglan(pipeYolu: String, surec: Process): MpvIpc {
            var sonHata: Throwable? = null
            repeat(80) {
                if (!surec.isAlive) error("MPV beklenmedik biçimde kapandı")
                try {
                    return MpvIpc(RandomAccessFile(pipeYolu, "rw"))
                } catch (hata: Throwable) {
                    sonHata = hata
                    TimeUnit.MILLISECONDS.sleep(125)
                }
            }
            throw IllegalStateException("MPV kontrol kanalı açılamadı", sonHata)
        }
    }
}

private fun jsonDegeri(deger: Any?): JsonElement = when (deger) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(deger)
    is Number -> JsonPrimitive(deger)
    else -> JsonPrimitive(deger.toString())
}