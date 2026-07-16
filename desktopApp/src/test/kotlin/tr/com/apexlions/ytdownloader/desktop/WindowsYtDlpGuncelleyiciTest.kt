package tr.com.apexlions.ytdownloader.desktop

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsYtDlpGuncelleyiciTest {
    @Test
    fun resmiToplamListesindenWindowsOzetiniAyiklar() {
        val beklenen = "a".repeat(64)
        val liste = "${"b".repeat(64)}  yt-dlp\n$beklenen  yt-dlp.exe\n"
        val guncelleyici = WindowsYtDlpGuncelleyici(createTempDirectory())

        assertEquals(beklenen, guncelleyici.beklenenSha256(liste, "yt-dlp.exe"))
    }
}
