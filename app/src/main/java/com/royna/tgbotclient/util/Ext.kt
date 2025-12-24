package com.royna.tgbotclient.util

object Ext {
    fun Int.toByteArray(): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (this shr 24).toByte()
        bytes[1] = (this shr 16).toByte()
        bytes[2] = (this shr 8).toByte()
        bytes[3] = this.toByte()
        return bytes
    }

    fun Long.toByteArray(): ByteArray {
        val bytes = ByteArray(8)
        bytes[0] = (this shr 56).toByte()
        bytes[1] = (this shr 48).toByte()
        bytes[2] = (this shr 40).toByte()
        bytes[3] = (this shr 32).toByte()
        bytes[4] = (this shr 24).toByte()
        bytes[5] = (this shr 16).toByte()
        bytes[6] = (this shr 8).toByte()
        bytes[7] = this.toByte()
        return bytes
    }

    fun Short.toByteArray(): ByteArray {
        val bytes = ByteArray(2)
        bytes[0] = (this.toInt() shr 8).toByte()
        bytes[1] = this.toByte()
        return bytes
    }

    fun Byte.toByteArray(): ByteArray {
        val bytes = ByteArray(1)
        bytes[0] = this
        return bytes
    }
}