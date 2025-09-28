package me.sweettie.oma.network.udp

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Key(
    val source: ByteArray,
    val gcmSpecNonceSize: Int = 12
) {
    private val secretKey: SecretKey = SecretKeySpec(source, "AES")
    private val cipherEncrypt = Cipher.getInstance("AES/GCM/NoPadding")
    private val cipherDecrypt = Cipher.getInstance("AES/GCM/NoPadding")

    private val secureRandom = SecureRandom()

    fun encrypt(packet: ByteArray): ByteArray {
        val nonce = ByteArray(gcmSpecNonceSize)
        secureRandom.nextBytes(nonce)
        val spec = GCMParameterSpec(128, nonce)
        cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encrypted = cipherEncrypt.doFinal(packet)
        return nonce + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > gcmSpecNonceSize) { "Data too short" }
        val nonce = data.copyOfRange(0, gcmSpecNonceSize)
        val cipherText = data.copyOfRange(gcmSpecNonceSize, data.size)
        val spec = GCMParameterSpec(128, nonce)
        cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipherDecrypt.doFinal(cipherText)
    }
}