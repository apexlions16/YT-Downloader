package tr.com.apexlions.ytdownloader.android

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidMetadataDonusturucuTest {
    private val mapper = ObjectMapper()

    @Test
    fun ayniCozunurluktaH264Mp4Av1denOnceSecilir() {
        val kok = mapper.readTree(
            """
            {
              "id": "deneme",
              "title": "Deneme videosu",
              "channel_id": "kanal",
              "channel": "Kanal",
              "duration": 120,
              "formats": [
                {
                  "format_id": "av1-1080",
                  "height": 1080,
                  "fps": 30,
                  "ext": "mp4",
                  "vcodec": "av01.0.08M.08",
                  "acodec": "none",
                  "filesize": 300000000
                },
                {
                  "format_id": "h264-1080",
                  "height": 1080,
                  "fps": 30,
                  "ext": "mp4",
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "filesize": 200000000
                },
                {
                  "format_id": "aac-tr",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "language": "tr",
                  "abr": 128,
                  "filesize": 2000000
                }
              ],
              "subtitles": {},
              "automatic_captions": {}
            }
            """.trimIndent(),
        )

        val sonuc = AndroidMetadataDonusturucu.analizSonucuOlustur("https://www.youtube.com/watch?v=deneme", kok)
        val secenek = sonuc.secenekler.first { it.yukseklik == 1080 && it.kareHizi == 30 }

        assertEquals("h264-1080", secenek.videoFormatKimligi)
        assertEquals("mp4", secenek.hedefUzanti)
        assertTrue(secenek.videoKodegi.orEmpty().startsWith("avc1"))
        assertTrue(secenek.ytDlpSecici.contains("bestaudio[ext=m4a]"))
    }

    @Test
    fun enHizliSecenekH264VeAacAkisiniOnceliklendirir() {
        val kok = mapper.readTree(
            """
            {
              "id": "deneme",
              "title": "Deneme videosu",
              "channel_id": "kanal",
              "channel": "Kanal",
              "formats": [],
              "subtitles": {},
              "automatic_captions": {}
            }
            """.trimIndent(),
        )

        val sonuc = AndroidMetadataDonusturucu.analizSonucuOlustur("https://youtu.be/deneme", kok)
        val secenek = sonuc.secenekler.first { it.kimlik == "video-en-hizli" }

        assertTrue(secenek.ytDlpSecici.contains("vcodec^=avc1"))
        assertTrue(secenek.ytDlpSecici.contains("acodec^=mp4a"))
        assertEquals("mp4", secenek.hedefUzanti)
    }
}
