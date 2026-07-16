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
import kotlinx.serialization.json.jsonObject
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
                hataGoster("BPC indirme motoru hazırlanamadı: ${hata.anlasilirMesaj()}")
            }
        }
    }

    override fun baglantiyiDegistir(baglanti: String) {
        _durum.update {
            it.copy(
                baglanti = baglanti.trim(),
                analizSonucu = null,
                seciliSecenekKimligi = null,
                seciliSesParcasiKimlikleri = emptySet(),
                seciliAltyaziDilleri = emptySet(),
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
                val komut = temelKomut(motor).apply {
                    addAll(
                        listOf(
                            "--dump-single-json",
                            "--skip-download",
                            "--no-playlist",
                            "--no-warnings",
                            "--socket-timeout", "30",
                            adres,
                        ),
                    )
                }
                val cikti = komutCalistir(komut, 3, TimeUnit.MINUTES)
                val jsonSatiri = cikti.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
                    ?: error("yt-dlp geçerli metadata döndürmedi")
                val sonuc = WindowsMetadataDonusturucu.analizSonucuOlustur(
                    adres,
                    json.parseToJsonElement(jsonSatiri).jsonObject,
                )
                val varsayilan = sonuc.secenekler.firstOrNull { it.kimlik == "video-en-hizli" }
                    ?: sonuc.secenekler.firstOrNull { it.tur == IcerikTuru.VIDEO }
                    ?: sonuc.secenekler.firstOrNull()

                _durum.update {
                    it.copy(
                        analizEdiliyor = false,
                        analizSonucu = sonuc,
                        seciliSecenekKimligi = varsayilan?.kimlik,
                        seciliSesParcasiKimlikleri = varsayilanSesKimlikleri(sonuc),
                        seciliAltyaziDilleri = emptySet(),
                        bilgiMesaji = "${sonuc.secenekler.size} kalite, ${sonuc.sesParcalari.size} ses ve ${sonuc.altyazilar.size} altyazı seçeneği bulundu.",
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

    override fun sesParcasiSec(formatKimligi: String, secili: Boolean) {
        _durum.update { mevcut ->
            mevcut.copy(
                seciliSesParcasiKimlikleri = mevcut.seciliSesParcasiKimlikleri.toMutableSet().apply {
                    if (secili) add(formatKimligi) else remove(formatKimligi)
                },
            )
        }
    }

    override fun altyaziSec(dilKodu: String, secili: Boolean) {
        _durum.update { mevcut ->
            mevcut.copy(
                seciliAltyaziDilleri = mevcut.seciliAltyaziDilleri.toMutableSet().apply {
                    if (secili) add(dilKodu) else remove(dilKodu)
                },
            )
        }
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

            val seciliSesler = analiz.sesParcalari.filter { it.formatKimligi in _durum.value.seciliSesParcasiKimlikleri }
            val seciliAltyazilar = analiz.altyazilar.filter { it.dilKodu in _durum.value.seciliAltyaziDilleri }
            val varsayilanSesler = varsayilanSesKimlikleri(analiz)
            val ozelSesSecimi = seciliSesler.map { it.formatKimligi }.toSet() != varsayilanSesler && seciliSesler.isNotEmpty()
            val ekParcaVar = secenek.tur == IcerikTuru.VIDEO && (ozelSesSecimi || seciliSesler.size > 1 || seciliAltyazilar.isNotEmpty())
            val ciktiUzantisi = if (ekParcaVar) "mkv" else secenek.hedefUzanti
            val secici = if (secenek.tur == IcerikTuru.VIDEO && ozelSesSecimi) {
                val video = secenek.videoFormatKimligi ?: "bestvideo"
                "$video+${seciliSesler.joinToString("+") { it.formatKimligi }}"
            } else {
                secenek.ytDlpSecici
            }

            val komut = temelKomut(motor).apply {
                addAll(
                    listOf(
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
                        "--progress-template", "download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                        "-o", geciciDizin.resolve("%(id)s.%(ext)s").absolutePath,
                        "-f", secici,
                    ),
                )
                if (secenek.tur == IcerikTuru.SES) {
                    addAll(listOf("--extract-audio", "--audio-format", sesDonusumAdi(secenek.hedefUzanti)))
                    if (secenek.hedefUzanti in setOf("mp3", "ogg")) addAll(listOf("--audio-quality", "0"))
                } else {
                    if (seciliSesler.size > 1) add("--audio-multistreams")
                    if (seciliAltyazilar.isNotEmpty()) {
                        if (seciliAltyazilar.any { !it.otomatik }) add("--write-subs")
                        if (seciliAltyazilar.any { it.otomatik }) add("--write-auto-subs")
                        addAll(listOf("--sub-langs", seciliAltyazilar.joinToString(",") { it.dilKodu }))
                        addAll(listOf("--convert-subs", "srt", "--embed-subs"))
                    }
                    addAll(listOf("--merge-output-format", ciktiUzantisi))
                }
                add(analiz.kaynakAdresi)
            }

            val surec = ProcessBuilder(komut)
                .directory(geciciDizin)
                .redirectErrorStream(true)
                .start()
            surecler[gorevKimligi] = surec
            val sonSatirlar = ArrayDeque<String>()

            surec.inputStream.bufferedReader().useLines { satirlar ->
                satirlar.forEach { satir ->
                    if (sonSatirlar.size >= 40) sonSatirlar.removeFirst()
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

            val medyaDosyasi = geciciDizin.walkTopDown()
                .filter(File::isFile)
                .filterNot { it.extension.lowercase() in YAN_DOSYA_UZANTILARI }
                .maxByOrNull(File::length)
                ?: error("İndirilen medya dosyası bulunamadı")

            val sistemDizini = kutuphaneDizini.resolve(".sistem")
            val kapakDizini = sistemDizini.resolve("kapaklar").apply { mkdirs() }
            val kapakKaynak = geciciDizin.walkTopDown()
                .firstOrNull { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            val kapakHedef = kapakKaynak?.let {
                kapakDizini.resolve("${analiz.medyaKimligi}.${it.extension.lowercase()}").also { hedef ->
                    it.copyTo(hedef, overwrite = true)
                }
            }

            val medyaKonumu: String
            val sifreliDosyaYolu: String
            val sifreli: Boolean
            val boyut: Long

            if (SurumBilgisi.developerSurumu) {
                goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.DISARI_AKTARILIYOR, ilerlemeYuzdesi = 100f) }
                val dosyaAdi = "${guvenliDosyaAdi(analiz.baslik)}-${System.currentTimeMillis()}.${medyaDosyasi.extension.ifBlank { ciktiUzantisi }}"
                val hedef = kutuphaneDizini.resolve(dosyaAdi)
                medyaDosyasi.copyTo(hedef, overwrite = true)
                medyaKonumu = hedef.absolutePath
                sifreliDosyaYolu = ""
                sifreli = false
                boyut = hedef.length()
            } else {
                goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.SIFRELENIYOR, ilerlemeYuzdesi = 100f) }
                val medyaDizini = sistemDizini.resolve("medya").apply { mkdirs() }
                val sifreliDosya = medyaDizini.resolve("${analiz.medyaKimligi}-${System.currentTimeMillis()}.ytdm")
                WindowsSifreliMedyaDeposu(kutuphaneDizini).sifrele(medyaDosyasi, sifreliDosya)
                medyaKonumu = sifreliDosya.absolutePath
                sifreliDosyaYolu = sifreliDosya.absolutePath
                sifreli = true
                boyut = sifreliDosya.length()
            }

            val kayit = KutuphaneKaydi(
                medyaKimligi = analiz.medyaKimligi,
                kaynakAdresi = analiz.kaynakAdresi,
                baslik = analiz.baslik,
                kanalKimligi = analiz.kanalKimligi,
                kanalAdi = analiz.kanalAdi,
                kanalKullaniciAdi = analiz.kanalKullaniciAdi,
                kapakDosyasi = kapakHedef?.absolutePath,
                sifreliMedyaDosyasi = sifreliDosyaYolu,
                medyaKonumu = medyaKonumu,
                sifreli = sifreli,
                asilUzanti = medyaDosyasi.extension.ifBlank { ciktiUzantisi },
                sureSaniye = analiz.sureSaniye,
                boyutBayt = boyut,
                cozunurluk = secenek.yukseklik?.let { "${it}p" },
                kareHizi = secenek.kareHizi,
                videoKodegi = secenek.videoKodegi,
                sesKodegi = secenek.sesKodegi,
                sesParcalari = seciliSesler.map { it.gorunenAd },
                altyaziParcalari = seciliAltyazilar.map { it.gorunenAd },
                indirilenTarihMillis = System.currentTimeMillis(),
            )

            val depo = WindowsKatalogDeposu(kutuphaneDizini)
            val yeniKutuphane = listOf(kayit) + depo.yukle().filterNot { it.medyaKimligi == kayit.medyaKimligi }
            depo.kaydet(yeniKutuphane)
            goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.TAMAMLANDI, ilerlemeYuzdesi = 100f) }
            kutuphaneyiDurumaYaz(yeniKutuphane)
            bilgiGoster(
                if (SurumBilgisi.developerSurumu) "“${analiz.baslik}” BPC Developer İndirmeleri klasörüne kaydedildi."
                else "“${analiz.baslik}” şifreli BPC kütüphanesine eklendi.",
            )
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
                val motor = motorKurucusu.hazirla()
                val oynatilacak: File
                val gecici: Boolean
                if (kayit.sifreli) {
                    val sifreliDosya = File(kayit.etkinMedyaKonumu)
                    require(sifreliDosya.isFile) { "Şifreli medya dosyası bulunamadı" }
                    val yerel = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
                        ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
                    val geciciDizin = File(yerel, "BPC/gecici/${UUID.randomUUID()}").apply { mkdirs() }
                    oynatilacak = geciciDizin.resolve("${kayit.medyaKimligi}.${kayit.asilUzanti}")
                    WindowsSifreliMedyaDeposu(kutuphane).coz(sifreliDosya, oynatilacak)
                    gecici = true
                } else {
                    oynatilacak = File(kayit.etkinMedyaKonumu)
                    require(oynatilacak.isFile) { "Açık medya dosyası bulunamadı" }
                    gecici = false
                }
                WindowsOynatici.oynat(kayit.baslik, oynatilacak, motor.mpv, gecici)
            }.onFailure { hata -> hataGoster("İçerik oynatılamadı: ${hata.anlasilirMesaj()}") }
        }
    }

    override fun medyayiSil(medyaKimligi: String) {
        val disk = _durum.value.seciliDiskYolu ?: return
        val kutuphane = diskServisi.kutuphaneYolu(disk)
        val depo = WindowsKatalogDeposu(kutuphane)
        val kayit = depo.yukle().firstOrNull { it.medyaKimligi == medyaKimligi } ?: return
        File(kayit.etkinMedyaKonumu).delete()
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
                val sonuc = motorKurucusu.guncelle()
                val surum = motorKurucusu.surum()
                _durum.update { it.copy(ytDlpSurumu = surum, bilgiMesaji = sonuc) }
            }.onFailure { hata -> hataGoster("Motor güncellenemedi: ${hata.anlasilirMesaj()}") }
        }
    }

    override fun mesajlariTemizle() {
        _durum.update { it.copy(bilgiMesaji = null, hataMesaji = null) }
    }

    private fun temelKomut(motor: WindowsMotorYollari): MutableList<String> = mutableListOf(
        motor.ytDlp.toAbsolutePath().toString(),
        "--ffmpeg-location", motor.ffmpeg.parent.toAbsolutePath().toString(),
    ).apply {
        motor.deno?.let { addAll(listOf("--js-runtimes", "deno:${it.toAbsolutePath()}")) }
    }

    private fun komutCalistir(komut: List<String>, sure: Long, birim: TimeUnit): String {
        val surec = ProcessBuilder(komut).redirectErrorStream(true).start()
        val cikti = StringBuilder()
        val okuyucu = Thread {
            surec.inputStream.bufferedReader().useLines { satirlar -> satirlar.forEach { cikti.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        val tamamlandi = surec.waitFor(sure, birim)
        if (!tamamlandi) {
            surec.descendants().forEach(ProcessHandle::destroyForcibly)
            surec.destroyForcibly()
            error("İşlem zaman aşımına uğradı")
        }
        okuyucu.join(5_000)
        check(surec.exitValue() == 0) {
            cikti.toString().lineSequence().takeLast(25).joinToString("\n").ifBlank { "Komut başarısız oldu" }
        }
        return cikti.toString()
    }

    private fun varsayilanSesKimlikleri(analiz: AnalizSonucu): Set<String> {
        val isaretli = analiz.sesParcalari.filter { it.varsayilan }.map { it.formatKimligi }.toSet()
        return if (isaretli.isNotEmpty()) isaretli else analiz.sesParcalari.firstOrNull()?.let { setOf(it.formatKimligi) }.orEmpty()
    }

    private fun seciliParcaSayisi(): Int {
        val profil = _durum.value.turboProfili
        if (profil != TurboProfili.OTOMATIK) return profil.parcaSayisi
        val islemci = Runtime.getRuntime().availableProcessors()
        val bellekGb = Runtime.getRuntime().maxMemory() / 1_073_741_824.0
        return when {
            islemci >= 12 && bellekGb >= 4 -> 16
            islemci >= 6 -> 12
            else -> 8
        }
    }

    private fun kutuphaneyiDurumaYaz(kayitlar: List<KutuphaneKaydi>) {
        val kanallar = kayitlar.groupBy { it.kanalKimligi }.map { (kimlik, liste) ->
            val ilk = liste.first()
            KanalProfili(
                kanalKimligi = kimlik,
                kanalAdi = ilk.kanalAdi,
                kullaniciAdi = ilk.kanalKullaniciAdi,
                profilGorseli = ilk.kapakDosyasi,
                indirilenIcerikSayisi = liste.size,
                toplamBoyutBayt = liste.sumOf { it.boyutBayt },
            )
        }.sortedBy { it.kanalAdi.lowercase() }
        _durum.update {
            it.copy(
                kutuphane = kayitlar.sortedByDescending(KutuphaneKaydi::indirilenTarihMillis),
                kanallar = kanallar,
            )
        }
    }

    private fun goreviGuncelle(gorevKimligi: String, donustur: (IndirmeGorevi) -> IndirmeGorevi) {
        _durum.update { mevcut ->
            mevcut.copy(
                aktifIndirmeler = mevcut.aktifIndirmeler.map {
                    if (it.gorevKimligi == gorevKimligi) donustur(it) else it
                },
            )
        }
    }

    private fun hataGoster(mesaj: String) {
        _durum.update { it.copy(hataMesaji = mesaj, bilgiMesaji = null) }
    }

    private fun bilgiGoster(mesaj: String) {
        _durum.update { it.copy(bilgiMesaji = mesaj, hataMesaji = null) }
    }

    private fun youtubeAdresiMi(adres: String): Boolean = runCatching {
        val host = URI(adres).host?.lowercase().orEmpty()
        host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }.getOrDefault(false)

    private fun sesDonusumAdi(uzanti: String): String = if (uzanti == "ogg") "vorbis" else uzanti

    private fun guvenliDosyaAdi(ad: String): String = ad
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(150)
        .ifBlank { "medya" }

    private fun Throwable.anlasilirMesaj(): String =
        message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(500) ?: javaClass.simpleName

    companion object {
        private val YAN_DOSYA_UZANTILARI = setOf(
            "json", "jpg", "jpeg", "png", "webp", "vtt", "srt", "ass", "lrc", "part", "ytdl", "description",
        )
    }
}