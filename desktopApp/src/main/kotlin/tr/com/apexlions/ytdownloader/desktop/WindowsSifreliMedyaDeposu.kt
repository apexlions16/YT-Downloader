package tr.com.apexlions.ytdownloader.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WindowsSifreliMedyaDeposu(
    private val kutuphaneDizini: File,
) {
    private val anahtar: SecretKey by lazy { anahtariAlVeyaOlustur() }

    fun sifrele(girdi: File, cikti: File) {
        cikti.parentFile?.mkdirs()
        val cipher = Cipher.getInstance(DONUSUM)
        cipher.init(Cipher.ENCRYPT_MODE, anahtar)
        val iv = cipher.iv

        DataOutputStream(BufferedOutputStream(cikti.outputStream())).use { veri ->
            veri.write(MAGIC)
            veri.writeInt(iv.size)
            veri.write(iv)
            CipherOutputStream(veri, cipher).use { sifreliCikti ->
                BufferedInputStream(girdi.inputStream()).use { kaynak ->
                    kaynak.copyTo(sifreliCikti, DEFAULT_BUFFER_SIZE * 32)
                }
            }
        }
    }

    fun coz(girdi: File, cikti: File) {
        cikti.parentFile?.mkdirs()
        DataInputStream(BufferedInputStream(girdi.inputStream())).use { veri ->
            val magic = ByteArray(MAGIC.size)
            veri.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Geçersiz YT İndirici medya dosyası" }

            val ivUzunlugu = veri.readInt()
            require(ivUzunlugu in 12..32) { "Geçersiz şifreleme başlangıç değeri" }
            val iv = ByteArray(ivUzunlugu)
            veri.readFully(iv)

            val cipher = Cipher.getInstance(DONUSUM)
            cipher.init(Cipher.DECRYPT_MODE, anahtar, GCMParameterSpec(128, iv))
            CipherInputStream(veri, cipher).use { acikGirdi ->
                BufferedOutputStream(cikti.outputStream()).use { hedef ->
                    acikGirdi.copyTo(hedef, DEFAULT_BUFFER_SIZE * 32)
                }
            }
        }
    }

    private fun anahtariAlVeyaOlustur(): SecretKey {
        val sistemDizini = kutuphaneDizini.resolve(".sistem").apply { mkdirs() }
        val anahtarDosyasi = sistemDizini.resolve("anahtar.dpapi")

        val hamAnahtar = if (anahtarDosyasi.isFile) {
            Crypt32Util.cryptUnprotectData(anahtarDosyasi.readBytes())
        } else {
            ByteArray(32).also(SecureRandom()::nextBytes).also { yeniAnahtar ->
                val korunmus = Crypt32Util.cryptProtectData(yeniAnahtar)
                anahtarDosyasi.writeBytes(korunmus)
                anahtarDosyasi.setReadable(false, false)
                anahtarDosyasi.setReadable(true, true)
                anahtarDosyasi.setWritable(false, false)
                anahtarDosyasi.setWritable(true, true)
            }
        }
        require(hamAnahtar.size == 32) { "Şifreleme anahtarı geçersiz" }
        return SecretKeySpec(hamAnahtar, "AES")
    }

    companion object {
        private const val DONUSUM = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x59, 0x54, 0x44, 0x4D, 0x01)
    }
}
