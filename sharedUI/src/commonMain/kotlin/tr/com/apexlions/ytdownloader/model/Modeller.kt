package tr.com.apexlions.ytdownloader.model

import kotlinx.serialization.Serializable

@Serializable
data class PlatformBilgisi(
    val ad: String,
    val diskSecimiDestekleniyor: Boolean,
    val depolamaAciklamasi: String,
)

@Serializable
data class DepolamaHedefi(
    val kokYolu: String,
    val gorunenAd: String,
    val toplamBayt: Long,
    val kullanilabilirBayt: Long,
    val onerilen: Boolean = false,
)

@Serializable
enum class UygulamaSekmesi(val gorunenAd: String) {
    INDIR("İndir"),
    INDIRMELER("İndirmeler"),
    KUTUPHANE("Kütüphane"),
    AYARLAR("Ayarlar"),
}

@Serializable
enum class IcerikTuru {
    VIDEO,
    SES,
}

@Serializable
enum class IndirmeDurumu {
    BEKLIYOR,
    INDIRILIYOR,
    ISLENIYOR,
    SIFRELENIYOR,
    TAMAMLANDI,
    IPTAL_EDILDI,
    HATA,
}

@Serializable
enum class TurboProfili(val gorunenAd: String, val parcaSayisi: Int) {
    DENGELI("Dengeli", 4),
    HIZLI("Hızlı", 8),
    TURBO("Turbo", 12),
    AZAMI("Azami", 16),
    OTOMATIK("Otomatik", 0),
}

@Serializable
data class IndirmeSecenegi(
    val kimlik: String,
    val gorunenAd: String,
    val tur: IcerikTuru,
    val hedefUzanti: String,
    val ytDlpSecici: String,
    val yukseklik: Int? = null,
    val kareHizi: Int? = null,
    val videoKodegi: String? = null,
    val sesKodegi: String? = null,
    val sesBitHiziKbps: Int? = null,
    val tahminiBoyutBayt: Long? = null,
    val donusturmeGerekli: Boolean = false,
)

@Serializable
data class AnalizSonucu(
    val kaynakAdresi: String,
    val medyaKimligi: String,
    val baslik: String,
    val aciklama: String? = null,
    val kanalKimligi: String,
    val kanalAdi: String,
    val kanalKullaniciAdi: String? = null,
    val kapakAdresi: String? = null,
    val sureSaniye: Long = 0,
    val yayinTarihi: String? = null,
    val secenekler: List<IndirmeSecenegi> = emptyList(),
)

@Serializable
data class IndirmeGorevi(
    val gorevKimligi: String,
    val medyaKimligi: String,
    val baslik: String,
    val kanalAdi: String,
    val secenekAdi: String,
    val durum: IndirmeDurumu,
    val ilerlemeYuzdesi: Float = 0f,
    val hizMetni: String = "",
    val kalanSureMetni: String = "",
    val hataMetni: String? = null,
)

@Serializable
data class KutuphaneKaydi(
    val medyaKimligi: String,
    val kaynakAdresi: String,
    val baslik: String,
    val kanalKimligi: String,
    val kanalAdi: String,
    val kanalKullaniciAdi: String? = null,
    val kapakDosyasi: String? = null,
    val sifreliMedyaDosyasi: String,
    val asilUzanti: String,
    val sureSaniye: Long,
    val boyutBayt: Long,
    val cozunurluk: String? = null,
    val kareHizi: Int? = null,
    val videoKodegi: String? = null,
    val sesKodegi: String? = null,
    val indirilenTarihMillis: Long,
    val sonKonumMillis: Long = 0,
)

@Serializable
data class KanalProfili(
    val kanalKimligi: String,
    val kanalAdi: String,
    val kullaniciAdi: String? = null,
    val profilGorseli: String? = null,
    val indirilenIcerikSayisi: Int,
    val toplamBoyutBayt: Long,
)

@Serializable
data class KatalogDosyasi(
    val surum: Int = 1,
    val kayitlar: List<KutuphaneKaydi> = emptyList(),
)

@Serializable
data class UygulamaDurumu(
    val seciliSekme: UygulamaSekmesi = UygulamaSekmesi.INDIR,
    val baglanti: String = "",
    val analizEdiliyor: Boolean = false,
    val analizSonucu: AnalizSonucu? = null,
    val seciliSecenekKimligi: String? = null,
    val aktifIndirmeler: List<IndirmeGorevi> = emptyList(),
    val kutuphane: List<KutuphaneKaydi> = emptyList(),
    val kanallar: List<KanalProfili> = emptyList(),
    val diskler: List<DepolamaHedefi> = emptyList(),
    val seciliDiskYolu: String? = null,
    val turboProfili: TurboProfili = TurboProfili.OTOMATIK,
    val ytDlpSurumu: String? = null,
    val bilgiMesaji: String? = null,
    val hataMesaji: String? = null,
)
