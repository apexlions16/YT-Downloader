package tr.com.apexlions.ytdownloader.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

class DiskSecimServisiTest {
    @Test
    fun enAzBirDiskGorur() {
        assertTrue(DiskSecimServisi().diskleriListele().isNotEmpty())
    }
}
