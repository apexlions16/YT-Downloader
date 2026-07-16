package tr.com.apexlions.ytdownloader.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class AndroidUygulamaDenetleyicisi(
    private val uygulama: YTIndiriciUygulamasi,
) : UygulamaDenetleyicisi {
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val katalog = AndroidKatalogDeposu(uygulama)
    private val sifreliDepo = SifreliMedyaDeposu()
    private val acikDepo = AndroidAcikMedyaDeposu(uygulama)
    private val _durum = MutableStateFlow(UygulamaDurumu())
    override val durum: StateFlow<UygulamaDurumu> = _durum.asStateFlow()

    init {
        kutuphaneyiYenile()
        kapsam.launch {
            runCatching {
                uygulama.motoruHazirla()
                _durum.update { it.copy(ytDlpSurumu = YoutubeDL.getInstance().versionName(uygulama)) }
            }.onFailure { hata ->
                hataGoster("İndirme motoru hazırlanamadı: ${hata.anlasilirMesaj()}")
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
                uygulama.motoruHazirla()
                val istek = YoutubeDLRequest(adres)
                    .addOption("--dump-single-json")
                    .addOption("--skip-download")
                    .addOption("--no-playlist")
                    .addOption("--no-warnings")
                    .addOption("--socket-timeout", 30)
                val cevap = YoutubeDL.getInstance().execute(istek)
                val jsonSatiri = cevap.out.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
                    ?: error("yt-dlp geçerli metadata döndürmedi")
                val sonuc = AndroidMetadataDonusturucu.analizSonucuOlustur(
                    adres,
                    YoutubeDL.objectMapper.readTree(jsonSatiri),
                )
                val varsayilanSecenek = sonuc.secenekler.firstOrNull { it.kimlik == "video-en-hizli" }
                    ?: sonuc.secenekler.firstOrNull { it.tur == IcerikTuru.VIDEO }
                    ?: sonuc.secenekler.firstOrNull()
                val varsayilanSesler = varsayilanSesKimlikleri(sonuc)

                _durum.update {
                    it.copy(
                        analizEdiliyor = false,
                        analizSonucu = sonuc,
                        seciliSecenekKimligi = varsayilanSecenek?.kimlik,
                        seciliSesParcasiKimlikleri = varsayilanSesler,
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
                bilgiMesaji = "Turbo indirme kuyruğa eklendi.",
            )
        }

        IndirmeHizmeti.baslat(uygulama, analiz.baslik)
        kapsam.launch { indir(gorevKimligi, analiz, secenek) }
    }

    private suspend fun indir(
        gorevKimligi: String,
        analiz: AnalizSonucu,
        secenek: IndirmeSecenegi,
    ) {
        val geciciDizin = File(uygulama.cacheDir, "indiriliyor/$gorevKimligi").apply { mkdirs() }
        try {
            uygulama.motoruHazirla()
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

            val ciktiKalibi = File(geciciDizin, "%(id)s.%(ext)s").absolutePath
            val istek = YoutubeDLRequest(analiz.kaynakAdresi)
                .addOption("--no-playlist")
                .addOption("--no-warnings")
                .addOption("--newline")
                .addOption("--continue")
                .addOption("--retries", 10)
                .addOption("--fragment-retries", 10)
                .addOption("--concurrent-fragments", seciliParcaSayisi())
                .addOption("--write-info-json")
                .addOption("--write-thumbnail")
                .addOption("--convert-thumbnails", "jpg")
                .addOption("--add-metadata")
                .addOption("-o", ciktiKalibi)
                .addOption("-f", secici)

            if (secenek.tur == IcerikTuru.SES) {
                istek.addOption("--extract-audio")
                    .addOption("--audio-format", sesDonusumAdi(secenek.hedefUzanti))
                if (secenek.hedefUzanti in setOf("mp3", "ogg")) {
                    istek.addOption("--audio-quality", "0")
                }
            } else {
                if (seciliSesler.size > 1) istek.addOption("--audio-multistreams")
                if (seciliAltyazilar.isNotEmpty()) {
                    val normalVar = seciliAltyazilar.any { !it.otomatik }
                    val otomatikVar = seciliAltyazilar.any { it.otomatik }
                    if (normalVar) istek.addOption("--write-subs")
                    if (otomatikVar) istek.addOption("--write-auto-subs")
                    istek.addOption("--sub-langs", seciliAltyazilar.joinToString(",") { it.dilKodu })
                        .addOption("--convert-subs", "srt")
                        .addOption("--embed-subs")
                }
                istek.addOption("--merge-output-format", ciktiUzantisi)
            }

            YoutubeDL.getInstance().execute(istek, gorevKimligi) { ilerleme, eta, satir ->
                val hiz = HIZ_DESENI.find(satir)?.groupValues?.getOrNull(1).orEmpty()
                val kalan = if (eta > 0) "Kalan ${eta}s" else ""
                goreviGuncelle(gorevKimligi) {
                    it.copy(
                        durum = if (ilerleme >= 100f) IndirmeDurumu.ISLENIYOR else IndirmeDurumu.INDIRILIYOR,
                        ilerlemeYuzdesi = ilerleme.coerceIn(0f, 100f),
                        hizMetni = hiz,
                        kalanSureMetni = kalan,
                    )
                }
                IndirmeHizmeti.guncelle(uygulama, analiz.baslik, ilerleme.toInt())
            }

            val medyaDosyasi = geciciDizin.walkTopDown()
                .filter { it.isFile }
                .filterNot { it.extension.lowercase() in YAN_DOSYA_UZANTILARI }
                .maxByOrNull { it.length() }
                ?: error("İndirilen medya dosyası bulunamadı")

            val kutuphaneDizini = File(uygulama.filesDir, "kutuphane")
            val kapakDizini = File(kutuphaneDizini, "kapaklar").apply { mkdirs() }
            val kapakKaynak = geciciDizin.walkTopDown()
                .firstOrNull { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            val kapakHedef = kapakKaynak?.let {
                File(kapakDizini, "${analiz.medyaKimligi}.${it.extension.lowercase()}").also { hedef ->
                    it.copyTo(hedef, overwrite = true)
                }
            }

            val medyaKonumu: String
            val sifreliDosyaYolu: String
            val sifreli: Boolean
            val gercekBoyut: Long

            if (BuildConfig.DEVELOPER_SURUMU) {
                goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.DISARI_AKTARILIYOR, ilerlemeYuzdesi = 100f) }
                medyaKonumu = acikDepo.kaydet(medyaDosyasi, analiz.baslik, medyaDosyasi.extension.ifBlank { ciktiUzantisi })
                sifreliDosyaYolu = ""
                sifreli = false
                gercekBoyut = medyaDosyasi.length()
            } else {
                goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.SIFRELENIYOR, ilerlemeYuzdesi = 100f) }
                val medyaDizini = File(kutuphaneDizini, "medya").apply { mkdirs() }
                val sifreliDosya = File(medyaDizini, "${analiz.medyaKimligi}-${System.currentTimeMillis()}.ytdm")
                sifreliDepo.sifrele(medyaDosyasi, sifreliDosya)
                medyaKonumu = sifreliDosya.absolutePath
                sifreliDosyaYolu = sifreliDosya.absolutePath
                sifreli = true
                gercekBoyut = sifreliDosya.length()
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
                boyutBayt = gercekBoyut,
                cozunurluk = secenek.yukseklik?.let { "${it}p" },
                kareHizi = secenek.kareHizi,
                videoKodegi = secenek.videoKodegi,
                sesKodegi = secenek.sesKodegi,
                sesParcalari = seciliSesler.map { it.gorunenAd },
                altyaziParcalari = seciliAltyazilar.map { it.gorunenAd },
                indirilenTarihMillis = System.currentTimeMillis(),
            )

            val yeniKutuphane = listOf(kayit) + _durum.value.kutuphane.filterNot { it.medyaKimligi == kayit.medyaKimligi }
            katalog.kaydet(yeniKutuphane)
            goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.TAMAMLANDI, ilerlemeYuzdesi = 100f) }
            kutuphaneyiDurumaYaz(yeniKutuphane)
            bilgiGoster(
                if (BuildConfig.DEVELOPER_SURUMU) "“${analiz.baslik}” İndirilenler/Bmobil Developer klasörüne kaydedildi."
                else "“${analiz.baslik}” şifreli Bmobil kütüphanesine eklendi.",
            )
        } catch (_: YoutubeDL.CanceledException) {
            goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.IPTAL_EDILDI) }
        } catch (hata: Throwable) {
            goreviGuncelle(gorevKimligi) {
                it.copy(durum = IndirmeDurumu.HATA, hataMetni = hata.anlasilirMesaj())
            }
            hataGoster("İndirme başarısız: ${hata.anlasilirMesaj()}")
        } finally {
            geciciDizin.deleteRecursively()
            if (_durum.value.aktifIndirmeler.none { it.durum in AKTIF_DURUMLAR }) {
                IndirmeHizmeti.durdur(uygulama)
            }
        }
    }

    override fun indirmeyiIptalEt(gorevKimligi: String) {
        YoutubeDL.getInstance().destroyProcessById(gorevKimligi)
        goreviGuncelle(gorevKimligi) { it.copy(durum = IndirmeDurumu.IPTAL_EDILDI) }
    }

    override fun medyayiOynat(medyaKimligi: String) {
        val kayit = _durum.value.kutuphane.firstOrNull { it.medyaKimligi == medyaKimligi }
            ?: return hataGoster("Kütüphane kaydı bulunamadı.")

        bilgiGoster("“${kayit.baslik}” oynatmaya hazırlanıyor…")
        kapsam.launch {
            var olusturulanGeciciDizin: File? = null
            try {
                eskiOynatmaDosyalariniTemizle()

                val konum: String
                val gecici: Boolean
                if (kayit.sifreli) {
                    val sifreliDosya = File(kayit.etkinMedyaKonumu)
                    require(sifreliDosya.isFile && sifreliDosya.length() > 0L) {
                        "Şifreli medya dosyası bulunamadı veya boş"
                    }
                    val oynatmaKoku = File(uygulama.filesDir, OYNATMA_GECICI_KLASORU).apply { mkdirs() }
                    val oynatmaDizini = File(oynatmaKoku, UUID.randomUUID().toString()).apply { mkdirs() }
                    olusturulanGeciciDizin = oynatmaDizini
                    val uzanti = guvenliUzanti(kayit.asilUzanti)
                    val acikDosya = File(oynatmaDizini, "${kayit.medyaKimligi}.$uzanti")
                    sifreliDepo.coz(sifreliDosya, acikDosya)
                    require(acikDosya.isFile && acikDosya.length() > 0L) {
                        "Video çözüldü ancak oynatılabilir dosya oluşturulamadı"
                    }
                    konum = Uri.fromFile(acikDosya).toString()
                    gecici = true
                } else {
                    val hamKonum = kayit.etkinMedyaKonumu
                    require(hamKonum.isNotBlank()) { "Açık medya konumu bulunamadı" }
                    val uri = konumuUriyeCevir(hamKonum)
                    acikKaynakDogrula(uri)
                    konum = uri.toString()
                    gecici = false
                }

                val intent = Intent(uygulama, OynaticiEtkinligi::class.java)
                    .putExtra(OynaticiEtkinligi.EK_MEDYA_KONUMU, konum)
                    .putExtra(OynaticiEtkinligi.EK_BASLIK, kayit.baslik)
                    .putExtra(OynaticiEtkinligi.EK_GECICI_DOSYA, gecici)
                    .putExtra(OynaticiEtkinligi.EK_ASIL_UZANTI, guvenliUzanti(kayit.asilUzanti))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                withContext(Dispatchers.Main.immediate) {
                    uygulama.startActivity(intent)
                }
                bilgiGoster("Oynatıcı açıldı.")
            } catch (hata: Throwable) {
                olusturulanGeciciDizin?.deleteRecursively()
                hataGoster("İçerik oynatılamadı: ${hata.anlasilirMesaj()}")
            }
        }
    }

    private fun konumuUriyeCevir(konum: String): Uri {
        val ayrisitirilmis = Uri.parse(konum)
        if (!ayrisitirilmis.scheme.isNullOrBlank()) return ayrisitirilmis
        return Uri.fromFile(File(konum))
    }

    private fun acikKaynakDogrula(uri: Uri) {
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val dosya = uri.path?.let(::File)
                require(dosya?.isFile == true && dosya.length() > 0L) { "Açık medya dosyası bulunamadı veya boş" }
            }
            "content" -> {
                val erisilebilir = uygulama.contentResolver.openAssetFileDescriptor(uri, "r")?.use { tanimlayici ->
                    tanimlayici.length != 0L
                } == true
                require(erisilebilir) { "Android medya dosyasına erişim izni bulunamadı" }
            }
            else -> error("Desteklenmeyen medya konumu: ${uri.scheme ?: "şemasız"}")
        }
    }

    private fun eskiOynatmaDosyalariniTemizle() {
        val kok = File(uygulama.filesDir, OYNATMA_GECICI_KLASORU)
        if (!kok.isDirectory) return
        val simdi = System.currentTimeMillis()
        kok.listFiles().orEmpty()
            .filter { simdi - it.lastModified() > OYNATMA_GECICI_OMRU_MILLIS }
            .forEach(File::deleteRecursively)
    }

    private fun guvenliUzanti(uzanti: String): String {
        val temiz = uzanti.trim().removePrefix(".").lowercase()
        return temiz.takeIf { it.length in 2..8 && it.all(Char::isLetterOrDigit) } ?: "mkv"
    }

    override fun medyayiSil(medyaKimligi: String) {
        val kayit = _durum.value.kutuphane.firstOrNull { it.medyaKimligi == medyaKimligi } ?: return
        if (kayit.sifreli) File(kayit.etkinMedyaKonumu).delete() else acikDepo.sil(kayit.etkinMedyaKonumu)
        kayit.kapakDosyasi?.let { File(it).delete() }
        val yeni = _durum.value.kutuphane.filterNot { it.medyaKimligi == medyaKimligi }
        katalog.kaydet(yeni)
        kutuphaneyiDurumaYaz(yeni)
        bilgiGoster("İçerik kütüphaneden silindi.")
    }

    override fun sekmeSec(sekme: UygulamaSekmesi) {
        _durum.update { it.copy(seciliSekme = sekme) }
    }

    override fun turboProfiliSec(profil: TurboProfili) {
        _durum.update { it.copy(turboProfili = profil, bilgiMesaji = "${profil.gorunenAd} hız profili seçildi.") }
    }

    override fun diskSec(kokYolu: String) = Unit

    override fun kutuphaneyiYenile() {
        kapsam.launch { kutuphaneyiDurumaYaz(katalog.yukle()) }
    }

    override fun ytDlpGuncelle() {
        kapsam.launch {
            runCatching {
                uygulama.motoruHazirla()
                val sonuc = YoutubeDL.getInstance().updateYoutubeDL(uygulama, YoutubeDL.UpdateChannel._NIGHTLY)
                val surum = YoutubeDL.getInstance().versionName(uygulama)
                _durum.update { it.copy(ytDlpSurumu = surum, bilgiMesaji = "yt-dlp güncelleme sonucu: $sonuc") }
            }.onFailure { hata ->
                hataGoster("yt-dlp güncellenemedi: ${hata.anlasilirMesaj()}")
            }
        }
    }

    override fun mesajlariTemizle() {
        _durum.update { it.copy(bilgiMesaji = null, hataMesaji = null) }
    }

    private fun varsayilanSesKimlikleri(analiz: AnalizSonucu): Set<String> {
        val isaretli = analiz.sesParcalari.filter { it.varsayilan }.map { it.formatKimligi }.toSet()
        return if (isaretli.isNotEmpty()) isaretli else analiz.sesParcalari.firstOrNull()?.let { setOf(it.formatKimligi) }.orEmpty()
    }

    private fun seciliParcaSayisi(): Int {
        val profil = _durum.value.turboProfili
        if (profil != TurboProfili.OTOMATIK) return profil.parcaSayisi

        val bellekSinifi = (uygulama.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        if (bellekSinifi < 256) return 4

        val baglanti = uygulama.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val yetenekler = baglanti.getNetworkCapabilities(baglanti.activeNetwork)
        return when {
            yetenekler?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 16
            yetenekler?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 16
            yetenekler?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> 8
            else -> 8
        }
    }

    private fun kutuphaneyiDurumaYaz(kayitlar: List<KutuphaneKaydi>) {
        val kanallar = kayitlar
            .groupBy { it.kanalKimligi }
            .map { (kimlik, kanalKayitlari) ->
                val ilk = kanalKayitlari.first()
                KanalProfili(
                    kanalKimligi = kimlik,
                    kanalAdi = ilk.kanalAdi,
                    kullaniciAdi = ilk.kanalKullaniciAdi,
                    profilGorseli = ilk.kapakDosyasi,
                    indirilenIcerikSayisi = kanalKayitlari.size,
                    toplamBoyutBayt = kanalKayitlari.sumOf { it.boyutBayt },
                )
            }
            .sortedBy { it.kanalAdi.lowercase() }
        _durum.update {
            it.copy(
                kutuphane = kayitlar.sortedByDescending { kayit -> kayit.indirilenTarihMillis },
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

    private fun Throwable.anlasilirMesaj(): String =
        message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(500) ?: javaClass.simpleName

    companion object {
        private const val OYNATMA_GECICI_KLASORU = "oynatma-gecici"
        private const val OYNATMA_GECICI_OMRU_MILLIS = 24L * 60L * 60L * 1000L
        private val HIZ_DESENI = Regex("at\\s+([^\\s]+/s)")
        private val YAN_DOSYA_UZANTILARI = setOf(
            "json", "jpg", "jpeg", "png", "webp", "vtt", "srt", "ass", "lrc", "part", "ytdl", "description",
        )
        private val AKTIF_DURUMLAR = setOf(
            IndirmeDurumu.BEKLIYOR,
            IndirmeDurumu.INDIRILIYOR,
            IndirmeDurumu.ISLENIYOR,
            IndirmeDurumu.SIFRELENIYOR,
            IndirmeDurumu.DISARI_AKTARILIYOR,
        )
    }
}
