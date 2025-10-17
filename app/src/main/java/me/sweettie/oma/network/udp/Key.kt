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
    private val secureRandom = SecureRandom()

    fun encrypt(packet: ByteArray): ByteArray {
        val nonce = ByteArray(gcmSpecNonceSize)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encrypted = cipher.doFinal(packet)
        return nonce + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > gcmSpecNonceSize) { "Data too short" }
        val nonce = data.copyOfRange(0, gcmSpecNonceSize)
        val cipherText = data.copyOfRange(gcmSpecNonceSize, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(cipherText)
    }
}