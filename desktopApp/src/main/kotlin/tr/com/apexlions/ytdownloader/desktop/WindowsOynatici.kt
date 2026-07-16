package tr.com.apexlions.ytdownloader.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities

object WindowsOynatici {
    fun oynat(baslik: String, dosya: File) {
        SwingUtilities.invokeLater {
            val panel = JFXPanel()
            val pencere = JFrame("YT İndirici • $baslik").apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                layout = BorderLayout()
                add(panel, BorderLayout.CENTER)
                minimumSize = Dimension(900, 560)
                setSize(1100, 680)
                setLocationRelativeTo(null)
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent?) {
                        dosya.delete()
                    }
                })
                isVisible = true
            }

            Platform.runLater {
                val medya = Media(dosya.toURI().toString())
                val oynatici = MediaPlayer(medya)
                val goruntu = MediaView(oynatici).apply { isPreserveRatio = true }
                val oynatDugmesi = Button("Duraklat")
                val zaman = Slider(0.0, 100.0, 0.0).apply { isDisable = true }
                val sureMetni = Label("00:00 / 00:00")
                var kullaniciSurukluyor = false

                oynatDugmesi.setOnAction {
                    if (oynatici.status == MediaPlayer.Status.PLAYING) {
                        oynatici.pause()
                        oynatDugmesi.text = "Oynat"
                    } else {
                        oynatici.play()
                        oynatDugmesi.text = "Duraklat"
                    }
                }
                zaman.setOnMousePressed { kullaniciSurukluyor = true }
                zaman.setOnMouseReleased {
                    kullaniciSurukluyor = false
                    oynatici.seek(Duration.seconds(zaman.value))
                }

                oynatici.setOnReady {
                    zaman.max = oynatici.totalDuration.toSeconds().coerceAtLeast(0.0)
                    zaman.isDisable = false
                    oynatici.play()
                }
                oynatici.currentTimeProperty().addListener { _, _, yeni ->
                    if (!kullaniciSurukluyor) zaman.value = yeni.toSeconds()
                    sureMetni.text = "${sure(yeni)} / ${sure(oynatici.totalDuration)}"
                }
                oynatici.setOnEndOfMedia {
                    oynatDugmesi.text = "Tekrar oynat"
                    oynatici.seek(Duration.ZERO)
                    oynatici.pause()
                }
                oynatici.setOnError {
                    sureMetni.text = "Oynatma hatası: ${oynatici.error?.message.orEmpty()}"
                }

                val kontroller = HBox(10.0, oynatDugmesi, zaman, sureMetni).apply {
                    alignment = Pos.CENTER
                    padding = Insets(10.0)
                    HBox.setHgrow(zaman, javafx.scene.layout.Priority.ALWAYS)
                }
                val kok = BorderPane().apply {
                    style = "-fx-background-color: #0d0f14;"
                    center = goruntu
                    bottom = kontroller
                }
                val sahne = Scene(kok, 1000.0, 620.0)
                goruntu.fitWidthProperty().bind(sahne.widthProperty())
                goruntu.fitHeightProperty().bind(sahne.heightProperty().subtract(80.0))
                panel.scene = sahne

                pencere.addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent?) {
                        oynatici.stop()
                        oynatici.dispose()
                    }
                })
            }
        }
    }

    private fun sure(sure: Duration?): String {
        if (sure == null || sure.isUnknown || sure.isIndefinite) return "00:00"
        val toplam = sure.toSeconds().toLong().coerceAtLeast(0)
        val saat = toplam / 3600
        val dakika = (toplam % 3600) / 60
        val saniye = toplam % 60
        return if (saat > 0) "%d:%02d:%02d".format(saat, dakika, saniye)
        else "%02d:%02d".format(dakika, saniye)
    }
}
