package tr.com.apexlions.ytdownloader.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BoyutBicimlendiriciTest {
    @Test
    fun gigabaytDegeriniTurkceBicimlendirir() {
        assertEquals("1.0 GB", 1_073_741_824L.okunabilirBoyut())
    }
}
