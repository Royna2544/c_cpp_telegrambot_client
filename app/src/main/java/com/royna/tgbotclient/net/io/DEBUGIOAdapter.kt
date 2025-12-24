package com.royna.tgbotclient.net.io

import com.royna.tgbotclient.net.data.Packet.Companion.hexdump
import com.royna.tgbotclient.util.Ext.toByteArray
import java.io.ByteArrayOutputStream

class DEBUGIOAdapter : IWriteChannel {
    private val mImpl = ByteArrayOutputStream()

    override suspend fun putLong(data: Long) {
        mImpl.write(data.toByteArray())
    }

    override suspend fun putInt(data: Int) {
        mImpl.write(data)
    }

    override suspend fun putShort(data: Short) {
        mImpl.write(data.toByteArray())
    }

    override suspend fun putByte(data: Byte) {
        mImpl.write(data.toByteArray())
    }

    override suspend fun putString(data: String) {
        mImpl.write(data.toByteArray())
    }

    override suspend fun put(data: ByteArray) {
        mImpl.write(data)
    }

    fun dump(name: String) {
        mImpl.toByteArray().hexdump("DEBUGIOAdapter::$name")
    }
}