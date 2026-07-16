package tr.com.apexlions.ytdownloader.desktop

/**
 * Kotlin Sequence üzerinde son elemanları güvenli biçimde almak için küçük uyumluluk uzantısı.
 */
internal fun <T> Sequence<T>.takeLast(adet: Int): List<T> = toList().takeLast(adet)
