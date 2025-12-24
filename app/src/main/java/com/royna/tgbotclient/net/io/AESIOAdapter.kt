package com.royna.tgbotclient.net.io

import com.royna.tgbotclient.net.crypto.AES
import com.royna.tgbotclient.util.Ext.toByteArray
import com.royna.tgbotclient.util.Logging
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

object AESIOAdapter {
    class Decrypt(private val builder : AES.DecryptBuilder) : IWriteChannel {

        override suspend fun putLong(data: Long) {
            require(builder.update(data.toByteArray()).isEmpty())
        }

        override suspend fun putInt(data: Int) {
            require(builder.update(data.toByteArray()).isEmpty())
        }

        override suspend fun putShort(data: Short) {
            require(builder.update(data.toByteArray()).isEmpty())
        }

        override suspend fun putByte(data: Byte) {
            require(builder.update(data.toByteArray()).isEmpty())
        }

        override suspend fun putString(data: String) {
            require(builder.update(data.toByteArray()).isEmpty())
        }

        override suspend fun put(data: ByteArray) {
            require(builder.update(data).isEmpty())
        }
    }

    class Encrypt(private val builder : AES.EncryptBuilder, private val channel: IWriteChannel) : IWriteChannel {

        override suspend fun putLong(data: Long) {
            channel.put(builder.update(data.toByteArray()))
        }

        override suspend fun putInt(data: Int) {
            channel.put(builder.update(data.toByteArray()))
        }

        override suspend fun putShort(data: Short) {
            channel.put(builder.update(data.toByteArray()))
        }

        override suspend fun putByte(data: Byte) {
            channel.put(builder.update(data.toByteArray()))
        }

        override suspend fun putString(data: String) {
            channel.put(builder.update(data.toByteArray()))
        }

        override suspend fun put(data: ByteArray) {
            channel.put(builder.update(data))
        }

        suspend fun finish() {
            try {
                val encrypted = builder.done()!!
                channel.put(encrypted)
            } catch (e: Exception) {
                Logging.error("Failed to encrypt", e)
            }
        }
    }
}