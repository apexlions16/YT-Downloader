package tr.com.apexlions.ytdownloader.android

import com.fasterxml.jackson.databind.JsonNode
import tr.com.apexlions.ytdownloader.model.AltyaziSecenegi
import tr.com.apexlions.ytdownloader.model.AnalizSonucu
import tr.com.apexlions.ytdownloader.model.IndirmeSecenegi
import tr.com.apexlions.ytdownloader.model.IcerikTuru
import tr.com.apexlions.ytdownloader.model.SesParcasiSecenegi
import java.util.Locale
import java.util.UUID
import kotlin.math.max

object AndroidMetadataDonusturucu {
    fun analizSonucuOlustur(adres: String, kok: JsonNode): AnalizSonucu {
        val sure = kok.long("duration")
        return AnalizSonucu(
            kaynakAdresi = adres,
            medyaKimligi = kok.metin("id").ifBlank { UUID.randomUUID().toString() },
            baslik = kok.metin("title").ifBlank { "Başlıksız içerik" },
            aciklama = kok.metin("description").ifBlank { null },
            kanalKimligi = kok.metin("channel_id").ifBlank {
                kok.metin("uploader_id").ifBlank { "bilinmeyen-kanal" }
            },
            kanalAdi = kok.metin("channel").ifBlank {
                kok.metin("uploader").ifBlank { "Bilinmeyen kanal" }
            },
            kanalKullaniciAdi = kok.metin("uploader_id").ifBlank { null },
            kapakAdresi = kok.metin("thumbnail").ifBlank { null },
            sureSaniye = sure,
            yayinTarihi = kok.metin("upload_date").tarihBicimineCevir().ifBlank { null },
            secenekler = secenekleriOlustur(kok.path("formats"), sure),
            sesParcalari = sesParcalariOlustur(kok.path("formats")),
            altyazilar = altyazilariOlustur(kok.path("subtitles"), kok.path("automatic_captions")),
        )
    }

    private fun secenekleriOlustur(formatlar: JsonNode, sureSaniye: Long): List<IndirmeSecenegi> {
        val liste = formatlar.takeIf(JsonNode::isArray)?.toList().orEmpty()
        val sesBoyutu = liste
            .filter { it.metin("acodec") !in setOf("", "none") && it.metin("vcodec") in setOf("", "none") }
            .maxOfOrNull { max(it.long("filesize"), it.long("filesize_approx")) }
            ?: 0L

        val videoSecenekleri = liste
            .filter { it.int("height") > 0 && it.metin("vcodec") !in setOf("", "none") }
            .groupBy { it.int("height") to it.int("fps") }
            .mapNotNull { (_, adaylar) ->
                val secilen = adaylar.maxByOrNull {
                    max(max(it.long("filesize"), it.long("filesize_approx")), it.long("tbr"))
                } ?: return@mapNotNull null
                val formatKimligi = secilen.metin("format_id")
                if (formatKimligi.isBlank()) return@mapNotNull null
                val yukseklik = secilen.int("height")
                val fps = secilen.int("fps").takeIf { it > 0 }
                val vcodec = secilen.metin("vcodec")
                val hedef = if (
                    secilen.metin("ext") == "mp4" &&
                    (vcodec.startsWith("avc") || vcodec.startsWith("h264") || vcodec.startsWith("av01"))
                ) "mp4" else "mkv"
                val boyut = max(secilen.long("filesize"), secilen.long("filesize_approx"))
                    .takeIf { it > 0 }
                    ?.plus(sesBoyutu)

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
                    videoFormatKimligi = formatKimligi,
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
            videoFormatKimligi = "bestvideo",
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

    private fun sesParcalariOlustur(formatlar: JsonNode): List<SesParcasiSecenegi> {
        val liste = formatlar.takeIf(JsonNode::isArray)?.toList().orEmpty()
        return liste
            .filter { it.metin("acodec") !in setOf("", "none") && it.metin("vcodec") in setOf("", "none") }
            .groupBy {
                it.metin("language").ifBlank {
                    it.metin("format_note").substringBefore("-").trim().ifBlank { "varsayilan" }
                }
            }
            .mapNotNull { (dil, adaylar) ->
                val secilen = adaylar.maxByOrNull {
                    max(max(it.long("abr"), it.long("tbr")), max(it.long("filesize"), it.long("filesize_approx")))
                } ?: return@mapNotNull null
                val kimlik = secilen.metin("format_id")
                if (kimlik.isBlank()) return@mapNotNull null
                val varsayilan = secilen.int("language_preference") > -1 ||
                    secilen.metin("format_note").contains("default", ignoreCase = true) ||
                    secilen.metin("format_note").contains("original", ignoreCase = true)
                val dilKodu = dil.takeUnless { it == "varsayilan" }
                val gorunenDil = dilKodu?.let(::dilAdi) ?: "Varsayılan ses"
                val not = secilen.metin("format_note")
                SesParcasiSecenegi(
                    formatKimligi = kimlik,
                    gorunenAd = if (not.isBlank() || not.equals(gorunenDil, true)) gorunenDil else "$gorunenDil • $not",
                    dilKodu = dilKodu,
                    kodek = secilen.metin("acodec").ifBlank { null },
                    bitHiziKbps = secilen.int("abr").takeIf { it > 0 },
                    varsayilan = varsayilan,
                )
            }
            .sortedWith(compareByDescending<SesParcasiSecenegi> { it.varsayilan }.thenBy { it.gorunenAd })
    }

    private fun altyazilariOlustur(normal: JsonNode, otomatik: JsonNode): List<AltyaziSecenegi> {
        val normalDiller = normal.takeIf(JsonNode::isObject)?.fieldNames()?.asSequence()?.toSet().orEmpty()
        val otomatikDiller = otomatik.takeIf(JsonNode::isObject)?.fieldNames()?.asSequence()?.toSet().orEmpty()
        val gecersiz = setOf("live_chat")
        val normalListe = normalDiller
            .filterNot { it in gecersiz }
            .map { AltyaziSecenegi(it, dilAdi(it), otomatik = false) }
        val otomatikListe = otomatikDiller
            .filterNot { it in gecersiz || it in normalDiller }
            .map { AltyaziSecenegi(it, "${dilAdi(it)} • Otomatik", otomatik = true) }
        return (normalListe + otomatikListe).sortedBy { it.gorunenAd }
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

    private fun JsonNode.metin(ad: String): String = path(ad).takeUnless { it.isMissingNode || it.isNull }?.asText().orEmpty()
    private fun JsonNode.int(ad: String): Int = path(ad).takeUnless { it.isMissingNode || it.isNull }?.asDouble()?.toInt() ?: 0
    private fun JsonNode.long(ad: String): Long = path(ad).takeUnless { it.isMissingNode || it.isNull }?.asDouble()?.toLong() ?: 0L

    private fun String.tarihBicimineCevir(): String =
        if (length == 8 && all(Char::isDigit)) "${substring(0, 4)}-${substring(4, 6)}-${substring(6, 8)}" else this

    private fun dilAdi(kod: String): String = runCatching {
        Locale.forLanguageTag(kod).getDisplayLanguage(Locale("tr")).ifBlank { kod }
    }.getOrDefault(kod)
}