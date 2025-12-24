package com.royna.tgbotclient.net.io

import com.royna.tgbotclient.net.crypto.HMAC
import java.nio.ByteBuffer

class HMACIOAdapter(private val mHMAC : HMAC.ComputeBuilder) : IWriteChannel {

    override suspend fun putLong(data: Long) {
        mHMAC.update(ByteBuffer.allocate(8).putLong(data).array())
    }

    override suspend fun putInt(data: Int) {
        mHMAC.update(ByteBuffer.allocate(4).putInt(data).array())
    }

    override suspend fun putShort(data: Short) {
        mHMAC.update(ByteBuffer.allocate(2).putShort(data).array())
    }

    override suspend fun putByte(data: Byte) {
        mHMAC.update(ByteBuffer.allocate(1).put(data).array())
    }

    override suspend fun putString(data: String) {
        mHMAC.update(data.toByteArray())
    }

    override suspend fun put(data: ByteArray) {
        mHMAC.update(data)
    }
}