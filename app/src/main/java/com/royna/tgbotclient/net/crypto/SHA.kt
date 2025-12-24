package com.royna.tgbotclient.net.crypto

import java.security.MessageDigest

class SHA : ISequenceData<Unit, SHA.SHAResult> {
    private val sha256 = MessageDigest.getInstance("SHA-256")

    data class SHAResult(val hash: ByteArray) {
        override fun toString(): String {
            // Convert the byte array to a hexadecimal string
            val hexString = StringBuilder()
            for (b in hash) {
                // Mask to ensure non-negative values
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) {
                    hexString.append('0') // Add leading zero if needed
                }
                hexString.append(hex)
            }
            return hexString.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SHAResult

            return hash.contentEquals(other.hash)
        }

        override fun hashCode(): Int {
            return hash.contentHashCode()
        }
    }


    override fun update(data: ByteArray) {
        sha256.update(data)
    }

    override fun update(data: ByteArray, inputOffset: Int, inputLength: Int) {
        sha256.update(data, inputOffset, inputLength)
    }

    override fun done(): SHAResult {
        return SHAResult(sha256.digest())
    }
}