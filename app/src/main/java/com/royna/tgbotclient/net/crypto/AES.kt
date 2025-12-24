package com.royna.tgbotclient.net.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {
    // Tag length of AES/GCM in bits.
    const val GCM_TAG_BITS = 16 * 8
    // Tag length of AES/GCM in bytes.
    const val GCM_TAG_BYTES = 16

    // Init vector length of AES/GCM in bytes.
    const val GCM_IV_LENGTH = 12

    const val ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Encrypts the given data using AES/GCM with the given secret key and IV.
     * @param opmode The operation mode to use. (One of Cipher.XXX_MODE)
     * @param secretKey The secret key to use for encryption.
     * @param iv The initialization vector to use for encryption.
     */
    open class BaseBuilder (opmode: Int, key: ByteArray, iv : ByteArray) : ISequenceData<ByteArray, ByteArray?> {
        private val cipher = Cipher.getInstance(ALGORITHM)
        private val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)

        init {
            cipher.init(opmode, SecretKeySpec(key, "AES"), gcmSpec)
        }

        override fun update(data: ByteArray, inputOffset: Int, inputLength : Int): ByteArray {
            data.verifyInputData()
            return cipher.update(data, inputOffset, inputLength)
        }

        override fun update(data: ByteArray): ByteArray {
            data.verifyInputData()
            return cipher.update(data)
        }

        override fun done(): ByteArray? = cipher.doFinal()
    }

    class EncryptBuilder(key: ByteArray, iv : ByteArray)
        : BaseBuilder(Cipher.ENCRYPT_MODE, key, iv)

    class DecryptBuilder(key: ByteArray, iv : ByteArray)
        : BaseBuilder(Cipher.DECRYPT_MODE, key, iv)

    // Generate a random IV
    fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }
}