package tr.com.apexlions.ytdownloader.model

data class PlatformBilgisi(
    val ad: String,
    val diskSecimiDestekleniyor: Boolean,
    val depolamaAciklamasi: String,
)

data class DepolamaHedefi(
    val kokYolu: String,
    val gorunenAd: String,
    val toplamBayt: Long,
    val kullanilabilirBayt: Long,
    val onerilen: Boolean = false,
)

data class KanalProfili(
    val kanalKimligi: String,
    val kanalAdi: String,
    val kullaniciAdi: String?,
    val profilGorseli: String?,
    val indirilenIcerikSayisi: Int,
    val toplamBoyutBayt: Long,
)

data class MedyaKaydi(
    val medyaKimligi: String,
    val kanalKimligi: String,
    val baslik: String,
    val kapakGorseli: String?,
    val sureSaniye: Long,
    val boyutBayt: Long,
    val cozunurluk: String,
    val kareHizi: Int?,
    val videoKodegi: String?,
    val sesKodegi: String?,
)

enum class TurboProfili(val gorunenAd: String, val parcaSayisi: Int) {
    DENGELI("Dengeli", 4),
    HIZLI("Hızlı", 8),
    TURBO("Turbo", 12),
    AZAMI("Azami", 16),
}
