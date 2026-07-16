package tr.com.apexlions.ytdownloader.util

import kotlin.math.round

fun Long.okunabilirBoyut(): String {
    if (this < 0) return "Bilinmiyor"
    if (this < 1_024) return "$this B"

    val birimler = listOf("KB", "MB", "GB", "TB")
    var deger = this.toDouble()
    var birim = -1

    while (deger >= 1_024 && birim < birimler.lastIndex) {
        deger /= 1_024
        birim++
    }

    val yuvarlanmis = round(deger * 10) / 10
    return "${yuvarlanmis} ${birimler[birim]}"
}
