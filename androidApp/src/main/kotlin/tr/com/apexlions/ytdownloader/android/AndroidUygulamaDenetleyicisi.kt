package tr.com.apexlions.ytdownloader.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import kotlin.math.max

class AndroidUygulamaDenetleyicisi(
    private val uygulama: YTIndiriciUygulamasi,
) : UygulamaDenetleyicisi {
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val katalog = AndroidKatalogDeposu(uygulama)
    private val sifreliDepo = SifreliMedyaDeposu()
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
                    .addOption("--no-playlist")
                    .addOption("--no-warnings")
                    .addOption("--socket-timeout", 30)
                val bilgi = YoutubeDL.getInstance().getInfo(istek)
                val secenekler = secenekleriOlustur(bilgi.formats.orEmpty(), bilgi.duration.toLong())
                val sonuc = AnalizSonucu(
                    kaynakAdresi = adres,
                    medyaKimligi = bilgi.id?.ifBlank { null } ?: UUID.randomUUID().toString(),
                    baslik = bilgi.title?.ifBlank { null } ?: "Başlıksız içerik",
                    aciklama = bilgi.description,
                    kanalKimligi = bilgi.uploaderId?.ifBlank { null } ?: bilgi.uploader.orEmpty().ifBlank { "bilinmeyen-kanal" },
                    kanalAdi = bilgi.uploader?.ifBlank { null } ?: "Bilinmeyen kanal",
                    kanalKullaniciAdi = bilgi.uploaderId,
                    kapakAdresi = bilgi.thumbnail,
                    sureSaniye = bilgi.duration.toLong(),
                    yayinTarihi = bilgi.uploadDate?.tarihBicimineCevir(),
                    secenekler = secenekler,
                )
                val varsayilan = secenekler.firstOrNull { it.kimlik == "video-en-hizli" }
                    ?: secenekler.firstOrNull { it.tur == IcerikTuru.VIDEO }
                    ?: secenekler.firstOrNull()

                _durum.update {
                    it.copy(
                        analizEdiliyor = false,
                        analizSonucu = sonuc,
                        seciliSecenekKimligi = varsayilan?.kimlik,
                        bilgiMesaji = "${secenekler.size} indirme seçeneği bulundu.",
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

            val parcaSayisi = seciliParcaSayisi()
            val ciktiKalibi = File(geciciDizin, "%(id)s.%(ext)s").absolutePath
            val istek = YoutubeDLRequest(analiz.kaynakAdresi)
                .addOption("--no-playlist")
                .addOption("--no-warnings")
                .addOption("--newline")
                .addOption("--continue")
                .addOption("--retries", 10)
                .addOption("--fragment-retries", 10)
                .addOption("--concurrent-fragments", parcaSayisi)
                .addOption("--write-info-json")
                .addOption("--write-thumbnail")
                .addOption("--convert-thumbnails", "jpg")
                .addOption("--add-metadata")
                .addOption("-o", ciktiKalibi)
                .addOption("-f", secenek.ytDlpSecici)

            if (secenek.tur == IcerikTuru.SES) {
                istek.addOption("--extract-audio")
                    .addOption("--audio-format", secenek.hedefUzanti)
                if (secenek.hedefUzanti in setOf("mp3", "ogg")) {
                    istek.addOption("--audio-quality", "0")
                }
            } else {
                istek.addOption("--merge-output-format", secenek.hedefUzanti)
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

            goreviGuncelle(gorevKimligi) {
                it.copy(durum = IndirmeDurumu.SIFRELENIYOR, ilerlemeYuzdesi = 100f, hizMetni = "", kalanSureMetni = "")
            }

            val medyaDosyasi = geciciDizin.walkTopDown()
                .filter { it.isFile }
                .filterNot { it.extension.lowercase() in METADATA_UZANTILARI }
                .maxByOrNull { it.length() }
                ?: error("İndirilen medya dosyası bulunamadı")

            val kutuphaneDizini = File(uygulama.filesDir, "kutuphane")
            val medyaDizini = File(kutuphaneDizini, "medya").apply { mkdirs() }
            val kapakDizini = File(kutuphaneDizini, "kapaklar").apply { mkdirs() }
            val sifreliDosya = File(medyaDizini, "${analiz.medyaKimligi}-${System.currentTimeMillis()}.ytdm")
            sifreliDepo.sifrele(medyaDosyasi, sifreliDosya)

            val kapakKaynak = geciciDizin.walkTopDown()
                .firstOrNull { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            val kapakHedef = kapakKaynak?.let {
                File(kapakDizini, "${analiz.medyaKimligi}.${it.extension.lowercase()}").also { hedef ->
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

            val yeniKutuphane = listOf(kayit) + _durum.value.kutuphane.filterNot { it.medyaKimligi == kayit.medyaKimligi }
            katalog.kaydet(yeniKutuphane)
            goreviGuncelle(gorevKimligi) {
                it.copy(durum = IndirmeDurumu.TAMAMLANDI, ilerlemeYuzdesi = 100f)
            }
            kutuphaneyiDurumaYaz(yeniKutuphane)
            bilgiGoster("“${analiz.baslik}” şifreli kütüphaneye eklendi.")
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

        kapsam.launch {
            runCatching {
                val sifreli = File(kayit.sifreliMedyaDosyasi)
                require(sifreli.isFile) { "Şifreli medya dosyası bulunamadı" }
                val gecici = File(uygulama.cacheDir, "oynatma/${kayit.medyaKimligi}.${kayit.asilUzanti}")
                sifreliDepo.coz(sifreli, gecici)
                val intent = Intent(uygulama, OynaticiEtkinligi::class.java)
                    .putExtra(OynaticiEtkinligi.EK_DOSYA_YOLU, gecici.absolutePath)
                    .putExtra(OynaticiEtkinligi.EK_BASLIK, kayit.baslik)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                uygulama.startActivity(intent)
            }.onFailure { hata ->
                hataGoster("İçerik oynatılamadı: ${hata.anlasilirMesaj()}")
            }
        }
    }

    override fun medyayiSil(medyaKimligi: String) {
        val kayit = _durum.value.kutuphane.firstOrNull { it.medyaKimligi == medyaKimligi } ?: return
        File(kayit.sifreliMedyaDosyasi).delete()
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

    private fun secenekleriOlustur(formatlar: List<VideoFormat>, sureSaniye: Long): List<IndirmeSecenegi> {
        val sesBoyutu = formatlar
            .filter { it.acodec != null && it.acodec != "none" && (it.vcodec == null || it.vcodec == "none") }
            .maxOfOrNull { max(it.fileSize, it.fileSizeApproximate) }
            ?: 0L

        val videoSecenekleri = formatlar
            .filter { it.height > 0 && it.vcodec != null && it.vcodec != "none" }
            .groupBy { it.height to it.fps.coerceAtLeast(0) }
            .mapNotNull { (_, adaylar) ->
                val secilen = adaylar.maxByOrNull { max(max(it.fileSize, it.fileSizeApproximate), it.tbr.toLong()) } ?: return@mapNotNull null
                val fps = secilen.fps.takeIf { it > 0 }
                val hedef = if (secilen.ext == "mp4" && (secilen.vcodec?.startsWith("avc") == true || secilen.vcodec?.startsWith("h264") == true)) "mp4" else "mkv"
                val boyut = max(secilen.fileSize, secilen.fileSizeApproximate).takeIf { it > 0 }?.plus(sesBoyutu)
                IndirmeSecenegi(
                    kimlik = "video-${secilen.formatId}-${hedef}",
                    gorunenAd = buildString {
                        append("${secilen.height}p")
                        fps?.let { append(" • ${it} FPS") }
                        append(" • ${hedef.uppercase()}")
                    },
                    tur = IcerikTuru.VIDEO,
                    hedefUzanti = hedef,
                    ytDlpSecici = "${secilen.formatId}+bestaudio/best",
                    yukseklik = secilen.height,
                    kareHizi = fps,
                    videoKodegi = secilen.vcodec,
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

    private fun sesSecenegi(
        uzanti: String,
        ad: String,
        bitHizi: Int,
        sureSaniye: Long,
        donusturme: Boolean,
    ) = IndirmeSecenegi(
        kimlik = "ses-$uzanti",
        gorunenAd = ad,
        tur = IcerikTuru.SES,
        hedefUzanti = uzanti,
        ytDlpSecici = if (uzanti == "m4a") "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best",
        sesKodegi = uzanti.uppercase(),
        sesBitHiziKbps = bitHizi,
        tahminiBoyutBayt = if (sureSaniye > 0) sureSaniye * bitHizi * 1000L / 8L else null,
        donusturmeGerekli = donusturme,
    )

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
        _durum.update { it.copy(kutuphane = kayitlar.sortedByDescending { kayit -> kayit.indirilenTarihMillis }, kanallar = kanallar) }
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

    private fun String.tarihBicimineCevir(): String =
        if (length == 8 && all(Char::isDigit)) "${substring(0, 4)}-${substring(4, 6)}-${substring(6, 8)}" else this

    private fun Throwable.anlasilirMesaj(): String =
        message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(260) ?: javaClass.simpleName

    companion object {
        private val METADATA_UZANTILARI = setOf("json", "jpg", "jpeg", "png", "webp", "part", "ytdm")
        private val AKTIF_DURUMLAR = setOf(
            IndirmeDurumu.BEKLIYOR,
            IndirmeDurumu.INDIRILIYOR,
            IndirmeDurumu.ISLENIYOR,
            IndirmeDurumu.SIFRELENIYOR,
        )
        private val HIZ_DESENI = Regex("""at\s+([^\s]+/s)""")
    }
}
