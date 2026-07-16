package tr.com.apexlions.ytdownloader.desktop

object SurumBilgisi {
    val developerSurumu: Boolean
        get() = System.getProperty("bpc.developerSurumu", "false").toBoolean()

    val uygulamaAdi: String
        get() = if (developerSurumu) "BPC Developer" else "BPC"

    const val surum: String = "0.3.0"
}