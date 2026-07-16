package tr.com.apexlions.ytdownloader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsMotorKurucusuTest {
    @Test
    fun varsayilanMotorDiziniBpcYoluylaBiter() {
        val yol = WindowsMotorKurucusu.varsayilanMotorDizini()
        assertEquals("motor", yol.fileName.toString())
        assertTrue(yol.toString().contains("BPC"))
    }
}