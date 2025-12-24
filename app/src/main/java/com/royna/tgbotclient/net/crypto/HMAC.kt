package com.royna.tgbotclient.net.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.royna.tgbotclient.net.data.Packet.Companion.hexdump

object HMAC {
    private const val ALGORITHM = "HmacSHA256"
    const val SHA256_LENGTH = 32

    class ComputeBuilder (key: ByteArray) : ISequenceData<Unit, ByteArray> {
        private var engine = Mac.getInstance(ALGORITHM)

        /**
         * Initializes the Mac engine. Must be called first.
         * Calling this again will reset the engine.
         */
        init {
            engine.init(SecretKeySpec(key, ALGORITHM))
        }

        /**
         * Adds partial data to the HMAC computation.
         * Throws IllegalStateException if secretKey() has not been called yet.
         */
        override fun update(data: ByteArray, inputOffset: Int, inputLength: Int) {
            engine.update(data, inputOffset, inputLength)
        }

        override fun update(data: ByteArray) {
            engine.update(data)
        }

        override fun done(): ByteArray {
            return engine.doFinal()
        }
    }

    class CompareBuilder (key: ByteArray, private val expected: ByteArray) : ISequenceData<Unit, Boolean> {
        private val mComputeBuilder = ComputeBuilder(key)

        override fun update(
            data: ByteArray,
            inputOffset: Int,
            inputLength: Int
        ) {
            mComputeBuilder.update(data, inputOffset, inputLength)
        }

        override fun update(data: ByteArray) {
            mComputeBuilder.update(data)
        }

        override fun done(): Boolean {
            val computed = mComputeBuilder.done()
            return MessageDigest.isEqual(computed, expected).also {
                if (!it) {
                    computed.hexdump("computed")
                    expected.hexdump("expected")
                }
            }
        }
    }
}