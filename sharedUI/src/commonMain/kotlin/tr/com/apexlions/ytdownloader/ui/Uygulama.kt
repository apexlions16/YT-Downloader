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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.com.apexlions.ytdownloader.model.DepolamaHedefi
import tr.com.apexlions.ytdownloader.model.PlatformBilgisi
import tr.com.apexlions.ytdownloader.util.okunabilirBoyut

private val Kirmizi = Color(0xFFE53935)
private val KoyuZemin = Color(0xFF0D0F14)
private val KartZemini = Color(0xFF171A21)
private val IkincilMetin = Color(0xFFADB3C0)
private val Basari = Color(0xFF45D483)

@Composable
fun YTIndiriciUygulamasi(
    platform: PlatformBilgisi,
    diskler: List<DepolamaHedefi> = emptyList(),
    seciliDiskYolu: String? = null,
    ilkBaglanti: String = "",
    diskSecildi: (String) -> Unit = {},
) {
    var baglanti by remember(ilkBaglanti) { mutableStateOf(ilkBaglanti) }

    MaterialTheme(colorScheme = uygulamaRenkleri()) {
        Surface(modifier = Modifier.fillMaxSize(), color = KoyuZemin) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { UstAlan(platform.ad) }
                item {
                    BaglantiKarti(
                        baglanti = baglanti,
                        baglantiDegisti = { baglanti = it },
                    )
                }
                item { HizDurumuKarti() }
                item {
                    DepolamaKarti(
                        platform = platform,
                        diskler = diskler,
                        seciliDiskYolu = seciliDiskYolu,
                        diskSecildi = diskSecildi,
                    )
                }
                item { Text("Kütüphanen", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                item { BilgiKarti("Kanal profilleri", "İçerikler kanala göre otomatik gruplanacak.", "Temel hazır") }
                item { BilgiKarti("Uygulama içi oynatma", "İzleme ilerlemesi cihazda tutulacak.", "Sırada") }
                item { BilgiKarti("Şifreli kütüphane", "Medya yalnızca uygulama içinde çözülecek.", "Sırada") }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun UstAlan(platformAdi: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HizliOynatIkonu()
        Column {
            Text("YT İndirici", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("$platformAdi • Tamamen Türkçe", color = IkincilMetin, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BaglantiKarti(
    baglanti: String,
    baglantiDegisti: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KartZemini),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bağlantıyı yapıştır", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Çözünürlük, FPS, kodek, ses biçimi ve tahmini boyut otomatik çıkarılacak.",
                color = IkincilMetin,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = baglanti,
                onValueChange = baglantiDegisti,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = {},
                enabled = baglanti.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("İçeriği analiz et", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HizDurumuKarti() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10251A)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(12.dp).background(Basari, RoundedCornerShape(100.dp)))
                Text("Turbo motor hazır", fontWeight = FontWeight.Bold)
            }
            Text("Bağlantıya göre 4–16 eşzamanlı parça seçilecek.", color = Color(0xFFB7D7C2), fontSize = 13.sp)
            Text("Varsayılan hız profili: 12×", color = Basari, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DepolamaKarti(
    platform: PlatformBilgisi,
    diskler: List<DepolamaHedefi>,
    seciliDiskYolu: String?,
    diskSecildi: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KartZemini),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Depolama", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(platform.depolamaAciklamasi, color = IkincilMetin, fontSize = 13.sp)

            if (platform.diskSecimiDestekleniyor) {
                if (diskler.isEmpty()) {
                    Text("Yazılabilir disk bulunamadı.", color = Kirmizi, fontWeight = FontWeight.Bold)
                }
                diskler.forEach { disk ->
                    DiskKarti(
                        disk = disk,
                        secili = seciliDiskYolu == disk.kokYolu,
                        secildi = { diskSecildi(disk.kokYolu) },
                    )
                }
            } else {
                Text(
                    "Dosyalar diğer uygulamaların erişemediği uygulamaya özel alanda tutulacak.",
                    color = Basari,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DiskKarti(
    disk: DepolamaHedefi,
    secili: Boolean,
    secildi: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = secildi),
        colors = CardDefaults.cardColors(
            containerColor = if (secili) Color(0xFF3A1B1B) else Color(0xFF20242D),
        ),
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(disk.gorunenAd, fontWeight = FontWeight.Bold)
            Text(
                "${disk.kullanilabilirBayt.okunabilirBoyut()} boş / ${disk.toplamBayt.okunabilirBoyut()}",
                color = IkincilMetin,
                fontSize = 12.sp,
            )
            Text(
                when {
                    secili -> "Seçildi"
                    disk.onerilen -> "Önerilen disk"
                    else -> "Seçmek için dokun"
                },
                color = if (secili || disk.onerilen) Kirmizi else IkincilMetin,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BilgiKarti(baslik: String, aciklama: String, durum: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KartZemini),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(Kirmizi, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(baslik.take(1), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            }
            Text(baslik, fontWeight = FontWeight.Bold)
            Text(aciklama, color = IkincilMetin, fontSize = 12.sp)
            Text(durum, color = Kirmizi, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HizliOynatIkonu() {
    Canvas(Modifier.size(52.dp)) {
        drawRoundRect(Kirmizi, cornerRadius = CornerRadius(size.width * .25f, size.height * .25f))
        drawPath(
            path = Path().apply {
                moveTo(size.width * .34f, size.height * .25f)
                lineTo(size.width * .72f, size.height * .50f)
                lineTo(size.width * .34f, size.height * .75f)
                close()
            },
            color = Color.White,
        )
        drawPath(
            path = Path().apply {
                moveTo(size.width * .60f, size.height * .15f)
                lineTo(size.width * .49f, size.height * .43f)
                lineTo(size.width * .61f, size.height * .43f)
                lineTo(size.width * .50f, size.height * .72f)
                lineTo(size.width * .74f, size.height * .38f)
                lineTo(size.width * .62f, size.height * .38f)
                close()
            },
            color = Color(0xFFFFD54F),
        )
    }
}

private fun uygulamaRenkleri(): ColorScheme = darkColorScheme(
    primary = Kirmizi,
    onPrimary = Color.White,
    background = KoyuZemin,
    surface = KartZemini,
    onBackground = Color.White,
    onSurface = Color.White,
)
