package tr.com.apexlions.ytdownloader.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import tr.com.apexlions.ytdownloader.model.AnalizSonucu
import tr.com.apexlions.ytdownloader.model.IndirmeDurumu
import tr.com.apexlions.ytdownloader.model.IndirmeGorevi
import tr.com.apexlions.ytdownloader.model.IndirmeSecenegi
import tr.com.apexlions.ytdownloader.model.IcerikTuru
import tr.com.apexlions.ytdownloader.model.KanalProfili
import tr.com.apexlions.ytdownloader.model.KutuphaneKaydi
import tr.com.apexlions.ytdownloader.model.TurboProfili
import tr.com.apexlions.ytdownloader.model.UygulamaDenetleyicisi
import tr.com.apexlions.ytdownloader.model.UygulamaDurumu
import tr.com.apexlions.ytdownloader.model.UygulamaSekmesi
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

class WindowsUygulamaDenetleyicisi(
    private val diskServisi: DiskSecimServisi = DiskSecimServisi(),
) : UygulamaDenetleyicisi {
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val motorKurucusu = WindowsMotorKurucusu()
    private val surecler = ConcurrentHashMap<String, Process>()
    private val diskler = diskServisi.diskleriListele()
    private val ilkDisk = diskServisi.seciliDiskYolu()
        ?: diskler.firstOrNull { it.onerilen }?.kokYolu
        ?: diskler.firstOrNull()?.kokYolu

    private val _durum = MutableStateFlow(
        UygulamaDurumu(
            diskler = diskler,
            seciliDiskYolu = ilkDisk,
        ),
    )
    override val durum: StateFlow<UygulamaDurumu> = _durum.asStateFlow()

    init {
        ilkDisk?.let {
            runCatching { diskServisi.diskSec(it) }
            kutuphaneyiYenile()
        }
        kapsam.launch {
            runCatching {
                val surum = motorKurucusu.surum()
                _durum.update { it.copy(ytDlpSurumu = surum) }
            }.onFailure { hata ->
                hataGoster("Windows indirme motoru hazırlanamadı: ${hata.anlasilirMesaj()}")
            }
        }
    }

    override fun baglantiyiDegistir(baglanti: String) {
        _durum.update {
            it.copy(
                baglanti = baglanti.trim(),
                analizSonucu = null,
                seciliSecenekKimligi = null,
                hataMesaji = null,
            )
        }
    }

    override fun analizEt() {
        val adres = _durum.value.baglanti.trim()
        if (!youtubeAdresiMi(adres)) {
            hataGoster("Geçerli bir YouTube, YouTube Music veya youtu.be bağlantısı gir.")
            return
        }
        _durum.update { it.copy(analizEdiliyor = true, hataMesaji = null, bilgiMesaji = null) }

        kapsam.launch {
            runCatching {
                val motor = motorKurucusu.hazirla()
                val komut = mutableListOf(
                    motor.ytDlp.toString(),
                    "--dump-single-json",
                    "--no-playlist",
                    "--no-warnings",
                    "--socket-timeout", "30",
                    "--ffmpeg-location", motor.ffmpeg.parent.toString(),
                )
                motor.deno?.let { komut += listOf("--js-runtimes", "deno:${it}") }
                komut += adres

                val cikti = komutCalistir(komut, 3, TimeUnit.MINUTES)
                val jsonSatiri = cikti.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
                    ?: error("yt-dlp geçerli metadata döndürmedi")
                val nesne = json.parseToJsonElement(jsonSatiri).jsonObject
                val sonuc = analizSonucuOlustur(adres, nesne)
                val varsayilan = sonuc.secenekler.firstOrNull { it.kimlik == "video-en-hizli" }
                    ?: sonuc.secenekler.firstOrNull()

                _durum.update {
                    it.copy(
                        analizEdiliyor = false,
                        analizSonucu = sonuc,
                        seciliSecenekKimligi = varsayilan?.kimlik,
                        bilgiMesaji = "${sonuc.secenekler.size} indirme seçeneği bulundu.",
                    )
                }
            }.onFailure { hata ->
                _durum.update { it.copy(analizEdiliyor = false) }
                hataGoster("İçerik analiz edilemedi: ${hata.anlasilirMesaj()}")
            }
        }
    }

    override fun secenekSec(secenekKimligi: String) {
        _durum.update { it.copy(seciliSecenekKimligi = secenekKimligi) }
    }

    override fun indirmeyiBaslat() {
        val analiz = _durum.value.analizSonucu ?: return hataGoster("Önce bağlantıyı analiz et.")
        val secenek = analiz.secenekler.firstOrNull { it.kimlik == _durum.value.seciliSecenekKimligi }
            ?: return hataGoster("Bir indirme seçeneği seç.")
        val disk = _durum.value.seciliDiskYolu ?: return hataGoster("Önce hedef diski seç.")

        val gorevKimligi = UUID.randomUUID().toString()
        val gorev = IndirmeGorevi(
            gorevKimligi = gorevKimligi,
            medyaKimligi = analiz.medyaKimligi,
            baslik = analiz.baslik,
            kanalAdi = analiz.kanalAdi,
            secenekAdi = secenek.gorunenAd,
            durum = IndirmeDurumu.BEKLIYOR,
        )
        _durum.update {
            it.copy(
                aktifIndirmeler = listOf(gorev) + it.aktifIndirmeler,
                seciliSekme = UygulamaSekmesi.INDIRMELER,
                bilgiMesaji = "Turbo indirme seçilen diskte başlatıldı.",
            )
        }
        kapsam.launch { indir(gorevKimligi, analiz, secenek, disk) }
    }

    private fun indir(
        gorevKimligi: String,
        analiz: AnalizSonucu,
        secenek: IndirmeSecenegi,
        diskYolu: String,
    ) {
        val kutuphaneDizini = diskServisi.kutuphaneYolu(diskYolu).apply { mkdirs() }
        val geciciDizin = kutuphaneDizini.resolve(".gecici/$gorevKimligi").apply { mkdirs() }

        try {
            val motor = motorKurucusu.hazirla()
            goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.INDIRILIYOR) }

            val komut = mutableListOf(
                motor.ytDlp.toString(),
                "--no-playlist",
                "--no-warnings",
                "--newline",
                "--continue",
                "--retries", "10",
                "--fragment-retries", "10",
                "--concurrent-fragments", seciliParcaSayisi().toString(),
                "--write-info-json",
                "--write-thumbnail",
                "--convert-thumbnails", "jpg",
                "--add-metadata",
                "--ffmpeg-location", motor.ffmpeg.parent.toString(),
                "--progress-template", "download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "-o", geciciDizin.resolve("%(id)s.%(ext)s").absolutePath,
                "-f", secenek.ytDlpSecici,
            )
            motor.deno?.let { komut += listOf("--js-runtimes", "deno:${it}") }
            if (secenek.tur == IcerikTuru.SES) {
                komut += listOf("--extract-audio", "--audio-format", sesDonusumAdi(secenek.hedefUzanti))
                if (secenek.hedefUzanti in setOf("mp3", "ogg")) komut += listOf("--audio-quality", "0")
            } else {
                komut += listOf("--merge-output-format", secenek.hedefUzanti)
            }
            komut += analiz.kaynakAdresi

            val surec = ProcessBuilder(komut)
                .directory(geciciDizin)
                .redirectErrorStream(true)
                .start()
            surecler[gorevKimligi] = surec
            val sonSatirlar = ArrayDeque<String>()

            surec.inputStream.bufferedReader().useLines { satirlar ->
                satirlar.forEach { satir ->
                    if (sonSatirlar.size >= 20) sonSatirlar.removeFirst()
                    sonSatirlar.addLast(satir)
                    if (satir.startsWith("download:")) {
                        val alanlar = satir.removePrefix("download:").split('|')
                        val yuzde = alanlar.getOrNull(0)?.trim()?.removeSuffix("%")?.toFloatOrNull() ?: 0f
                        val hiz = alanlar.getOrNull(1)?.trim().orEmpty().takeUnless { it == "NA" }.orEmpty()
                        val eta = alanlar.getOrNull(2)?.trim().orEmpty().takeUnless { it == "NA" }.orEmpty()
                        goreviGuncelle(gorevKimligi) {
                            it.copy(
                                durum = if (yuzde >= 100f) IndirmeDurumu.ISLENIYOR else IndirmeDurumu.INDIRILIYOR,
                                ilerlemeYuzdesi = yuzde.coerceIn(0f, 100f),
                                hizMetni = hiz,
                                kalanSureMetni = eta.takeIf(String::isNotBlank)?.let { kalan -> "Kalan $kalan" }.orEmpty(),
                            )
                        }
                    }
                }
            }
            val kod = surec.waitFor()
            surecler.remove(gorevKimligi)
            if (kod != 0) {
                if (_durum.value.aktifIndirmeler.any { it.gorevKimligi == gorevKimligi && it.durum == IndirmeDurumu.IPTAL_EDILDI }) return
                error(sonSatirlar.joinToString("\n").ifBlank { "yt-dlp çıkış kodu $kod" })
            }

            goreviGuncelle(gorevKimligi) {
                it.copy(durum = IndirmeDurumu.SIFRELENIYOR, ilerlemeYuzdesi = 100f, hizMetni = "", kalanSureMetni = "")
            }

            val medyaDosyasi = geciciDizin.walkTopDown()
                .filter(File::isFile)
                .filterNot { it.extension.lowercase() in METADATA_UZANTILARI }
                .maxByOrNull(File::length)
                ?: error("İndirilen medya dosyası bulunamadı")

            val medyaDizini = kutuphaneDizini.resolve("medya").apply { mkdirs() }
            val kapakDizini = kutuphaneDizini.resolve("kapaklar").apply { mkdirs() }
            val sifreliDosya = medyaDizini.resolve("${analiz.medyaKimligi}-${System.currentTimeMillis()}.ytdm")
            WindowsSifreliMedyaDeposu(kutuphaneDizini).sifrele(medyaDosyasi, sifreliDosya)

            val kapakKaynak = geciciDizin.walkTopDown()
                .firstOrNull { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            val kapakHedef = kapakKaynak?.let {
                kapakDizini.resolve("${analiz.medyaKimligi}.${it.extension.lowercase()}").also { hedef ->
                    it.copyTo(hedef, overwrite = true)
                }
            }

            val kayit = KutuphaneKaydi(
                medyaKimligi = analiz.medyaKimligi,
                kaynakAdresi = analiz.kaynakAdresi,
                baslik = analiz.baslik,
                kanalKimligi = analiz.kanalKimligi,
                kanalAdi = analiz.kanalAdi,
                kanalKullaniciAdi = analiz.kanalKullaniciAdi,
                kapakDosyasi = kapakHedef?.absolutePath,
                sifreliMedyaDosyasi = sifreliDosya.absolutePath,
                asilUzanti = medyaDosyasi.extension.ifBlank { secenek.hedefUzanti },
                sureSaniye = analiz.sureSaniye,
                boyutBayt = sifreliDosya.length(),
                cozunurluk = secenek.yukseklik?.let { "${it}p" },
                kareHizi = secenek.kareHizi,
                videoKodegi = secenek.videoKodegi,
                sesKodegi = secenek.sesKodegi,
                indirilenTarihMillis = System.currentTimeMillis(),
            )

            val depo = WindowsKatalogDeposu(kutuphaneDizini)
            val yeniKutuphane = listOf(kayit) + depo.yukle().filterNot { it.medyaKimligi == kayit.medyaKimligi }
            depo.kaydet(yeniKutuphane)
            goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.TAMAMLANDI, ilerlemeYuzdesi = 100f) }
            kutuphaneyiDurumaYaz(yeniKutuphane)
            bilgiGoster("“${analiz.baslik}” seçilen diskteki şifreli kütüphaneye eklendi.")
        } catch (hata: Throwable) {
            if (_durum.value.aktifIndirmeler.any { it.gorevKimligi == gorevKimligi && it.durum == IndirmeDurumu.IPTAL_EDILDI }) return
            goreviGuncelle(gorevKimligi) {
                it.copy(durum = IndirmeDurumu.HATA, hataMetni = hata.anlasilirMesaj())
            }
            hataGoster("İndirme başarısız: ${hata.anlasilirMesaj()}")
        } finally {
            surecler.remove(gorevKimligi)
            geciciDizin.deleteRecursively()
        }
    }

    override fun indirmeyiIptalEt(gorevKimligi: String) {
        surecler.remove(gorevKimligi)?.let { surec ->
            surec.descendants().forEach(ProcessHandle::destroyForcibly)
            surec.destroyForcibly()
        }
        goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.IPTAL_EDILDI) }
    }

    override fun medyayiOynat(medyaKimligi: String) {
        val kayit = _durum.value.kutuphane.firstOrNull { it.medyaKimligi == medyaKimligi }
            ?: return hataGoster("Kütüphane kaydı bulunamadı.")
        val disk = _durum.value.seciliDiskYolu ?: return hataGoster("Hedef disk seçili değil.")
        val kutuphane = diskServisi.kutuphaneYolu(disk)

        kapsam.launch {
            runCatching {
                val sifreli = File(kayit.sifreliMedyaDosyasi)
                require(sifreli.isFile) { "Şifreli medya dosyası bulunamadı" }
                val yerel = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
                    ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
                val gecici = File(yerel, "YT İndirici/gecici/${UUID.randomUUID()}.${kayit.asilUzanti}")
                WindowsSifreliMedyaDeposu(kutuphane).coz(sifreli, gecici)
                WindowsOynatici.oynat(kayit.baslik, gecici)
            }.onFailure { hata -> hataGoster("İçerik oynatılamadı: ${hata.anlasilirMesaj()}") }
        }
    }

    override fun medyayiSil(medyaKimligi: String) {
        val disk = _durum.value.seciliDiskYolu ?: return
        val kutuphane = diskServisi.kutuphaneYolu(disk)
        val depo = WindowsKatalogDeposu(kutuphane)
        val kayit = depo.yukle().firstOrNull { it.medyaKimligi == medyaKimligi } ?: return
        File(kayit.sifreliMedyaDosyasi).delete()
        kayit.kapakDosyasi?.let { File(it).delete() }
        val yeni = depo.yukle().filterNot { it.medyaKimligi == medyaKimligi }
        depo.kaydet(yeni)
        kutuphaneyiDurumaYaz(yeni)
        bilgiGoster("İçerik kütüphaneden silindi.")
    }

    override fun sekmeSec(sekme: UygulamaSekmesi) {
        _durum.update { it.copy(seciliSekme = sekme) }
    }

    override fun turboProfiliSec(profil: TurboProfili) {
        _durum.update { it.copy(turboProfili = profil, bilgiMesaji = "${profil.gorunenAd} hız profili seçildi.") }
    }

    override fun diskSec(kokYolu: String) {
        runCatching {
            diskServisi.diskSec(kokYolu)
            _durum.update { it.copy(seciliDiskYolu = kokYolu, bilgiMesaji = "$kokYolu hedef disk olarak seçildi.") }
            kutuphaneyiYenile()
        }.onFailure { hata -> hataGoster("Disk seçilemedi: ${hata.anlasilirMesaj()}") }
    }

    override fun kutuphaneyiYenile() {
        val disk = _durum.value.seciliDiskYolu ?: return kutuphaneyiDurumaYaz(emptyList())
        kapsam.launch {
            val kutuphane = diskServisi.kutuphaneYolu(disk).apply { mkdirs() }
            kutuphaneyiDurumaYaz(WindowsKatalogDeposu(kutuphane).yukle())
        }
    }

    override fun ytDlpGuncelle() {
        kapsam.launch {
            runCatching {
                motorKurucusu.hazirla()
                val surum = motorKurucusu.surum()
                _durum.update { it.copy(ytDlpSurumu = surum, bilgiMesaji = "yt-dlp, FFmpeg ve Deno bileşenleri denetlendi.") }
            }.onFailure { hata -> hataGoster("Motor güncellenemedi: ${hata.anlasilirMesaj()}") }
        }
    }

    override fun mesajlariTemizle() {
        _durum.update { it.copy(bilgiMesaji = null, hataMesaji = null) }
    }

    private fun analizSonucuOlustur(adres: String, nesne: JsonObject): AnalizSonucu {
        val sure = nesne.long("duration")
        val formatlar = nesne["formats"]?.jsonArray ?: JsonArray(emptyList())
        val secenekler = secenekleriOlustur(formatlar, sure)
        return AnalizSonucu(
            kaynakAdresi = adres,
            medyaKimligi = nesne.metin("id").ifBlank { UUID.randomUUID().toString() },
            baslik = nesne.metin("title").ifBlank { "Başlıksız içerik" },
            aciklama = nesne.metin("description").ifBlank { null },
            kanalKimligi = nesne.metin("channel_id").ifBlank { nesne.metin("uploader_id").ifBlank { "bilinmeyen-kanal" } },
            kanalAdi = nesne.metin("channel").ifBlank { nesne.metin("uploader").ifBlank { "Bilinmeyen kanal" } },
            kanalKullaniciAdi = nesne.metin("uploader_id").ifBlank { null },
            kapakAdresi = nesne.metin("thumbnail").ifBlank { null },
            sureSaniye = sure,
            yayinTarihi = nesne.metin("upload_date").tarihBicimineCevir().ifBlank { null },
            secenekler = secenekler,
        )
    }

    private fun secenekleriOlustur(formatlar: JsonArray, sureSaniye: Long): List<IndirmeSecenegi> {
        val nesneler = formatlar.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        val sesBoyutu = nesneler
            .filter { it.metin("acodec") !in setOf("", "none") && it.metin("vcodec") in setOf("", "none") }
            .maxOfOrNull { max(it.long("filesize"), it.long("filesize_approx")) }
            ?: 0L

        val videoSecenekleri = nesneler
            .filter { it.int("height") > 0 && it.metin("vcodec") !in setOf("", "none") }
            .groupBy { it.int("height") to it.int("fps") }
            .mapNotNull { (_, adaylar) ->
                val secilen = adaylar.maxByOrNull {
                    max(max(it.long("filesize"), it.long("filesize_approx")), it.long("tbr"))
                } ?: return@mapNotNull null
                val yukseklik = secilen.int("height")
                val fps = secilen.int("fps").takeIf { it > 0 }
                val formatKimligi = secilen.metin("format_id")
                if (formatKimligi.isBlank()) return@mapNotNull null
                val vcodec = secilen.metin("vcodec")
                val hedef = if (secilen.metin("ext") == "mp4" && (vcodec.startsWith("avc") || vcodec.startsWith("h264"))) "mp4" else "mkv"
                val boyut = max(secilen.long("filesize"), secilen.long("filesize_approx")).takeIf { it > 0 }?.plus(sesBoyutu)
                IndirmeSecenegi(
                    kimlik = "video-$formatKimligi-$hedef",
                    gorunenAd = buildString {
                        append("${yukseklik}p")
                        fps?.let { append(" • ${it} FPS") }
                        append(" • ${hedef.uppercase()}")
                    },
                    tur = IcerikTuru.VIDEO,
                    hedefUzanti = hedef,
                    ytDlpSecici = "$formatKimligi+bestaudio/best",
                    yukseklik = yukseklik,
                    kareHizi = fps,
                    videoKodegi = vcodec,
                    sesKodegi = "en iyi ses",
                    tahminiBoyutBayt = boyut,
                )
            }
            .distinctBy { Triple(it.yukseklik, it.kareHizi, it.hedefUzanti) }
            .sortedWith(compareByDescending<IndirmeSecenegi> { it.yukseklik ?: 0 }.thenByDescending { it.kareHizi ?: 0 })

        val hizli = IndirmeSecenegi(
            kimlik = "video-en-hizli",
            gorunenAd = "En hızlı • Tek dosya • MP4",
            tur = IcerikTuru.VIDEO,
            hedefUzanti = "mp4",
            ytDlpSecici = "best[ext=mp4]/best",
            videoKodegi = "hazır birleşik akış",
            sesKodegi = "hazır birleşik akış",
        )
        val azami = IndirmeSecenegi(
            kimlik = "video-azami",
            gorunenAd = "En yüksek kalite • Otomatik",
            tur = IcerikTuru.VIDEO,
            hedefUzanti = "mkv",
            ytDlpSecici = "bestvideo+bestaudio/best",
            videoKodegi = "en iyi",
            sesKodegi = "en iyi",
        )
        val sesSecenekleri = listOf(
            sesSecenegi("m4a", "M4A • Hızlı ve uyumlu", 192, sureSaniye, false),
            sesSecenegi("opus", "Opus • En verimli kalite", 192, sureSaniye, false),
            sesSecenegi("mp3", "MP3 • En yüksek kalite", 320, sureSaniye, true),
            sesSecenegi("ogg", "OGG Vorbis • En yüksek kalite", 320, sureSaniye, true),
            sesSecenegi("flac", "FLAC • Kayıpsız kapsayıcı", 900, sureSaniye, true),
            sesSecenegi("wav", "WAV • Sıkıştırılmamış", 1411, sureSaniye, true),
        )
        return listOf(hizli, azami) + videoSecenekleri + sesSecenekleri
    }

    private fun sesSecenegi(uzanti: String, ad: String, bitHizi: Int, sure: Long, donusturme: Boolean) =
        IndirmeSecenegi(
            kimlik = "ses-$uzanti",
            gorunenAd = ad,
            tur = IcerikTuru.SES,
            hedefUzanti = uzanti,
            ytDlpSecici = if (uzanti == "m4a") "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best",
            sesKodegi = uzanti.uppercase(),
            sesBitHiziKbps = bitHizi,
            tahminiBoyutBayt = if (sure > 0) sure * bitHizi * 1000L / 8L else null,
            donusturmeGerekli = donusturme,
        )

    private fun seciliParcaSayisi(): Int {
        val profil = _durum.value.turboProfili
        if (profil != TurboProfili.OTOMATIK) return profil.parcaSayisi
        val cekirdek = Runtime.getRuntime().availableProcessors()
        return when {
            cekirdek >= 8 -> 16
            cekirdek >= 4 -> 12
            else -> 8
        }
    }

    private fun kutuphaneyiDurumaYaz(kayitlar: List<KutuphaneKaydi>) {
        val kanallar = kayitlar.groupBy { it.kanalKimligi }.map { (kimlik, kanalKayitlari) ->
            val ilk = kanalKayitlari.first()
            KanalProfili(
                kanalKimligi = kimlik,
                kanalAdi = ilk.kanalAdi,
                kullaniciAdi = ilk.kanalKullaniciAdi,
                profilGorseli = ilk.kapakDosyasi,
                indirilenIcerikSayisi = kanalKayitlari.size,
                toplamBoyutBayt = kanalKayitlari.sumOf { it.boyutBayt },
            )
        }.sortedBy { it.kanalAdi.lowercase() }
        _durum.update { it.copy(kutuphane = kayitlar.sortedByDescending { kayit -> kayit.indirilenTarihMillis }, kanallar = kanallar) }
    }

    private fun goreviGuncelle(gorevKimligi: String, donustur: (IndirmeGorevi) -> IndirmeGorevi) {
        _durum.update { mevcut ->
            mevcut.copy(aktifIndirmeler = mevcut.aktifIndirmeler.map { if (it.gorevKimligi == gorevKimligi) donustur(it) else it })
        }
    }

    private fun komutCalistir(komut: List<String>, sure: Long, birim: TimeUnit): String {
        val surec = ProcessBuilder(komut).redirectErrorStream(true).start()
        val cikti = StringBuilder()
        val okuyucu = Thread { surec.inputStream.bufferedReader().useLines { it.forEach { satir -> cikti.appendLine(satir) } } }.apply { start() }
        if (!surec.waitFor(sure, birim)) {
            surec.destroyForcibly()
            error("Komut zaman aşımına uğradı")
        }
        okuyucu.join(10_000)
        check(surec.exitValue() == 0) { cikti.toString().takeLast(2_000).ifBlank { "Komut başarısız" } }
        return cikti.toString()
    }

    private fun JsonObject.metin(anahtar: String): String = this[anahtar]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.int(anahtar: String): Int = this[anahtar]?.jsonPrimitive?.intOrNull ?: 0
    private fun JsonObject.long(anahtar: String): Long = this[anahtar]?.jsonPrimitive?.longOrNull ?: 0L

    private fun youtubeAdresiMi(adres: String): Boolean = runCatching {
        val host = URI(adres).host?.lowercase().orEmpty()
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }.getOrDefault(false)

    private fun String.tarihBicimineCevir(): String =
        if (length == 8 && all(Char::isDigit)) "${substring(0, 4)}-${substring(4, 6)}-${substring(6, 8)}" else this

    private fun Throwable.anlasilirMesaj(): String =
        message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(300) ?: javaClass.simpleName

    private fun sesDonusumAdi(uzanti: String): String = if (uzanti == "ogg") "vorbis" else uzanti

    private fun hataGoster(mesaj: String) {
        _durum.update { it.copy(hataMesaji = mesaj, bilgiMesaji = null) }
    }

    private fun bilgiGoster(mesaj: String) {
        _durum.update { it.copy(bilgiMesaji = mesaj, hataMesaji = null) }
    }

    companion object {
        private val METADATA_UZANTILARI = setOf("json", "jpg", "jpeg", "png", "webp", "part", "ytdm")
    }
}
