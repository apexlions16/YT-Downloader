package tr.com.apexlions.ytdownloader.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SifreliMedyaDeposu {
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
                    kaynak.copyTo(sifreliCikti, DEFAULT_BUFFER_SIZE * 16)
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
                    acikGirdi.copyTo(hedef, DEFAULT_BUFFER_SIZE * 16)
                }
            }
        }
    }

    private fun anahtariAlVeyaOlustur(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ANAHTAR_TAKMA_ADI, null) as? SecretKey)?.let { return it }

        val uretici = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        uretici.init(
            KeyGenParameterSpec.Builder(
                ANAHTAR_TAKMA_ADI,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return uretici.generateKey()
    }

    companion object {
        private const val ANAHTAR_TAKMA_ADI = "yt_indirici_medya_anahtari_v1"
        private const val DONUSUM = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x59, 0x54, 0x44, 0x4D, 0x01)
    }
}
