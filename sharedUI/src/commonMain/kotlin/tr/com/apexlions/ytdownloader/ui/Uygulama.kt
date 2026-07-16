package tr.com.apexlions.ytdownloader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.com.apexlions.ytdownloader.model.AltyaziSecenegi
import tr.com.apexlions.ytdownloader.model.AnalizSonucu
import tr.com.apexlions.ytdownloader.model.DepolamaHedefi
import tr.com.apexlions.ytdownloader.model.IndirmeDurumu
import tr.com.apexlions.ytdownloader.model.IndirmeGorevi
import tr.com.apexlions.ytdownloader.model.IndirmeSecenegi
import tr.com.apexlions.ytdownloader.model.IcerikTuru
import tr.com.apexlions.ytdownloader.model.KanalProfili
import tr.com.apexlions.ytdownloader.model.KutuphaneKaydi
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.model.SesParcasiSecenegi
import tr.com.apexlions.ytdownloader.model.TurboProfili
import tr.com.apexlions.ytdownloader.model.UygulamaDenetleyicisi
import tr.com.apexlions.ytdownloader.model.UygulamaSekmesi
import tr.com.apexlions.ytdownloader.util.okunabilirBoyut

private val Kirmizi = Color(0xFFE53935)
private val KoyuZemin = Color(0xFF0D0F14)
private val KartZemini = Color(0xFF171A21)
private val IkincilMetin = Color(0xFFADB3C0)
private val Basari = Color(0xFF45D483)
private val Uyari = Color(0xFFFFD54F)

@Composable
fun YTIndiriciUygulamasi(
    denetleyici: UygulamaDenetleyicisi,
    platform: PlatformBilgisi,
    ilkBaglanti: String = "",
) {
    val durum by denetleyici.durum.collectAsState()

    LaunchedEffect(ilkBaglanti) {
        if (ilkBaglanti.isNotBlank() && durum.baglanti.isBlank()) {
            denetleyici.baglantiyiDegistir(ilkBaglanti)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Kirmizi,
            onPrimary = Color.White,
            background = KoyuZemin,
            surface = KartZemini,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = KoyuZemin) {
            Column(Modifier.fillMaxSize()) {
                UstAlan(platform, durum.ytDlpSurumu)
                Sekmeler(durum.seciliSekme, denetleyici::sekmeSec)

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (durum.seciliSekme) {
                        UygulamaSekmesi.INDIR -> IndirEkrani(
                            baglanti = durum.baglanti,
                            analizEdiliyor = durum.analizEdiliyor,
                            analiz = durum.analizSonucu,
                            seciliSecenekKimligi = durum.seciliSecenekKimligi,
                            seciliSesler = durum.seciliSesParcasiKimlikleri,
                            seciliAltyazilar = durum.seciliAltyaziDilleri,
                            baglantiDegisti = denetleyici::baglantiyiDegistir,
                            analizEt = denetleyici::analizEt,
                            secenekSec = denetleyici::secenekSec,
                            sesSec = denetleyici::sesParcasiSec,
                            altyaziSec = denetleyici::altyaziSec,
                            indir = denetleyici::indirmeyiBaslat,
                        )
                        UygulamaSekmesi.INDIRMELER -> IndirmelerEkrani(
                            gorevler = durum.aktifIndirmeler,
                            iptalEt = denetleyici::indirmeyiIptalEt,
                        )
                        UygulamaSekmesi.KUTUPHANE -> KutuphaneEkrani(
                            kanallar = durum.kanallar,
                            kayitlar = durum.kutuphane,
                            oynat = denetleyici::medyayiOynat,
                            sil = denetleyici::medyayiSil,
                        )
                        UygulamaSekmesi.AYARLAR -> AyarlarEkrani(
                            platform = platform,
                            diskler = durum.diskler,
                            seciliDiskYolu = durum.seciliDiskYolu,
                            turboProfili = durum.turboProfili,
                            diskSec = denetleyici::diskSec,
                            turboSec = denetleyici::turboProfiliSec,
                            ytDlpGuncelle = denetleyici::ytDlpGuncelle,
                        )
                    }
                }

                durum.hataMesaji?.let { MesajKarti(it, true, denetleyici::mesajlariTemizle) }
                durum.bilgiMesaji?.let { MesajKarti(it, false, denetleyici::mesajlariTemizle) }
            }
        }
    }
}

@Composable
private fun UstAlan(platform: PlatformBilgisi, ytDlpSurumu: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HizliOynatIkonu()
        Column(Modifier.weight(1f)) {
            Text(platform.ad, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (platform.developerSurumu) "Developer • Açık dosya • Tamamen Türkçe" else "Şifreli kütüphane • Tamamen Türkçe",
                color = if (platform.developerSurumu) Uyari else IkincilMetin,
                fontSize = 12.sp,
            )
        }
        Text(
            ytDlpSurumu?.let { "yt-dlp $it" } ?: "Motor hazırlanıyor",
            color = if (ytDlpSurumu == null) Uyari else Basari,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Sekmeler(secili: UygulamaSekmesi, sec: (UygulamaSekmesi) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        UygulamaSekmesi.entries.forEach { sekme ->
            FilterChip(
                selected = sekme == secili,
                onClick = { sec(sekme) },
                label = { Text(sekme.gorunenAd, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun IndirEkrani(
    baglanti: String,
    analizEdiliyor: Boolean,
    analiz: AnalizSonucu?,
    seciliSecenekKimligi: String?,
    seciliSesler: Set<String>,
    seciliAltyazilar: Set<String>,
    baglantiDegisti: (String) -> Unit,
    analizEt: () -> Unit,
    secenekSec: (String) -> Unit,
    sesSec: (String, Boolean) -> Unit,
    altyaziSec: (String, Boolean) -> Unit,
    indir: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bağlantıyı yapıştır", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Yalnızca indirme hakkına sahip olduğun içerikleri kullan.", color = IkincilMetin, fontSize = 12.sp)
                    OutlinedTextField(
                        value = baglanti,
                        onValueChange = baglantiDegisti,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Button(
                        onClick = analizEt,
                        enabled = baglanti.isNotBlank() && !analizEdiliyor,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (analizEdiliyor) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text("İçerik analiz ediliyor")
                        } else {
                            Text("İçeriği analiz et", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        analiz?.let { sonuc ->
            item { AnalizKarti(sonuc) }
            val videoSecenekleri = sonuc.secenekler.filter { it.tur == IcerikTuru.VIDEO }
            val sesSecenekleri = sonuc.secenekler.filter { it.tur == IcerikTuru.SES }
            val videoSecili = videoSecenekleri.any { it.kimlik == seciliSecenekKimligi }

            if (videoSecenekleri.isNotEmpty()) {
                item { BolumBasligi("Video seçenekleri", "${videoSecenekleri.size} seçenek") }
                items(videoSecenekleri, key = { it.kimlik }) { secenek ->
                    SecenekKarti(secenek, secenek.kimlik == seciliSecenekKimligi) { secenekSec(secenek.kimlik) }
                }
            }

            if (videoSecili && sonuc.sesParcalari.isNotEmpty()) {
                item { EkParcaBilgisi() }
                item { BolumBasligi("Dublaj ve ses parçaları", "${seciliSesler.size} seçili") }
                items(sonuc.sesParcalari, key = { "ses-${it.formatKimligi}" }) { parca ->
                    SesParcasiKarti(parca, parca.formatKimligi in seciliSesler) { secili ->
                        sesSec(parca.formatKimligi, secili)
                    }
                }
            }

            if (videoSecili && sonuc.altyazilar.isNotEmpty()) {
                item { BolumBasligi("Altyazılar", "${seciliAltyazilar.size} seçili") }
                items(sonuc.altyazilar, key = { "altyazi-${it.dilKodu}-${it.otomatik}" }) { altyazi ->
                    AltyaziKarti(altyazi, altyazi.dilKodu in seciliAltyazilar) { secili ->
                        altyaziSec(altyazi.dilKodu, secili)
                    }
                }
            }

            if (sesSecenekleri.isNotEmpty()) {
                item { BolumBasligi("Yalnızca ses seçenekleri", "${sesSecenekleri.size} seçenek") }
                items(sesSecenekleri, key = { it.kimlik }) { secenek ->
                    SecenekKarti(secenek, secenek.kimlik == seciliSecenekKimligi) { secenekSec(secenek.kimlik) }
                }
            }

            item {
                Button(
                    onClick = indir,
                    enabled = seciliSecenekKimligi != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Turbo indirmeyi başlat", fontWeight = FontWeight.ExtraBold) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AnalizKarti(sonuc: AnalizSonucu) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111F2A)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sonuc.baslik, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(sonuc.kanalAdi, color = Basari, fontWeight = FontWeight.Bold)
            Text("${sureMetni(sonuc.sureSaniye)} • ${sonuc.yayinTarihi ?: "Tarih bilinmiyor"}", color = IkincilMetin, fontSize = 12.sp)
            Text(
                "${sonuc.sesParcalari.size} ses parçası • ${sonuc.altyazilar.size} altyazı dili bulundu",
                color = IkincilMetin,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EkParcaBilgisi() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2410)), shape = RoundedCornerShape(16.dp)) {
        Text(
            "Seçtiğin ses ve altyazılar videoya gömülür. Oynatıcıdaki Ses ve Altyazı düğmelerinden anında değiştirebilirsin. Birden fazla parça seçildiğinde çıktı MKV olur.",
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            color = Uyari,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SesParcasiKarti(parca: SesParcasiSecenegi, secili: Boolean, degistir: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { degistir(!secili) },
        colors = CardDefaults.cardColors(containerColor = if (secili) Color(0xFF152E22) else KartZemini),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(parca.gorunenAd, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(parca.dilKodu, parca.kodek, parca.bitHiziKbps?.let { "$it kbps" }).joinToString(" • ").ifBlank { "Ses parçası" },
                    color = IkincilMetin,
                    fontSize = 11.sp,
                )
            }
            Text(if (secili) "İndirilecek" else "Ekle", color = if (secili) Basari else IkincilMetin)
        }
    }
}

@Composable
private fun AltyaziKarti(altyazi: AltyaziSecenegi, secili: Boolean, degistir: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { degistir(!secili) },
        colors = CardDefaults.cardColors(containerColor = if (secili) Color(0xFF1A2940) else KartZemini),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(altyazi.gorunenAd, fontWeight = FontWeight.Bold)
                Text(if (altyazi.otomatik) "Otomatik oluşturulan altyazı" else "Yayıncı altyazısı", color = IkincilMetin, fontSize = 11.sp)
            }
            Text(if (secili) "İndirilecek" else "Ekle", color = if (secili) Basari else IkincilMetin)
        }
    }
}

@Composable
private fun SecenekKarti(secenek: IndirmeSecenegi, secili: Boolean, sec: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = sec),
        colors = CardDefaults.cardColors(containerColor = if (secili) Color(0xFF3A1B1B) else KartZemini),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).background(if (secenek.tur == IcerikTuru.VIDEO) Kirmizi else Color(0xFF5C6BC0), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (secenek.tur == IcerikTuru.VIDEO) "V" else "S", fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                Text(secenek.gorunenAd, fontWeight = FontWeight.Bold)
                val ayrinti = buildList {
                    secenek.videoKodegi?.takeIf { it != "none" }?.let(::add)
                    secenek.sesKodegi?.takeIf { it != "none" }?.let(::add)
                    secenek.tahminiBoyutBayt?.takeIf { it > 0 }?.let { add(it.okunabilirBoyut()) }
                }.joinToString(" • ")
                Text(ayrinti.ifBlank { "Boyut indirme sırasında kesinleşir" }, color = IkincilMetin, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (secili) "Seçildi" else "Seç", color = if (secili) Kirmizi else IkincilMetin)
        }
    }
}

@Composable
private fun IndirmelerEkrani(gorevler: List<IndirmeGorevi>, iptalEt: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BolumBasligi("İndirmeler", "${gorevler.size} görev") }
        if (gorevler.isEmpty()) item { BosDurum("Henüz indirme yok", "Başlattığın indirmeler burada canlı olarak görünecek.") }
        items(gorevler, key = { it.gorevKimligi }) { gorev -> IndirmeKarti(gorev, iptalEt) }
    }
}

@Composable
private fun IndirmeKarti(gorev: IndirmeGorevi, iptalEt: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(gorev.baslik, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${gorev.kanalAdi} • ${gorev.secenekAdi}", color = IkincilMetin, fontSize = 12.sp)
            LinearProgressIndicator(progress = { (gorev.ilerlemeYuzdesi / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${gorev.ilerlemeYuzdesi.toInt()}% • ${durumMetni(gorev.durum)}", color = Basari, fontSize = 12.sp)
                Text(listOf(gorev.hizMetni, gorev.kalanSureMetni).filter { it.isNotBlank() }.joinToString(" • "), color = IkincilMetin, fontSize = 12.sp)
            }
            if (gorev.durum in listOf(IndirmeDurumu.BEKLIYOR, IndirmeDurumu.INDIRILIYOR, IndirmeDurumu.ISLENIYOR)) {
                TextButton(onClick = { iptalEt(gorev.gorevKimligi) }) { Text("İndirmeyi iptal et") }
            }
            gorev.hataMetni?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun KutuphaneEkrani(
    kanallar: List<KanalProfili>,
    kayitlar: List<KutuphaneKaydi>,
    oynat: (String) -> Unit,
    sil: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BolumBasligi("Kanal profilleri", "${kanallar.size} kanal") }
        if (kayitlar.isEmpty()) item { BosDurum("Kütüphane boş", "İndirdiğin içerikler kanal profillerine ayrılarak burada tutulacak.") }
        kanallar.forEach { kanal ->
            item(key = "kanal-${kanal.kanalKimligi}") { KanalKarti(kanal) }
            kayitlar.filter { it.kanalKimligi == kanal.kanalKimligi }.forEach { kayit ->
                item(key = "medya-${kayit.medyaKimligi}") { KutuphaneKarti(kayit, oynat, sil) }
            }
        }
    }
}

@Composable
private fun KanalKarti(kanal: KanalProfili) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10251A)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(Basari, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Text(kanal.kanalAdi.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text(kanal.kanalAdi, fontWeight = FontWeight.ExtraBold)
                Text("${kanal.indirilenIcerikSayisi} içerik • ${kanal.toplamBoyutBayt.okunabilirBoyut()}", color = IkincilMetin, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun KutuphaneKarti(kayit: KutuphaneKaydi, oynat: (String) -> Unit, sil: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kayit.baslik, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${sureMetni(kayit.sureSaniye)} • ${kayit.boyutBayt.okunabilirBoyut()} • ${kayit.cozunurluk ?: kayit.asilUzanti.uppercase()}",
                color = IkincilMetin,
                fontSize = 11.sp,
            )
            if (kayit.sesParcalari.isNotEmpty() || kayit.altyaziParcalari.isNotEmpty()) {
                Text(
                    "${kayit.sesParcalari.size} ses • ${kayit.altyaziParcalari.size} altyazı • ${if (kayit.sifreli) "Şifreli" else "Açık dosya"}",
                    color = if (kayit.sifreli) Basari else Uyari,
                    fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { oynat(kayit.medyaKimligi) }) { Text("Oynat") }
                OutlinedButton(onClick = { sil(kayit.medyaKimligi) }) { Text("Sil") }
            }
        }
    }
}

@Composable
private fun AyarlarEkrani(
    platform: PlatformBilgisi,
    diskler: List<DepolamaHedefi>,
    seciliDiskYolu: String?,
    turboProfili: TurboProfili,
    diskSec: (String) -> Unit,
    turboSec: (TurboProfili) -> Unit,
    ytDlpGuncelle: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BolumBasligi("Turbo motor", "Hız öncelikli") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Eşzamanlı parça profili", fontWeight = FontWeight.Bold)
                    Text("Otomatik profil bağlantı ve cihaz durumuna göre 4–16 parça seçer.", color = IkincilMetin, fontSize = 12.sp)
                    TurboProfili.entries.forEach { profil ->
                        FilterChip(
                            selected = profil == turboProfili,
                            onClick = { turboSec(profil) },
                            label = { Text(if (profil.parcaSayisi == 0) profil.gorunenAd else "${profil.gorunenAd} • ${profil.parcaSayisi} parça") },
                        )
                    }
                }
            }
        }
        item { BolumBasligi("Depolama", platform.ad) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(platform.depolamaAciklamasi, color = IkincilMetin, fontSize = 12.sp)
                    if (platform.diskSecimiDestekleniyor) {
                        diskler.forEach { disk -> DiskKarti(disk, disk.kokYolu == seciliDiskYolu) { diskSec(disk.kokYolu) } }
                    } else {
                        Text(
                            if (platform.developerSurumu) "Medya, cihazın İndirilenler/Bmobil Developer klasörüne açık dosya olarak yazılır."
                            else "Medya normal İndirilenler klasörüne çıkmaz; uygulamanın özel ve şifreli kütüphanesinde tutulur.",
                            color = if (platform.developerSurumu) Uyari else Basari,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        item { BolumBasligi("Motor güncellemesi", "Nightly kanal") }
        item { Button(onClick = ytDlpGuncelle, modifier = Modifier.fillMaxWidth()) { Text("yt-dlp güncellemesini şimdi denetle") } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DiskKarti(disk: DepolamaHedefi, secili: Boolean, sec: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = sec),
        colors = CardDefaults.cardColors(containerColor = if (secili) Color(0xFF3A1B1B) else Color(0xFF20242D)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(disk.gorunenAd, fontWeight = FontWeight.Bold)
                Text("${disk.kullanilabilirBayt.okunabilirBoyut()} boş / ${disk.toplamBayt.okunabilirBoyut()}", color = IkincilMetin, fontSize = 11.sp)
            }
            Text(if (secili) "Seçildi" else "Seç", color = if (secili) Kirmizi else IkincilMetin)
        }
    }
}

@Composable
private fun BolumBasligi(baslik: String, sagMetin: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(baslik, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(sagMetin, color = IkincilMetin, fontSize = 11.sp)
    }
}

@Composable
private fun BosDurum(baslik: String, aciklama: String) {
    Card(colors = CardDefaults.cardColors(containerColor = KartZemini), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(baslik, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(aciklama, color = IkincilMetin, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MesajKarti(metin: String, hata: Boolean, kapat: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (hata) Color(0xFF4A1717) else Color(0xFF12351F)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metin, modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = kapat) { Text("Kapat") }
        }
    }
}

@Composable
private fun HizliOynatIkonu() {
    Canvas(Modifier.size(50.dp)) {
        drawRoundRect(Kirmizi, cornerRadius = CornerRadius(size.width * .25f, size.height * .25f))
        drawPath(
            Path().apply {
                moveTo(size.width * .32f, size.height * .24f)
                lineTo(size.width * .72f, size.height * .50f)
                lineTo(size.width * .32f, size.height * .76f)
                close()
            },
            Color.White,
        )
        drawPath(
            Path().apply {
                moveTo(size.width * .60f, size.height * .12f)
                lineTo(size.width * .49f, size.height * .42f)
                lineTo(size.width * .62f, size.height * .42f)
                lineTo(size.width * .50f, size.height * .75f)
                lineTo(size.width * .78f, size.height * .36f)
                lineTo(size.width * .64f, size.height * .36f)
                close()
            },
            Uyari,
        )
    }
}

private fun durumMetni(durum: IndirmeDurumu): String = when (durum) {
    IndirmeDurumu.BEKLIYOR -> "Bekliyor"
    IndirmeDurumu.INDIRILIYOR -> "İndiriliyor"
    IndirmeDurumu.ISLENIYOR -> "Birleştiriliyor"
    IndirmeDurumu.SIFRELENIYOR -> "Şifreleniyor"
    IndirmeDurumu.DISARI_AKTARILIYOR -> "Dış depolamaya aktarılıyor"
    IndirmeDurumu.TAMAMLANDI -> "Tamamlandı"
    IndirmeDurumu.IPTAL_EDILDI -> "İptal edildi"
    IndirmeDurumu.HATA -> "Hata"
}

private fun sureMetni(saniye: Long): String {
    if (saniye <= 0) return "Süre bilinmiyor"
    val saat = saniye / 3600
    val dakika = (saniye % 3600) / 60
    val kalan = saniye % 60
    return if (saat > 0) "%d:%02d:%02d".format(saat, dakika, kalan) else "%02d:%02d".format(dakika, kalan)
}