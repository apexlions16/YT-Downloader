package tr.com.apexlions.ytdownloader.desktop

object SurumBilgisi {
    private val ozellikler by lazy {
        java.util.Properties().apply {
            SurumBilgisi::class.java.getResourceAsStream("/surum.properties")?.use(::load)
        }
    }

    val developerSurumu: Boolean
        get() = ozellikler.getProperty("developerSurumu", "false").toBoolean()

    val uygulamaAdi: String
        get() = if (developerSurumu) "BPC Developer" else "BPC"

    const val surum: String = "0.3.0"
}