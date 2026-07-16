package tr.com.apexlions.ytdownloader.model

import kotlinx.coroutines.flow.StateFlow

interface UygulamaDenetleyicisi {
    val durum: StateFlow<UygulamaDurumu>

    fun baglantiyiDegistir(baglanti: String)
    fun analizEt()
    fun secenekSec(secenekKimligi: String)
    fun indirmeyiBaslat()
    fun indirmeyiIptalEt(gorevKimligi: String)
    fun medyayiOynat(medyaKimligi: String)
    fun medyayiSil(medyaKimligi: String)
    fun sekmeSec(sekme: UygulamaSekmesi)
    fun turboProfiliSec(profil: TurboProfili)
    fun diskSec(kokYolu: String)
    fun kutuphaneyiYenile()
    fun ytDlpGuncelle()
    fun mesajlariTemizle()
}
